# Village Defense Buildings Step 8: validation and release evidence

Step 8 closes the automated portion of the 1.5 building plan. It adds a
separate, opt-in workload for startup pool integration, 100 passive building
placements, post-placement idle behavior, and JFR integrity. It does not add a
Training Yard controller or any other recurring gameplay system.

## What the workload proves

The fixture starts from the complete 25-entry built-in definition snapshot. It
times one additive startup publication and an immediate idempotence pass, then
places one passive Training Yard for each of 100 simulated village candidates.
The five biome templates are used evenly. Training Yards are intentional here:
they exercise the custom pool element, real template processing, anchor
consumption, deterministic identity, and SavedData publication without adding
guards, Armories, navigation, or combat noise to the measurement.

Every candidate uses a unique anchor inside an already loaded disposable test
fixture. The harness checks the complete template bounds before placement and
fails if a chunk is not already loaded. It never creates a ticket or forces a
chunk. The fixture is spatially compact and templates may overlap, so this is
not 100 naturally generated villages and is not visual or terrain-generation
evidence. It isolates the marginal VGG work that one eligible defense piece
would add to each of 100 village candidates.

After placement, 200 server ticks are sampled. The retained report requires
exactly 100 new passive records, no record growth during the idle sample, an
unchanged forced-chunk set, a successful 25-entry publication, and a 25-entry
idempotent repeat. The paired JFR must be readable and contain real execution
samples. These structural checks prove bounded work and the absence of a
per-record controller; source and correctness tests retain the no-scan,
no-ticket, and no-recurring-task architecture contracts.

## Timing interpretation

The JSON records startup time, total and percentile placement time, and idle
p50/p95/p99/max MSPT. There is deliberately no absolute timing gate: wall-clock
numbers from different hardware, JVM state, worlds, or mod sets are not valid
release comparisons. Regressions should be compared on the same machine using
the same Java 17 runtime and exact production artifact.

## Running the gate

Generate the performance evidence in its isolated disposable world:

```bash
./gradlew runGameTestServer -PvillageBuildingPerformance=true
./gradlew verifyVillageBuildingPerformanceReport
```

Then run the aggregate gate without the performance property:

```bash
./gradlew verifyVillageDefenseBuildingsReleaseCandidate
```

The aggregate task reruns the ordinary correctness GameTests and creates a
deterministic manifest under
`build/reports/village-building-release-candidate`. The manifest binds the
exact JAR and both performance files by byte size and SHA-256. It explicitly
marks the retained manual matrix as required instead of turning unchecked
natural-terrain, old-world, or third-party compatibility rows into passes.

## Remaining manual release boundary

Natural generation in all five village biomes, every rotation and terrain
edge, an upgraded 1.4 world, standalone/integrated Watchtower coexistence, and
representative village-overhaul mods or datapacks still require human review.
Those results belong in
[`village-defense-buildings-release-matrix.md`](village-defense-buildings-release-matrix.md)
against the exact candidate artifact.

## Verified development capture

The retained Step 8 capture passed one isolated performance GameTest. Startup
published 25 entries and the idempotence pass found the same 25 already
present. All 100 placements succeeded, the passive record count stayed at 100
through all 200 idle ticks, and the forced-chunk count remained 25 before and
after. The independent verifier parsed real execution samples from the JFR.

The Step 9 recapture is bound to `villagerguardgunners-1.5-dev.jar`, 1,075,740 bytes,
SHA-256
`c80aff053cef3c38362da1f08724ada3b3267f23b694e80b9963046186777e06`.
The captured JFR SHA-256 is
`b7d1073428cb36ecb65dae0717a96e3c7b4fe16cc5bdce1dc2fdb5431af1eb2b`.
The measured wall-clock values remain informational because this is not an
absolute timing gate.
