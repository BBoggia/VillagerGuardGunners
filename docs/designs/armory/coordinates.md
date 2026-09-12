# Regional Armory coordinate schedule

Generated architectural reservations. Read with the [drawing guide](../../regional-armory-drawings.md).

Positions are `(X,Y,Z)` unless labeled X/Z. Bounds are inclusive; north is Z=0.
Size order is width × depth × height. Floor Y is the standing surface, not the floor block.
All door leaves face north, start closed, and have matching upper/lower halves.
Both pairs in a Snowy lobby retain this orientation. Roof ranges describe material volumes,
not finalized stair states. The detailed half-block roof columns are in [drawings.json](drawings.json).

## Snowy / Compact storehouse

[Drawing sheet](snowy-compact.svg) · 15 × 17 × 12 · standing Y=1

| Reservation | Position or bounds |
| --- | --- |
| Armory, north-facing | (11, 1, 14) |
| Sentry | (10, 1, 2); anchor offset (-1, 0, -12) |
| First landing | X=5..6, Z=0..0; surface Y=1 |
| store shell | X=1..13, Z=7..15 |
| lobby shell | X=4..10, Z=3..7 |
| aisle | X=7..8, Z=8..13 |
| entry landing | X=6..8, Z=8..10 |
| sorting | X=9..11, Z=8..10 |
| service | X=10..12, Z=11..13 |
| bypass | X=7..9, Z=11..14 |
| service crossing | X=7..12, Z=11..13 |
| lobby | X=5..9, Z=4..6 |
| approach | X=5..6, Z=0..2 |

| Door pair | West leaf / hinge | East leaf / hinge |
| --- | --- | --- |
| store | (7, 1, 7) / left | (8, 1, 7) / right |
| outer lobby | (5, 1, 3) / left | (6, 1, 3) / right |

| Furniture | Base positions |
| --- | --- |
| crafting | (12, 1, 8) |
| worktop | (12, 1, 9) |
| shelf | (2, 1, 10); (2, 1, 11); (2, 1, 12); (2, 1, 13) |
| seat | (2, 1, 8) |

| Opening or decoration | Positions |
| --- | --- |
| west glazing, one block high | (1, 2, 8); (1, 2, 9) |
| east glazing, one block high | (13, 2, 8); (13, 2, 9) |
| west shutter | (0, 2, 7); solid backing (1, 2, 7) |
| west shutter | (0, 2, 10); solid backing (1, 2, 10) |
| east shutter | (14, 2, 7); solid backing (13, 2, 7) |
| east shutter | (14, 2, 10); solid backing (13, 2, 10) |
| blue banner | (12, 3, 6); backing (12, 3, 7) |

| Light | Lantern | Support beam endpoints |
| --- | --- | --- |
| porch | (7, 4, 2) | (4, 5, 2) → (11, 5, 2) |
| lobby | (7, 4, 5) | (4, 5, 5) → (10, 5, 5) |
| room | (8, 4, 10) | (1, 5, 10) → (13, 5, 10) |
| supply | (11, 4, 13) | (1, 5, 13) → (13, 5, 13) |

| Roof | X/Z footprint | Shape / rise axis | Surface Y range |
| --- | --- | --- | --- |
| lobby | X=3..11, Z=3..7 | flat / z | 6.5..6.5 |
| porch | X=3..12, Z=1..3 | flat / z | 6.5..6.5 |
| main | X=0..14, Z=6..16 | gable / x | 6.5..11.5 |

Post cells (X/Z): (4, 2); (11, 2).

## Snowy / Supply yard

[Drawing sheet](snowy-supply-yard.svg) · 17 × 20 × 12 · standing Y=1

| Reservation | Position or bounds |
| --- | --- |
| Armory, north-facing | (11, 1, 17) |
| Sentry | (12, 1, 2); anchor offset (1, 0, -15) |
| First landing | X=6..7, Z=0..0; surface Y=1 |
| store shell | X=1..15, Z=11..18 |
| lobby shell | X=5..11, Z=7..11 |
| aisle | X=8..9, Z=12..16 |
| interior crossing | X=8..14, Z=12..13 |
| service | X=10..12, Z=14..16 |
| bypass | X=13..14, Z=12..17 |
| packing | X=3..5, Z=13..15 |
| work bay | X=2..4, Z=7..9 |
| work crossing | X=2..7, Z=5..6 |
| approach | X=6..7, Z=0..6 |
| lobby | X=6..10, Z=8..10 |
| Open court | X=8..11, Z=2..5 |

| Door pair | West leaf / hinge | East leaf / hinge |
| --- | --- | --- |
| store | (8, 1, 11) / left | (9, 1, 11) / right |
| outer lobby | (6, 1, 7) / left | (7, 1, 7) / right |

| Furniture | Base positions |
| --- | --- |
| crafting | (2, 1, 4) |
| worktop | (2, 1, 13); (2, 1, 14); (3, 1, 4) |
| shelf | (2, 1, 17); (3, 1, 17); (4, 1, 17); (5, 1, 17) |
| seat | (3, 1, 12); (4, 1, 12) |
| crate | (2, 1, 3) |

