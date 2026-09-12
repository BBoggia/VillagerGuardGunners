#!/usr/bin/env python3
"""Run isolated Gradle validation stages sequentially and retain candidate evidence."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
GATES = [
    'verifyPhase8PerformanceReports', 'verifyVillageBuildingPerformanceReport',
    'verifyQuartermasterPerformanceReport', 'verifyMedicalPerformanceReport',
    'verifyQuartermasterServicePerformanceReport', 'verifyMedicAiPerformanceReport',
    'verifyTransportPerformanceReport',
]
STAGES = [
    ('correctness', ['check', 'runGameTestServer']),
    ('integration', ['runGameTestServer', '-PvillageIntegrationProbes=true']),
    ('performance', ['runGameTestServer', '-Pstep13Performance=true']),
    ('evidence-gates', GATES),
]
MANUAL = ['dedicated-server startup and two-client synchronization',
          'natural-terrain combined village/raid playthrough',
          'old-world restart and mixed-mod compatibility matrix',
          '400-guard and historical-village baseline comparison',
          'final public version, balance approval, and clean-checkout CI']


def stage_process_groups(pid):
    """Include Gradle's single-use daemon, which starts a separate process group."""
    listing = subprocess.check_output(['ps', '-axo', 'pid=,ppid=,pgid='], text=True, timeout=5)
    rows = [tuple(map(int, line.split())) for line in listing.splitlines() if line.strip()]
    descendants = {pid}
    while True:
        expanded = descendants | {child for child, parent, _ in rows if parent in descendants}
        if expanded == descendants:
            break
        descendants = expanded
    return {pid} | {group for child, _, group in rows
                    if child in descendants and group in descendants}


def execute_stage(command, root, stream, timeout=900):
    """Bound a stage and terminate only process groups descended from this invocation."""
    with subprocess.Popen(command, cwd=root, stdout=stream, stderr=subprocess.STDOUT,
                          start_new_session=True) as process:
        try:
            return process.wait(timeout=timeout)
        except BaseException:
            # Forge's child server must not survive a timed-out Gradle wrapper.
            try:
                groups = stage_process_groups(process.pid)
            except (OSError, subprocess.SubprocessError, ValueError) as error:
                groups = {process.pid}
                stream.write(f'\nCould not enumerate descendant groups; check for orphaned JVMs: {error}\n')
                stream.flush()
            for group in groups:
                try:
                    os.killpg(group, signal.SIGTERM)
                except ProcessLookupError:
                    pass
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                pass
            # The wrapper can exit before its descendants. Kill the remaining
            # owned group even when wait() has already reaped the wrapper.
            for group in groups:
                try:
                    os.killpg(group, signal.SIGKILL)
                except ProcessLookupError:
                    pass
            process.wait()
            raise


def retain_performance_reports(root, run_dir):
    for directory in sorted((root / 'build/reports').glob('*-performance')):
        shutil.copytree(directory, run_dir / directory.name, dirs_exist_ok=True)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_identity(root):
    files = [p for p in (root / 'src').rglob('*') if p.is_file()]
    files += [root / name for name in ('build.gradle', 'gradle.properties', 'settings.gradle',
                                      'tools/validate_village_expansion.py')]
    return {str(p.relative_to(root)): digest(p) for p in sorted(files) if p.is_file()}


def game_test_count(log):
    counts = re.findall(r'All (\d+) required tests passed', log)
    if len(counts) != 1 or int(counts[0]) <= 0:
        raise ValueError('Missing or ambiguous positive GameTest completion marker')
    if re.search(r'\b[1-9]\d* required tests failed\b|BUILD FAILED', log):
        raise ValueError('GameTest log also reports failure')
    return int(counts[0])


def junit_count(directory):
    files = sorted(directory.glob('TEST-*.xml'))
    if not files:
        raise ValueError('No JVM test results')
    total = 0
    for path in files:
        suite = ET.parse(path).getroot()
        if any(int(suite.get(k, '0')) != 0 for k in ('failures', 'errors', 'skipped')):
            raise ValueError(f'Nonpassing JVM results: {path.name}')
        count = int(suite.get('tests', '0'))
        if count < 0:
            raise ValueError('Negative test count')
        total += count
    if total == 0:
        raise ValueError('Empty JVM test run')
    return total


