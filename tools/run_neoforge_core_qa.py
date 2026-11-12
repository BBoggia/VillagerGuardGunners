#!/usr/bin/env python3
"""Run packaged gun, combat, supply, medical, and worldgen checks in an isolated NeoForge server.

Build first with ./gradlew stageCoreQaMods. This runner uses no Gradle/dev runtime.
EULA acceptance must be explicit. Only test-owned files under build/neoforge are reset.
"""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import shutil
import subprocess
import time
import urllib.request
from tacz_runtime_diagnostics import classify_errors, pending_known_error

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--java-home', type=Path, default=Path(os.environ.get('JAVA_HOME', '/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home')))
parser.add_argument('--accept-eula', action='store_true')
parser.add_argument('--fresh', action='store_true', help='Delete only this runner’s previous test world and expected record')
parser.add_argument('--suite', choices=('core', 'behavior', 'workflow', 'worldgen'), default='core')
parser.add_argument('--start-case', help='Debug a behavior-suite suffix; the report marks this as a partial run')
parser.add_argument('--worldgen-scenario', choices=('builtin', 'universal', 'providers', 'providers-baseline'), default='builtin')
parser.add_argument('--neo-version', help='Test another 21.1.x loader in a separate installation without changing the build pin')
args = parser.parse_args()
if args.start_case and args.suite != 'behavior':
    parser.error('--start-case requires --suite behavior')
if args.neo_version and not re.fullmatch(r'21\.1\.\d+', args.neo_version):
    parser.error('--neo-version must name a Minecraft 1.21.1 NeoForge build (21.1.x)')
runtime = ROOT / 'build/neoforge/installed-core-qa'
results_root = ROOT / 'build/neoforge/core-qa/results'
if args.neo_version:
    runtime = runtime.with_name(runtime.name + '-' + args.neo_version)
    results_root = results_root / args.neo_version
evidence = results_root / args.suite
scenario = args.worldgen_scenario
baseline = args.suite == 'worldgen' and scenario == 'providers-baseline'
if args.suite == 'worldgen':
    evidence = evidence / scenario
world_name = args.suite + ('-' + scenario if args.suite == 'worldgen' else '') + '-world'
expected_name = args.suite + ('-' + scenario if args.suite == 'worldgen' else '') + '-qa-expected.nbt'
log_prefix = {'core': 'VGG_CORE_QA_', 'behavior': 'VGG_BEHAVIOR_QA_', 'workflow': 'VGG_WORKFLOW_QA_', 'worldgen': 'VGG_WORLDGEN_QA_'}[args.suite]
if baseline:
    log_prefix = 'VGG_PORT_'
staged = ROOT / 'build/neoforge/core-qa/mods'
props = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
version = args.neo_version or props['neo_version']
java = str(args.java_home / 'bin/java')
if not args.accept_eula:
    raise SystemExit('Pass --accept-eula only after accepting Minecraft’s EULA for this isolated test server.')
if not list(staged.glob('vgg-core-qa-*.jar')):
    raise SystemExit('Run ./gradlew stageCoreQaMods first.')
evidence.mkdir(parents=True, exist_ok=True)
marker = runtime / '.vgg-core-qa-runtime'
if runtime.exists() and not marker.exists():
    raise SystemExit('Refusing to reuse a directory without this runner’s ownership marker.')
