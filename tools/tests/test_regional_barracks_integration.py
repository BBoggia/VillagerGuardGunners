import gzip
import io
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_regional_barracks_integration import integration, ROOT
from create_flat_sandbox import read_payload


class RegionalIntegrationTests(unittest.TestCase):
    def test_apron_preserves_doors_and_keeps_one_external_connector(self):
        plans = json.loads((ROOT / 'src/structureDesign/resources/regional-barracks-details.json').read_text())['layouts']
        for plan in plans:
            with self.subTest(layout=plan['id']):
                blob, entry, manifest = integration(plan)
                stream = io.BytesIO(gzip.decompress(blob))
                self.assertEqual(b'\x0a\x00\x00', stream.read(3))
                tag = read_payload(stream, 10)
                palette = tag['palette'][1][1]
                blocks = tag['blocks'][1][1]
                named = [(b, palette[b['state'][1]]['Name'][1]) for b in blocks]
                doors = [b for b, name in named if name.endswith('_door')]
                self.assertEqual(4 * len(plan['doors']), len(doors))
                connectors = [b for b, name in named if name == 'minecraft:jigsaw']
                self.assertEqual(1, len(connectors))
                self.assertEqual(0, connectors[0]['pos'][1][1][2])
                self.assertEqual(('mmv' if plan['region'] in ('jungle','swamp') else 'minecraft') + ':street', connectors[0]['nbt'][1]['name'][1])
                self.assertTrue(entry['target_pool'].endswith('/roads' if plan['region']=='ocean' else '/terminators'))
                anchors = [b for b, name in named if name == 'minecraft:structure_block']
                self.assertEqual(1, len(anchors))
                self.assertEqual(manifest['guards'][0], anchors[0]['pos'][1][1])
                self.assertEqual(plan['size'][1] + 1, tag['size'][1][1][2])
                self.assertEqual(4, len(entry['duty_slots']))
                self.assertEqual((.5 if plan['region'] in ('plains','desert','savanna','taiga','snowy') else .25), entry['selection_chance'])
                # Garrison offsets must still match the runtime's four-cell pattern.
                center, west, east, north = manifest['guards']
                self.assertEqual([center[0]-1,center[1],center[2]], west)
                self.assertEqual([center[0]+1,center[1],center[2]], east)
                self.assertEqual([center[0],center[1],center[2]-1], north)


if __name__ == '__main__':
    unittest.main()
