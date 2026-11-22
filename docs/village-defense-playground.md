# Village Defense Playground

This interactive development world contains a small village on superflat terrain
with armed defenders and attackers approaching from the east. Start the exercise
when you are ready, watch the guards, change their orders or supplies, and reset
it to try again.

## Open the world

Use Java 17 from the repository root:

```sh
python3 tools/run_village_sandbox.py
```

On the current macOS development machine:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home python3 tools/run_village_sandbox.py
```

The launcher opens **Village Defense Playground** in a separate development
client. It creates a fresh superflat save with seed `20260905`, grass at Y=60,
and cheats enabled. It uses version-matched `level.dat` metadata from the
existing performance test run; it copies no terrain or entities from that run.
If that metadata is missing, first run:

```sh
./gradlew --offline --no-daemon runGameTestServer -Pstep13Performance=true
```

The working directory is `build/village-sandbox`; the save is
`build/village-sandbox/saves/VillageDefensePlayground`. Run only one development
client/server at a time. This is a disposable exercise: each launch or reset
rebuilds its village, removes entities and dropped items inside the exercise
area, and restores your test inventory. Normal Minecraft saves are separate.

## What is in the village

| Area | Setup |
| --- | --- |
| Eastern firing line | Six guards with loaded AK-47s and reserve ammo; Hold Position governs combat movement, with a small home area keeping idle defenders near their posts. |
| Ammo test | One more frontline guard starts with an empty AK-47, no chambered round, and no reserve. Nearby defenders can share ammo. |
| Village Armory | Stocked with compatible loose ammo near the firing line. Guards with movement available can visit it. |
| Quartermaster | A working villager, a stocked station, and an unarmed recruit opted into the rifleman equipment profile. |
| Medical area | Two downed guards, two supplied medics with pistols, and two Recovery Cots under an open clinic roof. |
| Village | Four homes, villagers, beds, a bell, and an armed resident available for automatic coverage. |
| Player | Creative flight, night vision, the control book, both ledgers, bandages, ammo, an Armory, and a cot. |

The total is 13 guards, including the medics and casualties. Downing, transport,
medical care, and automatic coverage are enabled in this isolated client. The
village footprint and resident membership are registered by the exercise.
The village is assembled by the test setup, so this scene does not demonstrate
natural village generation or initial population sizing.

## Controls

Right-click the control book in your first hotbar slot and choose an action,
or enter its command in chat:

| Command | Action |
| --- | --- |
| `/vggtest start` | Release the staged guards and send 24 husks from the east, three per second. |
| `/vggtest reset` | Rebuild the exercise and restore guards, casualties, supplies, and the player kit. |
| `/vggtest wave` | Add 18 more attackers, or start the first exercise if it has not begun. |
| `/vggtest peaceful` | Remove current attackers and cancel pending attackers so you can inspect recovery and resupply. |
| `/vggtest status` | Show accepted gunshots, distinct attacker deaths, ammo-test shots, recovered casualties, observed carrying, and Quartermaster equipment service. |

Before Start, guards have AI paused and the two casualty timers are held at
their starting values. After Start, their normal AI and casualty timers run.
The exercise directs only the attackers toward living defenders; it does not
script guard targets, gunshots, healing, item transfers, or carrying. Husks
avoid the need to keep ordinary zombies alive in daylight. This is an attack
wave, not Minecraft's raid mechanic; Quartermasters normally suspend service
during an actual raid.

## Things to try

- Watch the empty-ammo guard receive shared ammo, reload, and fire. Disable its
  sharing order or remove donors' surplus and compare the result.
- Let the recruit visit the Quartermaster. Remove stock or the workstation,
  change service permissions, and inspect the guard's displayed status.
- Watch medics stabilize, carry, and revive the two original casualties. Empty
  their pouches, block a cot approach, remove a cot, or interrupt a carrier and
  compare the outcome after resetting.
- Clear attackers and watch the resident resume village coverage. Give it Hold
  Position or a follow order to verify that your orders take priority.
- Equip a sidearm or change gun profiles using the ordinary guard inventory.
  The starting ammo-test guard intentionally has no backup pistol.
- Add several waves to test sustained combat, supply depletion, and additional
  casualties. Guards and villagers can die; Reset restores the exercise.

## Automated smoke check

```sh
python3 tools/run_village_sandbox.py --verify
```

This mode starts the exercise, observes real gameplay, writes a result, captures
a screenshot, and closes the client. It requires gunfire and attacker deaths,
shared-ammo recovery and firing by the initially empty guard, Quartermaster
arming, observed carrying, and revival of both original casualties. Results and
runtime status are written under `build/village-sandbox`.

This single scene is useful for interaction testing. Passing it does not certify
all weapons, multiplayer synchronization, natural generation, saved-world
migration, every interruption, or performance at large village populations.
Use the broader [validation guide](village-expansion-validation.md) for those
checks. Playground commands and setup code are separate development sources
and are not included in the distributed mod JAR.

## Comparing cot and field recovery

Add `--one-cot` to the launcher to keep two casualties but provide only one
Recovery Cot. Add `--verify` to run the exercise automatically. Verification
requires one cot delivery and one field revival, with the cot patient returning
to duty first. Without `--one-cot`, both patients must be delivered to cots.

`care-trace.json` records patient positions and life states. `verification.json`
includes each patient's stabilization, treatment, delivery, revival, and active
state timestamps. `cot-care.png` captures the clinic during care. These files are
replaced on the next launch. The interactive control book still starts and
resets the exercise normally.

For pose inspection, add `--pose-check` to a verification run. Four additional
stationary patients demonstrate north-, east-, south-, and west-facing cots,
with an image for each direction and matching client/server position records.
The check deliberately offsets one client patient and starts its walking
animation, then requires cot alignment to restore its position and stop its legs.
These visual fixtures begin already stabilized and
attached; the two ordinary casualties still exercise live care.
Add `--steve-model` to inspect the other upstream guard model. Model selection
changes only this launcher's disposable client configuration. A normal launch
uses the standard guard model.
