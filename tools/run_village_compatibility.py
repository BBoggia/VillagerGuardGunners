#!/usr/bin/env python3
"""Reproducible production Forge client scenarios. Requires Python 3.11 and JDK 17."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import tomllib
import uuid
import zipfile

from create_flat_sandbox import read_payload, write_payload
from validate_village_expansion import execute_stage

REPO = Path(__file__).resolve().parents[1]
OUTPUT = REPO / 'build/village-compatibility'
BASE_MODS = ['villagerguardgunners', 'guardvillagers', 'tacz', 'fzzy_config',
             'kotlinforforge', 'vggcompatprobe']
# Verified runtime requirement omitted from the released Liberty mods.toml.
EXTRA_REQUIREMENTS = {'libertyvillagers': ['cloth_config']}
UNSAFE_ENV = ('MOD_CLASSES', 'MCP_MAPPINGS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS',
              'JDK_JAVA_OPTIONS', 'CLASSPATH')
FEATURES = {'managed': bool, 'coverage': bool, 'buildings': bool, 'maximum': int}


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def atomic_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n')
    temporary.replace(path)


def json_hash(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True).encode()).hexdigest()


def clean_environment():
    inherited = [key for key in UNSAFE_ENV if os.environ.get(key)]
    if inherited:
        raise ValueError('Unexpected Java/development environment: ' + ', '.join(inherited))


@contextmanager
def workload_lock():
    """One runner-owned JVM workload per user/host, across checkouts."""
    with (Path(tempfile.gettempdir()) / f'vgg-compatibility-{os.getuid()}.lock').open('a+') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise ValueError('Another compatibility build or client run holds the host lock') from error
        # Other launchers cannot honor our lock. Refuse to overlap known JVM workloads.
        listing = subprocess.check_output(['ps', '-axo', 'comm=,args='], text=True, timeout=5)
        busy = [line for line in listing.splitlines() if re.match(r'\s*\S*java\s', line)
                and any(marker in line for marker in ('GradleDaemon', 'GradleWrapperMain',
                                                       'BootstrapLauncher', 'net.minecraft'))]
        if busy:
            raise ValueError('An existing Minecraft/Gradle JVM is active; let it finish before running QA')
        try:
            yield
        finally:
            fcntl.flock(lock, fcntl.LOCK_UN)


def manifest_attributes(raw):
    lines = raw.decode('utf-8').replace('\r\n', '\n').replace('\n ', '').splitlines()
    return dict(line.split(': ', 1) for line in lines if ': ' in line)


def jar_metadata(path):
    """Read top-level and Jar-in-Jar mods without extracting or executing them."""
    def read(stream, origin, depth=0):
        if depth > 8:
            raise ValueError('Jar-in-Jar nesting exceeds eight levels: ' + origin)
        with zipfile.ZipFile(stream) as jar:
            names = set(jar.namelist())
            attrs = manifest_attributes(jar.read('META-INF/MANIFEST.MF')) if 'META-INF/MANIFEST.MF' in names else {}
            meta = tomllib.loads(jar.read('META-INF/mods.toml').decode()) if 'META-INF/mods.toml' in names else {}
            mods = {}
            for mod in meta.get('mods', []):
                version = mod.get('version', '1')
                if version == '${file.jarVersion}':
                    version = attrs.get('Implementation-Version', '0.0NONE')
                mods[mod['modId']] = str(version)
            result = [{'origin': origin, 'mods': mods, 'metadata': meta, 'attributes': attrs}]
            if 'META-INF/jarjar/metadata.json' in names:
                for child in json.loads(jar.read('META-INF/jarjar/metadata.json'))['jars']:
                    payload = jar.read(child['path'])
                    nested = read(io.BytesIO(payload), origin + '!/' + child['path'], depth + 1)
                    nested[0]['embedded'] = child
                    nested[0]['sha256'] = hashlib.sha256(payload).hexdigest()
                    result.extend(nested)
            return result
    return read(path, str(path))


def resolve_path(value, base):
    path = Path(value).expanduser()
    return (base / path).resolve() if not path.is_absolute() else path.resolve()


def load_config(path):
    path = Path(path).resolve()
    config = json.loads(path.read_text())
    if config.get('schema') != 1:
        raise ValueError('Configuration schema must be 1')
    config['_base'] = str(path.parent)
    names = [case['name'] for case in config['cases']]
    if len(set(names)) != len(names) or any(not re.fullmatch('[a-z0-9][a-z0-9-]*', name) for name in names):
        raise ValueError('Case names must be unique lowercase names with digits or hyphens')
    completed = set()
    for case in config['cases']:
        if case.get('restartFrom') and case['restartFrom'] not in completed:
            raise ValueError('restartFrom must name an earlier case')
        completed.add(case['name'])
        if type(case.get('coverageDeckChecks', False)) is not bool:
            raise ValueError('coverageDeckChecks must be boolean')
        if type(case.get('regionalSampleCount', 0)) is not int or not 0 <= case.get('regionalSampleCount', 0) <= 9:
            raise ValueError('regionalSampleCount must be 0..9')
        if type(case.get('seed', 314159)) is not int or not -(2**63) <= case.get('seed', 314159) < 2**63:
            raise ValueError('Seed must be a signed 64-bit integer')
        if case.get('behavior', 'natural') not in ('natural', 'checkpoint-combat'):
            raise ValueError('Unknown behavior')
        ticks = case.get('observationTicks', 500)
        minimum = 1600 if case.get('behavior') == 'checkpoint-combat' else 500
        if type(ticks) is not int or not minimum <= ticks <= 12000:
            raise ValueError(f'Observation ticks must be {minimum}..12000')
        if not 30 <= case.get('timeoutSeconds', 420) <= 1800:
            raise ValueError('Timeout must be 30..1800 seconds')
        if not 1 <= case.get('locateRadiusChunks', 100) <= 100 or not 1 <= case.get('maximumForcedChunks', 400) <= 400:
            raise ValueError('Locate and force-load limits exceed the supported observation bounds')
        for key, value in case.get('features', {}).items():
            if key not in FEATURES or type(value) is not FEATURES[key]:
                raise ValueError('Invalid feature setting: ' + key)
            if key == 'maximum' and not 0 <= value <= 32:
                raise ValueError('Current candidate maximumGuards range is 0..32')
        if not case.get('expectations'):
            raise ValueError('Each case needs explicit outcome expectations')
        validate_expectations(case['expectations'])
    return config


def embedded_metadata_versions(mod, candidates, selector):
    groups = {}
    for entry in candidates:
        identifier = entry['embedded']['identifier']
        groups.setdefault((identifier['group'], identifier['artifact']), []).append(entry)
    if len(groups) <= 1:
        return []
    # Older Integrated jars and newer libraries use different Maven coordinates.
    # MixinExtras elects its active service separately from Forge's mod-list entry.
    if mod != 'mixinextras' or any(e.get('attributes', {}).get('FMLModType') != 'GAMELIBRARY' for e in candidates):
        raise ValueError('Unverified embedded mod ID shared across artifact coordinates: ' + mod)
    return sorted({selector(entries)['mods'][mod] for entries in groups.values()})


def select_jars(config, case, embedded_selector=None):
    base = Path(config['_base'])
    explicit = {}
    expected_hashes = {}
    for key, specification in config.get('mods', {}).items():
        if isinstance(specification, str):
            specification = {'path': specification}
        path = resolve_path(specification['path'], base)
        explicit[key] = path
        if specification.get('sha256'):
            expected_hashes[path] = specification['sha256']
    paths = set(explicit.values())
    for directory in config.get('jarDirectories', []):
        directory = resolve_path(directory, base)
        if not directory.is_dir():
            raise ValueError('Missing external JAR directory: ' + str(directory))
        paths.update(directory.glob('*.jar'))
    metadata = {p: jar_metadata(p) for p in sorted(paths)}
    index = {}
    for path, entries in metadata.items():
        for entry in entries:
            for mod in entry['mods']:
                index.setdefault(mod, set()).add(path)
    selected = set()
    queue = BASE_MODS + case.get('mods', [])
    checks = []
    while queue:
        key = queue.pop(0)
        present = {mod for path in selected for entry in metadata[path] for mod in entry['mods']}
        if key in present or key in ('forge', 'minecraft'):
            continue
        candidates = {explicit[key]} if key in explicit else index.get(key, set())
        # Byte-identical downloads are interchangeable; different versions require a pin.
        unique = {sha(p): p for p in sorted(candidates)}
        if len(unique) != 1:
            raise ValueError(f'{key}: expected one JAR, found {len(unique)} distinct candidates; pin mods.{key}.path')
        path = next(iter(unique.values()))
        if not any(key in entry['mods'] for entry in metadata[path]):
            raise ValueError(f'{path} does not provide {key}')
        selected.add(path)
        for entry in metadata[path]:
            for mod in entry['mods']:
                queue.extend(EXTRA_REQUIREMENTS.get(mod, []))
            for dependencies in entry['metadata'].get('dependencies', {}).values():
                queue.extend(dep['modId'] for dep in dependencies if dep.get('mandatory') and dep.get('side') != 'SERVER')
    versions = {'minecraft': '1.20.1', 'forge': config['runtime']['forgeVersion']}
    providers = {}
    supplied = {}
    for path in sorted(selected):
        if path in expected_hashes and sha(path) != expected_hashes[path]:
            raise ValueError('SHA256 mismatch: ' + str(path))
        for entry in metadata[path]:
            for mod, version in entry['mods'].items():
                supplied.setdefault(mod, []).append(entry)
    embedded_resolutions = {}
    metadata_versions = {}
    for mod, candidates in supplied.items():
        selected_entry = candidates[0]
        if len(candidates) > 1:
            if not all('embedded' in entry for entry in candidates) or embedded_selector is None:
                raise ValueError('Duplicate selected mod ID: ' + mod)
            alternatives = embedded_metadata_versions(mod, candidates, embedded_selector)
            if alternatives: metadata_versions[mod] = alternatives
            selected_entry = embedded_selector(candidates)
            embedded_resolutions[mod] = {
                'selectedVersion': selected_entry['mods'][mod],
                'candidates': [entry['origin'] for entry in candidates],
                'rule': 'highest available version satisfying all embedded ranges; Forge verifies actual loading'}
        versions[mod] = selected_entry['mods'][mod]
        providers[mod] = selected_entry['origin']
    for path in sorted(selected):
        for entry in metadata[path]:
            meta = entry['metadata']
            if meta:
                loader = meta['modLoader']
                loader_version = versions['forge'] if loader in ('javafml', 'lowcodefml') else versions.get(loader)
                if loader_version is None:
                    raise ValueError('Missing language provider: ' + loader)
                checks.append([entry['origin'] + ' loader', loader_version, meta['loaderVersion']])
            for owner, deps in meta.get('dependencies', {}).items():
                if owner in providers and providers[owner] != entry['origin']:
                    continue
                for dep in deps:
                    if dep.get('side') == 'SERVER':
                        continue
                    version = versions.get(dep['modId'])
                    if version is None and dep.get('mandatory'):
                        raise ValueError(f'{owner}: missing {dep["modId"]}')
                    if version is not None:
                        checks.append([owner + ' -> ' + dep['modId'], version, dep.get('versionRange', '*')])
            child = entry.get('embedded')
            if child:
                checks.append([entry['origin'], child['version']['artifactVersion'], child['version']['range']])
    files = [{'source': str(path), 'file': path.name, 'sha256': sha(path), 'entries': metadata[path]}
             for path in sorted(selected)]
    if len({row['file'] for row in files}) != len(files):
        raise ValueError('Selected JARs have colliding filenames')
    return {'files': files, 'versions': versions, 'providers': providers, 'versionChecks': checks,
            'embeddedResolutions': embedded_resolutions, 'embeddedMetadataVersions': metadata_versions,
            'extraRequirements': EXTRA_REQUIREMENTS}


def allowed(rules):
    if not rules:
        return True
    allow = False
    for rule in rules:
        os_rule = rule.get('os', {})
        matches = (os_rule.get('name', 'osx') == 'osx'
                   and re.fullmatch(os_rule.get('arch', '.*'), 'aarch64')
                   and re.search(os_rule.get('version', '.*'), platform.release())
                   and not rule.get('features'))
        if matches:
            allow = rule['action'] == 'allow'
    return allow


def reject_development(args):
    joined = '\n'.join(args).lower()
    if any(marker in joined for marker in ('userdev', 'mapped_official', 'mod_classes',
                                           '/build/classes', '/build/resources', 'gradlestart')):
        raise ValueError('Development runtime/classpath is prohibited')
    if '--launchTarget' not in args or args.index('--launchTarget') + 1 == len(args) or args[args.index('--launchTarget') + 1] != 'forgeclient':
        raise ValueError('Expected the installed production forgeclient launch target')


def runtime_info(config):
    if platform.system() != 'Darwin' or platform.machine() not in ('arm64', 'aarch64'):
        raise ValueError('This installed-runtime adapter currently supports macOS Apple silicon only')
    base_dir = Path(config['_base'])
    runtime = config['runtime']
    mc = resolve_path(runtime['minecraftDirectory'], base_dir)
    java = resolve_path(runtime['java'], base_dir)
    java_info = subprocess.run([str(java), '-version'], text=True, capture_output=True, check=True).stderr
    if not re.search(r'version "17[.\"]', java_info):
        raise ValueError('Production Minecraft 1.20.1 scenarios require Java 17')
    version = runtime['version']
    base_path = mc / 'versions/1.20.1/1.20.1.json'
    forge_path = mc / 'versions' / version / (version + '.json')
    base = json.loads(base_path.read_text())
    forge = json.loads(forge_path.read_text())
    if forge.get('inheritsFrom') != '1.20.1' or forge.get('mainClass') != 'cpw.mods.bootstraplauncher.BootstrapLauncher':
        raise ValueError('Expected an installed Forge version inheriting Minecraft 1.20.1')
    game_args = forge['arguments']['game']
    reject_development(game_args)
    if game_args[game_args.index('--fml.forgeVersion') + 1] != runtime['forgeVersion']:
        raise ValueError('Installed Forge version does not match configuration')
    libraries = {}
    for lib in base['libraries'] + forge['libraries']:
        if not allowed(lib.get('rules')):
            continue
        if ':natives-macos' in lib['name'] and not lib['name'].endswith('arm64'):
            continue
        parts = lib['name'].split(':')
        libraries[':'.join(parts[:2] + parts[3:])] = lib
    rows = []
    for lib in libraries.values():
        artifact = lib.get('downloads', {}).get('artifact')
        if not artifact:
            raise ValueError('Installed library lacks artifact metadata: ' + lib['name'])
        path = mc / 'libraries' / artifact['path']
        digest = sha(path)
        if artifact.get('sha1'):
            with path.open('rb') as stream:
                if hashlib.file_digest(stream, 'sha1').hexdigest() != artifact['sha1']:
                    raise ValueError('Installed library checksum mismatch: ' + str(path))
        rows.append({'name': lib['name'], 'path': str(path), 'sha256': digest})
    version_jar = mc / 'versions' / version / (version + '.jar')
    rows.append({'name': version, 'path': str(version_jar), 'sha256': sha(version_jar)})
    reject_development([r['path'] for r in rows] + game_args)
    maven = os.pathsep.join(next(row['path'] for row in rows if row['name'].startswith(coordinate))
                            for coordinate in ('org.apache.maven:maven-artifact:', 'org.apache.commons:commons-lang3:'))
    assets_index = mc / 'assets/indexes' / (base['assetIndex']['id'] + '.json')
    return {'java': str(java), 'javaVersion': java_info.strip(), 'javaSha256': sha(java),
            'minecraftDirectory': str(mc), 'base': base, 'forge': forge, 'libraries': rows,
            'maven': maven, 'os': platform.platform(), 'architecture': platform.machine(),
            'metadataHashes': {str(p): sha(p) for p in (base_path, forge_path, assets_index)}}


def prepare(config, case, runtime):
    version_command = [runtime['java'], '-cp', runtime['maven'],
                       str(REPO / 'tools/village-compatibility/CheckVersions.java')]
    def select_embedded(entries):
        command = version_command + ['--select-embedded']
        for entry in entries:
            version = entry['embedded']['version']
            command.extend([version['artifactVersion'], version['range']])
        selected = subprocess.run(command, text=True, capture_output=True, timeout=30)
        if selected.returncode:
            raise ValueError('Conflicting embedded versions:\n' + selected.stderr)
        return entries[int(selected.stdout.strip())]
    jars = select_jars(config, case, select_embedded)
    for key, class_name in (('villagerguardgunners', 'com/gamergaming/villagerguardgunners/VillagerGuardGunners.class'),
                            ('vggcompatprobe', 'vggcompatprobe/Probe.class')):
        with zipfile.ZipFile(jars['providers'][key]) as archive:
            if class_name not in archive.namelist() or 'pack.mcmeta' not in archive.namelist():
                raise ValueError('Incomplete packaged artifact: ' + key)
            if json.loads(archive.read('pack.mcmeta')).get('pack', {}).get('pack_format') != 15:
                raise ValueError('Expected Minecraft 1.20.1 pack metadata: ' + key)
            if key == 'villagerguardgunners' and any('vggcompatprobe' in entry for entry in archive.namelist()):
                raise ValueError('Release JAR contains QA classes')
    checks = jars['versionChecks']
    command = version_command[:]
    command.extend(value for triple in checks for value in triple)
    checked = subprocess.run(command, text=True, capture_output=True, timeout=30)
    if checked.returncode:
        raise ValueError('Dependency range validation failed:\n' + checked.stdout + checked.stderr)
    jars['versionValidation'] = checked.stdout.strip()
    identity = {'case': case, 'jars': jars, 'runtime': runtime,
                'datapacks': datapack_manifest(case.get('datapacks', {})),
                'runnerSha256': sha(Path(__file__)),
                'helperHashes': {name: sha(REPO / 'tools' / name) for name in
                                 ('create_flat_sandbox.py', 'validate_village_expansion.py')},
                'versionCheckerSha256': sha(REPO / 'tools/village-compatibility/CheckVersions.java')}
    return identity, json_hash(identity)


def datapack_manifest(packs):
    result = {}
    for name, directory in packs.items():
        if not re.fullmatch(r'[a-z0-9_-]+', name):
            raise ValueError('Invalid datapack name')
        root = Path(directory).expanduser().resolve(strict=True)
        files = sorted(root.rglob('*'))
        if any(p.is_symlink() for p in files):
            raise ValueError('Datapack symlinks are not supported')
        if json.loads((root / 'pack.mcmeta').read_text())['pack']['pack_format'] != 15:
            raise ValueError('Expected Minecraft 1.20.1 datapack')
        result[name] = {'source': str(root), 'files': {str(p.relative_to(root)): sha(p) for p in files if p.is_file()}}
    return result


def fresh_world(destination, seed, mod_ids, datapacks=(), design_preview=False):
    """Minimal 1.20.1 normal-world metadata; Minecraft generates every chunk."""
    dimensions = {}
    for dimension, settings, biome in [('overworld', 'overworld', 'multi_noise'),
                                        ('the_nether', 'nether', 'multi_noise'),
                                        ('the_end', 'end', 'the_end')]:
        source = {'type': (8, 'minecraft:' + biome)}
        if biome == 'multi_noise':
            source['preset'] = (8, 'minecraft:' + settings)
        dimensions['minecraft:' + dimension] = (10, {
            'type': (8, 'minecraft:' + dimension), 'generator': (10, {
                'type': (8, 'minecraft:noise'), 'settings': (8, 'minecraft:' + settings),
                'biome_source': (10, source)})})
    if design_preview:
        dimensions['minecraft:overworld'][1]['generator'] = (10, {
            'type': (8, 'minecraft:flat'), 'settings': (10, {
                'biome': (8, 'minecraft:plains'), 'lakes': (1, 0), 'features': (1, 0),
                'structure_overrides': (9, (8, [])),
                'layers': (9, (10, [{'height': (3, height), 'block': (8, 'minecraft:' + block)}
                    for height, block in [(1, 'bedrock'), (123, 'dirt'), (1, 'grass_block')]]))})})
    data = {'DataVersion': (3, 3465), 'version': (3, 19133),
            'Version': (10, {'Name': (8, '1.20.1'), 'Id': (3, 3465),
                             'Snapshot': (1, 0), 'Series': (8, 'main')}),
            'LevelName': (8, 'Compatibility'), 'allowCommands': (1, 1), 'GameType': (3, 1),
            'hardcore': (1, 0), 'Difficulty': (1, 2), 'DifficultyLocked': (1, 0),
            'initialized': (1, 1), 'Time': (4, 0), 'DayTime': (4, 6000),
            'SpawnX': (3, 0), 'SpawnY': (3, 80), 'SpawnZ': (3, 0),
            'WorldGenSettings': (10, {'seed': (4, seed), 'generate_features': (1, 1),
                                     'bonus_chest': (1, 0), 'dimensions': (10, dimensions)}),
            'DataPacks': (10, {'Enabled': (9, (8, ['vanilla', 'mod:forge'] +
                                             ['mod:' + key for key in sorted(mod_ids)] +
                                             ['file/' + key for key in sorted(datapacks)])),
                               'Disabled': (9, (8, []))})}
    stream = io.BytesIO()
    stream.write(b'\x0a')
    write_payload(stream, 8, '')
    write_payload(stream, 10, {'Data': (10, data)})
    destination.mkdir(parents=True)
    (destination / 'level.dat').write_bytes(gzip.compress(stream.getvalue(), mtime=0))


def launch_arguments(runtime, jars, case, run):
    game = run / 'client'
    natives = run / 'natives'
    natives.mkdir()
    for row in runtime['libraries']:
        if ':natives-macos' in row['name']:
            with zipfile.ZipFile(row['path']) as jar:
                for name in jar.namelist():
                    if name.endswith('.dylib'):
                        (natives / Path(name).name).write_bytes(jar.read(name))
    base, forge = runtime['base'], runtime['forge']
    mc = Path(runtime['minecraftDirectory'])
    variables = {'natives_directory': str(natives), 'launcher_name': 'VGGCompatibility',
                 'launcher_version': '1', 'classpath': os.pathsep.join(row['path'] for row in runtime['libraries']),
                 'version_name': forge['id'], 'library_directory': str(mc / 'libraries'),
                 'classpath_separator': os.pathsep, 'auth_player_name': 'CompatObserver',
                 'game_directory': str(game), 'assets_root': str(mc / 'assets'),
                 'assets_index_name': base['assetIndex']['id'],
                 'auth_uuid': uuid.uuid3(uuid.NAMESPACE_DNS, 'CompatObserver').hex,
                 'auth_access_token': '0', 'clientid': '0', 'auth_xuid': '0',
                 'user_type': 'msa', 'version_type': 'release'}
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
    props = {'dir': str(run), 'structure': case.get('structure', 'minecraft:village_plains'),
             'extended': case.get('behavior') == 'checkpoint-combat', 'focused': case.get('focused', True),
             'observationTicks': case.get('observationTicks', 500),
             'locateRadiusChunks': case.get('locateRadiusChunks', 100),
             'maximumForcedChunks': case.get('maximumForcedChunks', 400),
             'runtimeChecks': case.get('syntheticLifecycle', False),
             'membershipChecks': case.get('membershipChecks', False),
             'refreshChecks': case.get('refreshChecks', False),
             'bridgeChecks': case.get('bridgeChecks', False),
             'cotRouteChecks': case.get('cotRouteChecks', False),
             'coverageDeckChecks': case.get('coverageDeckChecks', False),
             'regionalSampleCount': case.get('regionalSampleCount', 0),
             'layoutVariantChecks': case.get('layoutVariantChecks', False),
             'barracksDesignPreview': case.get('barracksDesignPreview', False),
             'quartermasterClaimChecks': case.get('quartermasterClaimChecks', False),
             'definitionReloadChecks': case.get('definitionReloadChecks', False)}
    props.update(case.get('features', {}))
    for key in BASE_MODS:
        provider = jars['providers'][key].split('!/', 1)[0]
        props['jar.' + key] = str(game / 'mods' / Path(provider).name)
    args = [runtime['java'], '-Xmx4G']
    args.extend('-Dvgg.compat.' + key + '=' + (str(value).lower() if type(value) is bool else str(value))
                for key, value in props.items())
    args.extend(expand(base['arguments']['jvm'] + forge['arguments']['jvm']))
    args.append(forge['mainClass'])
    args.extend(expand(base['arguments']['game'] + forge['arguments']['game']))
    args.extend(['--quickPlaySingleplayer', 'Compatibility', '--width', '1280', '--height', '800'])
    reject_development(args)
    return args


def read_nbt(path):
    stream = io.BytesIO(gzip.decompress(path.read_bytes()))
    if stream.read(1) != b'\x0a':
        raise ValueError('Expected NBT compound: ' + str(path))
    read_payload(stream, 8)
    return read_payload(stream, 10)


def plain_nbt(value):
    kind, content = value
    if kind == 10:
        return {key: plain_nbt(item) for key, item in content.items()}
    if kind == 9:
        child, items = content
        return [plain_nbt((child, item)) for item in items]
    return list(content) if kind == 7 else content


def world_evidence(world):
    population = world / 'data/villagerguardgunners_population.dat'
    root = read_nbt(population) if population.exists() else {}
    journals = {str(path.relative_to(world)): sha(path) for path in world.rglob('*')
                if path.is_file() and 'journal' in str(path.relative_to(world)).lower()}
    files = {str(path.relative_to(world)): sha(path) for path in sorted(world.rglob('*'))
             if path.is_file() and path.name != 'session.lock'}
    return {'population': plain_nbt(root['data']) if 'data' in root else {}, 'journals': journals,
            'files': files}


def validate_expectations(expectations):
    for assertion in expectations:
        if set(assertion) != {'path', 'op', 'value'} or assertion['op'] not in ('eq', 'gte', 'lte', 'contains', 'nonempty'):
            raise ValueError('Expectations require path, op (eq/gte/lte/contains/nonempty), and value')
        if not isinstance(assertion['path'], str) or not assertion['path']:
            raise ValueError('Expectation path must be a nonempty dotted path')
        if assertion['op'] == 'nonempty' and type(assertion['value']) is not bool:
            raise ValueError('nonempty requires a boolean expected value')


def evaluate(run, expectations=None):
    run = Path(run)
    manifest = json.loads((run / 'manifest.json').read_text())
    result_path = run / 'result.json'
    result = json.loads(result_path.read_text()) if result_path.exists() else {}
    saved = json.loads((run / 'world-evidence.json').read_text()) if (run / 'world-evidence.json').exists() else {}
    context = {'result': result, 'world': saved, 'manifest': manifest}
    assertions = [
        {'path': 'manifest.exitCode', 'op': 'eq', 'value': 0},
        {'path': 'result.status', 'op': 'eq', 'value': 'observed'},
        {'path': 'result.production', 'op': 'eq', 'value': True},
        {'path': 'result.releaseCodeSourcesVerified', 'op': 'eq', 'value': True},
    ] + (expectations if expectations is not None else manifest['identity']['case']['expectations'])
    validate_expectations(assertions)
    rows = []
    for assertion in assertions:
        actual = context
        try:
            for part in assertion['path'].split('.'):
                actual = actual[int(part)] if isinstance(actual, list) else actual[part]
            expected, op = assertion['value'], assertion['op']
            passed = {'eq': lambda: actual == expected and (type(actual) is type(expected)
                      if isinstance(actual, bool) or isinstance(expected, bool) else True), 'gte': lambda: actual >= expected,
                      'lte': lambda: actual <= expected, 'contains': lambda: expected in actual,
                      'nonempty': lambda: bool(actual) is expected}[op]()
            rows.append({**assertion, 'actual': actual, 'passed': bool(passed)})
        except (KeyError, IndexError, TypeError, ValueError):
            rows.append({**assertion, 'passed': False, 'reason': 'Missing or incompatible evidence'})
    actual_versions = {mod['id']: mod['version'] for mod in result.get('mods', [])}
    for mod, expected in manifest['identity'].get('jars', {}).get('versions', {}).items():
        actual = actual_versions.get(mod)
        alternatives = manifest['identity'].get('jars', {}).get('embeddedMetadataVersions', {}).get(mod)
        if alternatives:
            rows.append({'path': 'loadedModVersion:' + mod, 'op': 'one_of', 'value': alternatives,
                         'actual': actual, 'passed': actual in alternatives})
            log = run / 'client/logs/latest.log'
            active = re.findall(r'Initializing MixinExtras via [^\n]+\(version=([^)]*)\)', log.read_text(errors='replace')) if log.exists() else []
            rows.append({'path': 'activeMixinExtrasServiceVersion', 'op': 'eq', 'value': expected,
                         'actual': active, 'passed': bool(active) and all(version == expected for version in active)})
        else:
            rows.append({'path': 'loadedModVersion:' + mod, 'op': 'eq', 'value': expected,
                         'actual': actual, 'passed': actual == expected})
    settings = {'managed': 'managedGeneration', 'coverage': 'automaticCoverage',
                'buildings': 'buildings', 'maximum': 'maximumGuards'}
    for key, expected in manifest['identity']['case'].get('features', {}).items():
        actual = result.get('effectiveSettings', {}).get(settings[key])
        rows.append({'path': 'result.effectiveSettings.' + settings[key], 'op': 'eq',
                     'value': expected, 'actual': actual, 'passed': type(actual) is type(expected) and actual == expected})
    return {'passed': bool(rows) and all(row['passed'] for row in rows), 'assertions': rows}


def evidence_hashes(run):
    return {str(path.relative_to(run)): sha(path) for path in run.iterdir()
            if path.is_file() and path.name not in ('manifest.json', 'assertions.json')}


def record_preflight(output, cases, prepared):
    """Retain full matrix scope across filtered invocations; use a new output for a changed matrix."""
    output = Path(output)
    matrix = {'schema': 1, 'cases': cases, 'caseHash': json_hash(cases)}
    matrix_path = output / 'matrix.json'
    previous_path = output / 'preflight.json'
    previous = json.loads(previous_path.read_text())['cases'] if previous_path.exists() else {}
    if matrix_path.exists() and json.loads(matrix_path.read_text()) != matrix:
        raise ValueError('Matrix changed; choose a new output directory')
    if not set(previous) <= {case['name'] for case in cases}:
        raise ValueError('Existing case inventory would shrink; choose a new output directory')
    atomic_json(matrix_path, matrix)
    atomic_json(previous_path, {'schema': 1, 'cases': {**previous, **prepared}})


def record_attempt(output, name, run_name):
    path = Path(output) / 'attempts.json'
    index = json.loads(path.read_text()) if path.exists() else {'schema': 1, 'latest': {}}
    index['latest'][name] = run_name
    atomic_json(path, index)


def case_runs(output, name):
    output = Path(output)
    pattern = re.compile(re.escape(name) + r'-[0-9]{8}T[0-9]{6}\.[0-9]{6}Z')
    paths = {path for path in (output / 'runs').glob(name + '-*') if pattern.fullmatch(path.name)}
    index_path = output / 'attempts.json'
    if index_path.exists():
        latest = json.loads(index_path.read_text())['latest'].get(name)
        if latest:
            if not pattern.fullmatch(latest):
                raise ValueError('Invalid attempt index entry')
            paths.add(output / 'runs' / latest)
    return sorted(paths, reverse=True)


def completed_case(output, name, identity_hash):
    for run in case_runs(output, name):
        path = run / 'manifest.json'
        if not path.exists():
            return None
        try:
            manifest = json.loads(path.read_text())
        except (OSError, ValueError):
            return None
        if manifest.get('identityHash') != identity_hash:
            continue
        if manifest.get('status') != 'passed':
            return None
        if not all((run / name).is_file() and sha(run / name) == digest
                   for name, digest in manifest.get('evidence', {}).items()):
            return None
        if evaluate(run)['passed']:
            return run
        return None
    return None


def run_case(output, identity, identity_hash, restart=None):
    case = identity['case']
    name = case['name']
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
    run = output / 'runs' / (name + '-' + stamp)
    record_attempt(output, name, run.name)
    game = run / 'client'
    (game / 'mods').mkdir(parents=True)
    manifest = {'schema': 1, 'identity': identity, 'identityHash': identity_hash,
                'status': 'preparing', 'startedAt': stamp, 'exitCode': None}
    atomic_json(run / 'manifest.json', manifest)
    print(run, flush=True)
    try:
        for jar in identity['jars']['files']:
            target = game / 'mods' / jar['file']
            shutil.copy2(jar['source'], target)
            if sha(target) != jar['sha256']:
                raise ValueError('JAR changed after preflight: ' + jar['source'])
        world = game / 'saves/Compatibility'
        if restart:
            previous_world = restart / 'client/saves/Compatibility'
            previous_evidence = json.loads((restart / 'world-evidence.json').read_text())
            if world_evidence(previous_world) != {key: value for key, value in previous_evidence.items() if key != 'restart'}:
                raise ValueError('Restart source world changed after its completed case')
            shutil.copytree(previous_world, world, ignore=shutil.ignore_patterns('session.lock'))
            # Preserve the isolated installation's generated settings as well as its world.
            if (restart / 'client/config').exists():
                shutil.copytree(restart / 'client/config', game / 'config')
            manifest['restartSource'] = str(restart)
        else:
            fresh_world(world, case.get('seed', 314159),
                        set(identity['jars']['versions']) - {'minecraft', 'forge'}, identity.get('datapacks', {}),
                        design_preview=case.get('barracksDesignPreview', False))
            for pack_name, pack in identity.get('datapacks', {}).items():
                if datapack_manifest({pack_name: pack['source']})[pack_name] != pack:
                    raise ValueError('Datapack changed after preflight: ' + pack_name)
                shutil.copytree(pack['source'], world / 'datapacks' / pack_name)
        (game / 'options.txt').write_text('renderDistance:10\nsimulationDistance:10\nmaxFps:60\npauseOnLostFocus:false\n')
        args = launch_arguments(identity['runtime'], identity['jars'], case, run)
        manifest.update(status='running', launch=args)
        atomic_json(run / 'manifest.json', manifest)
        with (run / 'client.log').open('w') as log:
            manifest['exitCode'] = execute_stage(args, game, log, timeout=case.get('timeoutSeconds', 420))
        saved = world_evidence(world)
        if restart:
            previous_result = json.loads((restart / 'result.json').read_text())
            result = json.loads((run / 'result.json').read_text())
            saved['restart'] = {
                'sameGuardUUIDs': sorted(r['id'] for r in previous_result['settled']['guardRows']) ==
                                  sorted(r['id'] for r in result['settled']['guardRows']),
                'samePlans': saved['population'].get('Plans') == previous_evidence['population'].get('Plans'),
                'sameCredits': saved['population'].get('Credits') == previous_evidence['population'].get('Credits'),
                'sameJournals': bool(previous_evidence['journals']) and saved['journals'] == previous_evidence['journals']}
        atomic_json(run / 'world-evidence.json', saved)
        atomic_json(run / 'config-evidence.json', {
            str(path.relative_to(game)): sha(path) for path in sorted((game / 'config').rglob('*')) if path.is_file()})
        atomic_json(run / 'manifest.json', manifest)
        report = evaluate(run)
        atomic_json(run / 'assertions.json', report)
        manifest['status'] = 'passed' if report['passed'] else 'failed'
    except BaseException as error:
        manifest.update(status='interrupted' if isinstance(error, KeyboardInterrupt) else 'failed',
                        error=f'{type(error).__name__}: {error}')
        raise
    finally:
        manifest['evidence'] = evidence_hashes(run)
        atomic_json(run / 'manifest.json', manifest)
        print(name + ': ' + manifest['status'], flush=True)
    return run, manifest['status'] == 'passed'


def main(argv=None):
    def interrupted(signum, frame):
        raise KeyboardInterrupt()
    signal.signal(signal.SIGTERM, interrupted)
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    for name in ('preflight', 'run'):
        command = sub.add_parser(name)
        command.add_argument('--config', type=Path, required=True)
        command.add_argument('--case', action='append')
        command.add_argument('--output', type=Path, default=OUTPUT)
        if name == 'run':
            command.add_argument('--resume', action='store_true')
    command = sub.add_parser('evaluate')
    command.add_argument('run', type=Path)
    command.add_argument('--expectations', type=Path)
    command.add_argument('--report', type=Path)
    command = sub.add_parser('build-observer')
    command.add_argument('--java', type=Path, required=True, help='JDK 17 bin/java')
    command.add_argument('--verify-release', action='store_true')
    args = parser.parse_args(argv)
    try:
        if args.command == 'evaluate':
            expected = json.loads(args.expectations.read_text()) if args.expectations else None
            report = evaluate(args.run, expected)
            if args.report:
                atomic_json(args.report, report)
            print(json.dumps(report, indent=2))
            return 0 if report['passed'] else 1
        clean_environment()
        with workload_lock():
            if args.command == 'build-observer':
                os.environ['JAVA_HOME'] = str(args.java.expanduser().resolve().parents[1])
                command = [str(REPO / 'gradlew'), '--no-daemon', '-I',
                           'tools/village-compatibility-qa.gradle', 'compatProbeJar', 'reobfCompatProbeJar']
                if args.verify_release:
                    command.append('verifyReleaseArtifact')
                return execute_stage(command, REPO, sys.stdout, timeout=1200)
            config = load_config(args.config)
            runtime = runtime_info(config)
            names = {case['name'] for case in config['cases']}
            if args.case and not set(args.case) <= names:
                raise ValueError('Unknown requested case')
            required = set(args.case) if args.case else names
            parents = {case['name']: case.get('restartFrom') for case in config['cases']}
            for name in list(required):
                while parents[name]:
                    name = parents[name]
                    required.add(name)
            prepared = {}
            for case in config['cases']:
                if case['name'] in required:
                    prepared[case['name']] = prepare(config, case, runtime)
            output = args.output.expanduser().resolve()
            record_preflight(output, config['cases'], prepared)
            if args.command == 'preflight':
                print(f'Validated {len(prepared)} production configurations: {output / "preflight.json"}')
                return 0
            completed = {}
            for case in config['cases']:
                name = case['name']
                if name not in prepared:
                    continue
                identity, digest = prepared[name]
                restart = None
                if case.get('restartFrom'):
                    restart = completed.get(case['restartFrom'])
                    if restart is None and (not args.case or name in args.case):
                        raise ValueError('Restart source must complete or be resumed first: ' + case['restartFrom'])
                    identity['restartIdentity'] = json.loads((restart / 'manifest.json').read_text())['identityHash'] if restart else None
                    identity['restartEvidenceSha256'] = sha(restart / 'world-evidence.json') if restart else None
                    digest = json_hash(identity)
                    record_preflight(output, config['cases'], {name: [identity, digest]})
                previous = completed_case(output, name, digest) if args.resume else None
                if previous:
                    completed[name] = previous
                    print(name + ': retained ' + str(previous), flush=True)
                    continue
                if args.case and name not in args.case:
                    continue
                run, passed = run_case(output, identity, digest, restart)
                if not passed:
                    return 1
                completed[name] = run
            atomic_json(output / 'completed.json', {name: str(path) for name, path in completed.items()})
            return 0
    except (ValueError, OSError, subprocess.SubprocessError, KeyError, zipfile.BadZipFile) as error:
        print(f'Compatibility QA failed: {error}', file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print('Compatibility QA interrupted; partial evidence retained.', file=sys.stderr)
        return 130


if __name__ == '__main__':
    sys.exit(main())
