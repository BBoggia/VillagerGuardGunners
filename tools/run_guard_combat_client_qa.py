#!/usr/bin/env python3
"""Observe native combat in an isolated integrated client using exported dev launches."""
import json
import os
from pathlib import Path
import gzip
import io
from create_flat_sandbox import read_payload, write_payload
import subprocess
from datetime import datetime, timezone
from validate_village_expansion import ROOT, digest, source_identity


def main():
    root = ROOT / 'build/combat-client-qa'
    export = root / 'launch-export'
    export.mkdir(parents=True, exist_ok=True)
    # The export-only client exits after loading the harness. No server or EULA mutation.
    (export / 'server-result.txt').write_text('EXPORT ONLY\n')
    command = ['./gradlew', '--offline', '-I', 'tools/combat-client-qa.gradle',
               '-PcaptureCombatLaunch=true', 'reobfJar', 'runClient']
    with (export / 'prepare.log').open('w') as log:
        subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=180)
    launch = json.loads((root / 'runClient.json').read_text())
    directory = root / datetime.now(timezone.utc).strftime('integrated-%Y%m%dT%H%M%SZ')
    client = directory / 'client'
    world = client / 'saves/CombatNatural'
    stream = io.BytesIO(gzip.decompress((ROOT / 'run-step13-performance/world/level.dat').read_bytes()))
    if stream.read(1) != b'\x0a':
        raise ValueError('Expected a compound level.dat')
    name = read_payload(stream, 8)
    metadata = read_payload(stream, 10)
    data = metadata['Data'][1]
    generation = data['WorldGenSettings'][1]
    generation['seed'] = (4, 314159)
    generation['dimensions'][1]['minecraft:overworld'][1]['generator'] = (10, {
        'type': (8, 'minecraft:noise'), 'settings': (8, 'minecraft:overworld'),
        'biome_source': (10, {'type': (8, 'minecraft:multi_noise'), 'preset': (8, 'minecraft:overworld')})})
    data.pop('Player', None)
    data.update({'LevelName': (8, 'Combat Natural Terrain'), 'allowCommands': (1, 1),
                 'GameType': (3, 1), 'Time': (4, 0), 'DayTime': (4, 6000)})
    output = io.BytesIO(b'\x0a')
    output.seek(1)
    write_payload(output, 8, name)
    write_payload(output, 10, metadata)
    world.mkdir(parents=True)
    (world / 'level.dat').write_bytes(gzip.compress(output.getvalue()))
    args = launch['args'][:]
    args[args.index('--gameDir') + 1] = str(client)
    args[args.index('--username') + 1] = 'CombatIntegrated'
    quick = args.index('--quickPlayMultiplayer')
    args[quick:quick + 2] = ['--quickPlaySingleplayer', 'CombatNatural']
    args = [f'-Dcombat.qa.dir={directory}' if a.startswith('-Dcombat.qa.dir=') else a for a in args]
    args.insert(0, '-Dcombat.qa.clients=1')
    java = Path(os.environ['JAVA_HOME']) / 'bin/java'
    props = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines()
                 if '=' in line and not line.startswith('#'))
    artifact = ROOT / 'build/libs' / f"{props['mod_id']}-{props['mod_version']}.jar"
    before = digest(artifact)
    sources = source_identity(ROOT)
    runtime = {str(p.relative_to(ROOT)): digest(p) for base in (ROOT / "build/classes/java/main", ROOT / "build/resources/main", ROOT / "build/classes/java/combatQa") for p in sorted(base.rglob("*")) if p.is_file()}
    harness = [Path(__file__), ROOT / 'tools/combat-client-qa.gradle', ROOT / 'tools/create_flat_sandbox.py',
               *sorted((ROOT / 'tools/combat-qa-src').rglob('*.java'))]
    harness_hashes = {str(p.resolve().relative_to(ROOT)): digest(p) for p in harness}
    print(f'Integrated combat evidence: {directory}', flush=True)
    with (directory / 'client.log').open('w') as log:
        process = subprocess.Popen([str(java), *args], cwd=client,
                                   env={**os.environ, **launch['env']}, stdout=log, stderr=subprocess.STDOUT)
        try:
            code = process.wait(timeout=420)
        except subprocess.TimeoutExpired:
            process.terminate()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            code = -1
    result = directory / 'server-result.txt'
    text = result.read_text() if result.exists() else 'No server completion marker'
    samples_file = directory / 'CombatIntegrated.jsonl'
    samples = [json.loads(line) for line in samples_file.read_text().splitlines()] if samples_file.exists() else []
    scenes = {name: [s for s in samples if s['scene'] == name] for name in ('loaded', 'dry', 'reload', 'sidearm')}
    checks = {f'{name}_observed': len(rows) >= 100 for name, rows in scenes.items()}
    checks['aim_observed'] = any(s['aim'] for s in samples)
    checks['sidearm_observed'] = any(s['gun'] == 'tacz:glock_17' for s in scenes['sidearm'])
    checks['loaded_ammo_changed'] = bool(scenes['loaded']) and min(s['ammo'] for s in scenes['loaded']) < max(s['ammo'] for s in scenes['loaded'])
    checks['dry_stays_empty'] = bool(scenes['dry']) and all(s['ammo'] == 0 for s in scenes['dry'])
    for name in ('dry', 'reload'):
        rows = scenes[name]
        checks[name + '_client_movement'] = bool(rows) and max((s['x'] - rows[0]['x']) ** 2 + (s['z'] - rows[0]['z']) ** 2 for s in rows) >= 4
    checks['screenshots'] = all((directory / f'CombatIntegrated-{name}.png').exists() for name in scenes)
    passed = code == 0 and text.startswith('PASS') and all(checks.values()) and digest(artifact) == before and sources == source_identity(ROOT)
    passed = passed and all(digest(ROOT / p) == sha for p, sha in runtime.items())
    passed = passed and all(digest(ROOT / p) == sha for p, sha in harness_hashes.items())
    manifest = {'status': 'passed' if passed else 'failed', 'scope': 'Integrated client only',
                'result': text, 'exitCode': code, 'checks': checks, 'artifactSha256': before,
                'sources': sources, 'runtimeClassesAndResources': runtime,
                'harness': harness_hashes,
                'evidence': {p.name: digest(p) for p in directory.iterdir() if p.is_file()}}
    (directory / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(text, checks, flush=True)
    return 0 if passed else 1


if __name__ == '__main__':
    raise SystemExit(main())
