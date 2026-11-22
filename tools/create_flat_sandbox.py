"""Minimal standard NBT codec to create a fresh flat world from version-matched metadata."""
import gzip
import io
import struct
from pathlib import Path


def read_payload(stream, kind):
    def number(fmt):
        return struct.unpack('>' + fmt, stream.read(struct.calcsize('>' + fmt)))[0]
    if kind in (1, 2, 3, 4, 5, 6):
        return number({1:'b', 2:'h', 3:'i', 4:'q', 5:'f', 6:'d'}[kind])
    if kind == 7:
        return stream.read(number('i'))
    if kind == 8:
        return stream.read(number('H')).decode('utf-8')
    if kind == 9:
        child, count = number('b'), number('i')
        return child, [read_payload(stream, child) for _ in range(count)]
    if kind == 10:
        values = {}
        while True:
            child = number('b')
            if child == 0:
                return values
            name = read_payload(stream, 8)
            values[name] = (child, read_payload(stream, child))
    if kind in (11, 12):
        return [number('i' if kind == 11 else 'q') for _ in range(number('i'))]
    raise ValueError(f'Unsupported NBT type {kind}')


def write_payload(stream, kind, value):
    def number(fmt, val):
        stream.write(struct.pack('>' + fmt, val))
    if kind in (1, 2, 3, 4, 5, 6):
        number({1:'b', 2:'h', 3:'i', 4:'q', 5:'f', 6:'d'}[kind], value)
    elif kind == 7:
        number('i', len(value)); stream.write(value)
    elif kind == 8:
        raw = value.encode('utf-8'); number('H', len(raw)); stream.write(raw)
    elif kind == 9:
        child, values = value; number('b', child); number('i', len(values))
        for item in values:
            write_payload(stream, child, item)
    elif kind == 10:
        for name, (child, item) in value.items():
            number('b', child); write_payload(stream, 8, name); write_payload(stream, child, item)
        number('b', 0)
    elif kind in (11, 12):
        number('i', len(value))
        for item in value:
            number('i' if kind == 11 else 'q', item)
    else:
        raise ValueError(f'Unsupported NBT type {kind}')


def create(template: Path, destination: Path):
    if destination.exists():
        raise ValueError('Fresh world destination must not already exist')
    stream = io.BytesIO(gzip.decompress(template.read_bytes()))
    if stream.read(1) != b'\x0a':
        raise ValueError('level.dat must be an NBT compound')
    name = read_payload(stream, 8)
    root = read_payload(stream, 10)
    data = root['Data'][1]
    dimensions = data['WorldGenSettings'][1]['dimensions'][1]
    settings = {
        'biome': (8, 'minecraft:plains'), 'lakes': (1, 0), 'features': (1, 0),
        'structure_overrides': (9, (8, [])),
        'layers': (9, (10, [{'height': (3, height), 'block': (8, 'minecraft:' + block)}
                           for height, block in [(1, 'bedrock'), (123, 'dirt'), (1, 'grass_block')]])),
    }
    dimensions['minecraft:overworld'][1]['generator'] = (10, {'type': (8, 'minecraft:flat'), 'settings': (10, settings)})
    data['WorldGenSettings'][1]['seed'] = (4, 20260905)
    data.update({'LevelName': (8, 'Village Defense Playground'), 'allowCommands': (1, 1),
                 'GameType': (3, 1), 'hardcore': (1, 0), 'Difficulty': (1, 2),
                 'DifficultyLocked': (1, 0), 'SpawnX': (3, 0), 'SpawnY': (3, 61), 'SpawnZ': (3, 0),
                 'Time': (4, 0), 'DayTime': (4, 4000)})
    data.pop('Player', None)
    output = io.BytesIO(b'\x0a'); output.seek(1)
    write_payload(output, 8, name); write_payload(output, 10, root)
    destination.mkdir(parents=True)
    (destination / 'level.dat').write_bytes(gzip.compress(output.getvalue()))
    (destination / '.vgg-flat-world').write_text('Superflat: bedrock + dirt + grass; standing height 61; seed 20260905.\n')
