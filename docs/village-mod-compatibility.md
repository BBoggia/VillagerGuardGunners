# Village mod compatibility audit

Audit date: September 8, 2026. The nine tested mods can load and generate their
representative settlements with the corrected dependencies and packaged-startup
fixes described here. Guard spawning, village recognition, and patrol coverage
still have material integration gaps. This first pass measures installation,
natural generation, population, and movement; it does not establish support for
every feature, village variant, or combination.

Use the repository's [production compatibility runner](village-compatibility-testing.md)
to reproduce these controls and add explicit outcomes for later integrations.

## Template classification candidate

The current candidate adds fingerprint-bound residential and entrance metadata,
CTOV size families, individual settlement profiles, and explicit traversal
limitations. See [village template support](village-template-support.md) for the
pinned resources and support boundaries. Actual native request sources remain
separate from residential demand, and saved population plans are preserved.

## Packaged template validation

The final candidate SHA256 is `69684b975492f10cfba3a56f10b355e66fff38077578ad3a92c2f6688dc898bd`. Production Forge 47.4.10 validation included 17 broad classification cases, followed by 11 final-jar persistence, resource, reload, and layout checks and a required-dependencies-only dedicated server. The final correction affects only reload handling; all template definitions and other runtime classes match the broad-matrix jar.

Representative planned-template observations:

| Installation/sample | Residential pieces | Bed positions | Entrance candidates |
| --- | ---: | ---: | ---: |
| Vanilla | 8 | 9 | 25 |
| CTOV large plains | 18 | 23 | 92 |
| CTOV small plains | 18 | 22 | 73 |
| CTOV medium plains | 29 | 41 | 138 |
| CTOV fortified plains | 19 | 35 | 98 |
| CTOV mountain | 7 | 10 | 109 |
| CTOV tree village | 21 | 37 | 48 |
| MMV jungle | 6 | 6 | 17 |
| MMV dark forest | 6 | 7 | 15 |
| MMV swamp | 5 | 7 | 16 |
| Better Villages | 15 | 40 | 63 |
| Lios Overhauled Villages | 11 | 21 | 40 |
| Better Villages + Lios | 4 | 5 | 25 |
| Tidal Towns | 1 | 2 | 30 |
| Sky Villages | 6 | 9 | 4 |
| Integrated clockwork | 13 | 29 | 34 |
| Integrated airship | 8 | 47 | 34 |
| Integrated pirate | 6 | 26 | 19 |
| Integrated sunken | 11 | 22 | 18 |
| Liberty’s Villagers | 8 | 9 | 25 |

Feature switches did not change residential/entrance observations. Saved guards, plans, and credits survived restart and profile reloads. Changed templates lost stale hints even when their accompanying profile was invalid; restoring the resource and submitting valid data restored verified hints.

The witch settlement remained hostile and produced no guards. CTOV underground could not be located in an ordinary installation because it is absent from the shipped structure sets; a separately labelled activation datapack exercised the shipped layout. Missing resources and movement limits remain documented in [village template support](village-template-support.md). These results do not establish complete staffing or patrol compatibility.

## Recognition foundation validation

The preceding foundation candidate added shared settlement definitions, optional
piece readers, and a correction for the tested Integrated Villages village-tag
defect. See [settlement compatibility definitions](village-compatibility-definitions.md)
for data formats, version gates, exclusions, and operator commands. That candidate
SHA256 is `49f71bd3470debf48cef46e0364d8426351204929ca871172f1474feff9e789e`.

Production Forge 47.4.10 tests use packaged JARs and independent copied worlds.
The final vanilla population, save/restart, CTOV recognition, Integrated Villages,
and vanilla reload cases passed their declared outcomes. A separate production
dedicated server loaded only the required dependencies, exercised inspection and
reload commands, and shut down cleanly.

