import json
import sys
import tempfile
import unittest
import zipfile
import contextlib
import io
from unittest.mock import patch
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_combined_building_packs import combine, source_names, PROVIDERS
import build_combined_building_packs as builder


class CombinedBuildingPackTests(unittest.TestCase):
    def pack(self, path, resources, format_number=15):
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('pack.mcmeta', json.dumps({'pack': {'pack_format': format_number}}))
            for name, payload in resources.items():
                archive.writestr(name, payload)
        return path

    def test_union_preserves_bytes_and_is_independent_of_source_order(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = self.pack(root / 'first.zip', {'data/a/structures/tower.nbt': b'\x00\xff',
                                                  'data/a/shared.json': b'{}'})
            second = self.pack(root / 'second.zip', {'data/b/village_buildings/yard.json': b'{"weight":1}',
                                                    'data/a/shared.json': b'{}'})
            a, b = root / 'a.zip', root / 'b.zip'
            combine([first, second], a, 'All buildings')
            combine([second, first], b, 'All buildings')
            self.assertEqual(a.read_bytes(), b.read_bytes())
            with zipfile.ZipFile(a) as archive:
                self.assertEqual(4, len(archive.namelist()))
                self.assertEqual(b'\x00\xff', archive.read('data/a/structures/tower.nbt'))
                self.assertEqual(b'{"weight":1}', archive.read('data/b/village_buildings/yard.json'))
                self.assertEqual(15, json.loads(archive.read('pack.mcmeta'))['pack']['pack_format'])

    def test_conflict_keeps_previous_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = self.pack(root / 'first.zip', {'data/a/example.json': b'old'})
            second = self.pack(root / 'second.zip', {'data/a/example.json': b'new'})
            target = root / 'combined.zip'
            target.write_bytes(b'previous candidate')
            with self.assertRaisesRegex(ValueError, 'Conflicting resource'):
                combine([first, second], target, 'All buildings')
            self.assertEqual(b'previous candidate', target.read_bytes())

    def test_missing_or_wrong_format_source_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(FileNotFoundError):
                combine([root / 'missing.zip'], root / 'out.zip', 'All buildings')
            source = self.pack(root / 'wrong.zip', {'data/a/file.json': b'{}'}, 10)
            with self.assertRaisesRegex(ValueError, 'datapack format'):
                combine([source], root / 'out.zip', 'All buildings')
            self.assertFalse((root / 'out.zip').exists())

    def test_each_provider_selects_five_distinct_individual_packs(self):
        names = [name for provider in PROVIDERS for name in source_names(provider)]
        self.assertEqual(25, len(set(names)))
        self.assertTrue(all('all-buildings' not in name for name in names))

    def test_universal_download_contains_every_provider_and_family_with_original_ids(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            distributions = root / 'build/distributions'
            distributions.mkdir(parents=True)
            expected = {}
            for provider in PROVIDERS:
                for family, name in enumerate(source_names(provider)):
                    resource = f'data/example/village_buildings/{provider}/{family}.json'
                    payload = json.dumps({'provider': provider, 'family': family}).encode()
                    expected[resource] = payload
                    self.pack(distributions / name, {resource: payload})
            with patch.object(builder, 'ROOT', root), contextlib.redirect_stdout(io.StringIO()):
                builder.main()
            with zipfile.ZipFile(distributions / builder.UNIVERSAL_NAME) as archive:
                actual = {n: archive.read(n) for n in archive.namelist() if n.startswith('data/')}
                self.assertEqual(expected, actual)
                self.assertEqual(25, len(actual))


if __name__ == '__main__':
    unittest.main()
