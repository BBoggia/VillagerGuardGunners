# Production village compatibility testing

The compatibility runner tests packaged Villager Guard Gunners and third-party
mod JARs in the normal installed Forge client. Every case has explicit outcome
assertions. Startup alone does not establish spawning, patrol, supply, or medical
compatibility. Keep the earlier measured results and their limitations in
[Village mod compatibility](village-mod-compatibility.md) when comparing changes.

## Requirements and isolation

The current launcher adapter supports Minecraft 1.20.1 on macOS Apple silicon,
an installed production Forge runtime, JDK 17, and Python 3.11 or newer. It reads
the installed version JSON, libraries, and assets. It creates a separate game
directory under `build/village-compatibility/runs/` for each attempt, including
its own mods, options, settings, and worlds. All generated output is ignored.
No audit notes or previously generated world files are required.

The observer is a separate reobfuscated QA mod. It uses the release add-on and
dependency classes at runtime. Neither its sources nor its classes join the
release JAR. `verifyReleaseArtifact` rejects its package marker in that artifact.
The observer is intended only for isolated QA installations.

Install Forge through an ordinary launcher first and collect the released
dependencies. The runner does not download missing JARs or accept an EULA.
It uses a local observer identity for integrated single-player tests and does
not read account credentials. Dedicated multiplayer testing remains a separate
validation requirement.

## Build and run

From the repository root, build the observer with the same mappings as the
candidate:

```sh
python3 tools/run_village_compatibility.py build-observer \
  --java /Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java \
  --verify-release
```

This creates `build/compatibility-qa/vgg-compatibility-probe.jar`. The optional
`--verify-release` also runs the repository's release-artifact check and its
required Java tests. A frozen candidate can be supplied independently of the
newly built release JAR.

Copy `tools/village-compatibility/example.json` to an ignored working location
and edit its paths. Relative paths resolve against the configuration file's
directory, so update those paths when moving the example. Set:

- `runtime.minecraftDirectory`, `runtime.version`, `runtime.forgeVersion`, and
  `runtime.java` to the installed runtime and JDK.
- `jarDirectories` to external directories containing the required JARs.
- `mods` to pin particular mod IDs to files. A value can be a path string or
  an object with `path` and an optional expected `sha256`. Pin the candidate
  hash when preserving a comparison baseline. Guard Villagers must be present
  in one of these directories or explicitly pinned.
- `cases` to the desired observations and their expected outcomes.

```sh
python3 tools/run_village_compatibility.py preflight --config build/compat-config.json
python3 tools/run_village_compatibility.py run --config build/compat-config.json --resume
```

Preflight resolves mandatory dependencies, includes declared embedded mods,
checks optional dependency ranges when those mods are selected, and uses the
installed Maven version library for Forge's range semantics. It also includes
Cloth Config for Liberty's Villagers because the tested release needs it despite
omitting it from `mods.toml`. Distinct JARs providing the same requested mod ID
require an explicit pin; identical copies are interchangeable. Conflicting
standalone mod IDs and incompatible embedded version ranges are rejected. Shared
embedded mods select the highest available version satisfying all their ranges;
the runtime assertions also check the versions Forge actually loaded.
Selected JARs, installed libraries, runtime metadata, and the Java binary receive
hashes in the manifest. Published library SHA1 values are checked when present.
This verifies the selected files, not their publisher's authenticity.

The launcher rejects userdev targets, development class directories, mapped
development JAR paths, and inherited JVM/classpath overrides. The observer
independently checks production mode and the loaded JAR/class code sources for
Villager Guard Gunners, Guard Villagers, TaCZ, and itself.

## Scenario contract

The example contains the seed-314159 vanilla managed-spawning control and a
restart with the maximum increased to 32. Its expected six guards and six spent
credits describe that particular population-only baseline; they are not universal
population requirements for other seeds or future behavior changes.

Each case supports these fields:

| Field | Meaning |
| --- | --- |
| `name` | Unique lowercase name containing letters, digits, and hyphens. |
| `mods` | Additional mod IDs to install, plus their dependencies. |
| `structure` | Exact structure registry ID to locate naturally. Defaults to `minecraft:village_plains`. |
| `seed` | Signed 64-bit world seed, default `314159`. |
| `features` | Explicit `managed`, `coverage`, and `buildings` booleans, and `maximum` from 0 to 32. Omitted settings retain the installation defaults or copied restart settings. |
| `behavior` | `natural`, or `checkpoint-combat` for explicitly controlled orders and combat. |
| `layoutVariantChecks` | Runs the three controlled layout fixtures from `build/generated/village-layout-fixture-pack`, supplied as a case datapack; checks native placement and duties in four rotations. |
| `focused` | Uses a one-block native path tolerance and records the actual endpoint; defaults to true. |
| `observationTicks` | Simulation ticks after locating the settlement; 500–12,000 for natural observations, 1,600–12,000 for controlled behavior. |
| `timeoutSeconds` | Wall-clock limit for the owned client process, 30–1,800 seconds; default 420. |
| `locateRadiusChunks` | Natural structure lookup radius, at most 100 chunks. |
| `maximumForcedChunks` | Footprint observation limit, at most 400 loaded chunks. |
| `restartFrom` | Earlier completed case whose isolated world and generated settings are copied. The original is retained. |
| `syntheticLifecycle` | Runs the separately labeled discovery lifecycle fixtures after natural observations. Defaults to false. |
| `expectations` | Nonempty list of evidence paths, operators, and expected values. |

