#!/usr/bin/env python3
"""Verify the packaged 1.21.1 companion resources, unions, and NBT contracts."""
import gzip
import io
import json
import zipfile
from pathlib import Path
from build_combined_building_packs import FAMILIES, PROVIDERS, UNIVERSAL_NAME, source_names
from build_neoforge_building_packs import OUTPUT, PACK_FORMAT, resource
from create_flat_sandbox import read_payload


def contents(path):
    with zipfile.ZipFile(path) as pack:
        assert len(pack.namelist()) == len(set(pack.namelist())), path
        assert json.loads(pack.read('pack.mcmeta'))['pack']['pack_format'] == PACK_FORMAT, path
        result = {n: pack.read(n) for n in pack.namelist() if n != 'pack.mcmeta'}
    assert result and all(n.startswith('data/') and '..' not in Path(n).parts for n in result), path
    assert not any('/structures/' in n or '/functions/' in n or '/loot_tables/' in n or '/recipes/' in n for n in result), path
    assert not any('/worldgen/template_pool/' in n for n in result), 'Companion packs must not replace provider pools'
    return result


def verify(directory=OUTPUT):
    providers = []
    for provider in PROVIDERS:
        union = {}
        for filename in source_names(provider):
            pack = contents(directory / filename)
            for n, payload in pack.items():
                assert n not in union or union[n] == payload, f'Conflicting {n}'
                union[n] = payload
        assert contents(directory / f'villager-guard-gunners-all-buildings-{provider}.zip') == union
        providers.append(union)
    union = {}
    for pack in providers:
        for n, payload in pack.items():
            assert n not in union or union[n] == payload
            union[n] = payload
    assert contents(directory / UNIVERSAL_NAME) == union
    templates = {n: b for n, b in union.items() if n.endswith('.nbt')}
    definitions = {n: json.loads(b) for n, b in union.items() if '/village_buildings/' in n}
    assert len(templates) == 150 and len(definitions) == 175, (len(templates), len(definitions))
    for name, entry in definitions.items():
        if name.startswith('data/villagerguardgunners/'):
            assert entry['selection_chance'] == .25
            continue
        template = resource(entry['template'], 'structure', '.nbt')
        stream = io.BytesIO(gzip.decompress(templates[template])); assert stream.read(3) == b'\x0a\x00\x00'
        nbt = read_payload(stream, 10)
        # Preserve the actual source version so the game's data fixer can migrate
        # vanilla names and block-entity schemas. Do not just relabel old NBT.
        assert nbt['DataVersion'][1] == 3465
        palette = nbt['palette'][1][1]
        size = nbt['size'][1][1]
        markers = []
        connectors = []
        for block in nbt['blocks'][1][1]:
            assert all(0 <= p < limit for p, limit in zip(block['pos'][1][1], size))
            state = palette[block['state'][1]]['Name'][1]
            if state == 'minecraft:structure_block': markers.append(block['nbt'][1]['metadata'][1])
            if state == 'minecraft:jigsaw': connectors.append(block['nbt'][1])
        assert markers == ['village_anchor:' + entry['building_type']], name
        assert len(connectors) == 1 and connectors[0]['pool'][1] == 'minecraft:empty', name
    print(f'PASS: 31 packs; {len(templates)} templates; {len(definitions)} definitions; exact provider/universal unions')


if __name__ == '__main__':
    verify()