| Opening or decoration | Positions |
| --- | --- |
| west glazing, one block high | (1, 2, 13); (1, 2, 14) |
| east glazing, one block high | (15, 2, 14); (15, 2, 15) |
| west shutter | (0, 2, 12); solid backing (1, 2, 12) |
| west shutter | (0, 2, 15); solid backing (1, 2, 15) |
| east shutter | (16, 2, 13); solid backing (15, 2, 13) |
| east shutter | (16, 2, 16); solid backing (15, 2, 16) |
| blue banner | (14, 3, 10); backing (14, 3, 11) |

| Light | Lantern | Support beam endpoints |
| --- | --- | --- |
| workshop | (3, 4, 7) | (1, 5, 7) → (5, 5, 7) |
| crossing | (6, 4, 6) | (1, 5, 6) → (8, 5, 6) |
| lobby | (8, 4, 9) | (5, 5, 9) → (11, 5, 9) |
| sentry | (13, 4, 1) | (12, 5, 1) → (15, 5, 1) |
| room | (5, 4, 13) | (1, 5, 13) → (15, 5, 13) |
| supply | (11, 4, 17) | (1, 5, 17) → (15, 5, 17) |

| Roof | X/Z footprint | Shape / rise axis | Surface Y range |
| --- | --- | --- | --- |
| workshop | X=0..5, Z=2..10 | lean / x-reverse | 6.5..9 |
| crossing | X=2..7, Z=5..6 | flat / z | 6.5..6.5 |
| lobby | X=4..12, Z=6..11 | flat / z | 6.5..6.5 |
| sentry | X=12..15, Z=1..3 | flat / z | 6.5..6.5 |
| main | X=0..16, Z=10..19 | gable / z | 6.5..10.5 |

Post cells (X/Z): (8, 6); (12, 1); (15, 1).

## Deep ocean · Tidal Towns / Compact storehouse

[Drawing sheet](ocean-compact.svg) · 16 × 16 × 11 · standing Y=2

| Reservation | Position or bounds |
| --- | --- |
| Armory, north-facing | (10, 2, 13) |
| Sentry | (3, 2, 2); anchor offset (-7, 0, -11) |
| First landing | X=6..7, Z=0..0; surface Y=1.5 |
| store shell | X=2..12, Z=6..14 |
| aisle | X=6..7, Z=7..12 |
| entry landing | X=5..7, Z=7..9 |
| sorting | X=8..10, Z=7..9 |
| service | X=9..11, Z=10..12 |
| bypass | X=6..8, Z=10..13 |
| service crossing | X=6..11, Z=10..12 |
| approach | X=6..7, Z=0..5 |
| deck crossing | X=3..12, Z=4..5 |
| Refuge | X=10..12, Z=2..4 |

| Door pair | West leaf / hinge | East leaf / hinge |
| --- | --- | --- |
| store | (6, 2, 6) / left | (7, 2, 6) / right |

| Furniture | Base positions |
| --- | --- |
| crafting | (11, 2, 7) |
| worktop | (11, 2, 8) |
| shelf | (3, 2, 9); (3, 2, 10); (3, 2, 11); (3, 2, 12) |
| seat | (3, 2, 7) |

| Opening or decoration | Positions |
| --- | --- |
| west glazing, one block high | (2, 3, 7); (2, 3, 8) |
| east glazing, one block high | (12, 3, 7); (12, 3, 8) |
| west shutter | (1, 3, 6); solid backing (2, 3, 6) |
| west shutter | (1, 3, 9); solid backing (2, 3, 9) |
| east shutter | (13, 3, 6); solid backing (12, 3, 6) |
| east shutter | (13, 3, 9); solid backing (12, 3, 9) |
| cyan banner | (11, 4, 5); backing (11, 4, 6) |

| Light | Lantern | Support beam endpoints |
| --- | --- | --- |
| porch | (7, 5, 2) | (2, 6, 2) → (13, 6, 2) |
| room | (7, 5, 9) | (2, 6, 9) → (12, 6, 9) |
| supply | (10, 5, 12) | (2, 6, 12) → (12, 6, 12) |

| Roof | X/Z footprint | Shape / rise axis | Surface Y range |
| --- | --- | --- | --- |
| porch | X=1..14, Z=1..6 | flat / z | 7.5..7.5 |
| main | X=1..13, Z=5..15 | gable / x | 7.5..10.5 |

Post cells (X/Z): (2, 2); (13, 2).

## Deep ocean · Tidal Towns / Supply yard

[Drawing sheet](ocean-supply-yard.svg) · 18 × 18 × 11 · standing Y=2

| Reservation | Position or bounds |
| --- | --- |
| Armory, north-facing | (9, 2, 15) |
| Sentry | (12, 2, 2); anchor offset (3, 0, -13) |
| First landing | X=6..7, Z=0..0; surface Y=1.5 |
| store shell | X=1..13, Z=9..16 |
| aisle | X=6..7, Z=10..14 |
| interior crossing | X=6..12, Z=10..11 |
| service | X=8..10, Z=12..14 |
| bypass | X=11..12, Z=10..15 |
| packing | X=3..5, Z=11..13 |
| work bay | X=2..4, Z=5..7 |
| work crossing | X=2..7, Z=7..8 |
| frontage | X=6..12, Z=7..8 |
| approach | X=6..7, Z=0..8 |
| eastern return | X=14..15, Z=3..12 |
| deck crossing | X=11..15, Z=5..6 |
| Open court | X=7..10, Z=2..5 |
| Refuge | X=14..16, Z=13..15 |

