#!/usr/bin/env python3
"""Two real installed NeoForge clients and a localhost dedicated server (macOS ARM64).

Uses published Minecraft/NeoForge launch metadata and JARs, never the dev runtime.
No real launcher accounts, profiles, worlds, or configs are modified. Offline test
identities are restricted to the loopback-only, explicit-EULA test server.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import fcntl
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import socket
import subprocess
import time
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / 'build/neoforge/installed-multiplayer-qa'
RESULTS = ROOT / 'build/neoforge/multiplayer-qa/results'
MINECRAFT = Path.home() / 'Library/Application Support/minecraft'
PROPS = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines()
             if '=' in line and not line.startswith('#'))


def sha(path, algorithm='sha256'):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


def download(url, path, checksum=None):
    if path.exists() and (not checksum or sha(path, 'sha1') == checksum):
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=60) as response:
                data = response.read()
            if checksum and hashlib.sha1(data).hexdigest() != checksum:
                raise ValueError('Published checksum mismatch: ' + url)
            path.write_bytes(data)
            return
        except Exception:
            if attempt == 2:
                raise


def allowed(rules):
    result = not rules
    for rule in rules or []:
        # No demo, quick-play, or custom-resolution feature flags are advertised.
        if rule.get('features') and any(rule['features'].values()):
            continue
        os_rule = rule.get('os', {})
        if os_rule.get('name', 'osx') != 'osx':
            continue
        if os_rule.get('arch', 'aarch64') not in ('aarch64', 'arm64'):
            continue
        result = rule['action'] == 'allow'
    return result


def prepare(java):
    mcver, neover = PROPS['minecraft_version'], PROPS['neo_version']
    manifest_path = RUNTIME / 'version_manifest.json'
    download('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', manifest_path)
    entry = next(v for v in json.loads(manifest_path.read_text())['versions'] if v['id'] == mcver)
    basepath = RUNTIME / f'versions/{mcver}/{mcver}.json'
    download(entry['url'], basepath, entry['sha1'])
    base = json.loads(basepath.read_text())
    artifact = base['downloads']['client']
    download(artifact['url'], basepath.with_suffix('.jar'), artifact['sha1'])
    installer = RUNTIME / 'installer.jar'
    url = f'https://maven.neoforged.net/releases/net/neoforged/neoforge/{neover}/neoforge-{neover}-installer.jar'
    checksum = urllib.request.urlopen(url + '.sha1', timeout=60).read().decode().split()[0]
    download(url, installer, checksum)
    profile = RUNTIME / 'launcher_profiles.json'
    if not profile.exists():
        profile.write_text('{"profiles":{}}\n')
    for side, ready in [('Client', RUNTIME / f'versions/neoforge-{neover}/neoforge-{neover}.json'),
                        ('Server', RUNTIME / f'libraries/net/neoforged/neoforge/{neover}/unix_args.txt')]:
        if not ready.exists():
            print('Installing published NeoForge ' + side, flush=True)
            with (RESULTS / f'install-{side.lower()}.log').open('w') as log:
                subprocess.run([java, '-jar', str(installer), '--install' + side, str(RUNTIME)],
                               cwd=RUNTIME, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=300)
    neo = json.loads((RUNTIME / f'versions/neoforge-{neover}/neoforge-{neover}.json').read_text())
    libraries = {}
    for row in base['libraries'] + neo['libraries']:
        if not allowed(row.get('rules')):
            continue
        if ':natives-macos' in row['name'] and not row['name'].endswith('arm64'):
            continue
        parts = row['name'].split(':')
        libraries[':'.join(parts[:2] + parts[3:])] = row
    paths = []
    for row in libraries.values():
        data = row['downloads']['artifact']
        path = RUNTIME / 'libraries' / data['path']
        cached = MINECRAFT / 'libraries' / data['path']
        if not path.exists() and cached.exists() and sha(cached, 'sha1') == data['sha1']:
            path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(cached, path)
        download(data['url'], path, data['sha1'])
        paths.append(path)
    paths.append(RUNTIME / f'versions/neoforge-{neover}/neoforge-{neover}.jar')
    index = base['assetIndex']
    idxpath = RUNTIME / f'assets/indexes/{index["id"]}.json'
    download(index['url'], idxpath, index['sha1'])
    def asset(row):
        digest = row['hash']
        relative = Path('objects') / digest[:2] / digest
        path, cached = RUNTIME / 'assets' / relative, MINECRAFT / 'assets' / relative
        if not path.exists() and cached.exists() and sha(cached, 'sha1') == digest:
            path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(cached, path)
        download(f'https://resources.download.minecraft.net/{digest[:2]}/{digest}', path, digest)
    print('Verifying client assets and libraries', flush=True)
    with ThreadPoolExecutor(max_workers=12) as pool:
        list(pool.map(asset, json.loads(idxpath.read_text())['objects'].values()))
    natives = RUNTIME / 'natives'
    natives.mkdir(exist_ok=True)
    for path in paths:
        if 'natives-macos-arm64' in path.name:
            with zipfile.ZipFile(path) as archive:
                for name in archive.namelist():
                    if name.endswith('.dylib'):
                        (natives / Path(name).name).write_bytes(archive.read(name))
    return base, neo, paths


def client_command(java, base, neo, paths, role, port):
    game = RUNTIME / ('client-' + role)
    variables = {'natives_directory': str(RUNTIME / 'natives'), 'launcher_name': 'VGGInstalledQA',
                 'launcher_version': '1', 'classpath': os.pathsep.join(map(str, paths)),
                 'version_name': neo['id'], 'library_directory': str(RUNTIME / 'libraries'),
                 'classpath_separator': os.pathsep, 'auth_player_name': 'VggOwner' if role == 'A' else 'VggPeer',
                 'game_directory': str(game), 'assets_root': str(RUNTIME / 'assets'),
                 'assets_index_name': base['assetIndex']['id'], 'auth_uuid': uuid.uuid4().hex,
                 'auth_access_token': '0', 'clientid': '0', 'auth_xuid': '0',
                 'user_type': 'legacy', 'version_type': 'release'}
    def expand(items):
        result = []
        for item in items:
            if isinstance(item, dict):
                if not allowed(item.get('rules')):
                    continue
                item = item['value']
            for arg in item if isinstance(item, list) else [item]:
                for key, value in variables.items():
                    arg = arg.replace('${' + key + '}', value)
                if '${' in arg:
                    raise ValueError('Unexpanded launch argument: ' + arg)
                result.append(arg)
        return result
    command = [java, '-Xms512m', '-Xmx3G', '-Dvgg.multiplayer.role=' + role,
               '-Dvgg.multiplayer.shared=' + str(RESULTS), '-Dvgg.multiplayer.address=127.0.0.1:' + str(port)]
    command += expand(base['arguments']['jvm'] + neo['arguments']['jvm'])
    command += [neo['mainClass']] + expand(base['arguments']['game'] + neo['arguments']['game'])
    command += ['--width', '1280', '--height', '900']
    if any('forgeclientdev' in arg or '.gradle/caches' in arg for arg in command):
        raise ValueError('Development launch arguments are forbidden')
    return command


def run(args):
    if platform.system() != 'Darwin' or platform.machine() != 'arm64':
        raise ValueError('This installed-client adapter currently supports macOS ARM64 only')
    marker = RUNTIME / '.vgg-multiplayer-runtime'
    if RUNTIME.exists() and not marker.exists():
        raise ValueError('Refusing to reuse an unowned runtime directory')
    RUNTIME.mkdir(parents=True, exist_ok=True)
    marker.touch()
    lock = (RUNTIME / '.lock').open('a')
    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    RESULTS.mkdir(parents=True, exist_ok=True)
    java = str(args.java_home / 'bin/java')
    base, neo, paths = prepare(java)
    if args.prepare_only:
        print('Installed client/server runtime ready', flush=True)
        return 0
    if not args.accept_eula:
        raise ValueError('Pass --accept-eula after accepting Minecraft’s EULA for this isolated server')
    staged = ROOT / 'build/neoforge/multiplayer-qa/mods'
    if not list(staged.glob('vgg-multiplayer-qa-*.jar')):
        raise ValueError('Build stageMultiplayerQaMods first')
    if (RUNTIME / 'world').exists() and not args.fresh:
        raise ValueError('Use --fresh to reset the test-owned world and results')
    if args.fresh:
        shutil.rmtree(RUNTIME / 'world', ignore_errors=True)
        for pattern in ('*.done', '*.png', '*error.txt', 'control.json', 'server-result.json', 'result.json'):
            for old in RESULTS.glob(pattern):
                old.unlink()
    jars = {p.name: sha(p) for p in sorted(staged.glob('*.jar'))}
    for game in (RUNTIME, RUNTIME / 'client-A', RUNTIME / 'client-B'):
        (game / 'mods').mkdir(parents=True, exist_ok=True)
        for old in (game / 'mods').glob('*.jar'):
            old.unlink()
        for jar in staged.glob('*.jar'):
            shutil.copy2(jar, game / 'mods' / jar.name)
        # Only the test-owned config directories are reset between runs.
        shutil.rmtree(game / 'config', ignore_errors=True)
        if game == RUNTIME / 'client-B':
            (game / 'config').mkdir(exist_ok=True)
            (game / 'config/guardvillagers-client.toml').write_text('"Have guards use the steve model?" = true\n')
        if game != RUNTIME:
            (game / 'options.txt').write_text('renderDistance:4\nsimulationDistance:5\nmaxFps:30\n'
                'pauseOnLostFocus:false\nonboardAccessibility:false\nautoJump:false\nguiScale:2\n'
                'soundCategory_master:0.0\ntutorialStep:none\n')
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    (RUNTIME / 'eula.txt').write_text('eula=true\n')
    (RUNTIME / 'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\n'
        'level-seed=12345\ngenerate-structures=false\nview-distance=4\nsimulation-distance=3\n'
        'spawn-protection=0\nenable-rcon=false\nmax-players=2\nenforce-secure-profile=false\n')
    server_args = RUNTIME / f'libraries/net/neoforged/neoforge/{PROPS["neo_version"]}/unix_args.txt'
    commands = {'server': [java, '-Xms512m', '-Xmx2G', '-Dvgg.multiplayer.shared=' + str(RESULTS), '@' + str(server_args), 'nogui']}
    for role in ('A', 'B'):
        commands[role] = client_command(java, base, neo, paths, role, port)
    manifest = {'passed': False, 'sha256': jars, 'commands': commands, 'expectedClients': 2,
                'minecraft': PROPS['minecraft_version'], 'neoforge': PROPS['neo_version']}
    (RESULTS / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    processes, logs = {}, {}
    try:
        for role, command in commands.items():
            logs[role] = (RESULTS / (role + '-console.log')).open('w')
            processes[role] = subprocess.Popen(command, cwd=RUNTIME if role == 'server' else RUNTIME / ('client-' + role),
                    stdin=subprocess.PIPE, stdout=logs[role], stderr=subprocess.STDOUT, text=True)
            print('Started installed ' + role, flush=True)
            if role == 'server':
                deadline = time.monotonic() + 180
                while 'Done (' not in (RESULTS / 'server-console.log').read_text():
                    if processes[role].poll() is not None or time.monotonic() > deadline:
                        raise RuntimeError('Dedicated server did not start; inspect server-console.log')
                    time.sleep(1)
        deadline = time.monotonic() + 600
        last = ''
        while not (RESULTS / 'server-result.json').exists():
            control = RESULTS / 'control.json'
            if control.exists() and control.read_text() != last:
                last = control.read_text()
                print('Connected-client scenario ' + last, flush=True)
            if any(p.poll() is not None for p in processes.values()):
                raise RuntimeError('A test process exited before completion; inspect console logs')
            if time.monotonic() > deadline:
                raise TimeoutError('Connected-client acceptance timed out')
            time.sleep(1)
        manifest['serverResult'] = json.loads((RESULTS / 'server-result.json').read_text())
        for role in ('A', 'B'):
            processes[role].wait(timeout=40)
        processes['server'].stdin.write('stop\n')
        processes['server'].stdin.flush()
        processes['server'].wait(timeout=60)
    except Exception as error:
        manifest['failure'] = str(error)
        print(str(error), flush=True)
    finally:
        for role, process in processes.items():
            if process.poll() is None:
                if role == 'server':
                    try:
                        process.stdin.write('stop\n'); process.stdin.flush(); process.wait(timeout=20)
                    except (OSError, subprocess.TimeoutExpired):
                        process.terminate()
                else:
                    process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill(); process.wait()
            logs[role].close()
        manifest['exitCodes'] = {k: p.returncode for k, p in processes.items()}
        manifest['errors'] = {k: [line for line in (RESULTS / (k + '-console.log')).read_text(errors='replace').splitlines()
                                      if '/ERROR]' in line or '/FATAL]' in line or 'Failed to load texture:' in line] for k in processes}
        manifest['jarHashesUnchanged'] = all(jars == {p.name: sha(p) for p in (game / 'mods').glob('*.jar')}
                            for game in (RUNTIME, RUNTIME / 'client-A', RUNTIME / 'client-B'))
        manifest['captures'] = {p.name: sha(p) for p in RESULTS.glob('*.png')}
        manifest['passed'] = (not manifest.get('failure') and manifest.get('serverResult', {}).get('passed', False)
                    and all(v == 0 for v in manifest['exitCodes'].values())
                    and not any(manifest['errors'].values()) and manifest['jarHashesUnchanged'])
        (RESULTS / 'result.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps({'passed': manifest['passed'], 'result': str(RESULTS / 'result.json')}), flush=True)
    return 0 if manifest['passed'] else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, default=Path('/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home'))
    parser.add_argument('--prepare-only', action='store_true')
    parser.add_argument('--accept-eula', action='store_true')
    parser.add_argument('--fresh', action='store_true')
    raise SystemExit(run(parser.parse_args()))