| Check | Observed outcome |
| --- | --- |
| Vanilla managed population and restart | Six guards; identical saved UUIDs, plans, spent credits, and journals after restart. |
| Vanilla invalid and valid profile reloads | Invalid data retained the previous catalog; valid data published an exclusion. Existing plans and all six spent credits stayed unchanged. |
| CTOV structure recognition | All 68 village types in the installation remain recognized: 63 CTOV types and five vanilla types. The sampled village recorded 176 pieces within the 32-unit discovery budget. |
| Integrated clockwork piece reading | All 202 sampled pieces were readable, including 54 Integrated API elements. Placement/template identity and restored geometry passed native serialization checks. |
| Integrated village-tag composition | Vanilla, clockwork, and the independent pack's contribution remained in both the shared village tag and a dependent tag across rejected and accepted profile reloads. |
| Profile resource precedence | The independent pack's whole-profile override was the reported winning provider. |
| Base-only dedicated server | Optional profiles stayed inactive; native fallback and unsupported inspection worked. Reload advanced the catalog generation. |

These earlier results established the recognition and reload foundation. They did not
establish complete residence classification, patrol coverage, or staffing for
every village variant. In the sampled CTOV village, managed generation produced
an allowance of two and five observed guards. The preserved pre-change JAR
produced the same result under identical managed settings. Earlier observations
of 15 guards used unmanaged generation. The current template definitions address residential classification; additional
issuance sources and service behavior remain separate integration work.

## Core fix validation

The spawning and discovery corrections were retested after the initial audit.
The initial findings below describe the earlier artifact; they are retained as
reproduction evidence. The corrected candidate SHA256 is
`7b807e38ff1852a22fb45605ee81f769fbab152fe344bee69294defe51ed703b`.

Managed generation now distinguishes residential demand from actual native
spawn sources. The Guard Villagers template whitelist supplies issuance sites;
nonresidential markers do not inflate the house or occupied-cell counts. The
per-village cap and reserved garrison shares still apply. Plan format 2 reads
format-1 plans without changing their quotas, slot UUIDs, or immutable journals.
Previously frozen zero-allowance plans are not automatically refilled.

Discovery reads actual loaded native starts and references, with shared cursors
for unfinished village layouts. The former 64-type ceiling is gone. The existing
32-work-unit budget per dimension per tick remains. Origin unload invalidates
shared cursors, and tag reload makes old completed reviews unavailable until
reviewed again. Partial or changed layouts and missing references remain deferred.

Eight final-candidate profiles passed their runtime and outcome assertions.
All new terrain observations use the normal production Forge client, released
mod JARs, isolated worlds, and the same seed and settings described below.
Vanilla population controls disable defense buildings unless stated otherwise.

| Production profile | Staffing target / allowance | Observed guards | Result |
| --- | --- | ---: | --- |
| Vanilla, managed generation off | Unmanaged | 6 | Native behavior retained. |
| Vanilla, managed generation on | 9 / 6 | 6 | Default marker requests now produce guards; the initial audit produced zero. |
| Vanilla, maximum set to 2 | 2 / 2 | 2 | Native requests respect the village cap. |
| Vanilla, managed with defense buildings | 10 / 9 | 9 | Six ordinary slots and three reserved garrison slots share the allowance. |
| Saved managed village reopened with maximum 32 | 9 / 6, unchanged | 6 | Same six UUIDs and spent credits; existing journal files remain byte-identical. |

| Production profile | Pieces / homes | Full record by tick | Coverage READY by tick | Affiliated guards | Areas with recorded visits |
| --- | ---: | ---: | ---: | ---: | ---: |
| CTOV installation, 68 village types loaded | 176 / 21 | 200 | 1200 | 15 | 4 / 10 |
| Better Villages | 126 / 40 | 200 | 1600 | 6 | 6 / 13 |
| Lios Overhauled Villages | 122 / 21 | 200 | 1200 | 6 | 2 / 15 |