| Door pair | West leaf / hinge | East leaf / hinge |
| --- | --- | --- |
| store | (6, 2, 9) / left | (7, 2, 9) / right |

| Furniture | Base positions |
| --- | --- |
| crafting | (2, 2, 4) |
| worktop | (2, 2, 11); (2, 2, 12); (3, 2, 4) |
| shelf | (2, 2, 15); (3, 2, 15); (4, 2, 15); (5, 2, 15) |
| seat | (3, 2, 10); (4, 2, 10) |
| crate | (2, 2, 3) |

| Opening or decoration | Positions |
| --- | --- |
| west glazing, one block high | (1, 3, 11); (1, 3, 12) |
| east glazing, one block high | (13, 3, 12); (13, 3, 13) |
| west shutter | (0, 3, 10); solid backing (1, 3, 10) |
| west shutter | (0, 3, 13); solid backing (1, 3, 13) |
| cyan banner | (12, 4, 8); backing (12, 4, 9) |

| Light | Lantern | Support beam endpoints |
| --- | --- | --- |
| workshop | (3, 5, 5) | (1, 6, 5) → (5, 6, 5) |
| frontage | (10, 5, 7) | (1, 6, 7) → (13, 6, 7) |
| arrival | (12, 5, 1) | (11, 6, 1) → (16, 6, 1) |
| refuge | (15, 5, 15) | (13, 6, 15) → (17, 6, 15) |
| room | (5, 5, 11) | (1, 6, 11) → (13, 6, 11) |
| supply | (9, 5, 15) | (1, 6, 15) → (13, 6, 15) |

| Roof | X/Z footprint | Shape / rise axis | Surface Y range |
| --- | --- | --- | --- |
| workshop | X=0..6, Z=2..9 | lean / x-reverse | 7.5..9 |
| frontage | X=5..13, Z=7..9 | lean / z | 7..8 |
| sentry | X=11..16, Z=1..2 | flat / z | 7.5..7.5 |
| refuge | X=14..17, Z=12..16 | flat / z | 7.5..7.5 |
| main | X=0..14, Z=8..17 | gable / z | 7.5..9.5 |

Post cells (X/Z): (5, 5); (11, 1); (13, 7); (16, 1); (17, 15).

## Desert / Compact storehouse

[Drawing sheet](desert-compact.svg) · 13 × 13 × 9 · standing Y=1

| Reservation | Position or bounds |
| --- | --- |
| Armory, north-facing | (9, 1, 10) |
| Sentry | (8, 1, 2); anchor offset (-1, 0, -8) |
| First landing | X=5..6, Z=0..0; surface Y=1 |
| store shell | X=1..11, Z=3..11 |
| aisle | X=5..6, Z=4..9 |
| entry landing | X=4..6, Z=4..6 |
| sorting | X=7..9, Z=4..6 |
| service | X=8..10, Z=7..9 |
| bypass | X=5..7, Z=7..10 |
| service crossing | X=5..10, Z=7..9 |
| approach | X=5..6, Z=0..2 |

| Door pair | West leaf / hinge | East leaf / hinge |
| --- | --- | --- |
| store | (5, 1, 3) / left | (6, 1, 3) / right |

| Furniture | Base positions |
| --- | --- |
| crafting | (10, 1, 4) |
| worktop | (10, 1, 5) |
| shelf | (2, 1, 6); (2, 1, 7); (2, 1, 8); (2, 1, 9) |
| seat | (2, 1, 4) |

| Opening or decoration | Positions |
| --- | --- |
| west glazing, one block high | (1, 3, 4); (1, 3, 5) |
| east glazing, one block high | (11, 3, 4); (11, 3, 5) |
| west shutter | (0, 3, 3); solid backing (1, 3, 3) |
| west shutter | (0, 3, 6); solid backing (1, 3, 6) |
| east shutter | (12, 3, 3); solid backing (11, 3, 3) |
| east shutter | (12, 3, 6); solid backing (11, 3, 6) |
| yellow banner | (10, 3, 2); backing (10, 3, 3) |

| Light | Lantern | Support beam endpoints |
| --- | --- | --- |
| porch | (6, 4, 1) | (3, 5, 1) → (10, 5, 1) |
| room | (6, 4, 6) | (1, 5, 6) → (11, 5, 6) |
| supply | (9, 4, 9) | (1, 5, 9) → (11, 5, 9) |

| Roof | X/Z footprint | Shape / rise axis | Surface Y range |
| --- | --- | --- | --- |
| porch | X=2..11, Z=1..3 | flat / z | 6.5..6.5 |
| main | X=0..12, Z=2..12 | flat / x | 7..7.5 |

Post cells (X/Z): (3, 1); (10, 1).

## Desert / Supply yard

