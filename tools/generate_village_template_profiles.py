#!/usr/bin/env python3
"""Build reviewed, fingerprint-bound profiles from a template/pool resource audit.

An audit is evidence, not a compatibility verdict. Pool reachability describes
possible templates; it cannot prove a seed generates a particular building.
"""
import argparse
import json
from pathlib import Path


def strings(value, key):
    if isinstance(value, dict):
        for name, child in value.items():
            if name == key and isinstance(child, str):
                yield child
            yield from strings(child, key)
    elif isinstance(value, list):
        for child in value:
            yield from strings(child, key)


def reachable(audit, starts):
    pending = list(starts)
    pools, templates = set(), set()
    while pending:
        pool = pending.pop()
        if pool in pools:
            continue
        pools.add(pool)
        for resource in audit['pools'].get(pool, []):
            value = resource['value']
            pending.extend(strings(value, 'fallback'))
            for template in strings(value, 'location'):
                if template in templates:
                    continue
                templates.add(template)
                for variant in audit['templates'].get(template, []):
                    pending.extend(j['pool'] for j in variant['jigsaws'] if j.get('pool'))
    return pools, templates


def meaning(template, resource):
    beds = resource['beds']
    # Bed-bearing fragments alone contribute residential demand. A house pool
    # also contains pens, farms, workshops, and empty upper floors.
    kind = 'residence' if beds else 'decoration'
    parts = set(template.split('/'))
    if not beds:
        if parts & {'streets', 'roads', 'road', 'paths', 'path', 'bridges', 'bridge', 'docks'}:
            kind = 'road'
        elif parts & {'town_centers', 'town_center', 'centers', 'meeting_points'}:
            kind = 'gathering'
        elif parts & {'jobsite', 'workstations', 'villagers'}:
            kind = 'service'
        elif parts & {'defense', 'watchtowers', 'barracks'}:
            kind = 'defense'
    workstations = {'minecraft:lectern', 'minecraft:brewing_stand', 'minecraft:smithing_table',
                    'minecraft:fletching_table', 'minecraft:cartography_table', 'minecraft:loom',
                    'minecraft:stonecutter', 'minecraft:blast_furnace', 'minecraft:smoker', 'minecraft:composter'}
    if kind == 'decoration' and workstations.intersection(resource.get('blocks', {})):
        kind = 'service'
    if template.startswith('ctov:village/') and parts & {'barracks', 'archery_range'}:
        kind, beds = 'defense', []
    if resource['invalid_states']:
        kind, beds = 'unsupported', []
    # Door block positions are local candidate entrances, not tested approaches.
    doors = {tuple(d['pos']) for d in resource['doors']}
    if kind in {'residence', 'service', 'defense', 'gathering'}:
        doors.update(tuple(p) for p in resource.get('connectors', []))
    if kind == 'unsupported':
        doors.clear()
    doors = sorted(doors)
    return dict(kind=kind, sha256=resource['sha256'], beds=beds, entrances=doors)


def generate(audit):
    definitions = {}
    boundaries = {}
    conditions = {
        'ctov': {'ctov': '[3.4.14]'}, 'mmv': {'mmv': '[2.1.1]'},
        'joshie': {'mr_tidal_towns': '[1.3.4]'}, 'skyvillages': {'skyvillages': '[1.0.4]'},
        'integrated_villages': {'integrated_villages': '[1.3.2+1.20.1-forge]', 'integrated_api': '[1.8.0]'},
        'villagesandpillages': {'villagesandpillages': '[1.0.2]'}, 'minecraft': {},
    }
    for structure, resources in sorted(audit['structures'].items()):
        ns, name = structure.split(':')
        if ns not in conditions or (ns == 'minecraft' and not name.startswith('village_')):
            continue
        if ns == 'ctov' and '/village_' not in name:
            continue
        value = resources[0]['value']
        if 'start_pool' not in value:
            continue
        pools, templates = reachable(audit, [r['value']['start_pool'] for r in resources])
        # CTOV adds optional profession/defense pieces to pools in code. Record
        # the matching family's shipped templates as well as JSON reachability;
        # a mapping does not assert that its optional pool injection is active.
        if ns == 'ctov':
            family_prefix = value['start_pool'].rsplit('/', 1)[0] + '/'
            templates.update(t for t in audit['templates'] if t.startswith(family_prefix))
        mappings = {}
        for template in sorted(templates):
            variants = {r['sha256']: meaning(template, r) for r in audit['templates'].get(template, [])}
            if variants:
                mappings[template] = list(variants.values())
        mods = conditions[ns].copy()
        if 'required_mod' in value:
            mods[value['required_mod']] = '[0,)'
        if name.startswith('quark/'):
            mods['quark'] = '[0,)'
        limits = []
        if ns == 'joshie' or any(part in name for part in ('pirate', 'sunken', 'swamp')):
            limits.append('water')
        if ns == 'skyvillages' or any(part in name for part in ('airship', 'tree')):
            limits.append('elevation')
        if 'mountain' in name:
            limits.append('steep_terrain')
        if 'underground' in name:
            limits.append('underground')
        if 'clockwork' in name:
            limits.append('mechanisms')
        hostile = structure == 'villagesandpillages:village_witch'
        profile = dict(format=2, mods=mods, priority=20, intent='hostile' if hostile else 'friendly',
                       reader='integrated_api:single' if ns == 'integrated_villages' else 'villagerguardgunners:native',
                       structures=[structure], templates=mappings, traversal_limits=limits)
        if ns == 'ctov':
            profile['size_family'] = name.split('/')[0]
        if hostile:
            # Exclude the complete hostile settlement pool family, including
            # road fallbacks and currently unused branches. Shared vanilla pools must remain available elsewhere.
            prefix = value['start_pool'].rsplit('/', 1)[0] + '/'
            profile['target_pools'] = sorted(p for p in audit['pools'] if p.startswith(prefix))
        identifier = ns + '_' + name.replace('/', '_')
        # Preserve the existing overrideable clockwork definition ID.
        if structure == 'integrated_villages:clockwork_village':
            identifier = 'integrated_clockwork'
        definitions[identifier] = profile
        boundaries[structure] = dict(pools=sorted(pools), templates=len(mappings),
            bed_templates=sum(any(v['beds'] for v in vs) for vs in mappings.values()), traversal_limits=limits)
    for family in ['small', 'medium', 'large']:
        definitions['ctov_'+family+'_tag'] = dict(format=2, mods=conditions['ctov'], priority=10,
            intent='friendly', structure_tags=['ctov:'+family+'_village'], size_family=family,
            templates={}, traversal_limits=['unverified_variant'])
    return definitions, boundaries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--audit', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--report', required=True, type=Path)
    args = parser.parse_args()
    definitions, boundaries = generate(json.loads(args.audit.read_text()))
    args.output.mkdir(parents=True, exist_ok=True)
    for name, profile in definitions.items():
        (args.output / (name+'.json')).write_text(json.dumps(profile, indent=2)+'\n')
    args.report.write_text(json.dumps(boundaries, indent=2)+'\n')
    print(f'Generated {len(definitions)} profiles; {sum(len(p.get("templates",{})) for p in definitions.values())} mappings')


if __name__ == '__main__':
    main()
