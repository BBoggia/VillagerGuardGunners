# Quartermaster validation

Use a copied world and the normal packaged mod JAR when checking an upgrade.
The Gradle development runtime is useful for implementation, but does not establish
that reobfuscated calls, installed dependencies, or client screens work in a normal
Forge installation. Tested mod versions and interaction boundaries are listed in
[Quartermaster compatibility](quartermaster-compatibility.md).

## Upgrade behavior

Guard service data from released 1.8 preserves Manual, Restock and Profile policies,
the selected profile, replacement permission and inventory contents. Existing guards
start with automatic station selection until assigned explicitly. Station ownership,
stock, returns and identity remain intact. Existing villager offers retain their
prices and uses; new Quartermaster offers initialize once alongside them.

Breaking and placing a packed station retains its stock and station identity.
Open menus from the old placement become invalid. Chunk unload/reload and server
restart must preserve exact item data, including custom names and stack tags.
Recovery receipts remain subject to the existing [service recovery rules](quartermaster-service.md).

## Repeatable packaged checks

`tools/quartermaster-qa.gradle` builds a separate observer mod. It is only for
isolated acceptance installations and must never be distributed with the product.
Build the production artifact and observer with Java 17:

```sh
./gradlew --offline build verifyReleaseArtifact
./gradlew --offline -I tools/quartermaster-qa.gradle reobfQuartermasterProbeJar
```

The runners use the installed Forge 47.4.10 runtime and dependencies prepared under
`build/release-1.8-server-smoke`. Client runners also read the installed Minecraft
1.20.1 Forge runtime and assets. They copy mods and worlds into new output directories,
bind dedicated servers to loopback, and record artifact hashes and outcomes.
`--accept-eula` records acceptance only for those test directories. Each output path
must be new. Run one workload at a time.

```sh
python3 tools/run_quartermaster_acceptance.py \
  --jar build/libs/villagerguardgunners-1.8.jar \
  --legacy-jar build/distributions/release-candidate/1.8/villagerguardgunners-1.8.jar \
  --output build/quartermaster-upgrade-check --scenario upgrade --accept-eula
python3 tools/run_quartermaster_acceptance.py \
  --jar build/libs/villagerguardgunners-1.8.jar \
  --output build/quartermaster-scale-check --scenario performance --accept-eula
python3 tools/run_quartermaster_multiplayer_qa.py \
  --jar build/libs/villagerguardgunners-1.8.jar \
  --output build/quartermaster-multiplayer-check --accept-eula
```

Upgrade checks first save a world with the released artifact, then test an upgraded
copy and a second server restart. The original test world's files must stay unchanged.
Multiplayer checks use two actual local Minecraft clients with separate offline test
identities. They exercise competing previews, exclusive merchant access, disconnect,
reconnect and refusal of requests from an old management session. The test server
also loads the example trade datapack. Screenshots and logs remain in the output.

The ordinary `run_quartermaster_qa.py` suite also checks management regressions:
100 assigned guards remain reachable in successive batches, four varied 64-guard
bulk estimates share the overview's sixteen-unit budget, and changing stock or
source visibility cannot publish an old estimate. Settings changes cancel stale
previews. Manual can retain a revoked assignment while new enrollment remains
blocked. Roster cleanup rejects stale generations, replayed requests, unauthorized
players and entities loaded in another dimension, and returning guards restore
their records.

The rendered `run_quartermaster_client_qa.py` suite sends rapid Open/Back requests
through real screen buttons, checks that rejected navigation keeps the current
screen usable, and removes an unavailable roster entry through its row action.
Its screenshots include the control at the largest supported GUI scale.

## Interpreting scale measurements

The scale fixture uses 25 or 100 AI-enabled guards, four stations and a shared
Village Armory. It compares Manual service with no screens, Restock service with
no screens, and Restock service with four open management menus. All trials use
the same entity set and reset test supplies between stages. Operators retain AI
but have no walking speed. This measures a controlled supply area, not a generated
village's terrain or combat performance. Daylight is held at working time to let
large queues finish. In ordinary gameplay, a queue that outlasts the service window
continues during later working periods.

