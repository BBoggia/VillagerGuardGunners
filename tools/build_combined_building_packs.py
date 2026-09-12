#!/usr/bin/env python3
"""Merge each provider's five player packs without changing resource identities."""
import json
import tempfile
import zipfile
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[1]
FAMILIES = ('barracks', 'armories', 'sentry-posts', 'watchtowers', 'training-yards')
PROVIDERS = {
    'vanilla': 'Vanilla villages',
    'ctov': "ChoiceTheorem's Overhauled Village",
    'tidal-towns': 'Tidal Towns',
    'mmv': "Moog's Missing Villages",
    'sky-villages': 'Sky Villages',
}


def source_names(provider):
    prefix = 'regional-' if provider in ('vanilla', 'tidal-towns', 'mmv') else ''
    return [f'villager-guard-gunners-{prefix}{family}-{provider}.zip' for family in FAMILIES]


def combine(sources, target, description):
    """Reject conflicting resources before replacing a previously built archive."""
    resources = {}
    for source in sources:
        with zipfile.ZipFile(source) as archive:
            metadata = json.loads(archive.read('pack.mcmeta'))
            if metadata.get('pack', {}).get('pack_format') != 15:
                raise ValueError(f'Expected Minecraft 1.20.1 datapack format in {source}')
            for entry in archive.infolist():
                name = entry.filename
                if entry.is_dir() or name == 'pack.mcmeta':
                    continue
                if not name.startswith('data/') or '..' in PurePosixPath(name).parts or '\\' in name:
                    raise ValueError(f'Unexpected player resource in {source}: {name}')
                payload = archive.read(entry)
                if name in resources and resources[name] != payload:
                    raise ValueError(f'Conflicting resource in {source}: {name}')
                resources[name] = payload
    if not resources:
        raise ValueError('No player resources to combine')
    resources['pack.mcmeta'] = (json.dumps({'pack': {
        'pack_format': 15, 'description': description,
    }}, indent=2) + '\n').encode('utf-8')
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=target.parent, suffix='.zip', delete=False) as stream:
        temporary = Path(stream.name)
    try:
        with zipfile.ZipFile(temporary, 'w') as archive:
            for name, payload in sorted(resources.items()):
                archive.writestr(zipfile.ZipInfo(name), payload, compress_type=zipfile.ZIP_DEFLATED)
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)


def main():
    distributions = ROOT / 'build/distributions'
    for provider, label in PROVIDERS.items():
        target = distributions / f'villager-guard-gunners-all-buildings-{provider}.zip'
        combine([distributions / name for name in source_names(provider)], target,
                f'Villager Guard Gunners: all five building families for {label}')
        print(target)


if __name__ == '__main__':
    main()