[Drawing sheet](desert-supply-yard.svg) · 16 × 17 × 9 · standing Y=1

| Reservation | Position or bounds |
| --- | --- |
| Armory, north-facing | (10, 1, 14) |
| Sentry | (11, 1, 2); anchor offset (1, 0, -12) |
| First landing | X=7..8, Z=0..0; surface Y=1 |
| store shell | X=1..14, Z=8..15 |
| aisle | X=7..8, Z=9..13 |
| interior crossing | X=7..13, Z=9..10 |
| service | X=9..11, Z=11..13 |
| bypass | X=12..13, Z=9..14 |
| packing | X=3..5, Z=10..12 |
| work bay | X=2..4, Z=5..7 |
| work crossing | X=2..7, Z=6..7 |
| frontage | X=6..13, Z=6..7 |
| approach | X=7..8, Z=0..7 |
| Open court | X=7..10, Z=2..5 |

| Door pair | West leaf / hinge | East leaf / hinge |
| --- | --- | --- |
| store | (7, 1, 8) / left | (8, 1, 8) / right |

| Furniture | Base positions |
| --- | --- |
| crafting | (2, 1, 4) |
| worktop | (2, 1, 10); (2, 1, 11); (3, 1, 4) |
| shelf | (2, 1, 14); (3, 1, 14); (4, 1, 14); (5, 1, 14) |
| seat | (3, 1, 9); (4, 1, 9) |
| crate | (2, 1, 3) |

| Opening or decoration | Positions |
| --- | --- |
| west glazing, one block high | (1, 3, 10); (1, 3, 11) |
| east glazing, one block high | (14, 3, 11); (14, 3, 12) |
| west shutter | (0, 3, 9); solid backing (1, 3, 9) |
| west shutter | (0, 3, 12); solid backing (1, 3, 12) |
| east shutter | (15, 3, 10); solid backing (14, 3, 10) |
| east shutter | (15, 3, 13); solid backing (14, 3, 13) |
| yellow banner | (13, 3, 7); backing (13, 3, 8) |

| Light | Lantern | Support beam endpoints |
| --- | --- | --- |
| workshop | (3, 4, 5) | (1, 5, 5) → (5, 5, 5) |
| frontage | (10, 4, 6) | (1, 5, 6) → (14, 5, 6) |
| arrival | (12, 4, 1) | (11, 5, 1) → (14, 5, 1) |
| room | (5, 4, 10) | (1, 5, 10) → (14, 5, 10) |
| supply | (10, 4, 14) | (1, 5, 14) → (14, 5, 14) |

| Roof | X/Z footprint | Shape / rise axis | Surface Y range |
| --- | --- | --- | --- |
| workshop | X=0..6, Z=2..8 | flat / x-reverse | 6.5..6.5 |
| frontage | X=5..14, Z=6..8 | flat / z | 6.5..6.5 |
| sentry | X=11..14, Z=1..2 | flat / z | 6.5..6.5 |
| main | X=0..15, Z=7..16 | flat / z | 7..7.5 |

Post cells (X/Z): (5, 5); (11, 1); (14, 1); (14, 6).

## Savanna / Compact storehouse

[Drawing sheet](savanna-compact.svg) · 15 × 14 × 10 · standing Y=1

| Reservation | Position or bounds |
| --- | --- |
| Armory, north-facing | (10, 1, 11) |
| Sentry | (9, 1, 2); anchor offset (-1, 0, -9) |
| First landing | X=6..7, Z=0..0; surface Y=1 |
| store shell | X=2..12, Z=4..12 |
| aisle | X=6..7, Z=5..10 |
| entry landing | X=5..7, Z=5..7 |
| sorting | X=8..10, Z=5..7 |
| service | X=9..11, Z=8..10 |
| bypass | X=6..8, Z=8..11 |
| service crossing | X=6..11, Z=8..10 |
| approach | X=6..7, Z=0..3 |

| Door pair | West leaf / hinge | East leaf / hinge |
| --- | --- | --- |
| store | (6, 1, 4) / left | (7, 1, 4) / right |

| Furniture | Base positions |
| --- | --- |
| crafting | (11, 1, 5) |
| worktop | (11, 1, 6) |
| shelf | (3, 1, 7); (3, 1, 8); (3, 1, 9); (3, 1, 10) |
| seat | (11, 1, 3) |

| Opening or decoration | Positions |
| --- | --- |
| west glazing, one block high | (2, 2, 5); (2, 2, 6) |
| east glazing, one block high | (12, 2, 5); (12, 2, 6) |
| west shutter | (1, 2, 4); solid backing (2, 2, 4) |
| west shutter | (1, 2, 7); solid backing (2, 2, 7) |
| east shutter | (13, 2, 4); solid backing (12, 2, 4) |
| east shutter | (13, 2, 7); solid backing (12, 2, 7) |
| orange banner | (11, 3, 3); backing (11, 3, 4) |

| Light | Lantern | Support beam endpoints |
| --- | --- | --- |
| porch | (7, 4, 2) | (4, 5, 2) → (11, 5, 2) |
| room | (7, 4, 7) | (2, 5, 7) → (12, 5, 7) |
| supply | (10, 4, 10) | (2, 5, 10) → (12, 5, 10) |

