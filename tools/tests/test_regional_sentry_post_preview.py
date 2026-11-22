import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from build_regional_sentry_post_preview import REGIONS, build, decode


class RegionalSentryPostTests(unittest.TestCase):
    def test_raised_platforms_have_only_the_two_cell_entrance_gap(self):
        for region in ('ocean','jungle','swamp'):
            for lodge in (False,True):
                blob,row=build(region,lodge);grid=decode(blob);w,h,d=row['size'];f=row['floorY']
                ex=row['entrance'][0]
                for x in range(w):
                    for z in range(d):
                        if x not in (0,w-1) and z not in (0,d-1):continue
                        name=grid[x,f,z][0]
                        with self.subTest(region=region,lodge=lodge,cell=(x,z)):
                            self.assertNotEqual('minecraft:air',grid[x,f-1,z][0])
                            if z==0 and x in (ex,ex+1):self.assertEqual('minecraft:air',name)
                            else:self.assertTrue(name.endswith('_fence'))

    def test_roof_courses_overlap_at_steep_rises(self):
        for region in REGIONS:
            for lodge in (False,True):
                blob,row=build(region,lodge);grid=decode(blob);sx,sy,sz=row['shift']
                # Interior roof section, away from front canopy and rear wall.
                z=(6 if lodge else 4)+sz;maximum=7 if lodge else 8
                spans=[]
                for x in range(maximum+1):
                    occupied=[]
                    for (xx,y,zz),block in grid.items():
                        if xx==x+sx and zz==z and y>=4+sy and block[0]==f'minecraft:{REGIONS[region]["roof"]}_slab':
                            kind=dict(block[1])['type'];occupied.append((y+(0.5 if kind=='top' else 0),y+(0.5 if kind=='bottom' else 1)))
                    self.assertTrue(occupied,(region,lodge,x))
                    spans.append((min(a for a,b in occupied),max(b for a,b in occupied)))
                for a,b in zip(spans,spans[1:]):
                    self.assertGreater(min(a[1],b[1])-max(a[0],b[0]),0,(region,lodge,a,b))

    def test_roofs_and_awnings_connect_back_to_the_platform(self):
        for region in REGIONS:
            for lodge in (False,True):
                blob,row=build(region,lodge)
                occupied={p for p,s in decode(blob).items() if s[0]!='minecraft:air'}
                seen={p for p in occupied if p[1]==row['floorY']-1};pending=list(seen)
                for x,y,z in pending:
                    for p in ((x+1,y,z),(x-1,y,z),(x,y+1,z),(x,y-1,z),(x,y,z+1),(x,y,z-1)):
                        if p in occupied and p not in seen:seen.add(p);pending.append(p)
                self.assertFalse(occupied-seen,(region,lodge,sorted(occupied-seen)))

    def test_serialized_previews_are_reproducible_and_unstocked(self):
        for region in REGIONS:
            for lodge in (False,True):
                blob,row=build(region,lodge)
                self.assertEqual((blob,row),build(region,lodge))
                names=[s[0] for s in decode(blob).values()]
                self.assertEqual(1,names.count('minecraft:crafting_table'))
                self.assertFalse(set(names)&{'minecraft:jigsaw','minecraft:structure_block','minecraft:chest','minecraft:barrel','villagerguardgunners:armory'})

if __name__=='__main__':unittest.main()
