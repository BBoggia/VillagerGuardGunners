#!/usr/bin/env python3
"""Run the disposable integrated-client village care scenario after native tests."""
import json
from pathlib import Path
import subprocess
import sys
from validate_village_expansion import ROOT, digest, verify


def main():
    props = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines()
                 if '=' in line and not line.startswith('#'))
    artifact = ROOT / 'build/libs' / f"{props['mod_id']}-{props['mod_version']}.jar"
    if not artifact.is_file() or not (ROOT / 'run-step13-performance/world/level.dat').is_file():
        print('Run the automated village validation matrix first.', file=sys.stderr)
        return 1
    evidence_root = ROOT / 'build/reports/village-expansion'
    try:
        baseline = evidence_root / (evidence_root / 'latest-passed.txt').read_text().strip()
        verify(ROOT, baseline)
    except (OSError, ValueError, KeyError) as error:
        print(f'Current automated evidence is required: {error}', file=sys.stderr)
        return 1
    directory = ROOT / 'build/village-client-qa'
    directory.mkdir(parents=True, exist_ok=True)
    manifest = directory / 'manifest.json'
    # Never leave an earlier PASS manifest behind when a later launch fails.
    manifest.unlink(missing_ok=True)
    (directory / 'client-qa-result.txt').unlink(missing_ok=True)
    before = digest(artifact)
    command = ['./gradlew', '--offline', '--no-daemon', '-I', 'tools/village-client-qa.gradle', 'runClient']
    print(f'Launching isolated client; log: {directory / "client.log"}', flush=True)
    with (directory / 'client.log').open('w') as log:
        result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    result_path = directory / 'client-qa-result.txt'
    result_text = result_path.read_text() if result_path.is_file() else ''
    captures = [directory / f'client-transport-{state}.png' for state in ('downed', 'carrying', 'cot', 'recovered')]
    passed = (result.returncode == 0 and result_text.startswith('PASS:')
              and all(p.is_file() and p.stat().st_size > 0 for p in captures)
              and artifact.is_file() and digest(artifact) == before)
    try:
        verify(ROOT, baseline)
    except (OSError, ValueError, KeyError):
        passed = False
    evidence = [directory / 'client.log', *captures, result_path]
    harness = [Path(__file__), ROOT / 'tools/village-client-qa.gradle',
               *sorted((ROOT / 'tools/client-qa-src').rglob('*.java'))]
    data = {'schema': 1, 'status': 'passed' if passed else 'failed', 'command': command,
            'artifactSha256': before, 'result': result_text, 'exitCode': result.returncode,
            'evidence': {p.name: digest(p) for p in evidence if p.is_file()},
            'harness': {str(p.resolve().relative_to(ROOT)): digest(p) for p in harness},
            'scope': 'Integrated client; dedicated server and two-observer validation remain pending.'}
    manifest.write_text(json.dumps(data, indent=2) + '\n')
    print(result_text or 'No explicit client completion marker; inspect client.log.', flush=True)
    print(f'Evidence: {manifest}', flush=True)
    return 0 if passed else 1


if __name__ == '__main__':
    sys.exit(main())