| Roof | X/Z footprint | Shape / rise axis | Surface Y range |
| --- | --- | --- | --- |
| porch | X=3..12, Z=1..4 | flat / z | 6.5..6.5 |
| main | X=1..13, Z=3..13 | hip / x | 6.5..9.5 |

Post cells (X/Z): (4, 2); (11, 2).

## Savanna / Supply yard

[Drawing sheet](savanna-supply-yard.svg) · 17 × 18 × 10 · standing Y=1

| Reservation | Position or bounds |
| --- | --- |
| Armory, north-facing | (11, 1, 15) |
| Sentry | (12, 1, 2); anchor offset (1, 0, -13) |
| First landing | X=7..8, Z=0..0; surface Y=1 |
| store shell | X=1..15, Z=9..16 |
| aisle | X=7..8, Z=10..14 |
| interior crossing | X=7..14, Z=10..11 |
| service | X=10..12, Z=12..14 |
| bypass | X=13..14, Z=10..15 |
| packing | X=3..5, Z=11..13 |
| work bay | X=2..4, Z=5..7 |
| work crossing | X=2..7, Z=7..8 |
| frontage | X=6..14, Z=7..8 |
| approach | X=7..8, Z=0..8 |
| Open court | X=7..10, Z=2..5 |

| Door pair | West leaf / hinge | East leaf / hinge |
| --- | --- | --- |
| store | (7, 1, 9) / left | (8, 1, 9) / right |

| Furniture | Base positions |
| --- | --- |
| crafting | (2, 1, 4) |
| worktop | (2, 1, 11); (2, 1, 12); (3, 1, 4) |
| shelf | (2, 1, 15); (3, 1, 15); (4, 1, 15); (5, 1, 15) |
| seat | (3, 1, 10); (4, 1, 10) |
| crate | (2, 1, 3) |

| Opening or decoration | Positions |
| --- | --- |
| west glazing, one block high | (1, 2, 11); (1, 2, 12) |
| east glazing, one block high | (15, 2, 12); (15, 2, 13) |
| west shutter | (0, 2, 10); solid backing (1, 2, 10) |
| west shutter | (0, 2, 13); solid backing (1, 2, 13) |
| east shutter | (16, 2, 11); solid backing (15, 2, 11) |
| east shutter | (16, 2, 14); solid backing (15, 2, 14) |
| orange banner | (14, 3, 8); backing (14, 3, 9) |

| Light | Lantern | Support beam endpoints |
| --- | --- | --- |
| workshop | (3, 4, 5) | (1, 5, 5) → (5, 5, 5) |
| frontage | (10, 4, 7) | (1, 5, 7) → (15, 5, 7) |
| arrival | (12, 4, 1) | (11, 5, 1) → (15, 5, 1) |
| room | (5, 4, 11) | (1, 5, 11) → (15, 5, 11) |
| supply | (11, 4, 15) | (1, 5, 15) → (15, 5, 15) |

| Roof | X/Z footprint | Shape / rise axis | Surface Y range |
| --- | --- | --- | --- |
| workshop | X=0..6, Z=2..9 | lean / x-reverse | 6.5..8 |
| frontage | X=5..15, Z=7..9 | flat / z | 6.5..6.5 |
| sentry | X=11..15, Z=1..2 | flat / z | 6.5..6.5 |
| main | X=0..16, Z=8..17 | hip / z | 6.5..8.5 |

Post cells (X/Z): (5, 5); (11, 1); (15, 1); (15, 7).

## Taiga / Compact storehouse

[Drawing sheet](taiga-compact.svg) · 13 × 15 × 12 · standing Y=1

| Reservation | Position or bounds |
| --- | --- |
| Armory, north-facing | (9, 1, 12) |
| Sentry | (8, 1, 2); anchor offset (-1, 0, -10) |
| First landing | X=5..6, Z=0..0; surface Y=1 |
| store shell | X=1..11, Z=5..13 |
| aisle | X=5..6, Z=6..11 |
| entry landing | X=4..6, Z=6..8 |
| sorting | X=7..9, Z=6..8 |
| service | X=8..10, Z=9..11 |
| bypass | X=5..7, Z=9..12 |
| service crossing | X=5..10, Z=9..11 |
| approach | X=5..6, Z=0..4 |

| Door pair | West leaf / hinge | East leaf / hinge |
| --- | --- | --- |
| store | (5, 1, 5) / left | (6, 1, 5) / right |

| Furniture | Base positions |
| --- | --- |
| crafting | (10, 1, 6) |
| worktop | (10, 1, 7) |
| shelf | (2, 1, 8); (2, 1, 9); (2, 1, 10); (2, 1, 11) |
| seat | (2, 1, 4) |

| Opening or decoration | Positions |
| --- | --- |
| west glazing, one block high | (1, 2, 6); (1, 2, 7) |
| east glazing, one block high | (11, 2, 6); (11, 2, 7) |
| west shutter | (0, 2, 5); solid backing (1, 2, 5) |
| west shutter | (0, 2, 8); solid backing (1, 2, 8) |
| east shutter | (12, 2, 5); solid backing (11, 2, 5) |
| east shutter | (12, 2, 8); solid backing (11, 2, 8) |
| green banner | (10, 3, 4); backing (10, 3, 5) |

