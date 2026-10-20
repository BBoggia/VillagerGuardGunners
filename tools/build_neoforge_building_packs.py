#!/usr/bin/env python3
"""Package the reviewed architecture for Minecraft 1.21.1 (data format 48).

Reuse the geometry builders, not their Forge-era main/output routines. Structure
NBT retains its truthful source DataVersion; Minecraft upgrades it when loading.
No old build output or local development notes are required.
"""
import gzip
import hashlib
import io
import json
import shutil
import tempfile
import zipfile
from pathlib import Path

from build_combined_building_packs import FAMILIES, PROVIDERS, UNIVERSAL_NAME, combine, source_names
from build_regional_barracks_integration import integration as barracks
from build_regional_armory_integration import integration as armory
from build_regional_sentry_post_integration import integration as sentry
from build_regional_watchtower_integration import integration as watchtower
from build_regional_training_yard_integration import integration as training
from build_custom_armory_integration import PROFILES, integration as custom_armory
from build_custom_barracks_integration import integration as custom_barracks
from build_custom_sentry_post_integration import integration as custom_sentry
from build_custom_watchtower_integration import integration as custom_watchtower
from build_custom_training_yard_integration import integration as custom_training
from build_sky_village_integration import integration as sky
from create_flat_sandbox import read_payload

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'build/neoforge/distributions/companion-packs'
PACK_FORMAT = 48
REGIONS = ('plains', 'desert', 'savanna', 'taiga', 'snowy', 'ocean', 'jungle', 'swamp')
REGIONAL = dict(zip(FAMILIES[1:], (armory, sentry, watchtower, training)))
CUSTOM = dict(zip(FAMILIES[1:], (custom_armory, custom_sentry, custom_watchtower, custom_training)))


def json_bytes(value):
    return (json.dumps(value, indent=2) + '\n').encode()


def resource(identifier, directory, suffix):
    namespace, path = identifier.split(':', 1)
    if not namespace or not path or '..' in Path(path).parts or path.startswith('/'):
        raise ValueError(f'Invalid resource ID: {identifier}')
    return f'data/{namespace}/{directory}/{path}{suffix}'


def add_layout(resources, blob, entry, row):
    # Fail here instead of quietly producing a pack with conflicting definitions.
    for name, payload in ((resource(entry['template'], 'structure', '.nbt'), blob),
                          (resource(row['entry'], 'village_buildings', '.json'), json_bytes(entry))):
        if name in resources:
            raise ValueError(f'Duplicate layout resource: {name}')
        resources[name] = payload
    stream = io.BytesIO(gzip.decompress(blob))
    if stream.read(3) != b'\x0a\x00\x00':
        raise ValueError('Expected named-root structure NBT')
    tag = read_payload(stream, 10)
    if tag.get('entities', (9, (10, [])))[1][1]:
        raise ValueError('Companion templates must not embed entities')
    # No stale serialized item stacks may bypass the component-era codecs.
    for block in tag['blocks'][1][1]:
        if 'Items' in block.get('nbt', (10, {}))[1]:
            raise ValueError('Template inventories require an explicit item-component migration')


def write_pack(path, resources, description):
    with zipfile.ZipFile(path, 'w') as archive:
        contents = {**resources, 'pack.mcmeta': json_bytes({'pack': {
            'pack_format': PACK_FORMAT, 'description': description}})}
        for name, payload in sorted(contents.items()):
            archive.writestr(zipfile.ZipInfo(name), payload, compress_type=zipfile.ZIP_DEFLATED)


def build(destination=OUTPUT):
    plans = json.loads((ROOT / 'src/structureDesign/resources/regional-barracks-details.json').read_text())['layouts']
    packs = {(provider, family): {} for provider in PROVIDERS for family in FAMILIES}
    rows = []
    for region in REGIONS:
        provider = 'tidal-towns' if region == 'ocean' else 'mmv' if region in ('jungle', 'swamp') else 'vanilla'
        for family in FAMILIES:
            for large in (False, True):
                if family == 'barracks':
                    plan = next(p for p in plans if (p['region'], p['layout']) == (region, 'courtyard' if large else 'compact'))
                    result = barracks(plan)
                else:
                    result = REGIONAL[family](region, large)
                add_layout(packs[provider, family], *result)
                rows.append(dict(result[2], provider=provider, family=family))
    for profile in PROFILES:
        for family in FAMILIES:
            for large in (False, True):
                if profile.provider == 'sky-villages' and family != 'armories':
                    result = sky(family, large)
                elif family == 'barracks':
                    plan = next(p for p in plans if (p['region'], p['layout']) == (profile.region, 'courtyard' if large else 'compact'))
                    result = custom_barracks(profile, plan)
                else:
                    result = CUSTOM[family](profile, large)
                add_layout(packs[profile.provider, family], *result)
                rows.append(dict(result[2], provider=profile.provider, family=family))
    for family in FAMILIES:
        building_type = {'armories': 'armory', 'sentry-posts': 'sentry_post',
                         'watchtowers': 'watchtower', 'training-yards': 'training_yard'}.get(family, family)
        for region in REGIONS[:5]:
            name = f'data/villagerguardgunners/village_buildings/{building_type}/{region}.json'
            source = ROOT / 'build/neoforge/generated/village-building-templates' / name
            definition = json.loads(source.read_text())
            if definition['building_type'] != building_type:
                raise ValueError(f'Unexpected built-in definition: {source}')
            definition['selection_chance'] = .25
            packs['vanilla', family][name] = json_bytes(definition)
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=destination.parent) as temp:
        stage = Path(temp)
        for provider in PROVIDERS:
            for family, filename in zip(FAMILIES, source_names(provider)):
                write_pack(stage / filename, packs[provider, family],
                           f'Villager Guard Gunners: {PROVIDERS[provider]} {family} (Minecraft 1.21.1)')
        combined = []
        for provider, label in PROVIDERS.items():
            target = stage / f'villager-guard-gunners-all-buildings-{provider}.zip'
            combine([stage / name for name in source_names(provider)], target,
                    f'Villager Guard Gunners: all buildings for {label} (Minecraft 1.21.1)', PACK_FORMAT)
            combined.append(target)
        combine(combined, stage / UNIVERSAL_NAME,
                'Villager Guard Gunners: village buildings (Minecraft 1.21.1)', PACK_FORMAT)
        (stage / 'manifest.json').write_bytes(json_bytes({
            'minecraft': '1.21.1', 'packFormat': PACK_FORMAT, 'layouts': rows,
            'files': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(stage.glob('*.zip'))}}))
        # Publish only after the entire set succeeds; remove obsolete packs as a set.
        if destination.exists():
            shutil.rmtree(destination)
        shutil.copytree(stage, destination)
    print(f'Built {len(rows)} layouts and {len(list(destination.glob("*.zip")))} packs in {destination}')


if __name__ == '__main__':
    build()