def verify(root, manifest_path):
    data = json.loads(manifest_path.read_text())
    if data.get('schema') != 1 or data.get('automated_status') != 'passed':
        raise ValueError('No completed automated run')
    if data['sources'] != source_identity(root):
        raise ValueError('Sources changed after validation; rerun the matrix')
    artifact = root / data['artifact']['path']
    if digest(artifact) != data['artifact']['sha256']:
        raise ValueError('Candidate JAR changed after validation')
    if [s['name'] for s in data['stages']] != [s[0] for s in STAGES]:
        raise ValueError('Incomplete stage sequence')
    base = manifest_path.parent
    for relative, expected in data['evidence'].items():
        path = (base / relative).resolve()
        if not path.is_relative_to(base.resolve()) or digest(path) != expected:
            raise ValueError(f'Changed or invalid evidence: {relative}')
    for stage in data['stages']:
        if stage['exit_code'] != 0:
            raise ValueError('Failed stage')
        log = (base / (stage['name'] + '.log')).read_text(errors='replace')
        if 'BUILD SUCCESSFUL' not in log or 'BUILD FAILED' in log:
            raise ValueError('Missing successful Gradle completion')
        if stage['name'] != 'evidence-gates' and game_test_count(log) != stage['tests']:
            raise ValueError('GameTest count mismatch')
    if junit_count(base / 'junit') != data['jvm_tests']:
        raise ValueError('JVM test count mismatch')
    if data.get('release_ready') is not False or data.get('remaining_release_checks') != MANUAL:
        raise ValueError('Automated evidence cannot certify manual release checks')
    return data


def run(root):
    output = root / 'build/reports/village-expansion'
    output.mkdir(parents=True, exist_ok=True)
    # All variants use shared generated resources and worlds. Exclude another runner.
    import fcntl
    with (output / '.lock').open('w') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise ValueError('Another village validation runner is active') from error
        stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:8]
        run_dir = output / stamp
        run_dir.mkdir()
        sources = source_identity(root)
        props = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines()
                     if '=' in line and not line.startswith('#'))
        artifact = root / 'build/libs' / f"{props['mod_id']}-{props['mod_version']}.jar"
        data = {'schema': 1, 'started_utc': stamp, 'automated_status': 'running',
                'release_ready': False, 'remaining_release_checks': MANUAL,
                'sources': sources, 'stages': [], 'java_home': os.environ.get('JAVA_HOME'),
                'versions': {k: v for k, v in props.items() if 'version' in k}}
        manifest = run_dir / 'manifest.json'
        def save():
            manifest.write_text(json.dumps(data, indent=2) + '\n')
        save()
        try:
            for name, tasks in STAGES:
                print(f'{name}: running; log {run_dir / (name + ".log")}', flush=True)
                command = ['./gradlew', '--offline', '--no-daemon', *tasks]
                # Force fresh JVM execution without recompiling every dependency.
                if name == 'correctness':
                    command.insert(3, 'cleanTest')
                stage = {'name': name, 'command': command, 'exit_code': None}
                data['stages'].append(stage)
                save()
                with (run_dir / (name + '.log')).open('w') as stream:
                    try:
                        stage['exit_code'] = execute_stage(command, root, stream)
                    except subprocess.TimeoutExpired:
                        stage['timed_out'] = True
                        raise ValueError(f'{name} exceeded the 900-second stage limit')
                    finally:
                        if name == 'performance':
                            retain_performance_reports(root, run_dir)
                save()
                log = (run_dir / (name + '.log')).read_text(errors='replace')
                if stage['exit_code'] or 'BUILD SUCCESSFUL' not in log or 'BUILD FAILED' in log:
                    raise ValueError(f'{name} failed; inspect its retained log')
                if name != 'evidence-gates':
                    stage['tests'] = game_test_count(log)
                if name == 'correctness':
                    data['artifact'] = {'path': str(artifact.relative_to(root)), 'sha256': digest(artifact)}
                    shutil.copytree(root / 'build/test-results/test', run_dir / 'junit',
                                    ignore=shutil.ignore_patterns('binary'))
                    data['jvm_tests'] = junit_count(run_dir / 'junit')
                if source_identity(root) != sources or digest(artifact) != data['artifact']['sha256']:
                    raise ValueError('Sources or artifact changed during the matrix')
                print(f'{name}: passed', flush=True)
                save()
            data['evidence'] = {str(p.relative_to(run_dir)): digest(p)
                                for p in sorted(run_dir.rglob('*')) if p.is_file() and p != manifest}
            data['automated_status'] = 'passed'
            save()
            verify(root, manifest)
            (output / 'latest-passed.txt').write_text(str(manifest.relative_to(output)) + '\n')
            print(f'Automated validation passed: {manifest}\nManual release checks remain pending.', flush=True)
        except BaseException as error:
            data['automated_status'] = 'failed'
            data['error'] = str(error)
            data['evidence'] = {str(p.relative_to(run_dir)): digest(p)
                                for p in sorted(run_dir.rglob('*')) if p.is_file() and p != manifest}
            save()
            raise
        return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify', type=Path, help='Verify a retained manifest against current sources and JAR')
    args = parser.parse_args()
    try:
        if args.verify:
            data = verify(ROOT, args.verify.resolve())
            print(f"Verified automated evidence for {data['artifact']['sha256']}; manual release checks remain pending.")
        else:
            run(ROOT)
    except (ValueError, OSError, KeyError, ET.ParseError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
