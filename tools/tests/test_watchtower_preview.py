import gzip
import io
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_watchtower_preview import build
from create_flat_sandbox import read_payload


def decode(blob):
    stream = io.BytesIO(gzip.decompress(blob))
    assert stream.read(3) == b'\x0a\x00\x00'
    tag = read_payload(stream, 10)
    palette = tag['palette'][1][1]
    return tag, {tuple(b['pos'][1][1]): palette[b['state'][1]] for b in tag['blocks'][1][1]}


class WatchtowerPreviewTests(unittest.TestCase):
    def test_initial_ranged_offset_and_melee_cells_remain_clear(self):
        for gate in (False, True):
            blob, row = build(gate)
            _, grid = decode(blob)
            for member, deck in enumerate(row['initialDeckPositions']):
                lower = [row['anchor'][0]-member, row['anchor'][1], row['anchor'][2]]
                self.assertEqual([lower[0], lower[1]+5, lower[2]], deck)
                for x,y,z in (lower, deck):
                    for dy in range(3): self.assertEqual('minecraft:air', grid[x,y+dy,z]['Name'][1])
                    self.assertIn(grid[x,y-1,z]['Name'][1], ('minecraft:oak_planks','minecraft:cobblestone','minecraft:andesite'))

    def test_duty_spacing_keeps_neighbor_outside_muzzle_clearance(self):
        for gate in (False, True):
            _, row = build(gate)
            a,b = row['deckPositions']
            self.assertGreaterEqual((a[0]-b[0])**2+(a[2]-b[2])**2,4)

    def test_roof_frame_and_furnishings_connect_to_foundation(self):
        for gate in (False, True):
            blob, _ = build(gate)
            _, grid = decode(blob)
            occupied = {p for p,s in grid.items() if s['Name'][1] != 'minecraft:air'}
            seen = {p for p in occupied if p[1] == 0}
            pending = list(seen)
            for x,y,z in pending:
                for p in ((x-1,y,z),(x+1,y,z),(x,y-1,z),(x,y+1,z),(x,y,z-1),(x,y,z+1)):
                    if p in occupied and p not in seen: seen.add(p);pending.append(p)
            self.assertFalse(occupied-seen, (gate, sorted(occupied-seen)))

    def test_native_template_bounds_and_empty_entity_list(self):
        for gate in (False, True):
            blob, row = build(gate)
            tag, grid = decode(blob)
            self.assertEqual(row['size'], tag['size'][1][1])
            self.assertEqual([], tag['entities'][1][1])
            self.assertEqual((blob,row), build(gate))
            w,h,d = row['size']
            self.assertTrue(all(0<=x<w and 0<=y<h and 0<=z<d for x,y,z in grid))
            names = [s['Name'][1] for s in grid.values()]
            self.assertEqual(1, names.count('minecraft:crafting_table'))
            self.assertFalse(set(names) & {'minecraft:jigsaw','minecraft:structure_block','minecraft:chest','minecraft:barrel','villagerguardgunners:armory'})


if __name__ == '__main__': unittest.main()
