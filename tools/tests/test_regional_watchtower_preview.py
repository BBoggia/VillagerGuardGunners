import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from build_regional_watchtower_preview import build, REGIONS, decode, state
from build_watchtower_preview import build as plains
from test_watchtower_preview import decode as tags


class RegionalWatchtowerPreviewTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.designs = [(r,g,*build(r,g)) for r in REGIONS for g in (False,True)]

    def test_geometry_and_furniture_connect_to_foundation(self):
        for region,gate,blob,row in self.designs:
            grid = decode(blob); occupied = {p for p,s in grid.items() if s!=state('air')}
            seen = {p for p in occupied if p[1]==0}; pending = list(seen)
            for x,y,z in pending:
                for p in ((x-1,y,z),(x+1,y,z),(x,y-1,z),(x,y+1,z),(x,y,z-1),(x,y,z+1)):
                    if p in occupied and p not in seen: seen.add(p);pending.append(p)
            self.assertFalse(occupied-seen,(region,gate,sorted(occupied-seen)))

    def test_translation_preserves_duty_spacing_and_stair_geometry(self):
        for region,gate,blob,row in self.designs:
            source,base = plains(gate); grid=decode(blob); original=decode(source)
            dx,dy,dz = row['shift']
            for key in ('anchor','initialDeckPositions','deckPositions','stairs','slits'):
                values=[base[key]] if key=='anchor' else base[key]
                expected=[[x+dx,y+dy,z+dz] for x,y,z in values]
                self.assertEqual(expected,[row[key]] if key=='anchor' else row[key])
            for p in base['stairs']:
                x,y,z=p
                self.assertEqual(original[x,y,z][1],grid[x+dx,y+dy,z+dz][1])
            self.assertEqual(5,row['initialDeckPositions'][0][1]-row['anchor'][1])
            self.assertEqual(2,row['deckPositions'][0][0]-row['deckPositions'][1][0])

    def test_clear_lanes_stairs_roof_and_lantern_support(self):
        for region,gate,blob,row in self.designs:
            grid=decode(blob); doors={tuple(p) for p in row['doors']};air=state('air')
            for x,y,z in row['standing']:
                self.assertNotEqual(air,grid.get((x,y-1,z),air))
                if (x,y,z) not in doors:
                    for h in range(3): self.assertEqual(air,grid[x,y+h,z],(region,gate,x,y+h,z))
            for x,y,z in row['stairs']:
                for h in (1,2): self.assertEqual(air,grid[x,y+h,z])
            for (x,y,z),s in grid.items():
                if s[0]=='minecraft:lantern':self.assertNotEqual(air,grid[x,y+1,z])
            # Every occupied light sample has overhead shelter, including flat roofs.
            for x,y,z in row['targets']:
                self.assertTrue(any(grid.get((x,h,z),air)!=air for h in range(y+2,row['size'][1])))

    def test_old_roof_edge_is_removed_below_regional_eaves(self):
        for region,gate,blob,row in self.designs:
            grid=decode(blob);dx,dy,dz=row['shift'];base=decode(plains(gate)[0])
            for (x,y,z),s in base.items():
                if y==8 and s[0]=='minecraft:spruce_planks':
                    self.assertEqual(state('air'),grid[x+dx,y+dy,z+dz])

    def test_raised_platform_perimeter_and_snow_scope(self):
        for region,gate,blob,row in self.designs:
            grid=decode(blob);w,h,d=row['size'];snow=[p for p,s in grid.items() if s[0]=='minecraft:snow']
            self.assertEqual(region=='snowy',bool(snow))
            for x,y,z in snow:
                self.assertGreaterEqual(y,11);self.assertEqual('double',dict(grid[x,y-1,z][1]).get('type'))
            if row['floorY']==2:
                ex=row['entrance'][0]
                for x in range(w):
                    for z in range(d):
                        self.assertNotEqual(state('air'),grid[x,1,z])
                        if (x in (0,w-1) or z in (0,d-1)) and (x,z) not in ((ex,0),(ex+1,0)):
                            self.assertTrue(grid[x,2,z][0].endswith('_fence'))
                self.assertEqual(state('air'),grid[ex,2,0])

    def test_reproducible_native_bounds_and_preview_scope(self):
        for region,gate,blob,row in self.designs:
            tag,grid=tags(blob);w,h,d=row['size']
            self.assertEqual(row['size'],tag['size'][1][1]);self.assertEqual([],tag['entities'][1][1])
            self.assertEqual((blob,row),build(region,gate))
            self.assertTrue(all(0<=x<w and 0<=y<h and 0<=z<d for x,y,z in grid))
            names=[s['Name'][1] for s in grid.values()]
            self.assertEqual(1,names.count('minecraft:crafting_table'))
            self.assertFalse(set(names)&{'minecraft:jigsaw','minecraft:structure_block','minecraft:chest','minecraft:barrel','villagerguardgunners:armory'})
            self.assertFalse(any(n.endswith('_bed') for n in names))

if __name__=='__main__':unittest.main()
