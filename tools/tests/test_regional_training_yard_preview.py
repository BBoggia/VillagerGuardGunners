import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from build_regional_training_yard_preview import build,REGIONS
from build_armory_preview import rect
from test_watchtower_preview import decode


class RegionalTrainingYardPreviewTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.layouts=[(region,large,*build(region,large)) for region in REGIONS for large in (False,True)]

    def test_scope_bounds_and_reproducible_native_templates(self):
        for region,large,blob,row in self.layouts:
            tag,grid=decode(blob);self.assertEqual((blob,row),build(region,large))
            self.assertEqual(row['size'],tag['size'][1][1]);self.assertEqual([],tag['entities'][1][1])
            w,h,d=row['size'];self.assertTrue(all(0<=x<w and 0<=y<h and 0<=z<d for x,y,z in grid))
            names=[s['Name'][1] for s in grid.values()]
            self.assertEqual(2,names.count('minecraft:target'));self.assertEqual(1,names.count('minecraft:crafting_table'))
            self.assertFalse(set(names)&{'minecraft:jigsaw','minecraft:structure_block','minecraft:chest','minecraft:barrel','villagerguardgunners:armory'})
            self.assertFalse(any(n.endswith('_bed') for n in names))

    def test_roofs_frames_and_decorations_have_connected_support(self):
        for region,large,blob,row in self.layouts:
            _,grid=decode(blob);solid={p for p,s in grid.items() if s['Name'][1]!='minecraft:air'}
            seen={p for p in solid if p[1]==0};todo=list(seen)
            for x,y,z in todo:
                for p in ((x-1,y,z),(x+1,y,z),(x,y-1,z),(x,y+1,z),(x,y,z-1),(x,y,z+1)):
                    if p in solid and p not in seen:seen.add(p);todo.append(p)
            self.assertFalse(solid-seen,(region,large,sorted(solid-seen)))

    def test_raised_edges_are_closed_except_the_two_block_entrance(self):
        for region,large,blob,row in self.layouts:
            if row['floorY']==1:continue
            _,grid=decode(blob);w,_,d=row['size'];ex,_,_=row['entrance']
            expected=(rect(0,0,w-1,d-1)-rect(1,1,w-2,d-2))-{(ex,0),(ex+1,0)}
            self.assertEqual({(x,z) for x,y,z in row['rails']},expected)
            self.assertTrue(all(grid[x,2,z]['Name'][1].endswith('_fence') for x,z in expected))
            for x,z in rect(0,0,w-1,d-1):self.assertNotEqual('minecraft:air',grid[x,1,z]['Name'][1])
            for x in (ex,ex+1):self.assertEqual('minecraft:air',grid[x,2,0]['Name'][1])

    def test_duties_targets_and_routes_keep_their_relationship_after_lifting(self):
        for region,large,blob,row in self.layouts:
            a,b=row['initialPositions'];self.assertEqual([a[0]-1,a[1],a[2]],b)
            self.assertEqual([[a[0]+1,a[1],a[2]],[a[0]-2,a[1],a[2]]],row['dutyPositions'])
            routes={tuple(p) for p in row['circulation']}
            self.assertFalse(routes&{tuple(p) for p in row['dutyPositions']+row['field']})
            self.assertEqual(row['walkTargets'][-1],row['traffic'][0]);self.assertEqual(row['traffic'][0],row['traffic'][-1])
            for a,b in zip(row['dutyPositions'],reversed(row['targetBlocks'])):self.assertEqual([a[0],a[1]+1,a[2]+6],b)

    def test_winter_cover_gables_and_masonry_range(self):
        for region,large,blob,row in self.layouts:
            _,grid=decode(blob);dx,dy,dz=row['shift'];roof={(x,z):y for x,y,z in row['roof']}
            if large:
                for z in (5,13):
                    for x in range(1,6):
                        for y in range(3+dy,roof[x+dx,z+dz]):self.assertNotEqual('minecraft:air',grid[x+dx,y,z+dz]['Name'][1],(region,x,y,z))
            # Regional palettes must not turn the range backstop into wooden cladding.
            for x in range(6 if large else 2,14 if large else 10):
                for y in range(1,5):
                    n=grid[x+dx,y+dy,(14 if large else 13)+dz]['Name'][1]
                    self.assertFalse(n.endswith(('_log','_planks')),(region,n))
            if region=='snowy':
                for x,y,z in row['standing']:
                    self.assertNotEqual('minecraft:snow',grid[x,y,z]['Name'][1])
                self.assertTrue(any(s['Name'][1]=='minecraft:snow' for s in grid.values()))
            if region in ('snowy','taiga'):
                for x,z in rect(14 if large else 10,7,15 if large else 11,17 if large else 12):self.assertIn((x,z),roof)

if __name__=='__main__':unittest.main()
