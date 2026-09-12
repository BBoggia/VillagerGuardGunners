import sys
import unittest
from collections import deque
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from build_training_yard_preview import build
from test_watchtower_preview import decode


class TrainingYardPreviewTests(unittest.TestCase):
    def test_pack_is_reproducible_and_has_no_generation_or_rewards(self):
        for large in (False,True):
            blob,row=build(large);tag,grid=decode(blob)
            self.assertEqual((blob,row),build(large))
            self.assertEqual(row['size'],tag['size'][1][1])
            self.assertEqual([],tag['entities'][1][1])
            names=[s['Name'][1] for s in grid.values()]
            self.assertEqual(2,names.count('minecraft:target'))
            self.assertEqual(1,names.count('minecraft:crafting_table'))
            self.assertFalse(set(names)&{'minecraft:jigsaw','minecraft:structure_block','minecraft:chest','minecraft:barrel','villagerguardgunners:armory'})
            self.assertFalse(any(n.endswith('_bed') for n in names))

    def test_roofs_and_details_connect_to_supported_foundations(self):
        for large in (False,True):
            blob,_=build(large);_,grid=decode(blob)
            occupied={p for p,s in grid.items() if s['Name'][1]!='minecraft:air'}
            seen={p for p in occupied if p[1]==0};pending=list(seen)
            for x,y,z in pending:
                for p in ((x-1,y,z),(x+1,y,z),(x,y-1,z),(x,y+1,z),(x,y,z-1),(x,y,z+1)):
                    if p in occupied and p not in seen:seen.add(p);pending.append(p)
            self.assertFalse(occupied-seen,(large,sorted(occupied-seen)))

    def test_pavilion_gable_ends_close_below_roof(self):
        blob,row=build(True);_,grid=decode(blob)
        roof={(x,z):y for x,y,z in row['roof']}
        for z in (5,13):
            for x in range(1,6):
                for y in range(3,roof[x,z]):
                    self.assertNotEqual('minecraft:air',grid[x,y,z]['Name'][1],(x,y,z))

    def test_routes_avoid_duties_and_targets_and_reach_both_doors(self):
        for large in (False,True):
            blob,row=build(large);_,grid=decode(blob)
            routes={tuple(p) for p in row['circulation']}
            self.assertFalse(routes&{tuple(p) for p in row['field']})
            self.assertFalse(routes&{tuple(p) for p in row['dutyPositions']})
            allowed={tuple(p) for p in row['standing']}
            seen={tuple(row['entrance'])};pending=deque(seen)
            while pending:
                x,y,z=pending.popleft()
                for p in ((x-1,y,z),(x+1,y,z),(x,y,z-1),(x,y,z+1)):
                    if p in allowed and p not in seen:seen.add(p);pending.append(p)
            self.assertFalse(allowed-seen)
            for x,y,z in row['doors']:
                self.assertIn((x,y,z),seen)
                for dy,half in ((0,'lower'),(1,'upper')):
                    self.assertEqual('minecraft:oak_door',grid[x,y+dy,z]['Name'][1])
                    self.assertEqual(half,grid[x,y+dy,z]['Properties'][1]['half'][1])
            a,b=row['initialPositions'];self.assertEqual([a[0]-1,a[1],a[2]],b)
            self.assertEqual([[a[0]+1,1,a[2]],[a[0]-2,1,a[2]]],row['dutyPositions'])
            for duty,target in zip(row['dutyPositions'],reversed(row['targetBlocks'])):
                self.assertEqual([duty[0],2,duty[2]+6],target)


if __name__=='__main__':unittest.main()