The fixture confines stationary operators in physical work booths and checks their
availability repeatedly. Without those booths, a crowd can push a zero-speed
operator out of range; it cannot walk back as a normal villager would. Record fixture
changes with the results and compare each population against its own baseline.

The observer records server tick percentiles, four-menu broadcast time, four-scope
query time, encoded packet count and payload bytes. The four menus use FakePlayers
without sockets or rendering; these measurements are separate from the real-client
multiplayer checks. Payload sizes exclude network transport framing and the initial
screen-opening exchange. Explicit scope
queries are extra observer work, and the first 100 ticks of each stage are excluded
from latency samples. Measurements depend on hardware, warm-up and other running
applications.

The tick timer spans Forge's highest-priority START through lowest-priority END
events, including Quartermaster work and observer work. Minecraft's native tick-time
array is not used: in this Forge version it is finalized before END-event work.
Packet counts intercept the connection's send methods, which also receive Forge
custom payloads. They must be nonzero with active overview menus.

Completion, item conservation and service limits matter alongside timing. Guards
must receive their requested reserve, overview captures must respect their per-tick
budget, and the fixture must not acquire additional forced chunks. The 100-guard
trial also verifies that a limited overview reports truncated coverage. A bounded
report is not a claim that every guard in a large village has been inspected.

These checks do not establish compatibility with arbitrary merchant replacements,
WAN latency, every gun pack or every village layout. Normal Minecraft disconnect
handling can drop unused merchant input items near the player; conservation checks
include those items rather than expecting every input to remain in player storage.

## Recorded service-queue sample

The Quartermaster review JAR with SHA256
`f027d44ba5a7061b0293b882b35e547e4aeea5a8649653e6ace852d14ef2e0b8`
completed both populations on an Apple M4 Max with 64 GiB RAM, Java 17,
Forge 47.4.10 and a 2 GiB server heap. Each guard needed one additional loose round
from two shared Armory stacks. Every guard received it in both active stages;
inventory totals, the transfer limit, capture budget and coverage cap passed.

| Guards | Mode | Tick p95 | Tick p99 | Last delivery |
| ---: | --- | ---: | ---: | ---: |
| 25 | Manual, no screens | 1.674 ms | 3.536 ms | — |
| 25 | Service, no screens | 1.123 ms | 6.997 ms | 76.55 s |
| 25 | Service, four screens | 1.559 ms | 6.872 ms | 79.00 s |
| 100 | Manual, no screens | 3.236 ms | 4.637 ms | — |
| 100 | Service, no screens | 2.622 ms | 4.104 ms | 645.55 s |
| 100 | Service, four screens | 2.845 ms | 4.232 ms | 648.00 s |

The four-menu payload rate averaged about 793 bytes/s for 25 guards and 827 bytes/s
for 100 guards. Two viewers had restricted visibility of the shared Armory, so this
is not a worst-case all-sources-visible packet sample. The 25-guard sample used
unconfined stationary operators; the 100-guard sample used work booths. Fixed stage
order, JIT warm-up and path choices also affect comparisons. Lower later percentiles
do not show that service has no cost.

The main practical limit in this sample was delivery time. A 100-guard queue needed
about 10 minutes 48 seconds despite low tick cost. Large shared-stock queues can
span multiple ordinary working periods. Bulk settings apply immediately; collecting
supplies still requires queued service and travel.

This sample predates the incremental bulk-estimate changes. It measures overview
reporting and service delivery, not the cost of a varied bulk selection. The bulk
regression uses individually named guns and ammo plus armor with different wear,
and records preview enqueue time and shared-budget processing time separately.
Those diagnostic work passes omit normal world ticking and network transport;
they must not be reported as full server tick percentiles.
