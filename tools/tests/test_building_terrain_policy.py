import importlib
import json
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_regional_barracks_integration import ROOT, integration as barracks


class BuildingTerrainPolicyTests(unittest.TestCase):
    def test_land_regions_opt_in_but_ocean_and_mmv_do_not(self):
        land = {'plains', 'desert', 'savanna', 'taiga', 'snowy'}
        plans = json.loads((ROOT/'src/structureDesign/resources/regional-barracks-details.json').read_text())['layouts']
        for plan in plans:
            with self.subTest(family='barracks', region=plan['region'], layout=plan['layout']):
                _, entry, _ = barracks(plan)
                self.assertEqual(plan['region'] in land, entry.get('terrain_safety', False))
        for family in ('armory', 'sentry_post', 'watchtower', 'training_yard'):
            build = importlib.import_module('build_regional_'+family+'_integration').integration
            for region in (*sorted(land), 'ocean', 'jungle', 'swamp'):
                for large in (False, True):
                    with self.subTest(family=family, region=region, large=large):
                        _, entry, _ = build(region, large)
                        self.assertEqual(region in land, entry.get('terrain_safety', False))

    def test_balance_overrides_only_the_requested_builtin_family(self):
        import tempfile
        from village_building_pack_balance import REGIONS, write_builtin_balance
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            originals = {}
            for region in REGIONS:
                relative = Path(f'data/villagerguardgunners/village_buildings/sentry_post/{region}.json')
                source = root/'build/generated/village-building-templates'/relative
                source.parent.mkdir(parents=True, exist_ok=True)
                originals[relative] = {'format': 1, 'building_type': 'sentry_post',
                                       'target_pool': f'minecraft:village/{region}/houses',
                                       'template': f'villagerguardgunners:village/{region}/sentry_post',
                                       'weight': 2, 'projection': 'rigid', 'processors': 'minecraft:empty'}
                source.write_text(json.dumps(originals[relative]))
            output = root/'pack'
            write_builtin_balance(output, 'sentry_post', root)
            self.assertEqual(5, len(list(output.rglob('*.json'))))
            for relative, original in originals.items():
                actual = json.loads((output/relative).read_text())
                self.assertEqual(.25, actual.pop('selection_chance'))
                self.assertEqual(original, actual)
                self.assertEqual(original, json.loads((root/'build/generated/village-building-templates'/relative).read_text()))
