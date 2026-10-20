#!/usr/bin/env python3
"""Refresh native village profiles from the pinned, checksum-verified 1.21.1 JARs.

Run the installed provider scenario first to populate build/neoforge/village-mods,
then pass --minecraft-jar pointing at the installed Minecraft extra JAR. Review
profile changes before committing; this is deliberately not part of normal builds.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tomllib
import zipfile
from generate_village_template_profiles import generate

ROOT = Path(__file__).resolve().parents[1]
NAMESPACES = {'ct-overhaul-village': ('ctov', 'ctov'), 'tidal-towns': ('joshie', 'mr_tidal_towns'),
              'mmv-moogs-missing-villages': ('mmv', 'mmv'), 'sky-villages': ('skyvillages', 'skyvillages')}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--minecraft-jar', required=True, type=Path)
    args = parser.parse_args()
    with zipfile.ZipFile(args.minecraft_jar) as jar:
        assert json.loads(jar.read('version.json'))['id'] == '1.21.1', 'Expected Minecraft 1.21.1 resources'
    jars = [args.minecraft_jar.resolve()]
    conditions = {}
    for pin in json.loads((ROOT / 'gradle/neoforge-village-mods.json').read_text()):
        if pin['name'] not in NAMESPACES: continue
        path = ROOT / 'build/neoforge/village-mods' / pin['filename']
        assert hashlib.sha512(path.read_bytes()).hexdigest() == pin['sha512'], path
        namespace, mod_id = NAMESPACES[pin['name']]
        with zipfile.ZipFile(path) as jar:
            metadata = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
        version = next(m['version'] for m in metadata['mods'] if m['modId'] == mod_id)
        conditions[namespace] = {mod_id: '[' + version + ']'}
        jars.append(path)
    output = ROOT / 'build/neoforge/reports/village-profiles'
    output.mkdir(parents=True, exist_ok=True)
    audit = output / 'resource-audit.json'
    subprocess.run([sys.executable, str(ROOT/'tools/audit_village_template_resources.py'), '--output', str(audit), *map(str,jars)],check=True)
    definitions, boundaries = generate(json.loads(audit.read_text()), conditions)
    destination = ROOT / 'src/main/resources/data/villagerguardgunners/village_compatibility'
    # Remove obsolete profiles only for the namespaces audited by this refresh.
    for path in destination.glob('*.json'):
        if any(path.name.startswith(ns + '_') for ns in (*conditions, 'minecraft')) and path.stem not in definitions:
            path.unlink()
    for name, profile in definitions.items():
        (destination/(name+'.json')).write_text(json.dumps(profile,indent=2)+'\n')
    (output/'boundaries.json').write_text(json.dumps(boundaries,indent=2)+'\n')
    (output/'versions.json').write_text(json.dumps(conditions,indent=2)+'\n')
    print(f'Refreshed {len(definitions)} profiles from verified 1.21.1 resources')


if __name__ == '__main__': main()
