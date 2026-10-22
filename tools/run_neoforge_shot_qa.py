#!/usr/bin/env python3
"""Exercise packaged shot wrappers with an optional competing-redirect addon.

Uses the normal NeoForge installer/runtime and published dependency JARs. Tests
own their new output directory; no launcher profiles or player saves are changed.
Run --prepare once, then regression/client tests with and without --conflict.
The old released JAR with --conflict --expect-conflict is the negative control.
"""
import argparse
import json
from pathlib import Path
import shutil
import socket
import subprocess
import time
import zipfile

import run_neoforge_multiplayer_qa as installed
from run_guard_firing_qa import regression_guns

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / 'build/neoforge/installed-shot-qa'
STAGED = ROOT / 'build/neoforge/shot-qa/mods'


def only(paths):
    paths = list(paths)
    if len(paths) != 1:
        raise ValueError('Expected exactly one artifact: ' + str(paths))
    return paths[0]


def json_write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n')


def stop(process):
    if process.poll() is not None:
        return
    try:
        process.stdin.write('stop\n')
        process.stdin.flush()
        process.wait(timeout=30)
    except (OSError, subprocess.TimeoutExpired):
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)


def run(args):
    installed.RUNTIME = RUNTIME
    if args.prepare:
        RUNTIME.mkdir(parents=True, exist_ok=True)
        installed.RESULTS = RUNTIME / 'installation-evidence'
        installed.RESULTS.mkdir(exist_ok=True)
        base, neo, paths = installed.prepare(str(args.java))
        json_write(RUNTIME / 'launch-metadata.json', dict(base=base, neo=neo, paths=list(map(str, paths))))
        print('Installed NeoForge runtime ready', flush=True)
        return 0
    if not args.accept_eula:
        raise ValueError('--accept-eula is required for the isolated server')
    if not args.output or not args.jar:
        raise ValueError('--output (new disposable directory) and --jar are required')
    if args.expect_conflict and (not args.conflict or args.mode != 'regression'):
        raise ValueError('--expect-conflict requires --conflict and --mode regression')
    if args.cases and args.mode != 'regression':
        raise ValueError('--cases applies only to the regression suite')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    (output / '.vgg-shot-run').touch()
    (output / 'libraries').symlink_to(RUNTIME / 'libraries', target_is_directory=True)
    pins = json.loads((ROOT / 'gradle/neoforge-mods.json').read_text())
    jars = []
    tacz = None
    for pin in pins:
        path = only(STAGED.glob(f'{pin["projectId"]}-{pin["versionId"]}.jar'))
        if installed.sha(path, 'sha512') != pin['sha512']:
            raise ValueError('Dependency checksum mismatch: ' + pin['name'])
        jars.append(path)
        if pin['name'] == 'tacz':
            tacz = path
    with zipfile.ZipFile(args.jar) as archive:
        if any(n.startswith(('shotqa/', 'shotconflict/')) for n in archive.namelist()):
            raise ValueError('Test fixture leaked into gameplay JAR')
    jars.extend([args.jar.resolve(), only(STAGED.glob('vgg-shot-qa-*.jar'))])
    if args.conflict:
        # Older versioned fixtures can coexist in this build directory.
        version = installed.PROPS['mod_version']
        jars.append(only((ROOT / 'build/neoforge/shot-qa').glob(f'vgg-shot-conflict-qa-{version}.jar')))
    mods = output / 'mods'
    mods.mkdir()
    for jar in jars:
        shutil.copy2(jar, mods / jar.name)
    hashes = {p.name: installed.sha(p) for p in mods.glob('*.jar')}
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    (output / 'eula.txt').write_text('eula=true\n')
    (output / 'server.properties').write_text(
        f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\n'
        'level-seed=12345\ngenerate-structures=false\nview-distance=3\nsimulation-distance=3\n'
        'spawn-protection=0\nenable-rcon=false\nmax-players=1\n')
    server_args = RUNTIME / f'libraries/net/neoforged/neoforge/{installed.PROPS["neo_version"]}/unix_args.txt'
    command = [str(args.java), '-Xms512m', '-Xmx2G']
    if args.conflict:
        command.append('-Dvgg.shotqa.conflict=true')
    if args.mode == 'regression':
        regression_guns(tacz, output)
        command.append('-Dvgg.regression.output=' + str(output / 'result.json'))
        if args.conflict:
            command.append('-Dvgg.regression.spawnEvent=shotconflict.HookCounters$ProjectileEvent')
        if args.cases:
            command.append('-Dvgg.regression.cases=' + args.cases)
        result_path = output / 'result.json'
    else:
        command.extend(['-Dvgg.packqa.dir=' + str(output), '-Dvgg.packqa.distance=16'])
        result_path = output / 'complete.json'
    command.extend(['@' + str(server_args), 'nogui'])
    manifest = dict(passed=False, mode=args.mode, conflict=args.conflict, expectedConflict=args.expect_conflict,
                    minecraft=installed.PROPS['minecraft_version'], neoforge=installed.PROPS['neo_version'],
                    sha256=hashes, command=command)
    json_write(output / 'manifest.json', manifest)
    server = client = None
    client_log = None
    with (output / 'console.log').open('w') as log:
        try:
            server = subprocess.Popen(command, cwd=output, stdin=subprocess.PIPE, stdout=log,
                                      stderr=subprocess.STDOUT, text=True)
            start = time.monotonic()
            last_progress = ''
            while True:
                text = (output / 'console.log').read_text(errors='replace')
                if args.mode == 'client' and client is None and 'Done (' in text:
                    game = output / 'client'
                    game.mkdir()
                    shutil.copytree(mods, game / 'mods')
                    (game / 'options.txt').write_text('renderDistance:4\nsimulationDistance:5\nmaxFps:30\n'
                        'pauseOnLostFocus:false\nonboardAccessibility:false\nautoJump:false\nguiScale:2\n'
                        'soundCategory_master:0.0\ntutorialStep:none\n')
                    metadata = json.loads((RUNTIME / 'launch-metadata.json').read_text())
                    client_command = installed.client_command(str(args.java), metadata['base'], metadata['neo'],
                                                             metadata['paths'], 'A', port)
                    client_command = [a for a in client_command if not a.startswith('-Dvgg.multiplayer.')]
                    client_command[1:1] = ['-Dvgg.packqa.dir=' + str(output), '-Dvgg.packqa.address=127.0.0.1:' + str(port)]
                    client_command[client_command.index('--gameDir') + 1] = str(game)
                    manifest['clientCommand'] = client_command
                    client_log = (output / 'client-console.log').open('w')
                    client = subprocess.Popen(client_command, cwd=game, stdin=subprocess.PIPE, stdout=client_log,
                                              stderr=subprocess.STDOUT, text=True)
                    print('Installed graphical client launched', flush=True)
                progress = output / ('progress.json' if args.mode == 'regression' else 'results.json')
                if progress.exists():
                    content = progress.read_text()
                    if content != last_progress:
                        try:
                            rows = json.loads(content)
                            print('Completed case: ' + str(rows[-1].get('case', rows[-1].get('gun'))), flush=True)
                            last_progress = content
                        except (ValueError, IndexError):
                            pass
                if server.poll() is not None:
                    break
                if result_path.exists():
                    if client:
                        client.wait(timeout=45)
                    stop(server)
                    break
                if client and client.poll() is not None:
                    raise RuntimeError('Client exited before completion; inspect client-console.log')
                if time.monotonic() - start > args.timeout:
                    raise TimeoutError('Packaged test exceeded timeout')
                time.sleep(1)
            text = (output / 'console.log').read_text(errors='replace')
            manifest['serverExitCode'] = server.returncode
            manifest['runtimeErrors'] = [l for l in text.splitlines() if '/ERROR]' in l or '/FATAL]' in l]
            if args.expect_conflict:
                # The observer catches the transformation error, records it, and
                # shuts the server down cleanly; a nonzero exit is not required.
                result = json.loads(result_path.read_text()) if result_path.exists() else {}
                manifest['result'] = result
                manifest['reproduced'] = (not result.get('passed', False) and
                    'MixinTransformerError' in result.get('failure', '') and
                    'TaCZGuardShotMixin' in text and 'failed injection check, (0/1) succeeded' in text)
                manifest['passed'] = manifest['reproduced']
            else:
                result = json.loads(result_path.read_text()) if result_path.exists() else {}
                manifest['result'] = result
                if args.mode == 'regression':
                    expected_count = len(args.cases.split(',')) if args.cases else 20
                    expected = result.get('passed') and len(result.get('cases', [])) == expected_count
                else:
                    expected = (result.get('completed') and len(result.get('results', [])) == 4
                        and all(r.get('playerProjectiles', 0) >= 3 and r.get('guardProjectiles', 0) >= 3
                                and r.get('reloadObserved') and r.get('postReloadProjectile') for r in result.get('results', [])))
                manifest['passed'] = bool(expected and server.returncode == 0 and not manifest['runtimeErrors'])
                if client:
                    manifest['clientExitCode'] = client.returncode
                    manifest['clientRuntimeErrors'] = [l for l in (output / 'client-console.log').read_text(errors='replace').splitlines()
                                                      if '/ERROR]' in l or '/FATAL]' in l]
                    manifest['clientJarsUnchanged'] = hashes == {
                        p.name: installed.sha(p) for p in (output / 'client/mods').glob('*.jar')}
                    manifest['passed'] &= (client.returncode == 0 and not manifest['clientRuntimeErrors']
                        and not (output / 'client-error.json').exists() and manifest['clientJarsUnchanged'])
            manifest['jarsUnchanged'] = hashes == {p.name: installed.sha(p) for p in mods.glob('*.jar')}
            manifest['passed'] &= manifest['jarsUnchanged']
        except Exception as error:
            manifest['failure'] = str(error)
            raise
        finally:
            if client and client.poll() is None:
                client.terminate()
                try:
                    client.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    client.kill(); client.wait(timeout=10)
            if server:
                stop(server)
            if client_log:
                client_log.close()
            json_write(output / 'manifest.json', manifest)
    print(json.dumps({'passed': manifest['passed'], 'output': str(output)}), flush=True)
    return 0 if manifest['passed'] else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare', action='store_true')
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--mode', choices=['regression', 'client'], default='regression')
    parser.add_argument('--conflict', action='store_true')
    parser.add_argument('--expect-conflict', action='store_true')
    parser.add_argument('--accept-eula', action='store_true')
    parser.add_argument('--cases', help='Comma-separated regression cases for focused investigation')
    parser.add_argument('--timeout', type=int, default=480)
    parser.add_argument('--java', type=Path, default=Path('/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java'))
    return run(parser.parse_args())


if __name__ == '__main__':
    raise SystemExit(main())
