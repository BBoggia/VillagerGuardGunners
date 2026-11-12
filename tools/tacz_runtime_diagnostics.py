"""Classify the exact resource error reproduced with official TaCZ alone.

Keep this diagnostic in reports. Other missing references, additional lines,
different dependency artifacts, and all other errors remain failures.
"""

TACZ_120_NEOFORGE_SHA256 = 'b01384895b51a32e65ae9e2de6b99fa92b2f9285c2dafcac2112932fc93bff21'
TAG_ERROR = "[minecraft/TagLoader]: Couldn't load tag minecraft:placeable as it is missing following references:"
MISSING_BLOCK = 'tacz:blood_strike_1 (from mod/tacz)'


def pending_known_error(line, jar_hashes):
    return (TACZ_120_NEOFORGE_SHA256 in jar_hashes.values()
            and '/ERROR]' in line and line.strip().endswith(TAG_ERROR))


def classify_errors(text, jar_hashes):
    lines = text.splitlines()
    errors, known = [], []
    for i, line in enumerate(lines):
        if '/ERROR]' not in line and '/FATAL]' not in line:
            continue
        errors.append(line.strip())
        if (pending_known_error(line, jar_hashes) and i + 1 < len(lines)
                and lines[i + 1].strip() == MISSING_BLOCK
                and (i + 2 == len(lines) or not lines[i + 2].startswith(('\t', ' ')))):
            known.append(line.strip())
    return errors, known