Discovery timings are observation samples in simulation ticks after locating
the village, not exact completion timestamps or wall-clock performance guarantees.
The large-village profiles run for 2,400 ticks. READY means a coverage layout
was available; successful visits are counted separately. Manual checkpoint and
combat exercises temporarily occupy some guards, as in the original audit.
Lios was surveying terrain again at the final sample: the existing periodic
coverage rebuild pauses coverage while it runs. Its earlier READY state and
visits do not establish continuous or complete patrol coverage.

The unit suite passes all 846 tests, including native-marker quotas, capped
allocation, garrison sharing, and format-1 journal preservation. The release
artifact check passes. New permanent native integration fixtures cover default
spawn requests and shared discovery lifecycle behavior. Their sources compile;
this retest uses production-client observations instead of a development
GameTest launch. A separate packaged lifecycle probe also confirms a shared
128-piece scan completes in nine ticks without partial publication, and checks
origin unload, replaced starts, tag reload, and missing references.

These fixes do not add custom village tags, residence definitions, building-pool
adapters, larger population caps, or mod-specific defaults. CTOV's residence
classification, other mods' missing/invalid tags, and terrain reachability remain
separate integration work. Existing worlds keep their frozen spawning allowances.

## Initial audit runtime and evidence

The initial audit used a packaged development build labeled 1.6, before the
release identity was corrected to 1.7. It does not describe the published 1.6
release. That build ran on
Minecraft 1.20.1 / Forge 47.4.10, Java 17, with Guard Villagers 1.6.19 and
Timeless and Classics Zero (TaCZ) 1.1.8-hotfix. Fzzy Config 0.7.6 and Kotlin for
Forge 4.12.0 supply the base dependencies. The candidate SHA256 is
`fa5878d894b8f69bb644782dd35c5e46c677402da535d836782910ac7f66af54`.

Tests use the normal installed Forge bootstrap (`--launchTarget forgeclient`)
and release JARs in isolated `mods` folders. They do not use Forge userdev, mapped dependency JARs, development
class directories, or the GameTest runtime. A separate, packaged and remapped
observer mod records the production flag, actual loaded code source, selected
JAR hashes, structure metadata, resident counts, movement, damage, and screenshots.
The ordinary launcher profile and existing worlds are not modified. There are
37 completed production-profile observations, including baseline, managed, and
focused cases. The two initial Liberty dependency failures were retested with
Cloth Config. Interrupted and calibration runs are excluded from those results.

Each initial profile generates a fresh normal world with seed `314159`, Normal
difficulty, and a naturally located representative structure. The observer loads
its footprint and records residents after 200 and 500 ticks. Render and simulation
distances are both 10 chunks. The selected footprint stays loaded during
observation. Counts include only entities inside the observed footprint plus an eight-block margin. They are not
cumulative spawn totals, stable gun-upgrade percentages, or population promises.

The default and managed profiles use separate fresh worlds. The latter enable
managed generation, automatic coverage, and Village Defense Buildings. Adding
buildings changes jigsaw generation, so differences between these profiles are
not by themselves proof that a particular guard was lost. A separate population-only
control isolates that question. Behavior profiles retain ordinary generation,
enable automatic coverage, and issue explicit native checkpoint orders on the
existing terrain. Guards supplied where none generated are labeled separately.

A calculated path is not a completed journey. Checkpoint results require actual
movement within three blocks of the destination. Controlled combat supplies a
valid loaded AK-47, places a live, mobile zombie on generated terrain, and sets
the guard and zombie targets once. Ammo consumption and damage source are recorded. Initial cases require a walking
path to the zombie; focused cases record reachability separately. These checks
exercise combat execution rather than proving autonomous target acquisition.
Other natural defenders can intervene; a bullet hit with the designated guard's ammo unchanged proves
settlement gunfire, not that the designated AK-47 fired. Melee and arrow kills
do not prove gunfire. The observer does not directly invoke shooting, move guards along their
routes, disable their AI, or modify the terrain.

Dedicated multiplayer servers, mixed installations containing all nine mods,
every biome/seed, full raids, long-term population growth, resupply, medical care,
and complete Squad Command Ledger workflows remain outside this first pass.