World creation writes version-matched normal world metadata and leaves every
chunk to Minecraft's generator. The observer locates the requested structure,
loads its footprint and a border, places the observer in spectator mode, and
sets daytime and clear weather. Counts cover the footprint plus eight blocks.
The natural and settled censuses occur after 200 and 500 ticks respectively.
Increasing the observation window extends diagnostics; it does not move those
two census times. Generated terrain is retained in the case directory.

The following registry IDs reproduce the representative settlements used in the
initial audit. They do not cover every village variant. Actual tested versions
and dependency requirements are listed in the existing compatibility report.

| Mod ID | Representative structure |
| --- | --- |
| `ctov` | `ctov:large/village_plains` |
| `villagesandpillages` | `villagesandpillages:village_witch` |
| `integrated_villages` | `integrated_villages:clockwork_village` |
| `mr_tidal_towns` | `joshie:village_ocean` |
| `bettervillage` | `minecraft:village_plains` |
| `skyvillages` | `skyvillages:skyvillage` |
| `mmv` | `mmv:village_jungle` |
| `lios_overhauled_villages` | `minecraft:village_plains` |
| `libertyvillagers` | `minecraft:village_plains` |

The witch settlement is a hostile-structure control. A passing installation
must not be interpreted as a request to staff it with friendly guards.

## Evidence and assertions

Each run retains its complete launch manifest, log, framebuffer, progress,
resource provider stacks, structure and piece classifications, censuses,
discovery/coverage diagnostics, population data, and assertion results.
Resource records include the effective provider and low-to-high stack hashes
for the village tag, requested structure, readable native templates, and up to
512 matching template pools. Unsupported pool elements retain their actual
class name; the observer does not classify them as houses by guessing.

Movement/service samples record up to 32 guards every 20 ticks: active movement
goals, navigation targets and progress, coverage availability, service policy
and status, receipt revision, medical status, and transport reason. Coverage
diagnostics include village identity, issuance allowance and spent credits,
assignments, obstruction reasons, and ages of successful visits. Work counters
are sampled every observed server tick and retain their maxima. These counters
measure configured work units, not elapsed CPU time or all native pathfinding.

Service evidence is passive status and receipt observation. A baseline with no
service attempts reports `controlledServiceScenario: not_exercised`. It does
not pass a supply or treatment integration test. Dedicated service scenarios
must add their own outcomes when those integrations are implemented.

Controlled checkpoint trials select native paths on generated terrain, record
the requested point, actual endpoint, node count, and reachability, then issue
the native patrol order. Arrival requires getting within three blocks. Results
distinguish `no_route_selected`, `arrived`, `deadline_without_arrival`, and guard
death. No selected route is not a failed issued route, but it cannot satisfy an
arrival assertion. Active movement-owner samples help explain interference;
an observed timeout alone does not identify the root obstruction.

Controlled combat supplies a loaded AK-47 and a mobile zombie with a single
initial target assignment. Supplied guards are recorded separately from natural
spawning. Ammo consumption and damage attribution distinguish the designated
gunner from intervening defenders. Synthetic lifecycle checks are recorded under
`syntheticLifecycle`, after the natural observation, and restore the modified
native structure/registry state in a `finally` block.

An assertion has exactly `path`, `op`, and `value`. Paths start with `result`,
`world`, or `manifest`, and use dots to reach object fields or numeric list
indices. Operators are `eq`, `gte`, `lte`, `contains`, and `nonempty` (the latter
expects a boolean). Missing evidence fails. Every evaluation also requires a
zero client exit code, an observed result, production mode, and verified release
code sources. Loaded mod versions and explicitly requested feature settings
must match the preflight configuration. For example:

```json
[
  {"path": "result.controlledBehavior.routesIssued", "op": "gte", "value": 1},
  {"path": "result.controlledBehavior.arrivals", "op": "gte", "value": 1},
  {"path": "result.workBudgets.maximumDiscoveryUnits", "op": "lte", "value": 32}
]
```

The restart control requires the same guard UUIDs, population plans, spent
credits, and byte-identical existing population journals. It does not require
the entire world to remain byte-identical while Minecraft advances time. Source
world hashes detect edits made after the previous case finished.

