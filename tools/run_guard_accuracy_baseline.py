#!/usr/bin/env python3
"""Measure packaged guard combat in an isolated installed Forge client.

Build the observer separately with:
  ./gradlew -I tools/accuracy-qa.gradle reobfAccuracyProbeJar
This runner never builds or modifies the production mod JAR.
"""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import time
import zipfile

import create_flat_sandbox
import run_village_compatibility as qa

ROOT = Path(__file__).resolve().parents[1]


def run(args):
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    client = out / 'client'
    mods = client / 'mods'
    mods.mkdir(parents=True)
    expected = {'guardvillagers', 'tacz', 'fzzy_config', 'kotlinforforge'}
    providers = {}
    for path in args.mods.glob('*.jar'):
        found = {key for row in qa.jar_metadata(path) for key in row['mods']} & expected
        for key in found:
            if key in providers:
                raise ValueError('Ambiguous dependency: ' + key)
            providers[key] = str(path.resolve())
    if set(providers) != expected:
        raise ValueError('Missing required dependencies: ' + str(expected - set(providers)))
    providers['villagerguardgunners'] = str(args.jar.resolve())
    observer = ROOT / 'build/accuracy-qa/vgg-accuracy-probe.jar'
    with zipfile.ZipFile(args.jar) as jar:
        if any(name.startswith('accuracyprobe/') for name in jar.namelist()):
            raise ValueError('Production artifact contains observer classes')
    for path in [Path(p) for p in providers.values()] + [observer]:
        shutil.copy2(path, mods / path.name)
    # Existing launcher helper expects this legacy key solely for diagnostic properties.
    providers['vggcompatprobe'] = str(observer)
    create_flat_sandbox.create(args.template.resolve(), client / 'saves/Compatibility')
    (client / 'options.txt').write_text('renderDistance:3\nsimulationDistance:3\n'
            'pauseOnLostFocus:false\nmaxFps:60\nenableVsync:false\n'
            'onboardAccessibility:false\nautoJump:false\n')
    cfg = {'_base': str(ROOT), 'runtime': {
        'minecraftDirectory': str(args.minecraft.resolve()), 'java': str(args.java),
        'version': '1.20.1-forge-47.4.10', 'forgeVersion': '47.4.10'}}
    runtime = qa.runtime_info(cfg)
    command = qa.launch_arguments(runtime, {'providers': providers}, {}, out)
    command = [a for a in command if not a.startswith('-Dvgg.compat.')]
    command[1:1] = ['-Dvgg.accuracy.dir=' + str(out), '-Dvgg.accuracy.trials=' + str(args.trials)]
    command[1:1] = ['-Dvgg.accuracy.error=' + str(args.aim_error), '-Dvgg.accuracy.point=' + args.aim_point]
    if args.player_error is not None:
        command.insert(1, '-Dvgg.accuracy.playerError=' + str(args.player_error))
    jars_before = {p.name: qa.sha(p) for p in mods.glob('*.jar')}
    harness = [Path(__file__), ROOT / 'tools/accuracy-qa.gradle',
               *sorted((ROOT / 'tools/accuracy-qa-src').rglob('*.java')),
               *sorted((ROOT / 'tools/accuracy-qa-resources').rglob('*'))]
    harness = {str(p.relative_to(ROOT)): qa.sha(p) for p in harness if p.is_file()}
    manifest = {'scope': 'Packaged integrated client; real survival player; scripted arena',
                'command': command, 'jars': jars_before, 'runtime': runtime,
                'harness': harness, 'templateSha256': qa.sha(args.template), 'trials': args.trials}
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print('Starting packaged accuracy baseline: ' + str(out), flush=True)
    with (out / 'client.log').open('w') as log:
        process = subprocess.Popen(command, cwd=client, stdout=log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + args.timeout
        try:
            while process.poll() is None and time.monotonic() < deadline:
                time.sleep(1)
            if process.poll() is None:
                process.terminate()
            code = process.wait(timeout=20)
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
    result_file = out / 'complete.json'
    result = json.loads(result_file.read_text()) if result_file.exists() else {}
    unchanged = jars_before == {p.name: qa.sha(p) for p in mods.glob('*.jar')}
    manifest.update({'exitCode': code, 'jarsUnchanged': unchanged,
                     'completed': result.get('completed', False),
                     'expectedEncounters': args.trials * 20,
                     'observedEncounters': result.get('encounters', 0)})
    passed = code == 0 and unchanged and result.get('completed') and result.get('encounters') == args.trials * 20
    manifest['passed'] = bool(passed)
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps({k: manifest[k] for k in ('passed', 'exitCode', 'completed', 'observedEncounters')}), flush=True)
    return 0 if passed else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--mods', type=Path, required=True, help='Folder containing the four required dependency JARs')
    parser.add_argument('--template', type=Path, required=True, help='Existing level.dat; only metadata is copied into a new flat world')
    parser.add_argument('--output', type=Path, required=True, help='New isolated output directory')
    parser.add_argument('--aim-error', type=float, default=0)
    parser.add_argument('--aim-point', choices=['EYE_LEVEL', 'CENTER_MASS'], default='EYE_LEVEL')
    parser.add_argument('--player-error', type=float, help='Enable the player-only override with this error')
    parser.add_argument('--trials', type=int, default=3)
    parser.add_argument('--timeout', type=int, default=900)
    parser.add_argument('--minecraft', type=Path, default=Path.home() / 'Library/Application Support/minecraft')
    parser.add_argument('--java', type=Path, default=Path('/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java'))
    args = parser.parse_args()
    import math
    if any(not math.isfinite(v) or v < 0 or v > 10 for v in [args.aim_error, args.player_error if args.player_error is not None else 0]):
        parser.error('Aim errors must be finite values from 0 to 10 degrees')
    if args.trials < 1 or args.trials > 20:
        parser.error('--trials must be between 1 and 20')
    with qa.workload_lock():
        qa.clean_environment()
        return run(args)


if __name__ == '__main__':
    raise SystemExit(main())
