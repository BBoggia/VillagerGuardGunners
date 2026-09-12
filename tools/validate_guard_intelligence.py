#!/usr/bin/env python3
"""Run isolated combat observations or future acceptance checks and retain their identity."""
import argparse
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[1]
CONTRACTS = {
    'dry': {'noMeleePursuit', 'noOffensiveAdvance', 'purposefulWithdrawal'},
    'sidearm': {'keepUsableSidearmAcrossTargetReplacement'},
    'reload': {'noAdvanceIntoClosingGroupWhileReloading', 'createSeparationDuringVulnerableReload'},
    'wall': {'reachSafeSideOfWall'},
}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_identity(root):
    files = [p for folder in ('src', 'docs', 'tools', '.github', 'gradle')
             for p in (root / folder).rglob('*') if p.is_file()
             and p.name != '.DS_Store' and '__pycache__' not in p.parts]
    files += [root / name for name in ('build.gradle', 'settings.gradle', 'gradle.properties',
              'gradlew', 'gradlew.bat', 'AGENTS.md', 'README.md', 'CHANGELOG.md', '.gitignore')]
    return {str(p.relative_to(root)): digest(p) for p in sorted(set(files)) if p.is_file()}


def read_reports(directory, mode):
    """An observation pass proves fixture validity, never future AI acceptance."""
    results = {}
    if {p.stem for p in directory.glob('*.json')} != set(CONTRACTS):
        raise ValueError('Missing or unexpected combat scenario reports')
    for scenario, names in CONTRACTS.items():
        data = json.loads((directory / f'{scenario}.json').read_text())
        if data.get('schema') != 1 or data.get('scenario') != scenario or data.get('mode') != mode:
            raise ValueError(f'{scenario}: report identity/mode mismatch')
        if data.get('fixtureValid') is not True or data.get('fixtureFailure') is not None:
            raise ValueError(f'{scenario}: fixture did not complete validly: {data.get("fixtureFailure")}')
        samples = data.get('samples', [])
        if len(samples) != 180 or [row.get('tick') for row in samples] != list(range(1, 181)):
            raise ValueError(f'{scenario}: incomplete observation window')
        contracts = data.get('acceptance', {})
        if set(contracts) != names or any(type(value) is not bool for value in contracts.values()):
            raise ValueError(f'{scenario}: missing or invalid acceptance assertions')
        results[scenario] = {'metrics': data['metrics'], 'acceptance': contracts}
    return results


def verify_acceptance_failure(log, exit_code, observations):
    failed = sum(not all(row['acceptance'].values()) for row in observations.values())
    reported = re.findall(r'\b([1-4]) required tests failed\b', log)
    assertions = log.count('failed! Planned combat behavior is not implemented:')
    if exit_code == 0 or failed == 0 or reported != [str(failed)] or assertions != failed:
        raise ValueError('Runtime failure does not match the planned acceptance assertions')


def run(mode, offline):
    base = ROOT / 'build/reports/guard-intelligence'
    base.mkdir(parents=True, exist_ok=True)
    # Both modes share one disposable runtime and generated Gradle resources.
    with (base / '.lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        run_id = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:8]
        output = base / run_id
        reports = output / 'scenarios'
        reports.mkdir(parents=True)
        sources = source_identity(ROOT)
        manifest = {
            'schema': 1, 'mode': mode, 'run_id': run_id, 'status': 'running',
            'sources': sources,
            'git_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
            'git_status': subprocess.check_output(['git', 'status', '--porcelain=v1', '-uall'], cwd=ROOT, text=True).splitlines(),
            'gradle_properties': (ROOT / 'gradle.properties').read_text(),
            'java_home': os.environ.get('JAVA_HOME'),
            'preexisting_artifacts': {p.name: digest(p) for p in (ROOT / 'build/libs').glob('*.jar')},
        }
        path = output / 'manifest.json'
        path.write_text(json.dumps(manifest, indent=2) + '\n')
        command = ['./gradlew', 'reobfJar', 'runGameTestServer', '--console=plain',
                   f'-PguardIntelligenceTestMode={mode}', f'-PguardIntelligenceReportDir={reports}']
        if offline:
            command.append('--offline')
        print(f'Combat {mode} evidence: {output}', flush=True)
        with (output / 'gradle.log').open('w') as log:
            result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        manifest['command'] = command
        manifest['exit_code'] = result.returncode
        config = ROOT / 'build/guard-intelligence-runtime/config'
        if config.exists():
            shutil.copytree(config, output / 'runtime-config')
        manifest['artifacts'] = {p.name: digest(p) for p in (ROOT / 'build/libs').glob('*.jar')}
        try:
            if sources != source_identity(ROOT):
                raise ValueError('Sources changed during the run; evidence cannot identify one candidate')
            observations = read_reports(reports, mode)
            manifest['observations'] = observations
            accepted = all(all(row['acceptance'].values()) for row in observations.values())
            manifest['acceptance_passed'] = accepted
            log = (output / 'gradle.log').read_text(errors='replace')
            if mode == 'baseline' or accepted:
                if result.returncode != 0 or re.findall(r'All (\d+) required tests passed', log) != ['4']:
                    raise ValueError('Missing successful completion of exactly four runtime scenarios')
                manifest['status'] = 'observed' if mode == 'baseline' else 'accepted'
            else:
                verify_acceptance_failure(log, result.returncode, observations)
                manifest['status'] = 'acceptance_failed'
        except (ValueError, KeyError, TypeError) as error:
            manifest['status'] = 'invalid_evidence'
            manifest['error'] = str(error)
        manifest['evidence'] = {str(p.relative_to(output)): digest(p) for p in output.rglob('*')
                                if p.is_file() and p != path}
        path.write_text(json.dumps(manifest, indent=2) + '\n')
        print(json.dumps({'status': manifest['status'], 'manifest': str(path),
                          'acceptance_passed': manifest.get('acceptance_passed'),
                          'error': manifest.get('error')}, indent=2))
        return 0 if manifest['status'] in ('observed', 'accepted') else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mode', choices=('baseline', 'acceptance'), default='baseline')
    parser.add_argument('--offline', action='store_true')
    args = parser.parse_args()
    try:
        sys.exit(run(args.mode, args.offline))
    except BlockingIOError:
        sys.exit('Another combat validation runner is active')
