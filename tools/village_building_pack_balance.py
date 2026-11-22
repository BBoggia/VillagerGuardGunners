"""Keep a regional family's built-in fallback from dominating overhaul villages."""
import json
from pathlib import Path

REGIONS = ('plains', 'desert', 'savanna', 'taiga', 'snowy')
BUILTIN_SELECTION_CHANCE = 0.25


def write_builtin_balance(pack, family, root):
    for region in REGIONS:
        relative = Path(f'data/villagerguardgunners/village_buildings/{family}/{region}.json')
        source = root/'build/generated/village-building-templates'/relative
        definition = json.loads(source.read_text())
        if definition['building_type'] != family:
            raise ValueError(f'Unexpected built-in family: {source}')
        definition['selection_chance'] = BUILTIN_SELECTION_CHANCE
        target = pack/relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(definition, indent=2)+'\n')