Re-evaluate retained evidence without launching Minecraft:

```sh
python3 tools/run_village_compatibility.py evaluate build/village-compatibility/runs/CASE
python3 tools/run_village_compatibility.py evaluate build/village-compatibility/runs/CASE \
  --expectations build/alternate-expectations.json --report build/alternate-result.json
```

Exit code 0 means all selected expectations passed, 1 means an outcome failed,
2 means configuration/preflight/runtime execution failed, and 130 means an
interruption. Alternate evaluation does not overwrite the original acceptance
report unless its path is explicitly supplied.

## Serialization and recovery

The runner and its observer-build command share a per-user host lock. They refuse
to overlap an already visible Minecraft or Gradle JVM. External launchers do not
honor this lock, so keep them closed during QA. The runner terminates only process
groups descended from its own invocation on timeout or interruption; it never
kills an unrelated JVM to obtain the lock. Partial evidence remains available.

`--resume` reuses only passing cases with the same scenario, tool, observer,
candidate, dependencies, runtime, and unchanged evidence hashes. Restart cases
also bind to their source case's identity and saved evidence. A failed or
interrupted attempt gets a new directory on retry. `--case NAME` selects a case;
use `--resume` to reuse a completed prerequisite for a selected restart.

Run the runner's failure-oriented tests with:

```sh
python3 -m unittest discover -s tools/tests -p test_village_compatibility.py -v
```

## Independent data packs and definition reloads

A case can provide `datapacks` as a map from a simple pack name to a directory.
The runner verifies pack format 15, fingerprints every file, rejects symlinks,
and enables the pack in the isolated new world. A saved-world restart preserves
its saved packs. Datapack changes invalidate resumed evidence.

The fixture at `tools/village-compatibility/fixtures/contribution` contributes a
structure to `minecraft:village` and defines a tag depending on that shared tag.
It also overrides the bundled clockwork profile with equivalent recognition and
a different priority, so the winning pack provider can be asserted. It has no
structure set and adds no natural generation. Mount it using the name
`vgg-compat-contribution` and set `definitionReloadChecks: true` to exercise a
malformed reload followed by a valid exclusion profile. These controlled edits
happen only in the copied test world after natural observation. They check catalog
retention, publication, tag composition, and frozen population state.

Every case reports `compatibilityChecks`, including native structure serialization
round-trip results, supported piece counts, the active catalog, and tag-correction
status. See [settlement definitions](village-compatibility-definitions.md) for the
format, lifecycle, and supported version boundaries.

Some Integrated dependencies bundle MixinExtras under both old and new Maven
coordinates. Forge's mod-list entry can report the older library while MixinExtras
runs a newer elected service. The runner retains both coordinate selections,
checks the reported metadata against those selected artifacts, and separately
requires the logged active service version to match the compatible selected
version. Missing service evidence or an unexpected version fails the case.
Other duplicate embedded mod IDs across different coordinates remain unsupported.

## Template classification evidence

The packaged observer records `templateClassification` independently of the
managed-generation and coverage switches. It includes the selected profiles,
size family, traversal limitations, residential piece count, unique planned bed
positions, candidate entrances, and native request template IDs. The request
list comes from the installed Guard Villagers configuration and generated pieces;
it is not an inferred spawning allowance.

`unverifiedPieces` counts readable pieces whose template resource lacks verified
metadata. Reader failures remain separately visible in `pieces` through
`readerSupported`, `readerReason`, and `elementClass`. A hostile settlement can
therefore be excluded correctly while its custom pieces remain unreadable.
Neither an empty reload-error list nor a zero unverified-template count proves
that all pieces are readable or that guards can traverse the settlement.

Compare residential demand with `diagnostics.issuance`, the natural/settled
censuses, and the saved-world evidence. A census around a settlement can include
nearby guards outside its managed plan. Template bed positions describe planned
resources; actual blocks after processors, clearance, and paths require their
own terrain checks.

For a resource-precedence regression, use an isolated datapack that replaces a
known template with different resource bytes. The replacement should remain
loadable while its old template-specific hints disappear. Combined Better
Villages/Lios cases should likewise report one winning provider for each resource.
Do not combine metadata just because both mods are present.

Keep controlled activation separate from ordinary installation evidence. For
example, CTOV 3.4.14 ships underground structures without registering them in its
structure sets. A test datapack can activate one for inspection, but that test
cannot establish that unmodified CTOV naturally generates underground villages.

For rejected-reload coverage, test both an invalid profile alone and an invalid
profile together with a changed template resource. The first should retain the
exact catalog. The second should retain profile choices, revoke changed hints,
and advance the observation generation. A later valid reload should restore
verified hints when the original resource returns. Saved plans and credits must
remain unchanged in both cases.

