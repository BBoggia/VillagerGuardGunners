import gzip
import io
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_regional_armory_preview import build
from create_flat_sandbox import read_payload
from draw_regional_armories import REGIONS, author, furniture_interval


def decode(blob):
    stream = io.BytesIO(gzip.decompress(blob))
    assert stream.read(3) == b'\x0a\x00\x00'
    tag = read_payload(stream, 10)
    palette = tag['palette'][1][1]
    grid = {tuple(b['pos'][1][1]): palette[b['state'][1]] for b in tag['blocks'][1][1]}
    return tag, grid


def interval(pos, block):
    y = pos[1]
    name = block['Name'][1]
    if name == 'minecraft:air': return None
    if name.endswith('_slab'):
        half = block['Properties'][1]['type'][1]
        return {'bottom': (y, y+.5), 'top': (y+.5, y+1), 'double': (y, y+1)}[half]
    return y, y+1


class RegionalArmoryPreviewTests(unittest.TestCase):
    def test_encoded_templates_preserve_scheduled_furniture_and_landings(self):
        for region in REGIONS:
            for yard in (False, True):
                p = author(region, yard)
                blob, row = build(region, yard)
                tag, grid = decode(blob)
                with self.subTest(layout=p['id']):
                    self.assertEqual([p['size'][0], p['size'][2], p['size'][1]], tag['size'][1][1])
                    self.assertEqual([], tag['entities'][1][1])
                    names = [b['Name'][1] for b in grid.values()]
                    self.assertEqual(1, names.count('villagerguardgunners:armory'))
                    self.assertEqual(1, names.count('minecraft:crafting_table'))
                    self.assertFalse(set(names) & {'minecraft:chest', 'minecraft:barrel',
                                                   'minecraft:jigsaw', 'minecraft:structure_block',
                                                   'minecraft:structure_void'})
                    for furniture in p['furniture']:
                        for block in furniture['blocks']:
                            pos = tuple(block['pos'])
                            self.assertEqual(furniture_interval(block)[2:], interval(pos, grid[pos]))
                            self.assertEqual('minecraft:'+block['material'], grid[pos]['Name'][1])
                    for threshold in row['thresholds']:
                        pos = tuple(threshold['pos'])
                        self.assertEqual(threshold['top'], interval(pos, grid[pos])[1])
                    # Untouched margin at ground level is omitted, not structure void/air.
                    self.assertNotIn((0, 0, 0), grid)
                    for pos in row['openSky']:
                        x,y,z = pos
                        self.assertTrue(all(grid[x,h,z]['Name'][1]=='minecraft:air' for h in range(y,p['size'][2])))

    def test_roof_courses_survive_serialization_and_union_at_joins(self):
        for region in REGIONS:
            for yard in (False, True):
                p = author(region, yard)
                _, grid = decode(build(region, yard)[0])
                with self.subTest(layout=p['id']):
                    for r in p['roofColumns']:
                        for half_y in range(round(r['lo']*2), round(r['hi']*2)):
                            pos = r['x'], half_y//2, r['z']
                            lo, hi = interval(pos, grid[pos])
                            self.assertLessEqual(lo, half_y/2)
                            self.assertGreaterEqual(hi, (half_y+1)/2)
                    for lamp in p['lights']:
                        pos = tuple(lamp['support'])
                        lo, _ = interval(pos, grid[pos])
                        self.assertEqual(pos[1], lo, 'Hanging lantern needs the support underside')

    def test_snowy_threshold_plates_cover_both_sides_of_both_door_pairs(self):
        for yard in (False, True):
            blob, row = build('snowy', yard)
            _, grid = decode(blob)
            plates = {pos for pos, block in grid.items() if block['Name'][1] == 'minecraft:spruce_pressure_plate'}
            expected = {(x, y, z+dz) for x,y,z in row['doors'] for dz in (-1,1)}
            self.assertEqual(expected, plates)
            self.assertEqual(8, len(plates))
            for x,y,z in plates:
                self.assertEqual((y-1,y), interval((x,y-1,z),grid[x,y-1,z]))

    def test_every_regional_banner_has_a_native_block_entity_tag(self):
        for region in REGIONS:
            tag, _ = decode(build(region, False)[0])
            palette = tag['palette'][1][1]
            banners = [b for b in tag['blocks'][1][1]
                       if palette[b['state'][1]]['Name'][1].endswith('_wall_banner')]
            self.assertEqual(1, len(banners))
            self.assertEqual('minecraft:banner', banners[0]['nbt'][1]['id'][1])

    def test_preview_builds_are_reproducible(self):
        for region in ('desert', 'snowy', 'ocean'):
            self.assertEqual(build(region, True), build(region, True))


if __name__ == '__main__': unittest.main()
