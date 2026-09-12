import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from draw_regional_armories import REGIONS, author, geometry, section_geometry


class RegionalArmoryDrawingTests(unittest.TestCase):
    def test_scheduled_slab_halves_and_shelf_gap_are_visible(self):
        solids = geometry(author('desert', False))

        def furniture_at(x, z):
            return sorted((lo, hi) for a, b, lo, hi, _, tag in solids
                          if (a, b, tag) == (x, z, 'furniture'))

        self.assertEqual([(1.5, 2)], furniture_at(10, 5))  # Top-slab worktop.
        self.assertEqual([(1, 1.5)], furniture_at(2, 4))  # Bottom-slab bench.
        self.assertEqual([(1, 2)], furniture_at(10, 4))  # Crafting table.
        self.assertEqual([(1, 2), (2.5, 3)], furniture_at(2, 7))

    def test_named_sections_include_shelves_and_armories_without_false_clearance(self):
        for region in REGIONS:
            for yard in (False, True):
                p = author(region, yard)
                with self.subTest(layout=p['id']):
                    x, y, z = p['anchor']
                    armories = [(a, b, lo, hi) for a, b, lo, hi, _, tag in geometry(p)
                                if tag == 'armory']
                    self.assertEqual([(x, z, y, y + 1)], armories)
                    section = section_geometry(p)
                    if yard:
                        self.assertEqual(('x', x), (section['axis'], section['cut']))
                        self.assertEqual([(z, y, y + 1)],
                                         [(u, lo, hi) for u, lo, hi, _, tag in section['solids']
                                          if tag == 'armory'])
                        self.assertNotIn(z, section['clearance'])
                    else:
                        self.assertEqual('z', section['axis'])
                        shelf = next(f for f in p['furniture'] if f['kind'] == 'shelf'
                                     and f['pos'][2] == section['cut'])
                        sx, sy, _ = shelf['pos']
                        self.assertEqual([(sy, sy + 1), (sy + 1.5, sy + 2)],
                                         sorted((lo, hi) for u, lo, hi, _, tag in section['solids']
                                                if u == sx and tag == 'furniture'))
                        self.assertNotIn(sx, section['clearance'])
                    self.assertTrue(section['clearance'])
                    for u, lo, hi, _, tag in section['solids']:
                        self.assertFalse(u in section['clearance'] and lo < y + 3 and hi > y,
                                         (p['id'], u, tag))

    def test_section_rejects_furniture_added_to_a_clear_route(self):
        p = author('desert', False)
        p['furniture'].append(dict(blocks=[dict(pos=[5, 1, 7],
                                              material='acacia_planks', properties={})]))
        with self.assertRaisesRegex(AssertionError, 'occupied section clearance'):
            section_geometry(p)

    def test_taiga_porch_extends_the_main_gable(self):
        p = author('taiga', False)
        self.assertEqual([13, 15, 12], p['size'])
        porch = next(r for r in p['roofs'] if r['key'] == 'porch')
        self.assertEqual(('gable', 'x'), (porch['kind'], porch['axis']))
        columns = {(r['x'], r['z']): (r['lo'], r['hi'])
                   for r in p['roofColumns'] if r['roof'] == 'porch'}
        main = {(r['x'], r['z']): (r['lo'], r['hi'])
                for r in p['roofColumns'] if r['roof'] == 'main'}
        self.assertGreater(columns[6, 1][1], columns[0, 1][1])
        overlap = columns.keys() & main.keys()
        self.assertTrue(overlap)
        for cell in overlap:
            self.assertEqual(main[cell], columns[cell])

    def test_ocean_frontage_slopes_into_main_roof_without_losing_headroom(self):
        p = author('ocean', True)
        self.assertEqual([18, 18, 11], p['size'])
        columns = {(r['x'], r['z']): (r['lo'], r['hi'])
                   for r in p['roofColumns'] if r['roof'] == 'frontage'}
        self.assertEqual([7, 7.5, 8], [columns[9, z][1] for z in (7, 8, 9)])
        main = {(r['x'], r['z']): (r['lo'], r['hi'])
                for r in p['roofColumns'] if r['roof'] == 'main'}
        overlap = columns.keys() & main.keys()
        self.assertTrue(overlap)
        for cell in overlap:
            self.assertEqual(main[cell], columns[cell])
        self.assertGreaterEqual(min(lo for lo, _ in columns.values()), p['floorY'] + 3)


if __name__ == '__main__':
    unittest.main()
