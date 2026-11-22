# Village Defense Buildings manual validation

This guide validates the player-visible behavior that a headless GameTest
cannot judge: natural terrain fit, village-road appearance, navigation through
real neighboring pieces, multiplayer presentation, generation frequency, and
compatibility with a representative village overhaul. Run every case against
one exact candidate JAR and record the evidence in
[`village-defense-buildings-release-matrix.md`](village-defense-buildings-release-matrix.md).

The automated suite validates templates, rotations, jigsaw attachment,
one-time building actions, persistence, and bounded performance. It does not
turn a visual or mixed-mod result into a pass.

## Prepare the exact candidate

Use Java 17 and generate the automated evidence before creating the manual
handoff:

```text
./gradlew clean check
./gradlew runGameTestServer -Pstep13Performance=true
./gradlew prepareVillageDefenseBuildingManualValidation
```

The handoff is written beneath
`build/distributions/village-building-manual-validation/<version>/`. It
contains the exact JAR, its SHA-256 file, both automated evidence manifests,
this guide, and a copy of the results matrix. Test the JAR from that directory;
do not silently replace it during the pass.

Back up any existing world before testing. Install the candidate and the exact
dependency versions recorded in the matrix on both client and server. Record
the Java version, gun-pack versions, other village mods or datapacks, seed,
difficulty, and candidate SHA-256 with the retained evidence.

## Enable the feature

Village Defense Buildings are off by default while manual validation is
incomplete. In the synchronized Fzzy Config file, set:

```text
villageBuildings.enabled=true
```

Keep all five building switches enabled for the complete pass. Pool changes
are installed only during server startup, so restart the server after changing
the master switch or village-building datapack definitions. `/reload` validates
new definitions but deliberately does not rewrite live pools.

After startup, run:

```text
/vgg villagebuildings status
/vgg villagebuildings entries
/vgg villagebuildings pools
/vgg villagebuildings errors
```

Confirm that the five supported vanilla house pools contain the expected VGG
entries and that startup reported no missing template, processor, or pool.

## Natural-generation pass

Use newly generated chunks. Existing villages are intentionally not modified.
Locate several examples of each vanilla village style:

```text
/locate structure minecraft:village_plains
/locate structure minecraft:village_desert
/locate structure minecraft:village_savanna
/locate structure minecraft:village_snowy
/locate structure minecraft:village_taiga
```

Generate at least 20 to 30 villages across multiple seeds. Retain screenshots
and coordinates for every defense-building type and for representative slopes,
water edges, roads, neighboring houses, and rotations. Confirm that:

- the palette matches the village style;
- the entrance meets a road without a blocked doorway, floating path, or
  impassable step;
- beds, doors, lanterns, ladders, fences, work areas, and guard positions are
  usable;
- buildings do not cut through neighboring pieces or leave unsafe drops; and
- the relative frequency is useful without crowding out ordinary homes and
  professions.

## Functional pass

Test Barracks, Village Armories, Sentry Posts, integrated Watchtowers, and
Training Yards separately. Exercise Peaceful, Easy, Normal, and Hard. Verify
the configured guard ceiling, difficulty-aware groups, roles, squads, native
TaCZ loadouts, one complete ammunition stack, Armory stock, and guard resupply.

At each Sentry Post, confirm that players and Guard Villagers can use the
three-block-wide entrance, that the guard remains near the checkpoint during
ordinary combat, and that the low and raised firing lanes are not blocked by
the mixed side cover or roof frame. At each integrated Watchtower, confirm that
TaCZ-gun and crossbow guards finish on the upper deck while melee or unarmed
guards remain in the lower ready room. Walk a Guard Villager up and down the
stairs, inspect every side of the connected fence railing, and verify the
raised roof leaves a two-block-high observation opening with clear firing lanes
from the deck in all four rotations. Confirm that every iron bar, fence, wall,
stair corner, and door has settled against its generated neighbors rather than
retaining a disconnected template state.

Kill or move generated guards, empty or break an Armory, unload and reload its
chunks, save and restart the server, and revisit the building. Completed
actions must not respawn a removed guard, rebuild an Armory, or reroll stock.
Temporarily disable each building type and the garrison, Armory, and initial-
stock controls, restart, and confirm that each setting suppresses only new
eligible work.

Generate standalone Village Watchtowers and integrated Watchtowers in the same
world. Their structures, squads, Armories, controls, identities, and
diagnostics must remain independent.

## Compatibility and server pass

Load a backed-up 1.4 world with the 1.5 candidate. Existing chunks and
structures must remain unchanged; newly generated chunks may use the enabled
village pool entries.

Repeat representative generation with at least one village-overhaul mod or
datapack used by the target modpack. Confirm that its existing pool entries and
weights remain present, removed pools are skipped safely, and no malformed
road connections or duplicate VGG entries appear. Record the exact project and
version. This mixed-mod check cannot be replaced by the built-in synthetic
pool-preservation test.

Run the same candidate on a dedicated server with one operating client and,
where practical, one observing client. Confirm guard models, held weapons,
sounds, firing, Armory access, chunk reload, reconnect, and server restart.
Review `latest.log` for VGG errors after generation and restart.

## Record and release

For every `VBUILD-MANUAL-*` row, change the result to `Pass` only after the
expected result succeeds against the candidate identified at the top of the
matrix. Add evidence such as seed and coordinates, screenshot or recording
names, log names, and tested mod versions. `Failed` and `Not run` remain release
blockers.

Then run:

```text
./gradlew verifyVillageDefenseBuildingManualChecklist
```

Public CurseForge packaging also runs this gate, the broader manual checklist,
the complete automated village-building release gate, and a release-identity
check. A `-dev` build cannot be packaged as a public release.
