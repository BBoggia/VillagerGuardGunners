#!/usr/bin/env python3
"""Run packaged native firing/safety fixtures or a production-only Forge server smoke test."""
import argparse
import copy
import json
from pathlib import Path
import shutil
import subprocess
import time
import zipfile

import run_village_compatibility as qa

ROOT = Path(__file__).resolve().parents[1]


def test_guns(tacz, destination):
    """Derive two ordinary test guns from the pinned TaCZ JAR without editing it."""
    with zipfile.ZipFile(tacz) as archive:
        base = json.loads(archive.read('assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/glock_17_data.json'))
    pack = destination / 'tacz/guard_aim_qa'
    for folder in ['data/vgg_aim_qa/index/guns', 'data/vgg_aim_qa/data/guns']:
        (pack / folder).mkdir(parents=True)
    (pack / 'gunpack.meta.json').write_text(json.dumps({'namespace': 'vgg_aim_qa'}))
    for name in ['fast', 'charge']:
        data = copy.deepcopy(base)
        if name == 'fast':
            data.update(rpm=1200, fire_mode=['auto'], ammo_amount=200)
            # Native RPM clamps to 1200 before applying heat's RPM multiplier.
            data['heat'] = {'max': 1000, 'per_shot': 1, 'cooling_multiplier': 1,
                            'cooling_delay': 100, 'over_heat_time': 1000,
                            'min_inaccuracy': 1, 'max_inaccuracy': 1,
                            'min_rpm_mod': 2, 'max_rpm_mod': 2}
        else:
            data['charging'] = {'semi': {'type': 'auto', 'increase_per_tick': .1,
                'decrease_per_tick': .1, 'decrease_on_fire': 1, 'max_charge': 1,
                'fire_threshold': .8, 'charge_during_cooldown': False}}
        (pack / f'data/vgg_aim_qa/data/guns/{name}.json').write_text(json.dumps(data))
        (pack / f'data/vgg_aim_qa/index/guns/{name}.json').write_text(json.dumps({
            'name': 'Guard aim QA ' + name, 'display': 'tacz:glock_17_display',
            'data': 'vgg_aim_qa:' + name, 'type': 'pistol'}))


def regression_guns(tacz, destination):
    with zipfile.ZipFile(tacz) as archive:
        base = json.loads(archive.read('assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/glock_17_data.json'))
    pack = destination / 'tacz/guard_aim_qa'
    for folder in ['data/vgg_aim_qa/index/guns', 'data/vgg_aim_qa/data/guns', 'data/vgg_aim_qa/scripts']:
        (pack / folder).mkdir(parents=True, exist_ok=True)
    (pack / 'gunpack.meta.json').write_text(json.dumps({'namespace': 'vgg_aim_qa'}))
    for name in ['dynamic', 'dynamic-explosion', 'dynamic-explosion-values', 'dynamic-pellets', 'unsupported', 'oversized']:
        data = copy.deepcopy(base)
        if name == 'oversized':
            data['bullet']['life'] = 20
        else:
            data['script'] = 'vgg_aim_qa:' + name
        if name == 'dynamic-pellets':
            data['bullet']['bullet_amount'] = 8
        script = """local M = {}
function M.modify_property(api, name, value)
 local even = api:getAmmoCountInMagazine() % 2 == 0
 if name == 'bullet_gravity' then if even then return 0.001 else return 0.5 end end
 if name == 'bullet_friction' then if even then return 0.02 else return 0.15 end end
 if name == 'bullet_life' then if even then return 0.2 else return 0.7 end end
 EXPLOSION
 return value
end
return M
""".replace('EXPLOSION', """if name == 'explode_enabled' then return even end
 if name == 'explosion_radius' then if even then return 0.25 else return 0.5 end end
 if name == 'explosion_delay' then if even then return -1 else return 3 end end""" if name.startswith('dynamic-explosion') else '')
        if name == 'dynamic-explosion-values':
            script = script.replace("then return even end", "then return true end").replace("then return -1 else return 3 end", "then return 3 else return 4 end")
        if name == 'unsupported':
            script = 'local M = {}\nfunction M.calcSpread(api, ammo, spread) return {0,0} end\nreturn M\n'
        (pack / f'data/vgg_aim_qa/scripts/{name}.lua').write_text(script)
        (pack / f'data/vgg_aim_qa/data/guns/{name}.json').write_text(json.dumps(data))
        (pack / f'data/vgg_aim_qa/index/guns/{name}.json').write_text(json.dumps({
            'name': 'Guard regression ' + name, 'display': 'tacz:glock_17_display',
            'data': 'vgg_aim_qa:' + name, 'type': 'pistol'}))


