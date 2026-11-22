# Village feature integration boundaries

This document records executable integration experiments for future village
staffing, patrol coverage, Quartermaster service, and medical features in
Villager Guard Gunners. These experiments do not enable those gameplay features.
Ordinary villages remain in scope, and future downing/revival must remain
optional and disabled by default.

## Running the probes

Use Java 17 and the repository's pinned dependencies: Minecraft 1.20.1,
Forge 47.4.10, Guard Villagers 1.6.19, and Timeless and Classics Zero (TaCZ)
1.1.8-hotfix. Dependency declarations in `gradle.properties` and
`src/main/resources/META-INF/mods.toml` remain authoritative.

```sh
./gradlew --offline --no-daemon check
./gradlew --offline --no-daemon runGameTestServer -PvillageIntegrationProbes=true
./gradlew --offline --no-daemon runGameTestServer
```

Omit `--offline` when dependencies have not yet been cached. The integration run
uses the disposable `run-gametest` workspace and its existing preparation task.
Do not run it concurrently with another task using that workspace.

The `integrationGameTest` source set lives in `src/integrationtest`. `check`
compiles its Java and resources; the property explicitly selects its runtime
suite. The normal correctness suite uses a separate source set. Integration and
performance flags cannot be combined.

Only the opt-in run loads `mixins.villagerguardgunners.integration.json`. Its
mixins use development mappings with `remap = false`; they are executable
prototypes, not shipping mixins. The test profession uses a gold block as a
stand-in workstation. No integration classes, registry entries, mixin config,
or test arena belong in the production JAR.

## Village identity and observation

`VillageIntegrationGameTests` generates complete native jigsaw layouts for all
five vanilla village styles with Village Defense Buildings disabled. Generation
supplies the start chunk and piece list before block placement. Native
serialization preserves the structure ID, start chunk, piece count, template
contents, positions, rotations, and junction metadata in the tested layouts.

Some generated piece bounding boxes change after native deserialization because
Minecraft reconstructs them from the templates. Do not hash raw start NBT or
original bounding boxes to establish identity. Use dimension, registered
structure ID, and structure start chunk as the generated-village identity
inputs. Maintain observed geometry separately, with a versioned codec and
explicit refresh rules in the persistent implementation.

The loaded-discovery experiment attaches a generated ordinary village start to
a loaded test chunk. A small observer reads native start/reference metadata
through `getChunkNow`, finds that village, and stops after four references.
References to unloaded starts and references beyond the budget remain
unresolved. The experiment also reads an existing cached home POI. Neither
observation requests absent chunks or POI storage reads.

`PoiManager.getInRange` is **not a cache-only query**: even a radius of one
requests uncached POI columns in the positive-control experiment. It need not
load a world chunk to cause storage access. A cache-only adapter to
`SectionStorage.get` can distinguish an uncached section (`null`) from a cached
empty section (`Optional.empty`) and a cached populated section.

The production discovery service now uses chunk lifecycle observations and
bounded cached queries; see [Village records and discovery](village-records.md)
for its persistence, limits, affiliations, and diagnostics. Unknown data is
deferred, not treated as an empty village. Player-built registration remains
part of the future Quartermaster station. The probes
instrument synchronous absent-chunk requests through `ServerChunkCache.getChunk`
and POI column reads; they are not a universal detector for every possible
asynchronous loading API.

## Native structure spawning

Guard Villagers can insert guard NBT into proto-chunks during structure
placement, before guards enter a live world. A live entity-join event is too late
to be the only admission point for initial village staffing.

The prototype scopes the current `StructureStart` around piece placement and
the native template ID around single-pool-element placement. It can inspect
those values at `Guard.finalizeSpawn` and mark a selected request canceled using
Forge's spawn-canceled flag before `WorldGenRegion` inserts the guard NBT.

The native placement probe demonstrates:

- A selected house in a managed village places its blocks but inserts no guards.
- A different house still inserts the configured two guards.
- The selected template under an unrelated structure still inserts two guards.
- An injected spawn exception restores enclosing context; the next placement
  succeeds without inheriting the failed placement's context.

The production adapter now supplies the remapped structure-start scope and
persists an initial allowance; see [village population](village-population.md).
The integration suite reuses that scope and adds test-only observations around
native template placement. World-generation workers publish persistent request
markers rather than mutating live village saved data. The population tests
exercise size limits, canceled insertion, unloaded residents, restart data, and
shared garrison shares. Unrelated structures retain native behavior; native
requests inside an enrolled village are limited to classified sources.


## Healing and death

`MedicalIntegrationGameTests` records the actual transformed entity/event paths.
Normal `heal` can be canceled without increasing health; Guard Villagers eating
also enters the healing event. Future treatment must recheck live health and
charge a dose only for effective healing, while preserving dose/container
ownership under event cancellation or re-entry. These probes do not yet test the
complete cleric, regeneration, and medic coexistence matrix.

The verified lethal zombie-hit order is:

```text
LivingHurtEvent → LivingDamageEvent → Guard.die
    → LivingConversionEvent.Pre → LivingDeathEvent
```

Canceling `LivingDeathEvent` does not preserve the original guard when upstream
conversion has already removed it. The prototype intercepts the beginning of
`Guard.die`, before upstream conversion. A usable totem resolves before this
boundary. Intercepting it preserves the same guard UUID and original equipment
without death, conversion, or drops. A subsequent void-damage bypass reaches the
normal death and drops events once in the fixture.

TaCZ's native bullet hit path can apply ordinary and armor-penetrating damage
separately. With the prototype restoring one health at each lethal boundary,
one bullet enters `Guard.die` twice. A real casualty implementation must make the
active-to-downed transition idempotent and explicitly handle subsequent damage;
restoring health and canceling each death callback is not a downed-state system.

Keep this hook conditional on the future feature switch and an eligible active
life state. Preserve normal totem handling and define true-death bypasses and
finishing damage. Validate queued fire, healing while downed, XP, conversion,
other event subscribers, persistence, and feature-disable recovery before
shipping. Only the narrow event order and entity/inventory ownership boundaries
are established here.

## Quartermaster and equipment ownership

`QuartermasterIntegrationGameTests` registers an isolated POI with one ticket and
a custom villager profession. Native POI acquisition rejects a second claim;
native profession assignment recognizes the workstation, sets job memory, and
uses the adult work schedule. Profession and job memory survive entity NBT
serialization. Removing the station invalidates job memory and resets the
untraded novice's profession through native behaviors.

This establishes the native profession/POI route. The station block, inventory,
permissions, menus, operator travel, traded-villager behavior, and automatic
service transactions remain separate work. Reading the work schedule is not a
simulation of a full villager workday.

`EquipmentIntegrationGameTests` initializes the existing native gun controller
and proves its equipment-swap gate is open while idle, closed during a native
bolt operation, and open again afterward. A blocked sidearm activation leaves
the original primary unchanged. An active sidearm survives guard NBT save/load;
the stowed primary and configured sidearm retain their exact item tags and
return to their original owners. Reconfiguration cannot overwrite the occupied
active layout.

Quartermaster service must use the supported ownership APIs and native action
gate. It cannot assume the main hand always contains the primary gun. The
experiment verifies existing guard-side ownership recovery; it does not prove
crash consistency across separately saved guard and station inventories. That
requires the service operation journal and interruption matrix before automated
equipment exchange is enabled.