| Light | Lantern | Support beam endpoints |
| --- | --- | --- |
| porch | (6, 4, 2) | (3, 5, 2) → (10, 5, 2) |
| room | (6, 4, 8) | (1, 5, 8) → (11, 5, 8) |
| supply | (9, 4, 11) | (1, 5, 11) → (11, 5, 11) |

| Roof | X/Z footprint | Shape / rise axis | Surface Y range |
| --- | --- | --- | --- |
| porch | X=0..12, Z=1..5 | gable / x | 6.5..11.5 |
| main | X=0..12, Z=4..14 | gable / x | 6.5..11.5 |

Post cells (X/Z): (3, 2); (10, 2).

## Taiga / Supply yard

[Drawing sheet](taiga-supply-yard.svg) · 16 × 18 × 13 · standing Y=1

| Reservation | Position or bounds |
| --- | --- |
| Armory, north-facing | (10, 1, 15) |
| Sentry | (11, 1, 2); anchor offset (1, 0, -13) |
| First landing | X=7..8, Z=0..0; surface Y=1 |
| store shell | X=1..14, Z=9..16 |
| aisle | X=7..8, Z=10..14 |
| interior crossing | X=7..13, Z=10..11 |
| service | X=9..11, Z=12..14 |
| bypass | X=12..13, Z=10..15 |
| packing | X=3..5, Z=11..13 |
| work bay | X=2..4, Z=5..7 |
| work crossing | X=2..7, Z=7..8 |
| frontage | X=6..13, Z=7..8 |
| approach | X=7..8, Z=0..8 |
| Open court | X=7..10, Z=2..5 |

| Door pair | West leaf / hinge | East leaf / hinge |
| --- | --- | --- |
| store | (7, 1, 9) / left | (8, 1, 9) / right |

| Furniture | Base positions |
| --- | --- |
| crafting | (2, 1, 4) |
| worktop | (2, 1, 11); (2, 1, 12); (3, 1, 4) |
| shelf | (2, 1, 15); (3, 1, 15); (4, 1, 15); (5, 1, 15) |
| seat | (3, 1, 10); (4, 1, 10) |
| crate | (4, 1, 4) |

| Opening or decoration | Positions |
| --- | --- |
| west glazing, one block high | (1, 2, 11); (1, 2, 12) |
| east glazing, one block high | (14, 2, 12); (14, 2, 13) |
| west shutter | (0, 2, 10); solid backing (1, 2, 10) |
| west shutter | (0, 2, 13); solid backing (1, 2, 13) |
| east shutter | (15, 2, 11); solid backing (14, 2, 11) |
| east shutter | (15, 2, 14); solid backing (14, 2, 14) |
| green banner | (13, 3, 8); backing (13, 3, 9) |

| Light | Lantern | Support beam endpoints |
| --- | --- | --- |
| workshop | (3, 4, 5) | (1, 5, 5) → (5, 5, 5) |
| frontage | (10, 4, 7) | (1, 5, 7) → (14, 5, 7) |
| arrival | (12, 4, 1) | (11, 5, 1) → (14, 5, 1) |
| room | (5, 4, 11) | (1, 5, 11) → (14, 5, 11) |
| supply | (10, 4, 15) | (1, 5, 15) → (14, 5, 15) |

| Roof | X/Z footprint | Shape / rise axis | Surface Y range |
| --- | --- | --- | --- |
| workshop | X=0..6, Z=2..9 | lean / x-reverse | 6.5..8 |
| frontage | X=5..14, Z=7..9 | flat / z | 6.5..6.5 |
| sentry | X=11..14, Z=1..2 | flat / z | 6.5..6.5 |
| main | X=0..15, Z=8..17 | gable / z | 6.5..10.5 |

Post cells (X/Z): (5, 5); (11, 1); (14, 1); (14, 7).

## Jungle · MMV / Compact storehouse

[Drawing sheet](jungle-compact.svg) · 15 × 15 × 12 · standing Y=2

| Reservation | Position or bounds |
| --- | --- |
| Armory, north-facing | (10, 2, 12) |
| Sentry | (9, 2, 2); anchor offset (-1, 0, -10) |
| First landing | X=6..7, Z=0..0; surface Y=1.5 |
| store shell | X=2..12, Z=5..13 |
| aisle | X=6..7, Z=6..11 |
| entry landing | X=5..7, Z=6..8 |
| sorting | X=8..10, Z=6..8 |
| service | X=9..11, Z=9..11 |
| bypass | X=6..8, Z=9..12 |
| service crossing | X=6..11, Z=9..11 |
| approach | X=6..7, Z=0..4 |

| Door pair | West leaf / hinge | East leaf / hinge |
| --- | --- | --- |
| store | (6, 2, 5) / left | (7, 2, 5) / right |

| Furniture | Base positions |
| --- | --- |
| crafting | (11, 2, 6) |
| worktop | (11, 2, 7) |
| shelf | (3, 2, 8); (3, 2, 9); (3, 2, 10); (3, 2, 11) |
| seat | (3, 2, 4) |

