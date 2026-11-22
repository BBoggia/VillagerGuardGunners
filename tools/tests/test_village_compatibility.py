"""Failure-oriented checks for production QA evidence and launch boundaries."""
import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import run_village_compatibility as qa


class CompatibilityTests(unittest.TestCase):
    def test_userdev_and_class_directories_rejected(self):
        for args in (['--launchTarget', 'forgeclientuserdev'], ['--launchTarget'],
                     ['--launchTarget', 'forgeclient', '/x/build/classes/java/main'],
                     ['--launchTarget', 'forgeclient', '/x/forge_mapped_official.jar']):
            with self.subTest(args=args), self.assertRaises(ValueError):
                qa.reject_development(args)
        qa.reject_development(['--launchTarget', 'forgeclient', '/installed/libraries/forge.jar'])

    def test_inherited_jvm_injection_is_rejected(self):
        with patch.dict(os.environ, {'JAVA_TOOL_OPTIONS': '-Dchanged=true'}), self.assertRaises(ValueError):
            qa.clean_environment()

    def test_fresh_normal_world_has_no_template_or_old_player(self):
        with tempfile.TemporaryDirectory() as temporary:
            world = Path(temporary) / 'world'
            qa.fresh_world(world, -123, ['villagerguardgunners'])
            data = qa.plain_nbt(qa.read_nbt(world / 'level.dat')['Data'])
            self.assertEqual(3465, data['DataVersion'])
            self.assertEqual(-123, data['WorldGenSettings']['seed'])
            self.assertEqual('minecraft:noise', data['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']['type'])
            self.assertNotIn('Player', data)
            self.assertEqual(['level.dat'], [p.name for p in world.iterdir()])
            with self.assertRaises(FileExistsError):
                qa.fresh_world(world, 1, [])

    def test_datapack_hashes_and_enablement(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            pack = root / 'pack'
            pack.mkdir()
            (pack / 'pack.mcmeta').write_text('{"pack":{"pack_format":15}}')
            initial = qa.datapack_manifest({'contribution': str(pack)})
            (pack / 'test.json').write_text('{}')
            self.assertNotEqual(initial, qa.datapack_manifest({'contribution': str(pack)}))
            qa.fresh_world(root / 'world', 1, [], ['contribution'])
            data = qa.plain_nbt(qa.read_nbt(root / 'world/level.dat')['Data'])
            self.assertIn('file/contribution', data['DataPacks']['Enabled'])
            with self.assertRaises(ValueError):
                qa.datapack_manifest({'../escape': str(pack)})
            (pack / 'link').symlink_to(pack / 'test.json')
            with self.assertRaises(ValueError):
                qa.datapack_manifest({'contribution': str(pack)})

    def test_design_preview_world_is_flat_without_natural_structures(self):
        with tempfile.TemporaryDirectory() as temporary:
            world = Path(temporary) / 'world'
            qa.fresh_world(world, 123, ['villagerguardgunners'], ['barracks'], design_preview=True)
            data = qa.plain_nbt(qa.read_nbt(world / 'level.dat')['Data'])
            generator = data['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']
            self.assertEqual('minecraft:flat', generator['type'])
            self.assertEqual([], generator['settings']['structure_overrides'])
            self.assertEqual(125, sum(layer['height'] for layer in generator['settings']['layers']))
            self.assertIn('file/barracks', data['DataPacks']['Enabled'])
            self.assertNotIn('Player', data)

    def test_mixinextras_metadata_does_not_replace_active_service_evidence(self):
        candidates = [
            {'mods': {'mixinextras': version}, 'attributes': {'FMLModType': 'GAMELIBRARY'},
             'embedded': {'identifier': {'group': group, 'artifact': 'mixinextras-forge'}}}
            for group, version in [('old', '0.2'), ('new', '0.3'), ('new', '0.4')]]
        select = lambda entries: max(entries, key=lambda e: e['mods']['mixinextras'])
        self.assertEqual(['0.2', '0.4'], qa.embedded_metadata_versions('mixinextras', candidates, select))
        with self.assertRaises(ValueError):
            qa.embedded_metadata_versions('other', candidates, select)
        with tempfile.TemporaryDirectory() as temporary:
            run = Path(temporary)
            self.fixture(run)
            manifest = json.loads((run / 'manifest.json').read_text())
            manifest['identity']['jars'] = {'versions': {'mixinextras': '0.4'},
                'embeddedMetadataVersions': {'mixinextras': ['0.2', '0.4']}}
            qa.atomic_json(run / 'manifest.json', manifest)
            result = json.loads((run / 'result.json').read_text())
            result['mods'] = [{'id': 'mixinextras', 'version': '0.2'}]
            qa.atomic_json(run / 'result.json', result)
            self.assertFalse(qa.evaluate(run)['passed'])
            log = run / 'client/logs/latest.log'
            log.parent.mkdir(parents=True)
            log.write_text('Initializing MixinExtras via service(version=0.4).\n')
            self.assertTrue(qa.evaluate(run)['passed'])
            log.write_text('Initializing MixinExtras via service(version=0.2).\n')
            self.assertFalse(qa.evaluate(run)['passed'])

    def fixture(self, run, expectations=None):
        expectations = expectations or [{'path': 'result.natural.guards', 'op': 'eq', 'value': 6}]
        qa.atomic_json(run / 'manifest.json', {'exitCode': 0, 'status': 'passed', 'identityHash': 'abc',
                                             'identity': {'case': {'expectations': expectations}}})
        qa.atomic_json(run / 'result.json', {'status': 'observed', 'production': True,
                                           'releaseCodeSourcesVerified': True, 'natural': {'guards': 6}})

    def test_successful_launch_with_wrong_outcome_exits_nonzero(self):
        with tempfile.TemporaryDirectory() as temporary:
            run = Path(temporary)
            self.fixture(run, [{'path': 'result.natural.guards', 'op': 'eq', 'value': 9}])
            completed = subprocess.run([sys.executable, str(qa.REPO / 'tools/run_village_compatibility.py'),
                                        'evaluate', str(run)], capture_output=True, text=True)
            self.assertEqual(1, completed.returncode)
            self.assertFalse(json.loads(completed.stdout)['passed'])

    def test_missing_evidence_and_fake_production_flag_fail(self):
        with tempfile.TemporaryDirectory() as temporary:
            run = Path(temporary)
            self.fixture(run, [{'path': 'result.checkpointResults.0.arrivedWithin3', 'op': 'eq', 'value': True}])
            self.assertFalse(qa.evaluate(run)['passed'])
            result = json.loads((run / 'result.json').read_text())
            result['production'] = 1
            qa.atomic_json(run / 'result.json', result)
            self.assertFalse(qa.evaluate(run, [])['passed'])

    def test_no_route_selected_cannot_satisfy_arrival_requirement(self):
        with tempfile.TemporaryDirectory() as temporary:
            run = Path(temporary)
            self.fixture(run, [{'path': 'result.controlledBehavior.arrivals', 'op': 'gte', 'value': 1}])
            result = json.loads((run / 'result.json').read_text())
            result['controlledBehavior'] = {'routesIssued': 0, 'arrivals': 0, 'failedIssuedRoutes': 0, 'noRouteSelected': True}
            qa.atomic_json(run / 'result.json', result)
            self.assertFalse(qa.evaluate(run)['passed'])

    def test_loaded_versions_and_effective_settings_must_match(self):
        with tempfile.TemporaryDirectory() as temporary:
            run = Path(temporary)
            self.fixture(run)
            manifest = json.loads((run / 'manifest.json').read_text())
            manifest['identity']['jars'] = {'versions': {'example': '2'}}
            manifest['identity']['case']['features'] = {'maximum': 2}
            qa.atomic_json(run / 'manifest.json', manifest)
            result = json.loads((run / 'result.json').read_text())
            result.update(mods=[{'id': 'example', 'version': '1'}], effectiveSettings={'maximumGuards': 16})
            qa.atomic_json(run / 'result.json', result)
            self.assertFalse(qa.evaluate(run)['passed'])
            result.update(mods=[{'id': 'example', 'version': '2'}], effectiveSettings={'maximumGuards': 2})
            qa.atomic_json(run / 'result.json', result)
            self.assertTrue(qa.evaluate(run)['passed'])

    def test_world_evidence_detects_external_terrain_edits(self):
        with tempfile.TemporaryDirectory() as temporary:
            world = Path(temporary) / 'world'
            qa.fresh_world(world, 123, [])
            (world / 'region').mkdir()
            chunk = world / 'region/r.0.0.mca'
            chunk.write_bytes(b'original fixture terrain')
            original = qa.world_evidence(world)
            chunk.write_bytes(b'edited fixture terrain')
            self.assertNotEqual(original, qa.world_evidence(world))

    def test_resume_rejects_modified_evidence_and_changed_identity(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            run = output / 'runs/control-20260101T000000.000001Z'
            self.fixture(run)
            manifest = json.loads((run / 'manifest.json').read_text())
            manifest['evidence'] = qa.evidence_hashes(run)
            qa.atomic_json(run / 'manifest.json', manifest)
            self.assertEqual(run, qa.completed_case(output, 'control', 'abc'))
            self.assertIsNone(qa.completed_case(output, 'control', 'different'))
            (run / 'result.json').write_text((run / 'result.json').read_text() + '\n')
            self.assertIsNone(qa.completed_case(output, 'control', 'abc'))

    def test_nested_mods_and_manifest_version_are_read(self):
        with tempfile.TemporaryDirectory() as temporary:
            child = io.BytesIO()
            with zipfile.ZipFile(child, 'w') as jar:
                jar.writestr('META-INF/mods.toml', '[[mods]]\nmodId="embedded"\nversion="${file.jarVersion}"\n')
                jar.writestr('META-INF/MANIFEST.MF', 'Implementation-Version: 4.12.0\r\n')
            path = Path(temporary) / 'library.jar'
            with zipfile.ZipFile(path, 'w') as jar:
                jar.writestr('META-INF/jarjar/metadata.json', json.dumps({'jars': [{'path': 'inner.jar'}]}))
                jar.writestr('inner.jar', child.getvalue())
            entries = qa.jar_metadata(path)
            self.assertEqual({'embedded': '4.12.0'}, entries[1]['mods'])
            self.assertIn('!/inner.jar', entries[1]['origin'])

    def test_missing_dependency_is_rejected_before_launch(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            jar = root / 'test.jar'
            with zipfile.ZipFile(jar, 'w') as archive:
                archive.writestr('META-INF/mods.toml', 'modLoader="javafml"\nloaderVersion="[47,)"\n'
                                 '[[mods]]\nmodId="example"\nversion="1"\n'
                                 '[[dependencies.example]]\nmodId="missing"\nmandatory=true\nversionRange="[1,)"\n')
            config = {'_base': temporary, 'runtime': {'forgeVersion': '47.4.10'}, 'jarDirectories': [temporary]}
            with patch.object(qa, 'BASE_MODS', ['example']), self.assertRaisesRegex(ValueError, 'missing'):
                qa.select_jars(config, {})

    def test_wrong_hash_and_ambiguous_versions_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for version in ('1', '2'):
                with zipfile.ZipFile(root / (version + '.jar'), 'w') as archive:
                    archive.writestr('META-INF/mods.toml', 'modLoader="javafml"\nloaderVersion="[47,)"\n'
                                     f'[[mods]]\nmodId="example"\nversion="{version}"\n')
            config = {'_base': temporary, 'runtime': {'forgeVersion': '47.4.10'}, 'jarDirectories': [temporary]}
            with patch.object(qa, 'BASE_MODS', ['example']):
                with self.assertRaisesRegex(ValueError, 'distinct candidates'):
                    qa.select_jars(config, {})
                config['mods'] = {'example': {'path': '1.jar', 'sha256': 'incorrect'}}
                with self.assertRaisesRegex(ValueError, 'SHA256 mismatch'):
                    qa.select_jars(config, {})

    def test_unknown_assertion_operator_and_empty_scenario_rejected(self):
        with self.assertRaises(ValueError):
            qa.validate_expectations([{'path': 'result.x', 'op': 'approximately', 'value': 1}])
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'config.json'
            qa.atomic_json(path, {'schema': 1, 'cases': [{'name': 'control', 'expectations': []}]})
            with self.assertRaisesRegex(ValueError, 'explicit outcome'):
                qa.load_config(path)

    def test_interrupted_owned_run_preserves_manifest(self):
        with tempfile.TemporaryDirectory() as temporary:
            identity = {'case': {'name': 'control'}, 'jars': {'files': [], 'versions': {}}, 'runtime': {}}
            with patch.object(qa, 'launch_arguments', return_value=['java']), \
                    patch.object(qa, 'execute_stage', side_effect=KeyboardInterrupt), \
                    contextlib.redirect_stdout(io.StringIO()), self.assertRaises(KeyboardInterrupt):
                qa.run_case(Path(temporary), identity, 'abc')
            run = next((Path(temporary) / 'runs').iterdir())
            self.assertEqual('interrupted', json.loads((run / 'manifest.json').read_text())['status'])
            self.assertEqual(run.name, json.loads((Path(temporary) / 'attempts.json').read_text())['latest']['control'])


if __name__ == '__main__':
    unittest.main()
