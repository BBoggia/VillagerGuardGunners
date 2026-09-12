import gzip
import io
import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from build_regional_armory_integration import integration, REGIONS
from build_armory_preview import build as plains_compact, build_supply_yard
from build_regional_armory_preview import build as regional
from create_flat_sandbox import read_payload


def decode(blob):
    f=io.BytesIO(gzip.decompress(blob));assert f.read(3)==b'\x0a\x00\x00'
    return read_payload(f,10)


class RegionalArmoryIntegrationTests(unittest.TestCase):
    def test_all_templates_preserve_architecture_except_armory_marker_and_apron(self):
        for region in ('plains',*REGIONS):
            for yard in (False,True):
                with self.subTest(region=region,yard=yard):
                    preview,old=(build_supply_yard() if yard else plains_compact()) if region=='plains' else regional(region,yard)
                    original=decode(preview);blob,entry,row=integration(region,yard);tag=decode(blob)
                    palette=tag['palette'][1][1]
                    blocks={tuple(b['pos'][1][1]):b for b in tag['blocks'][1][1]}
                    named=lambda b:palette[b['state'][1]]['Name'][1]
                    self.assertEqual([old['size'][0],old['size'][1],old['size'][2]+1],tag['size'][1][1])
                    for b in original['blocks'][1][1]:
                        x,y,z=b['pos'][1][1];actual=blocks[x,y,z+1]
                        if [x,y,z]==old['anchor']:
                            self.assertEqual('minecraft:structure_block',named(actual))
                            self.assertEqual('village_anchor:armory',actual['nbt'][1]['metadata'][1])
                        else:
                            self.assertEqual(original['palette'][1][1][b['state'][1]],palette[actual['state'][1]])
                            self.assertEqual(b.get('nbt'),actual.get('nbt'))
                    self.assertEqual(0,sum(named(b)=='villagerguardgunners:armory' for b in blocks.values()))
                    connectors=[b for b in blocks.values() if named(b)=='minecraft:jigsaw']
                    self.assertEqual(1,len(connectors));self.assertEqual(0,connectors[0]['pos'][1][1][2])
                    self.assertEqual('minecraft:empty',connectors[0]['nbt'][1]['pool'][1])
                    self.assertEqual(('mmv' if region in ('jungle','swamp') else 'minecraft')+':street',connectors[0]['nbt'][1]['name'][1])
                    self.assertEqual('armory',entry['building_type']);self.assertEqual((.5 if region in ('plains','desert','savanna','taiga','snowy') else .25),entry['selection_chance'])
                    self.assertEqual(1,len(entry['duty_slots']))
                    self.assertTrue(all(abs(n)<=16 for n in entry['duty_slots'][0]['offset']))
                    for key in ('anchor','sentry','entrance'):
                        x,y,z=old[key];self.assertEqual([x,y,z+1],row[key])
                    for key in ('doors','targets','standing','openSky','lanterns'):
                        self.assertEqual([[x,y,z+1] for x,y,z in old[key]],row[key])
                    self.assertEqual([[[x,y,z+1] for x,y,z in pair] for pair in old['walkTargets']],row['walkTargets'])
                    self.assertEqual([row['anchor'][0],row['anchor'][1],row['anchor'][2]-2],row['service'])

    def test_snowy_yard_uses_direct_native_writer_for_expanded_envelope(self):
        blob,_,row=integration('snowy',True)
        self.assertEqual([17,12,21],decode(blob)['size'][1][1])
        self.assertEqual(4284,__import__('math').prod(row['size']))


if __name__=='__main__':unittest.main()
