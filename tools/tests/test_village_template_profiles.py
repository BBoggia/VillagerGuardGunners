import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from generate_village_template_profiles import reachable, meaning, generate

class TemplateProfilesTest(unittest.TestCase):
    def test_normal_house_can_reach_zombie_residents_without_zombie_start(self):
        audit={'pools':{'normal': [{'value':{'elements':[{'location':'house'}]}}],
                        'zombie': [{'value':{'elements':[{'location':'resident'}]}}]},
               'templates':{'house':[{'jigsaws':[{'pool':'zombie'}]}]}}
        pools, templates=reachable(audit,['normal'])
        self.assertEqual({'normal','zombie'},pools)
        self.assertEqual({'house','resident'},templates)
    def test_house_name_does_not_supply_beds(self):
        r=dict(beds=[],doors=[],invalid_states=0,sha256='a'*64)
        self.assertEqual('decoration',meaning('minecraft:village/plains/houses/animal_pen',r)['kind'])
        r['beds']=[[1,2,3]]
        self.assertEqual('residence',meaning('example:multipart/upper_floor',r)['kind'])
    def test_invalid_palette_suppresses_capacity(self):
        r=dict(beds=[[1,2,3]],doors=[],invalid_states=1,sha256='a'*64)
        self.assertEqual([],meaning('example:house',r)['beds'])
    def test_cyclic_pool_graph_terminates(self):
        audit={'pools':{'a':[{'value':{'fallback':'b'}}],'b':[{'value':{'fallback':'a'}}]},'templates':{}}
        self.assertEqual(({'a','b'},set()),reachable(audit,['a']))

    def test_road_attachment_points_are_not_building_entrances(self):
        r=dict(beds=[],doors=[],connectors=[[1,2,3]],invalid_states=0,sha256='a'*64)
        self.assertEqual([],meaning('minecraft:village/plains/streets/straight',r)['entrances'])
        r['beds']=[[4,5,6]]
        self.assertEqual([(1,2,3)],meaning('minecraft:village/plains/houses/cottage',r)['entrances'])

    def test_bedless_workshop_is_service_without_residential_capacity(self):
        r=dict(beds=[],doors=[],blocks={'minecraft:smithing_table':1},invalid_states=0,sha256='a'*64)
        self.assertEqual('service',meaning('minecraft:village/plains/houses/smith',r)['kind'])
        self.assertEqual([],meaning('minecraft:village/plains/houses/smith',r)['beds'])

    def test_hostile_profile_covers_child_pools_without_excluding_shared_pools(self):
        prefix = 'villagesandpillages:village_witch/'
        audit = {'structures': {'villagesandpillages:village_witch': [
            {'value': {'start_pool': prefix+'center'}}]}, 'templates': {}, 'pools': {
            prefix+'center': [{'value': {'fallback': prefix+'road_and_house'}}],
            prefix+'road_and_house': [{'value': {'fallback': prefix+'terminators'}}],
            prefix+'terminators': [{'value': {'fallback': 'minecraft:empty'}}],
            prefix+'decoration': [{'value': {}}],
            'minecraft:empty': [{'value': {}}]}}
        profiles, _ = generate(audit)
        profile = profiles['villagesandpillages_village_witch']
        self.assertEqual('hostile', profile['intent'])
        self.assertEqual([prefix+'center', prefix+'decoration', prefix+'road_and_house', prefix+'terminators'],
                         profile['target_pools'])
        self.assertNotIn('minecraft:empty', profile['target_pools'])
