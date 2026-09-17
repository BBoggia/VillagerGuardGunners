#!/usr/bin/env python3
"""Render Quartermaster menus in a disposable installed Forge client with packaged JARs."""
import argparse
import gzip
import io
import json
import shutil
from pathlib import Path
from create_flat_sandbox import read_payload, write_payload
from run_village_compatibility import clean_environment, workload_lock, sha
import run_village_compatibility as qa
from validate_village_expansion import execute_stage


def run(args):
    clean_environment()
    out = args.output.resolve()
    game = out / 'client'
    out.mkdir(parents=True, exist_ok=False)
    shutil.copytree(args.mods, game / 'mods')
    for previous in (game / 'mods').glob('villagerguardgunners-*.jar'):
        previous.unlink()
    shutil.copy2(args.jar, game / 'mods' / args.jar.name)
    shutil.copy2(args.observer, game / 'mods' / args.observer.name)
    config_hashes = {}
    for config in args.config_file:
        destination = game / 'config' / config.name
        destination.parent.mkdir(exist_ok=True)
        if destination.exists():
            raise ValueError('Duplicate configuration filename: ' + config.name)
        shutil.copy2(config, destination)
        config_hashes[config.name] = sha(destination)
    jars = {p.name: sha(p) for p in (game / 'mods').glob('*.jar')}
    stream = io.BytesIO(gzip.decompress(args.template.read_bytes()))
    if stream.read(1) != b'\x0a':
        raise ValueError('Expected compound world metadata')
    name = read_payload(stream, 8)
    root = read_payload(stream, 10)
    data = root['Data'][1]
    data.pop('Player', None)
    data.update({'LevelName': (8, 'Quartermaster QA'), 'allowCommands': (1, 0),
                 'GameType': (3, 1), 'Time': (4, 0), 'DayTime': (4, 3000),
                 'SpawnX': (3, 0), 'SpawnY': (3, 100), 'SpawnZ': (3, 0)})
    dimensions = data['WorldGenSettings'][1]['dimensions'][1]
    dimensions['minecraft:overworld'][1]['generator'] = (10, {
        'type': (8, 'minecraft:flat'), 'settings': (10, {
            'biome': (8, 'minecraft:plains'), 'lakes': (1, 0), 'features': (1, 0),
            'structure_overrides': (9, (8, [])),
            'layers': (9, (10, [{'height': (3, 1), 'block': (8, 'minecraft:bedrock')}]))})})
    world = game / 'saves/QuartermasterQA'
    world.mkdir(parents=True)
    output = io.BytesIO(b'\x0a'); output.seek(1)
    write_payload(output, 8, name); write_payload(output, 10, root)
    (world / 'level.dat').write_bytes(gzip.compress(output.getvalue()))
    (game / 'options.txt').write_text('renderDistance:6\nsimulationDistance:5\nmaxFps:60\npauseOnLostFocus:false\nonboardAccessibility:false\nautoJump:false\n')
    runtime = qa.runtime_info({'_base': str(Path(__file__).resolve().parents[1]), 'runtime': {
        'minecraftDirectory': str(args.minecraft.resolve()), 'java': str(args.java),
        'version': '1.20.1-forge-47.4.10', 'forgeVersion': '47.4.10'}})
    providers = {key: str(jar.resolve()) for jar in (game / 'mods').glob('*.jar')
                 for row in qa.jar_metadata(jar) for key in row['mods']}
    providers['vggcompatprobe'] = str((game / 'mods' / args.observer.name).resolve())
    command = qa.launch_arguments(runtime, {'providers': providers}, {}, out)
    command = [arg for arg in command if not arg.startswith('-Dvgg.compat.')]
    command.insert(1, '-Dvgg.management.client=' + str(out))
    command[command.index('--quickPlaySingleplayer') + 1] = 'QuartermasterQA'
    command[command.index('--height') + 1] = '960'
    manifest = {'command': command, 'jars': jars, 'runtime': runtime,
                'initialConfigFiles': config_hashes, 'passed': False}
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    with (out / 'client.log').open('w') as log:
        code = execute_stage(command, game, log, timeout=240)
    result_file = out / 'client-result.json'
    result = json.loads(result_file.read_text()) if result_file.exists() else {'passed': False, 'failure': 'No completion marker'}
    captures = {p.name: sha(p) for p in out.glob('management-*.png')}
    unchanged = jars == {p.name: sha(p) for p in (game / 'mods').glob('*.jar')}
    passed = code == 0 and result['passed'] and unchanged and len(captures) == 18
    manifest.update(exitCode=code, result=result, captures=captures, jarsUnchanged=unchanged, passed=passed)
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps({'passed': passed, 'result': result, 'captures': len(captures)}), flush=True)
    return 0 if passed else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ('jar', 'mods', 'template', 'output'):
        parser.add_argument('--' + key, type=Path, required=True)
    parser.add_argument('--observer', type=Path, default=Path('build/quartermaster-qa/vgg-quartermaster-probe.jar'))
    parser.add_argument('--config-file', type=Path, action='append', default=[])
    parser.add_argument('--minecraft', type=Path, default=Path.home() / 'Library/Application Support/minecraft')
    parser.add_argument('--java', type=Path, default=Path('/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java'))
    with workload_lock():
        return run(parser.parse_args())


if __name__ == '__main__':
    raise SystemExit(main())