runtime.mkdir(parents=True, exist_ok=True)
runtime_lock = (runtime / '.vgg-qa.lock').open('a')
try:
    fcntl.flock(runtime_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
except BlockingIOError:
    raise SystemExit('Another QA runner owns this installation; wait for it to stop before changing worlds or JARs.')
marker.touch()
launch_args = runtime / f'libraries/net/neoforged/neoforge/{version}/unix_args.txt'
if not launch_args.exists():
    url = f'https://maven.neoforged.net/releases/net/neoforged/neoforge/{version}/neoforge-{version}-installer.jar'
    with urllib.request.urlopen(url, timeout=60) as response:
        data = response.read()
    with urllib.request.urlopen(url + '.sha1', timeout=60) as response:
        checksum = response.read().decode().split()[0]
    if hashlib.sha1(data).hexdigest() != checksum:
        raise SystemExit('Installer checksum mismatch')
    installer = runtime / 'installer.jar'
    installer.write_bytes(data)
    with (evidence / 'install.log').open('w') as log:
        subprocess.run([java, '-jar', str(installer), '--installServer', str(runtime)], cwd=runtime,
                       stdout=log, stderr=subprocess.STDOUT, check=True, timeout=240)
    installer.unlink()
if args.fresh:
    shutil.rmtree(runtime / world_name, ignore_errors=True)
    (runtime / expected_name).unlink(missing_ok=True)
elif (runtime / expected_name).exists():
    raise SystemExit('Previous test exists. Use --fresh to run the complete suite again.')
mods = runtime / 'mods'
mods.mkdir(exist_ok=True)
# This runtime belongs exclusively to this runner; stale QA/mod versions must not coexist.
for old in mods.glob('*.jar'):
    old.unlink()
for jar in staged.glob('*.jar'):
    if baseline and (jar.name.startswith('vgg-core-qa-') or jar.name.startswith('villagerguardgunners-')):
        continue
    shutil.copy2(jar, mods / jar.name)
if baseline:
    probes = list((ROOT / 'build/neoforge/bootstrap').glob('vgg-port-bootstrap-*.jar'))
    if len(probes) != 1: raise SystemExit('Build checkNeoForgeFoundation before the provider-only baseline.')
    shutil.copy2(probes[0], mods / probes[0].name)
def hashes():
    return {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(mods.glob('*.jar'))}
# Optional providers are published, checksum-pinned JARs, never dev dependencies.
if args.suite == 'worldgen':
    if scenario in ('providers', 'providers-baseline'):
        cache = ROOT / 'build/neoforge/village-mods'
        cache.mkdir(parents=True, exist_ok=True)
        for pin in json.loads((ROOT / 'gradle/neoforge-village-mods.json').read_text()):
            jar = cache / pin['filename']
            if not jar.exists():
                with urllib.request.urlopen(pin['url'], timeout=60) as response:
                    jar.write_bytes(response.read())
            if hashlib.sha512(jar.read_bytes()).hexdigest() != pin['sha512']:
                raise SystemExit('Village mod checksum mismatch: ' + jar.name)
            shutil.copy2(jar, mods / jar.name)
    if scenario not in ('builtin', 'providers-baseline'):
        datapacks = runtime / world_name / 'datapacks'
        datapacks.mkdir(parents=True, exist_ok=True)
        pack = ROOT / 'build/neoforge/distributions/companion-packs/villager-guard-gunners-village-buildings.zip'
        shutil.copy2(pack, datapacks / pack.name)
before = hashes()
def pack_hashes():
    return {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((runtime / world_name / 'datapacks').glob('*.zip'))}
packs_before = pack_hashes()
# Two invalid item-frame attachment positions in the published Sky Villages
# house_1 template. Accept these only after reproducing both without VGG or its
# pack, with the same pinned Sky Villages JAR. Keep the messages in the report.
known_provider_messages = set()
if args.suite == 'worldgen' and scenario == 'providers':
    baseline_report = results_root / 'worldgen/providers-baseline/result.json'
    if baseline_report.exists():
        proof = json.loads(baseline_report.read_text())
        sky = 'SkyVillages-1.0.6-1.21.x-neoforge-release.jar'
        messages = {'[minecraft/BlockAttachedEntity]: Block-attached entity at invalid position: BlockPos{x=124, y=-23, z=87}',
                    '[minecraft/BlockAttachedEntity]: Block-attached entity at invalid position: BlockPos{x=114, y=-23, z=87}'}
        if (proof.get('worldgenScenario') == 'providers-baseline'
                and proof.get('sha256', {}).get(sky) == before.get(sky)
                and proof.get('jarHashesUnchanged') and proof.get('datapackSha256') == {}
                and not any(n.startswith(('villagerguardgunners-', 'vgg-core-qa-')) for n in proof['sha256'])
                and proof['combat']['markerSeen'] and proof['combat']['exitCode'] == 0
                and all(any(line.endswith(message) for line in proof['combat']['runtimeErrors']) for message in messages)):
            known_provider_messages = messages
(runtime / 'eula.txt').write_text('eula=true\n')
(runtime / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n'
        f'level-seed=12345\nlevel-name={world_name}\nlevel-type=minecraft:normal\ngenerate-structures={str(args.suite == "worldgen").lower()}\n'
        'view-distance=2\nsimulation-distance=2\nspawn-protection=0\nenable-rcon=false\nmax-players=1\n')

def run(name, success_marker):
    command = [java, '-Xms512m', '-Xmx4G' if args.suite == 'worldgen' else '-Xmx2G', '-Dvgg.qa.suite=' + args.suite, '-Dvgg.qa.worldgenScenario=' + scenario, '@' + str(launch_args), 'nogui']
    if args.start_case:
        command.insert(3, '-Dvgg.qa.startCase=' + args.start_case)
    start = time.monotonic()
    process = subprocess.Popen(command, cwd=runtime, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, bufsize=1)
    selector = selectors.DefaultSelector()
    selector.register(process.stdout, selectors.EVENT_READ)
    passed = ready = stopped = failed = False
    pending = ''
    cases = []
    errors = []
    known_errors = []

    def record(line):
        nonlocal passed, ready, failed
        if log_prefix in line or 'Done (' in line:
            print(line.strip(), flush=True)
        passed |= success_marker in line
        ready |= 'Done (' in line
        failed |= log_prefix + 'FAIL' in line
        if '/ERROR]' in line or '/FATAL]' in line:
            errors.append(line.strip())
            if any(line.strip().endswith(message) for message in known_provider_messages):
                known_errors.append(line.strip())
            elif not pending_known_error(line, before):
                failed = True
        if log_prefix + 'CASE_PASS' in line:
            cases.append(line.split(log_prefix + 'CASE_PASS', 1)[1].strip())

    with (evidence / (name + '-console.log')).open('w') as log:
        while process.poll() is None:
            if time.monotonic() - start > 360:
                process.terminate()
                try: process.wait(timeout=15)
                except subprocess.TimeoutExpired: process.kill(); process.wait()
                failed = True
                break
            if not selector.select(timeout=1):
                continue
            # Read available bytes, not TextIO.readline: buffered lines can otherwise
            # strand the final acceptance marker until some later log is produced.
            chunk = os.read(process.stdout.fileno(), 65536).decode('utf-8', errors='replace')
            log.write(chunk); log.flush()
            pending += chunk
            lines = pending.split('\n'); pending = lines.pop()
            for line in lines:
                record(line)
            if ((passed and ready) or failed) and not stopped:
                process.stdin.write('stop\n'); process.stdin.flush(); stopped = True
        tail = process.stdout.read()
        log.write(tail)
        for line in (pending + tail).splitlines():
            record(line)
    selector.close()
    # Validate the complete multiline diagnostic before accepting any deferred header.
    all_errors, tacz_errors = classify_errors((evidence / (name + '-console.log')).read_text(), before)
    known_errors.extend(tacz_errors)
    failed |= any(line not in known_errors for line in all_errors)
    if (runtime / 'logs/latest.log').exists():
        shutil.copy2(runtime / 'logs/latest.log', evidence / (name + '-latest.log'))
    return {'passed': passed and ready and stopped and not failed and process.returncode == 0,
            'markerSeen': passed, 'serverReady': ready, 'exitCode': process.returncode,
            'cases': cases, 'runtimeErrors': errors, 'knownProviderErrors': known_errors,
            'knownTaCZErrors': tacz_errors,
            'elapsedSeconds': round(time.monotonic() - start, 1)}

first = run('combat', 'VGG_PORT_BOOTSTRAP_PASS' if baseline else log_prefix + 'READY_TO_RESTART')
second = run('restart', log_prefix + 'RESTART_PASS') if first['passed'] and not baseline else None
report = {'minecraft': props['minecraft_version'], 'neoForge': version, 'installedServer': True,
          'suite': args.suite,
          'worldgenScenario': scenario if args.suite == 'worldgen' else None, 'completeSuite': args.start_case is None, 'startCase': args.start_case,
          'combat': first, 'restart': second, 'jarHashesUnchanged': before == hashes(), 'sha256': before, 'datapackSha256': packs_before, 'datapackHashesUnchanged': packs_before == pack_hashes()}
report['passed'] = first['passed'] and (baseline or second is not None and second['passed']) and report['jarHashesUnchanged'] and report['datapackHashesUnchanged']
(evidence / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report), flush=True)
raise SystemExit(0 if report['passed'] else 1)