## Guard membership lifecycle checks

A production case can enable `membershipChecks` to supply two stationary guards
on safe natural surfaces, one in the generated village and one at least 30 blocks
below it. The observer waits for normal discovery, verifies that only the upper
guard affiliates, saves and removes that guard, then loads and rejoins it at
ground level. Saved affiliation and patrol/NoAI settings must remain intact.
These supplied entities are reported separately from the natural census. This
checks entity rejoin and registry serialization, not a full server restart or
native chunk-unload cycle.

## Periodic coverage observations

`refreshChecks` records coverage state every 20 ticks, bounded to 256 samples.
Samples include leases, successful-visit times, guard positions and availability,
navigation completion, and native/VGG goal priorities, running states and flags.
Each sample includes at most 32 guards and 64 goals per guard. The observer does
not change goal priorities or call goal eligibility methods to collect evidence.
An unchanged refresh counter establishes publication continuity; lease-token
and visit-time comparisons are needed to assess retained assignments and service
frequency. Neither a READY status nor that counter alone proves useful patrols.

## Controlled footing checks

`bridgeChecks` runs after natural observations. A small temporary block fixture
straddles two already loaded chunks, tests a supported step, nonadjacent and
diagonal endpoints, removed support, step-up headroom, and an absent remote
chunk, then restores the original block states. Results are explicitly labeled
artificial footing checks. They validate production linkage and step predicates;
they are not evidence of a generated guard completing a bridge patrol or water
recovery. The fixture does not create a capability for boats or moving platforms.

## Controlled cot route checks

The optional `cotRouteChecks` case flag creates a restored platform above the
observed settlement in a production Forge client. A real Guard Villagers entity
uses native navigation to find an alternate route when the nearer endpoint has
ordinary guard clearance but insufficient passenger clearance. The fixture also
removes endpoint support and verifies rejection. It refuses to overwrite terrain.
This checks packaged routing and clearance code, not evacuation, cot reservations,
medical transfers, or navigation through generated buildings.

## Villager AI coexistence checks

`tools/village-compatibility/coexistence.json` provides a baseline case, a Liberty's
Villagers case, and a witch-settlement case with managed generation, coverage and
buildings enabled. Build the observer and release jar before running the manifest.

The Liberty case pins the supplied Liberty's Villagers 2.0.0+forge+1.20.1,
Architectury 9.2.14 and Cloth Config 11.1.136 jars by hash. Liberty's metadata
requires Architectury 9.1.12 or later but omits Cloth Config; the runner supplies
the observed missing requirement. These are optional test dependencies, not
required addon dependencies. Other library versions have not been qualified by
this manifest.

`quartermasterClaimChecks` runs controlled native POI claim/release operations
after natural village observation. It checks live profession and job memory,
the station service window, released tickets with stale operator state, native
operator NBT save/load, and station removal. It does not prove autonomous job
acquisition, a full server restart, item transfers, every Liberty setting, or
healing coexistence.

## Combined installation matrix

`tools/village-compatibility/combined-installations.json` runs the baseline,
nine individual village mods, six targeted pairs, and the full nine-mod set.
All cases enable managed generation, coverage and buildings. Their assertions
cover production loading, exact dependency versions, definition reload results,
and settlement intent. Witch cases also require no friendly record or observed
guards. Liberty cases include the controlled Quartermaster claim fixture.
Passing these assertions is not proof of patrol coverage, service delivery,
healing coexistence or safe traversal.

Use one frozen addon jar and pin its SHA-256 in the run configuration. Preserve
that configuration, the preflight manifest and every run directory. To summarize
completed evidence against that hash:

```sh
python3 tools/report_village_compatibility.py \
  --output build/village-compatibility/runs \
  --candidate-sha256 <frozen-jar-sha256> \
  --report build/village-compatibility/matrix-report.json
```

The report rechecks recorded file hashes and assertions, rejects a different
candidate, and reports missing cases. The immutable `matrix.json` inventory
retains every configured case even when `--case` selects only one to prepare or
run. Changing that inventory requires a new output directory. Preserve
`attempts.json` as well as the preflight manifest and run directories: it records
the latest attempt before launch, so a missing or corrupt manifest, or a deleted
latest run directory, cannot reveal an older pass as current evidence. A newer
failed attempt is not replaced by an older pass. Legacy output with only a
preflight manifest cannot establish full matrix completeness. The report includes effective resource providers, loaded mod
versions, issuance counts and bounded-work counters. These counters are not
server tick latency, memory profiling or a higher-population qualification.

The controlled cot fixture also installs a route whose winning candidate was
queried before another candidate, verifies the native remembered destination,
and checks that a carrier lease intercepts native path recomputation. The lease
is temporary fixture state; this does not qualify a complete casualty journey.
