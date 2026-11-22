#!/usr/bin/env python3
"""Launch the disposable village defense playground; --verify runs an automatic smoke test."""
import argparse
from create_flat_sandbox import create
from datetime import datetime
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify', action='store_true')
    parser.add_argument('--pose-check', action='store_true', help='Capture resting guards on cots facing all four directions')
    parser.add_argument('--steve-model', action='store_true', help='Use the upstream Steve model in this disposable client')
    parser.add_argument('--one-cot', action='store_true', help='Exercise emergency field care with only one cot')
    args = parser.parse_args()
    root = ROOT / 'build/village-sandbox'
    world = root / 'saves/VillageDefensePlayground'
    if not (world / '.vgg-flat-world').is_file():
        source = ROOT / 'run-step13-performance/world/level.dat'
        if not source.is_file():
            sys.exit('First create version-matched world metadata with ./gradlew runGameTestServer -Pstep13Performance=true')
        if world.exists():
            world.rename(root / ('previous-template-' + datetime.now().strftime('%Y%m%d-%H%M%S')))
        create(source, world)
    root.mkdir(parents=True, exist_ok=True)
    for name in ('ready.json', 'verification.json', 'status.json', 'village-overview.png', 'verified-scene.png', 'cot-care.png', 'care-trace.json'):
        (root / name).unlink(missing_ok=True)
    config = root / 'config/guardvillagers-client.toml'
    config.parent.mkdir(parents=True, exist_ok=True)
    value = 'true' if args.steve_model else 'false'
    key = '"Have guards use the steve model?"'
    lines = config.read_text().splitlines() if config.exists() else []
    lines = [line for line in lines if not line.startswith(key)]
    config.write_text('\n'.join(lines + [key + ' = ' + value]) + '\n')
    for screenshot in root.glob('cot-*.png'):
        screenshot.unlink()
    for state in root.glob('cot-*.json'):
        state.unlink()
    command = ['./gradlew', '--offline', '--no-daemon', '-I', 'tools/village-sandbox.gradle', 'runClient']
    if args.pose_check:
        command.append('-PsandboxPoseCheck=true')
    if args.one_cot:
        command.append('-PsandboxOneCot=true')
    if args.verify:
        command.append('-PsandboxVerify=true')
    print('Village Defense Playground: /vggtest start, reset, wave, status, peaceful.\nLog: ' + str(root / 'client.log'), flush=True)
    with (root / 'client.log').open('w') as log:
        result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    if args.verify:
        import json
        report = root / 'verification.json'
        if not report.is_file():
            sys.exit('No verification result; inspect client.log')
        data = json.loads(report.read_text())
        print(json.dumps(data, indent=2))
        poses = True
        if args.pose_check:
            for direction in ('north','east','south','west'):
                view = root / ('cot-' + direction)
                try:
                    server = json.loads(Path(str(view) + '-server.json').read_text())
                    client = json.loads(Path(str(view) + '-client.json').read_text())
                    poses &= view.with_suffix('.png').is_file() and server['onCot'] and client['displacementChallenge'] and server['position'] == client['position'] and client['walkSpeeds'] == [0, 0, 0]
                except (OSError, ValueError, KeyError):
                    poses = False
        return 0 if result.returncode == 0 and data.get('passed') and (not args.pose_check or poses) else 1
    return result.returncode


if __name__ == '__main__':
    sys.exit(main())