## Dependency sets

The profiles use the following additional released dependencies:

| Profile | Additional JARs |
| --- | --- |
| CTOV | Lithostitched 1.4.11 |
| Villages&Pillages | YUNG's API 4.0.6 |
| Integrated Villages | Integrated API 1.8.0, Create 6.0.8, Supplementaries 3.1.43, Moonlight 2.16.35; Create supplies its embedded Flywheel/Ponder libraries |
| Better Villages | Library Ferret 4.0.0 |
| MMV | Moog's Structure Lib 3.1.2 |
| Liberty's Villagers | Architectury 9.2.14 and Cloth Config 11.1.136 for corrected runs |
| Tidal Towns, Sky Villages, Lios | No additional external dependencies in these profiles |

The user's downloaded copies are used when available. Missing files came from
official Modrinth release metadata, with downloaded hashes verified. Lios 0.1.1's
release page explicitly lists Minecraft 1.20.1 support.
[Lios 0.1.1 file](https://www.curseforge.com/minecraft/mc-mods/lios-overhauled-villages/files/7814363)

## Initial natural-generation samples

| Mod / tested version | Representative structure | Villagers / guards / gunners | Village tag | Main finding |
| --- | --- | ---: | --- | --- |
| Baseline | Vanilla plains | 8 / 6 / 1 | Present | Reference behavior; managed population needs a separate correction. |
| CTOV 3.4.14 | Large plains | 20 / 15 / 2 | Present | Its village types exceed discovery's limit; its houses are not classified. |
| Villages&Pillages 1.0.2 | Witch village | 0 / 0 / 0 | Absent | Hostile settlement; absence of friendly guards is appropriate. |
| Integrated Villages 1.3.2 | Clockwork village | 48 / 14 / 3 | Rejected | A missing required structure invalidates the whole village tag. |
| Tidal Towns 1.3.4 | Ocean village | 16 / 6 / 2 | Absent | Local movement and gunfire work; automatic village coverage is unintegrated. |
| Better Villages 3.3.1 | Overhauled vanilla plains | 53 / 6 / 1 | Present | Vanilla IDs work, but staffing and coverage need to match the larger settlement. |
| Sky Villages 1.0.4 | Sky village | 24 / 0 / 0 | Absent | No natural guards in the sample; needs spawn and elevation-aware integration. |
| Moog's Missing Villages 2.1.1 | Jungle village | 11 / 0 / 0 | Absent | No natural guards in the sample; custom residences are not classified. |
| Lios Overhauled Villages 0.1.1 | Overhauled vanilla plains | 15 / 6 / 0 | Present | Automatic patrol visits work; staffing and reachability remain uneven across 15 areas. |
| Liberty's Villagers 2.0.0 | Vanilla plains | 8 / 6 / 1 | Present | Loads with Cloth Config; checkpoint movement and TaCZ gunfire observed. |

These samples range from 70 vanilla pieces to 202 clockwork pieces. The CTOV
sample is 123 × 168 blocks; Better Villages is 178 × 185; Lios is 195 × 195.
Sky Villages spans Y=206–273. Those dimensions describe structure bounding boxes,
not reachable walking areas.

## Confirmed data and implementation problems

### Initial managed-population spawn-source regression (corrected)

Guard Villagers 1.6.19 defaults to six guard requests at
`minecraft:village/common/iron_golem`. Our population adapter classifies ordinary
house pieces and defense buildings, then suppresses native requests outside those
allocated sources once a village is enrolled. The iron-golem piece is not one of
those sources. Homes measure demand, but they are not the upstream spawn trigger.

The population-only production control confirmed the effect: the same 70-piece
vanilla layout with 13 recognized house pieces produced **zero guards**, compared
with six in the ordinary profile, with Village Defense Buildings disabled in both.
The existing integration fixture explicitly changes the native whitelist to a
house and the native count to two, so it did not cover the default installation.

The fix should separate residential demand from actual issuance sources. Keep
one village allowance, identify the native requests that really occur, and reserve
safe distributed spawn positions without counting roads or golem markers as homes.
If demand exceeds the native six requests, the managed system needs its own
explicit issuance schedule; a higher cap cannot manufacture extra upstream requests.
Preserve one-time credits and restart behavior. Do not require players to rewrite
Guard Villagers' template list just to retain its normal village population.

### Initial CTOV discovery type limit (corrected)

CTOV registers 21 village variants at each of three sizes: 63 variants plus the
five vanilla types. `VillageDiscoveryRuntime` retains at most 64 types. When that
limit is exceeded it also suspends home observations, guard affiliation, and
building association. This affects more than the types omitted from the list.

Replace the lifetime type ceiling with bounded work that eventually visits every
eligible loaded structure. Prioritize actual loaded starts/references rather
than repeatedly searching every possible type in every loaded chunk. Keep a
per-tick work budget and explicit unresolved state.

CTOV house IDs also use its own namespace and `/house/` paths. The current
`isHouse` classifier only recognizes `minecraft:village/.../houses/...`.
Introduce explicit per-mod residence definitions and use those consistently
for demand and patrol anchors.

### Integrated Villages invalidates the common village tag

The 1.3.2 Forge JAR lists `integrated_villages:swamp_village` as a required member
of `minecraft:village`, but contains no such registered structure. It does contain
`sunken_village`; that is not evidence that the two are interchangeable. The
production log rejects the entire tag and then reports failures in dependent
Supplementaries destination tags. The actual clockwork holder is not tagged. Because this is the shared tag,
the defect also removes the vanilla village entries in that profile; it is not
limited to recognizing clockwork villages.

Prefer an upstream corrected file. A version-specific compatibility correction
must preserve other packs' tag contributions and make genuinely optional members
optional. Merely appending the clockwork ID in another additive tag file does not
remove the invalid required reference. Our own explicit friendly-village registry
can reduce reliance on one shared tag, but would not repair the upstream tag for
other mods.

There is a second independent integration gap: many Integrated Villages houses
use `integrated_api:integrated_api_single_pool_element`. Its `IASinglePoolElement`
extends `IAPoolElement`, not Minecraft's `SinglePoolElement`. Our population
adapter therefore skips those pieces before even checking the template ID.
Fixing the tag and adding names to the residence list would not be sufficient;
an optional adapter must extract template and geometry from that actual type.

### Friendly custom structures lack integration metadata

Tidal Towns, Sky Villages, and MMV do not supply the village tag used by
Villager Guard Gunners' discovery and population systems. Their custom residence templates are also outside the vanilla classifier.
Sky/MMV generated no guards in these samples. A larger configured cap does not
create spawn requests or enroll an unrecognized village.

Add versioned definitions for friendly structures, residences, and actual spawn
sources. Handle the actual pool-element types used by each mod. Keep hostile structures
explicitly excluded: Villages&Pillages advertises hostile witch settlements, so
a generic name-based village detector would produce the wrong behavior.
[Villages&Pillages project](https://modrinth.com/mod/villages-and-pillages)

### Liberty's Villagers needs an undeclared JAR dependency

The downloaded Forge JAR declares Architectury, but omits Cloth Config from
`mods.toml`. Without Cloth Config it fails before world creation with missing
AutoConfig classes. Cloth Config is listed on its official project page.
With Forge Cloth Config 11.1.136 and Architectury 9.2.14 installed, the production
client loaded and generated the baseline village with eight villagers and six
guards. The earlier installation error is resolved in the isolated test profiles,
including a repeat of the previously blocked managed-generation profile.
[Liberty's Villagers project](https://www.curseforge.com/minecraft/mc-mods/libertys-villagers)

Both native checkpoint trials in the first behavior run completed. Its zombie
was killed by melee guards while the designated gun stayed loaded, so that run
alone does not verify Liberty's Villagers gunfire compatibility. The focused
follow-up recorded TaCZ bullet damage from a different guard in the settlement
and another completed checkpoint journey.

The inspected broad navigation, safe-fall, climbing, and melee mixins mostly gate
on vanilla villager/golem entity types. They do not automatically improve guards'
movement. Their villager POI, work, and sleep changes still warrant a separate
Quartermaster and medical-service coexistence test.

## Movement and combat evidence

The longer vanilla control reached automatic coverage after several thousand
observation ticks. At 6,500 ticks all six guards were registered, four of five
coverage areas had recorded real visits, and the remaining area reported
`NO_REACHABLE_GUARD`. Discovery was still incomplete at the original 500-tick
census. That short census is appropriate for resident counts, but cannot by
itself establish that discovery is broken. With roughly 1,014 loaded chunks in
this fixture, registration latency is also a reason to improve the discovery
work queue; this is not a general performance benchmark. The queue rotates four
chunks per tick and grants each eight work units. Multiple chunks can begin
reading the same not-yet-published structure one piece at a time. Sharing one
in-progress structure scan would avoid repeating that work across its references.

Native checkpoint trials run for 600 ticks and use actual generated terrain.
The Guard Villagers checkpoint goal targets an exact block, allows an attempt
for 200 ticks, and then imposes a 200-tick delay after timeout. It does not declare
a `MOVE` goal flag. That makes competing idle movement a plausible contributor
to missed checkpoints, alongside terrain and path changes; this audit does not
claim to have isolated the cause of every missed destination.

The CTOV run reached two of three checkpoint destinations. The third guard walked
about 80 blocks during the trial but remained at least 11.6 blocks from its
12.9-block-distant destination. Its gun test produced actual TaCZ bullet damage,
spent two rounds, killed the zombie, and left the guard alive. Automatic coverage
stayed inactive, with all 15 guards awaiting association and the structure-type
limit flag set.

Tidal Towns' final census found all six guards alive, on ground and out of water.
They stood on or beside bottom slabs, unlit campfires, and waterlogged top slabs
and trapdoors. Their net displacement ranged from 2.5 to 10.9 blocks. The coverage
graph rejects waterlogged floors and all campfires, including unlit ones, and
uses integer-height clearance. Even after village recognition is added, those
rules need to be checked against the actual dry deck surfaces guards can use.

The Integrated Villages checkpoint sample reached its destination. Both
Integrated Villages and Lios produced TaCZ bullet damage while the designated
AK-47's magazine remained unchanged, so those observations establish gunfire
from defenders in the settlement without attributing it to that specific guard.
The Villages&Pillages friendly-village control reached both checkpoint targets
and produced TaCZ bullet damage with ammo consumption. Its hostile witch-village
sample remains a separate generation/exclusion check.

The first Tidal, Sky, and MMV checkpoint selector found no qualifying reachable
villager-position target 8–48 blocks away. This is **not** a measured zero-percent
arrival rate. Sky and MMV used three explicitly supplied guards because none
spawned naturally. MMV's supplied gunner produced TaCZ bullet damage and killed
the live zombie. The focused follow-up allows one-block path-target tolerance, orders the actual
returned endpoint, and gives newly supplied guards 100 ticks to settle before
selection. It then observes 500 ticks of checkpoint movement. Combat samples
actual fractional-height collision surfaces, permits shots across gaps, and
records whether a walking path exists. This deliberately separates the ability
to shoot from the ability to walk to the target.

With that adjustment, a Tidal Towns guard completed a local 9.6-block checkpoint
journey and fired two rounds that killed the zombie. This demonstrates local
movement and gunfire on the generated deck, not coverage of the entire outer
walkway network. The initial lack of selected paths must not be presented as a
native pathfinding failure.

Sky Villages' focused test supplied three guards. Its path selector attempted
18 resident-position queries and found no complete qualifying path, so no
checkpoint journey was scored. A supplied guard nevertheless fired two rounds
at a zombie approximately 23 blocks away, killing it while remaining alive.
The observer recorded no walking path to that combat position. This verifies
ranged combat in the sky village; it leaves bridge traversal unresolved.

MMV's focused follow-up completed one checkpoint journey with a supplied guard
and confirmed TaCZ bullet damage with ammo consumption. This establishes useful
local behavior on its jungle terrain, while natural guard spawning and village
services remain unintegrated.

Better Villages remained unregistered at 4,000 observation ticks. In the extended
6,500-tick run, discovery did register all 126 pieces and 40 home POIs; four of
six guards had affiliated at 6,400 ticks. By 6,500 ticks all six had affiliated
and a `READY` layout contained 13 coverage areas. No completed automatic coverage
visits were recorded by the end of that run. The layout was only 41 ticks old:
two areas were assigned, seven reported insufficient guards, two were blocked,
and two had no reachable guard. That short active interval cannot establish a
steady-state arrival failure. The result establishes slow but working recognition;
sustained patrol coverage still needs a longer observation after initialization. The initial explicit checkpoint
trial missed its destination; the longer run found no qualifying checkpoint
destination and did not score a journey.

Lios reached `READY` automatic coverage in the completed 6,500-tick observation.
Its record contained 122 pieces and 21 home POIs, all six guards had affiliated,
and seven of 15 coverage areas had recorded successful visits. The remaining
areas and assignments still showed staffing and reachability gaps. This is
positive evidence of automatic patrol execution in a large village, not full
coverage of its entire footprint. The 15-area layout is also close to the current
16-area ceiling, although this sample did not hit that limit.

## Behavior sample summary

These are small observations, not measured success rates. “No selected route”
means the test found no qualifying complete path and issued no checkpoint order.
All gunfire entries below require damage from a real TaCZ bullet.

| Mod | Checkpoint observations | Gunfire | Automatic village coverage |
| --- | --- | --- | --- |
| Baseline | 1/1 arrived | Confirmed | At 6,500 ticks: 6 associated guards, 4/5 areas visited. |
| CTOV | 2/3 arrived | Confirmed | Blocked by structure-type limit. |
| Villages&Pillages | 2/2 arrived in friendly control | Confirmed in friendly control | Witch village intentionally excluded; friendly control too short to assess settled coverage. |
| Integrated Villages | 1/1 arrived | Confirmed from another defender | Shared village tag invalid; no enrollment. |
| Tidal Towns | 1/1 arrived in focused deck check | Confirmed | Custom village not enrolled. |
| Better Villages | 0/1 arrived initially; no selected route in longer run | Confirmed | At 6,500 ticks: 6 associated guards, 13 areas, 2 assignments; layout only 41 ticks old. |
| Sky Villages | No selected route; supplied guards | Confirmed from supplied guard | No natural guards or village enrollment. |
| MMV | 1/1 arrived in focused check; supplied guard | Confirmed from supplied guard | No natural guards or village enrollment. |
| Lios | No selected route in either checkpoint run | Confirmed, including ammo consumption in longer run | At 6,500 ticks: 6 associated guards, 7/15 coverage areas visited. |
| Liberty's Villagers | 2/2 initially and 1/1 in focused check | Confirmed in focused check | Village record discovered by 3,000 ticks; settled coverage not assessed. |

## Secondary log findings

CTOV also logs missing optional Wares item references in two of its own tags.
Integrated Villages' dependency set logs a Supplementaries advancement referring
to Farmer's Delight rich soil without that optional mod installed. Some Sky/Lios
world generation logs invalid hanging-entity positions. These are separate data
or decoration observations; no causal connection to guard AI was established.
An isolated offline observer account also causes a profile-key retrieval error,
and one client logged an OpenAL stop error during shutdown. Neither is evidence
of village compatibility failure.

## Staffing and navigation changes to consider next

There are two different ceilings. Managed generation has a default maximum of
16 guards and a hard limit of 32, and is disabled by default. Natural gun upgrades
are enabled by default and have their own local limit: **two automatically armed
guards within 64 blocks on Normal and Hard**, and one on Easy. Normal upgrades
also require an eligible initial crossbow and a successful 50% chance. Their
config-screen maximum is separately capped at 32. A larger guard allowance alone
therefore does not provide a larger armed defense. Existing manually equipped
guards are not counted against that automatic-loadout limit.

1. Increase the configurable population ceiling consistently across the config,
   population plan, validation, saved-data limits, and diagnostics. Today the
   default maximum is 16 and the hard limit is 32. Benchmark higher values before
   selecting new defaults. Preserve explicit user settings when applying mod-aware
   defaults, and distinguish population from armed/available defenders. Existing
   villages are observation-only: a higher initial cap does not add guards to them.
   Any repair or recruitment option needs a separate explicit policy and protection
   against issuing duplicate guards across unloads and restarts.
2. Base staffing on recognized residences, village extent, and connected regions.
   CTOV detection can supply a sensible initial preset, but its small and large
   villages should not automatically receive identical staffing. Additional guards
   need valid distributed spawn positions and practical access to supplies.
3. Survey and schedule coverage in bounded regions. The current graph can reject
   a whole village when exceeding limits of 64 anchors, 16,384 nodes, 256 domain
   chunks, or 16 coverage areas. Allow useful partial coverage while explicitly
   reporting regions that still need processing.
4. Make patrol destinations and routes match Minecraft collision and traversal
   rules for doors, slabs, stairs, bridges, and safe footing. Preserve separate
   connected components and stage long journeys through reachable intermediate
   points. Dry escape/return routes matter for Tidal Towns; altitude and bridge
   connectivity matter for Sky Villages and elevated Integrated villages.
5. Initial affiliation and new home/building associations now require local
   piece elevation evidence, preserving existing durable guard membership.
   Service routes still need their own terrain and connectivity checks;
   membership geometry alone does not establish access.
6. Add Village Armory, medical, and defense-building integration through each mod's
   actual pools and walkable entrances. Existing building definitions target the
   five vanilla house pools; tagging a custom village does not insert those buildings.

The graph and altitude items above are implementation risks and design proposals;
a startup log or one native checkpoint trial cannot certify them.

The core spawning and discovery corrections are now validated above. Next,
correct the Integrated Villages tag issue and add explicit friendly structure
and piece adapters, then address reachable coverage, distributed guard and
gunner counts, and mod-aware presets. Defense buildings and services should
follow once guards can be assigned and travel reliably.

## Packaged-startup fixes already applied

Two base-mod mixin mapping errors blocked the first production launches before
any of the village mods were added. The Recovery Cot name-tag redirect now remaps
its Minecraft invocation, and the checkpoint-goal mixin remaps the inherited
Minecraft goal methods while retaining the third-party field name. The release
artifact check now verifies those exact mappings.

The frozen candidate used for this audit passes all 843 unit tests and the
release-artifact checks. No compatibility presets, population caps, structure tags,
or village-specific gameplay changes have been applied yet.

## Implementation entry points

- Discovery and affiliation: `VillageDiscoveryRuntime`, `VillageRegistrySavedData`,
  and `VillageFootprint`.
- Demand, issuance, and allowances: `VillagePopulationPlacement`,
  `VillagePopulationPlan`, and `VillagePopulationSavedData`.
- Automatic gun distribution: `NaturalGuardLoadoutDefaults` and the
  `naturalLoadouts` configuration.
- Coverage geometry and scheduling: `CoverageTerrain`, `VillageCoverageLayout`,
  and `VillageCoverageRuntime`.
- Building pools and template adapters: `VillageBuildingPoolIntegration`,
  `VillageBuildingDefinitions`, and `VillagePoolElementAccessor`.

The default spawn-source and more-than-64-type regression cases are now covered
by the core retest above. Future production regression cases should cover an invalid
required tag reference, custom pool elements, dry fractional-height decks,
vertically overlapping settlements, and villages large enough to hit coverage
limits. Each should assert actual enrollment, spawning or arrival as appropriate,
not merely successful startup or a non-null path.
