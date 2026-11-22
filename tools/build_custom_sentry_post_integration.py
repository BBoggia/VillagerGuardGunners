#!/usr/bin/env python3
"""Build optional Sentry Posts for the six reviewed CTOV road contracts."""
import gzip
import io
import json
import shutil
import tempfile
import zipfile
from pathlib import Path

from build_custom_armory_integration import PROFILES
from build_regional_sentry_post_integration import ROOT, integration as regional
from create_flat_sandbox import read_payload, write_payload

CTOV_PROFILES = tuple(p for p in PROFILES if p.provider == 'ctov')


def integration(profile, lodge):
    if profile not in CTOV_PROFILES or not isinstance(lodge, bool):
        raise ValueError('Expected a reviewed CTOV profile and a boolean layout selector')
    blob, entry, row = regional(profile.region, lodge)
    layout = row['layout']
    stream = io.BytesIO(gzip.decompress(blob))
    assert stream.read(3) == b'\x0a\x00\x00'
    tag = read_payload(stream, 10)
    palette = tag['palette'][1][1]
    connectors = [b for b in tag['blocks'][1][1]
                  if palette[b['state'][1]]['Name'][1] == 'minecraft:jigsaw']
    if len(connectors) != 1:
        raise ValueError('Expected one regional entrance connector')
    for key in ('name', 'target'):
        connectors[0]['nbt'][1][key] = (8, profile.connector)
    path = f'compat/ctov/{profile.theme}/{layout}'
    resource = f'vggsentries:{path}'
    # These adapters need their own terrain review (including floating islands).
    entry.pop('terrain_safety', None)
    entry['selection_chance'] = 0.25
    entry.update(target_pool=profile.pool, template=resource)
    row.update(id=f'ctov-{profile.theme}-{layout}', entry=resource,
               template=resource, targetPool=profile.pool, connectorName=profile.connector,
               provider='ctov', theme=profile.theme, region=profile.region,
               layout=layout, structure=profile.structure,
               dutySlots=entry['duty_slots'])
    out = io.BytesIO(b'\x0a\x00\x00'); out.seek(3); write_payload(out, 10, tag)
    return gzip.compress(out.getvalue(), mtime=0), entry, row


def main():
    output = ROOT / 'build/generated/custom-sentry-post-integration'
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as td:
        staged = Path(td); pack = staged / 'ctov'; rows = []
        for profile in CTOV_PROFILES:
            for lodge in (False, True):
                blob, entry, row = integration(profile, lodge); rows.append(row)
                path = entry['template'].split(':', 1)[1]
                for relative, data in (
                    (f'data/vggsentries/structures/{path}.nbt', blob),
                    (f'data/vggsentries/village_buildings/{path}.json', (json.dumps(entry, indent=2)+'\n').encode()),
                ):
                    target = pack / relative; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(data)
        (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 15,
            'description': 'Villager Guard Gunners: CTOV Sentry Posts'}})+'\n')
        (staged / 'manifest.json').write_text(json.dumps(rows, indent=2)+'\n')
        if output.exists(): shutil.rmtree(output)
        shutil.copytree(staged, output)
    archive = ROOT / 'build/distributions/villager-guard-gunners-sentry-posts-ctov.zip'
    archive.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
        for path in sorted((output / 'ctov').rglob('*')):
            if path.is_file():
                z.writestr(zipfile.ZipInfo(str(path.relative_to(output / 'ctov'))), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
    print(archive)


if __name__ == '__main__': main()