| Opening or decoration | Positions |
| --- | --- |
| west glazing, one block high | (2, 3, 6); (2, 3, 7) |
| east glazing, one block high | (12, 3, 6); (12, 3, 7) |
| south glazing, one block high | (9, 5, 13); (10, 5, 13) |
| west shutter | (1, 3, 5); solid backing (2, 3, 5) |
| west shutter | (1, 3, 8); solid backing (2, 3, 8) |
| east shutter | (13, 3, 5); solid backing (12, 3, 5) |
| east shutter | (13, 3, 8); solid backing (12, 3, 8) |
| green banner | (11, 4, 4); backing (11, 4, 5) |

| Light | Lantern | Support beam endpoints |
| --- | --- | --- |
| porch | (7, 5, 2) | (4, 6, 2) → (11, 6, 2) |
| room | (7, 5, 8) | (2, 6, 8) → (12, 6, 8) |
| supply | (10, 5, 11) | (2, 6, 11) → (12, 6, 11) |

| Roof | X/Z footprint | Shape / rise axis | Surface Y range |
| --- | --- | --- | --- |
| porch | X=3..12, Z=1..5 | flat / z | 7.5..7.5 |
| main | X=1..13, Z=4..14 | hip / x | 7.5..11.5 |

Post cells (X/Z): (4, 2); (11, 2).

## Jungle · MMV / Supply yard

[Drawing sheet](jungle-supply-yard.svg) · 17 × 18 × 12 · standing Y=2

| Reservation | Position or bounds |
| --- | --- |
| Armory, north-facing | (11, 2, 15) |
| Sentry | (12, 2, 2); anchor offset (1, 0, -13) |
| First landing | X=7..8, Z=0..0; surface Y=1.5 |
| store shell | X=1..15, Z=9..16 |
| aisle | X=7..8, Z=10..14 |
| interior crossing | X=7..14, Z=10..11 |
| service | X=10..12, Z=12..14 |
| bypass | X=13..14, Z=10..15 |
| packing | X=3..5, Z=11..13 |
| work bay | X=2..4, Z=5..7 |
| work crossing | X=2..7, Z=7..8 |
| frontage | X=6..14, Z=7..8 |
| approach | X=7..8, Z=0..8 |
| Open court | X=7..10, Z=2..5 |

| Door pair | West leaf / hinge | East leaf / hinge |
| --- | --- | --- |
| store | (7, 2, 9) / left | (8, 2, 9) / right |

| Furniture | Base positions |
| --- | --- |
| crafting | (2, 2, 4) |
| worktop | (2, 2, 11); (2, 2, 12); (3, 2, 4) |
| shelf | (2, 2, 15); (3, 2, 15); (4, 2, 15); (5, 2, 15) |
| seat | (3, 2, 10); (4, 2, 10) |
| crate | (2, 2, 3) |

| Opening or decoration | Positions |
| --- | --- |
| west glazing, one block high | (1, 3, 11); (1, 3, 12) |
| east glazing, one block high | (15, 3, 12); (15, 3, 13) |
| south glazing, one block high | (10, 5, 16); (11, 5, 16) |
| west shutter | (0, 3, 10); solid backing (1, 3, 10) |
| west shutter | (0, 3, 13); solid backing (1, 3, 13) |
| east shutter | (16, 3, 11); solid backing (15, 3, 11) |
| east shutter | (16, 3, 14); solid backing (15, 3, 14) |
| green banner | (14, 4, 8); backing (14, 4, 9) |

| Light | Lantern | Support beam endpoints |
| --- | --- | --- |
| workshop | (3, 5, 5) | (1, 6, 5) → (5, 6, 5) |
| frontage | (10, 5, 7) | (1, 6, 7) → (15, 6, 7) |
| arrival | (12, 5, 1) | (11, 6, 1) → (15, 6, 1) |
| room | (5, 5, 11) | (1, 6, 11) → (15, 6, 11) |
| supply | (11, 5, 15) | (1, 6, 15) → (15, 6, 15) |

| Roof | X/Z footprint | Shape / rise axis | Surface Y range |
| --- | --- | --- | --- |
| workshop | X=0..6, Z=2..9 | lean / x-reverse | 7.5..9 |
| frontage | X=5..15, Z=7..9 | flat / z | 7.5..7.5 |
| sentry | X=11..15, Z=1..2 | flat / z | 7.5..7.5 |
| main | X=0..16, Z=8..17 | hip / z | 7.5..11.5 |

Post cells (X/Z): (5, 5); (11, 1); (15, 1); (15, 7).

## Swamp · MMV / Compact storehouse

[Drawing sheet](swamp-compact.svg) · 15 × 15 × 11 · standing Y=2

| Reservation | Position or bounds |
| --- | --- |
| Armory, north-facing | (10, 2, 12) |
| Sentry | (9, 2, 2); anchor offset (-1, 0, -10) |
| First landing | X=6..7, Z=0..0; surface Y=1.5 |
| store shell | X=2..12, Z=5..13 |
| aisle | X=6..7, Z=6..11 |
| entry landing | X=5..7, Z=6..8 |
| sorting | X=8..10, Z=6..8 |
| service | X=9..11, Z=9..11 |
| bypass | X=6..8, Z=9..12 |
| service crossing | X=6..11, Z=9..11 |
| approach | X=6..7, Z=0..4 |