def run(args):
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    mods = out / 'mods'
    mods.mkdir()
    expected = {'guardvillagers', 'tacz', 'fzzy_config', 'kotlinforforge'}
    providers = {}
    for jar in args.mods.glob('*.jar'):
        ids = {key for row in qa.jar_metadata(jar) for key in row['mods']} & expected
        for key in ids:
            if key in providers:
                raise ValueError('Ambiguous dependency: ' + key)
            providers[key] = jar.resolve()
    if set(providers) != expected:
        raise ValueError('Missing dependencies: ' + str(expected - set(providers)))
    with zipfile.ZipFile(args.jar) as archive:
        if any(name.startswith('accuracyprobe/') for name in archive.namelist()):
            raise ValueError('Production artifact contains test observer classes')
    for jar in {*providers.values(), args.jar.resolve()}:
        shutil.copy2(jar, mods / jar.name)
    if args.mode != 'smoke':
        shutil.copy2(args.observer, mods / args.observer.name)
    # Forge resolves shared Jar-in-Jar libraries; reject duplicate top-level mods only.
    installed_ids = {key for jar in mods.glob('*.jar') for key in qa.jar_metadata(jar)[0]['mods']}
    for jar in getattr(args, 'extra_mod', []):
        ids = set(qa.jar_metadata(jar)[0]['mods'])
        if not ids or ids & installed_ids or (mods / jar.name).exists():
            raise ValueError('Missing mod metadata or duplicate compatibility mod: ' + str(jar))
        shutil.copy2(jar, mods / jar.name)
        installed_ids.update(ids)
    config_hashes = {}
    for config in getattr(args, 'config_file', []):
        destination = out / 'config' / config.name
        destination.parent.mkdir(exist_ok=True)
        if destination.exists():
            raise ValueError('Duplicate configuration filename: ' + config.name)
        shutil.copy2(config, destination)
        config_hashes[config.name] = qa.sha(destination)
    if args.mode == 'firing':
        test_guns(providers['tacz'], out)
    if args.mode == 'regression':
        regression_guns(providers['tacz'], out)
    (out / 'libraries').symlink_to(args.libraries.resolve(), target_is_directory=True)
    arguments = out / 'libraries/net/minecraftforge/forge/1.20.1-47.4.10/unix_args.txt'
    if not arguments.is_file():
        raise ValueError('Expected an installed Forge 1.20.1-47.4.10 server libraries directory')
    (out / 'eula.txt').write_text('eula=true\n')
    (out / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n'
        'level-type=minecraft:flat\ngenerate-structures=false\nspawn-animals=false\nspawn-monsters=false\n'
        'view-distance=3\nsimulation-distance=3\nmax-tick-time=120000\n')
    command = [str(args.java), '-Xmx2G']
    if args.mode != 'smoke':
        command.append('-Dvgg.' + args.mode + '.output=' + str(out / 'result.json'))
    command += ['@libraries/net/minecraftforge/forge/1.20.1-47.4.10/unix_args.txt', 'nogui']
    before = {p.name: qa.sha(p) for p in mods.glob('*.jar')}
    manifest = {'command': command, 'mode': args.mode, 'jars': before,
                'initialConfigFiles': config_hashes,
                'runnerSha256': qa.sha(Path(__file__)), 'forgeArgumentsSha256': qa.sha(arguments)}
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    log_path = out / 'console.log'
    stopped = False
    with log_path.open('w') as log:
        process = subprocess.Popen(command, cwd=out, stdout=log, stderr=subprocess.STDOUT,
                                   stdin=subprocess.PIPE, text=True)
        deadline = time.monotonic() + args.timeout
        try:
            while process.poll() is None and time.monotonic() < deadline:
                if args.mode == 'smoke' and not stopped and 'For help, type "help"' in log_path.read_text(errors='replace'):
                    process.stdin.write('stop\n')
                    process.stdin.flush()
                    stopped = True
                time.sleep(.5)
            if process.poll() is None:
                process.terminate()
            code = process.wait(timeout=20)
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
    unchanged = before == {p.name: qa.sha(p) for p in mods.glob('*.jar')}
    if args.mode == 'smoke':
        result = {'passed': stopped and code == 0, 'observerInstalled': False}
        (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    else:
        result_path = out / 'result.json'
        result = json.loads(result_path.read_text()) if result_path.exists() else {
            'passed': False, 'failure': 'Server exited without a completion marker; see console.log'}
    passed = code == 0 and unchanged and result.get('passed', False)
    manifest.update(exitCode=code, jarsUnchanged=unchanged, passed=passed)
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps({'passed': passed, 'cases': len(result.get('cases', [])),
                      'failure': result.get('failure', ''), 'output': str(out)}), flush=True)
    return 0 if passed else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--mods', type=Path, required=True, help='Directory containing the four dependencies')
    parser.add_argument('--libraries', type=Path, required=True, help='Existing installed Forge server libraries')
    parser.add_argument('--output', type=Path, required=True, help='New disposable server directory')
    parser.add_argument('--mode', choices=['firing', 'safety', 'regression', 'smoke'], default='firing')
    parser.add_argument('--observer', type=Path, default=ROOT / 'build/accuracy-qa/vgg-accuracy-probe.jar')
    parser.add_argument('--java', type=Path, default=Path('/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java'))
    parser.add_argument('--timeout', type=int, default=300)
    parser.add_argument('--accept-eula', action='store_true', help='Record acceptance only in the new isolated server')
    args = parser.parse_args()
    if not args.accept_eula:
        parser.error('--accept-eula is required to run the isolated Minecraft server')
    with qa.workload_lock():
        qa.clean_environment()
        return run(args)


if __name__ == '__main__':
    raise SystemExit(main())
