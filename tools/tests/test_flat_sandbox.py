import gzip
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('flat', Path(__file__).parents[1] / 'create_flat_sandbox.py')
flat = importlib.util.module_from_spec(spec)
spec.loader.exec_module(flat)


class FlatWorldTest(unittest.TestCase):
    def test_all_nbt_payload_types_round_trip(self):
        values = {1: -12, 2: 1234, 3: 1234567, 4: 2**50, 5: 1.5, 6: -7.25,
                  7: b'\x00\xff', 8: 'Village', 9: (3, [1, 2, 3]),
                  10: {'nested': (8, 'value')}, 11: [1, -2], 12: [2**45, -1]}
        for kind, value in values.items():
            with self.subTest(kind=kind):
                stream = io.BytesIO()
                flat.write_payload(stream, kind, value)
                stream.seek(0)
                self.assertEqual(value, flat.read_payload(stream, kind))

    def test_new_world_is_flat_and_existing_destination_is_protected(self):
        data = {'DataVersion': (3, 3465), 'Player': (10, {'Health': (5, 20.0)}),
                'WorldGenSettings': (10, {'seed': (4, 123), 'dimensions': (10, {
                    'minecraft:overworld': (10, {'type': (8, 'minecraft:overworld'),
                                               'generator': (10, {'type': (8, 'minecraft:noise')})})})})}
        stream = io.BytesIO(b'\x0a'); stream.seek(1)
        flat.write_payload(stream, 8, '')
        flat.write_payload(stream, 10, {'Data': (10, data)})
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            template = root / 'template.dat'; template.write_bytes(gzip.compress(stream.getvalue()))
            destination = root / 'playground'
            flat.create(template, destination)
            decoded = io.BytesIO(gzip.decompress((destination / 'level.dat').read_bytes()))
            decoded.read(1); flat.read_payload(decoded, 8)
            saved = flat.read_payload(decoded, 10)['Data'][1]
            generator = saved['WorldGenSettings'][1]['dimensions'][1]['minecraft:overworld'][1]['generator'][1]
            self.assertEqual('minecraft:flat', generator['type'][1])
            self.assertEqual(125, sum(layer['height'][1] for layer in generator['settings'][1]['layers'][1][1]))
            self.assertEqual(3465, saved['DataVersion'][1])
            self.assertNotIn('Player', saved)
            self.assertFalse((destination / 'region').exists())
            original = (destination / 'level.dat').read_bytes()
            with self.assertRaises(ValueError):
                flat.create(template, destination)
            self.assertEqual(original, (destination / 'level.dat').read_bytes())


if __name__ == '__main__':
    unittest.main()