| Door pair | West leaf / hinge | East leaf / hinge |
| --- | --- | --- |
| store | (6, 2, 5) / left | (7, 2, 5) / right |

| Furniture | Base positions |
| --- | --- |
| crafting | (11, 2, 6) |
| worktop | (11, 2, 7) |
| shelf | (3, 2, 8); (3, 2, 9); (3, 2, 10); (3, 2, 11) |
| seat | (3, 2, 4) |

| Opening or decoration | Positions |
| --- | --- |
| west glazing, one block high | (2, 3, 6); (2, 3, 7) |
| east glazing, one block high | (12, 3, 6); (12, 3, 7) |
| west shutter | (1, 3, 5); solid backing (2, 3, 5) |
| west shutter | (1, 3, 8); solid backing (2, 3, 8) |
| east shutter | (13, 3, 5); solid backing (12, 3, 5) |
| east shutter | (13, 3, 8); solid backing (12, 3, 8) |
| brown banner | (11, 4, 4); backing (11, 4, 5) |

| Light | Lantern | Support beam endpoints |
| --- | --- | --- |
| porch | (7, 5, 2) | (4, 6, 2) → (11, 6, 2) |
| room | (7, 5, 8) | (2, 6, 8) → (12, 6, 8) |
| supply | (10, 5, 11) | (2, 6, 11) → (12, 6, 11) |

| Roof | X/Z footprint | Shape / rise axis | Surface Y range |
| --- | --- | --- | --- |
| porch | X=3..12, Z=1..5 | flat / z | 7.5..7.5 |
| main | X=1..13, Z=4..14 | gable / x | 7.5..10.5 |

Post cells (X/Z): (4, 2); (11, 2).

## Swamp · MMV / Supply yard

[Drawing sheet](swamp-supply-yard.svg) · 17 × 18 × 11 · standing Y=2

| Reservation | Position or bounds |
| --- | --- |
| Armory, north-facing | (11, 2, 15) |
| Sentry | (12, 2, 2); anchor offset (1, 0, -13) |
| First landing | X=7..8, Z=0..0; surface Y=1.5 |
| store shell | X=1..15, Z=9..16 |
| aisle | X=7..8, Z=10..14 |
| interior crossing | X=7..14, Z=10..11 |
| service | X=10..12, Z=12..14 |
| bypass | X=13..14, Z=10..15 |
| packing | X=3..5, Z=11..13 |
| work bay | X=2..4, Z=5..7 |
| work crossing | X=2..7, Z=7..8 |
| frontage | X=6..14, Z=7..8 |
| approach | X=7..8, Z=0..8 |
| Open court | X=7..10, Z=2..5 |

| Door pair | West leaf / hinge | East leaf / hinge |
| --- | --- | --- |
| store | (7, 2, 9) / left | (8, 2, 9) / right |

| Furniture | Base positions |
| --- | --- |
| crafting | (2, 2, 4) |
| worktop | (2, 2, 11); (2, 2, 12); (3, 2, 4) |
| shelf | (2, 2, 15); (3, 2, 15); (4, 2, 15); (5, 2, 15) |
| seat | (3, 2, 10); (4, 2, 10) |
| crate | (2, 2, 3) |

| Opening or decoration | Positions |
| --- | --- |
| west glazing, one block high | (1, 3, 11); (1, 3, 12) |
| east glazing, one block high | (15, 3, 12); (15, 3, 13) |
| west shutter | (0, 3, 10); solid backing (1, 3, 10) |
| west shutter | (0, 3, 13); solid backing (1, 3, 13) |
| east shutter | (16, 3, 11); solid backing (15, 3, 11) |
| east shutter | (16, 3, 14); solid backing (15, 3, 14) |
| brown banner | (14, 4, 8); backing (14, 4, 9) |

| Light | Lantern | Support beam endpoints |
| --- | --- | --- |
| workshop | (3, 5, 5) | (1, 6, 5) → (5, 6, 5) |
| frontage | (10, 5, 7) | (1, 6, 7) → (15, 6, 7) |
| arrival | (12, 5, 1) | (11, 6, 1) → (15, 6, 1) |
| room | (5, 5, 11) | (1, 6, 11) → (15, 6, 11) |
| supply | (11, 5, 15) | (1, 6, 15) → (15, 6, 15) |

| Roof | X/Z footprint | Shape / rise axis | Surface Y range |
| --- | --- | --- | --- |
| workshop | X=0..6, Z=2..9 | lean / x-reverse | 7.5..9 |
| frontage | X=5..15, Z=7..9 | flat / z | 7.5..7.5 |
| sentry | X=11..15, Z=1..2 | flat / z | 7.5..7.5 |
| main | X=0..16, Z=8..17 | gable / z | 7.5..9.5 |

Post cells (X/Z): (5, 5); (11, 1); (15, 1); (15, 7).

