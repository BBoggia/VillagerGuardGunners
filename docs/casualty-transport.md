# Casualty transport and Recovery Cots

Enable `medical.transport` alongside `medical.downed` to let supplied medics
carry stabilized guards to nearby Recovery Cots. Both options start **off**.
Transport also requires `medical.enabled`, `medical.medicAi`, and valid
treatment profiles. It works in ordinary villages and with recruited guards.

## Preparing a recovery area

Craft a Recovery Cot with three wool across the top row, three planks across
the middle, and sticks in the bottom corners. Place it on a clear two-block
space. Leave a wide, open approach with four blocks of headroom for a carrier
and patient. Cots do not count as villager beds or claim villagers' homes.
Breaking either half drops one cot; cots store no items.

Give a guard medical supplies in its pouch and enable its **Medic** duty.
The medic first stabilizes a downed ally where it fell. If a clear, unoccupied
cot is within 32 blocks of the patient, the medic can pick up that original
guard and carry it slowly to the cot. The carrier must have at least 75% health,
remaining treatment supplies, and no conflicting player order or combat action.
Follow orders, including orders from offline owners, manual patrols, hold
orders, and active squad commands take precedence over transport for both guards.
These patient orders still allow in-place treatment.

The patient remains visible while carried, with a **Being carried** label.
The medic displays **Carrying a casualty** and pauses firing, reloading,
equipment service, and other automatic work until the carry ends.

A delivered patient lies along the mattress, with its head at the pillow. The
cot stays occupied through treatment and rest. The medic can attend someone
else after completing the revival dose. The patient gets up beside the cot
when a clear exit is available; a blocked exit keeps it resting with an
**Exit blocked** label.

Cots make revival and recovery faster. Both methods still need one dose for
stabilization and a second dose for revival, after the supply cooldown. Cots
provide no supplies, passive healing, or extra bleed-out time.

| With transport enabled | At a cot | Emergency field care |
| --- | --- | --- |
| Medic stabilization | 2 seconds | 2 seconds |
| Revival dose | 2 seconds | 8 seconds |
| Rest after revival | 5 seconds | 20 seconds |
| Revival health | Up to 2 hearts | Up to 2 hearts |

Travel and assessment add time. A distant cot can still take longer overall.
Ordinary injury healing and transport-disabled revival keep their existing
short treatment timings.

If no free cot or usable route exists, medics perform emergency field care.
For a selected cot, the carrier checks up to four approach positions and chooses
the shortest complete native route that passes support and passenger-clearance
checks. An unreachable side does not exclude other sides of the same cot.
Candidate queries are isolated, and the winning destination is rebuilt and
screened before installation (at most five native path requests per selection).
Route costs and clearance checks use the remaining nodes, so terrain behind the
carrier does not invalidate completed travel. Native terrain-triggered replans
stop movement and return control to the transport goal for another screened route.
Routes are limited to 128 nodes, and upcoming carrying nodes are rechecked for
support and clearance while moving. These node checks do not establish safe
clearance along every native shortcut between nodes.

Failed routes permit another destination, with at most three remembered failed
cots per medic and patient for ten seconds. Medics leave time for field care
when the stabilized patient's deadline is approaching. Damage, combat, and
missing supplies can still prevent rescue. A field channel already in progress
continues if a cot becomes available.

Manual second-dose treatment follows the same two- or eight-second channel.
Use a supply on the patient and stay within four blocks with line of sight.
The action bar shows progress. Changing the source stack, leaving range,
accepted damage, loss of access, or disconnecting cancels without spending the
dose. The pouch Treat action follows the same rule. Quick manual stabilization
and ordinary injury healing remain immediate.

Server timing settings are `medical.cotRevivalTicks` (40),
`medical.fieldRevivalTicks` (160), `medical.cotRecoveryTicks` (100), and
`medical.fieldRecoveryTicks` (400). Revival channels accept 20–600 ticks;
recovery accepts 20–2400. Field values are at least their cot counterparts.
A treatment snapshots its timing when its channel starts. These defaults assume
20 simulation ticks per second; unloaded simulation time stays paused.

## Interruptions and loading

Combat, accepted damage, new player orders, death, an obstructed route, a broken
or unloaded destination, and disabling transport release the assignment. The
medic puts the original patient down on nearby safe loaded ground when available.
An interrupted carrier waits ten seconds before another transport attempt.
Manual treatment ends transport before beginning its dose or revival channel.

Breaking a cot before revival releases the patient for field care. Breaking it
during rest preserves the completed revival and credits time already rested
against the longer field recovery. Canceled cot treatment cannot carry its
shorter channel progress into field treatment. Unloading furniture preserves
the patient’s saved resting relationship; it is not treated as destruction.

Transport never loads chunks, keeps chunks loaded, or carries between dimensions.
A dimension-travel attempt during physical carrying is canceled and ends the
carry. Routes through unloaded terrain or with insufficient passenger clearance
are declined. Care remains available without a route to a cot.

Saving uses Minecraft's ordinary passenger serialization for the original guard.
After loading, an interrupted passenger is released near its saved position;
the mod does not resume an old destination or recreate the casualty. Its UUID,
guns, ammo, armor, medical pouch, role, orders, and squad remain on that entity.

## Integration and limits

Transport reserves one carrier, one patient, and one cot together. Reservations
are transient and expire after 400 loaded ticks. Each level tracks at most
256 loaded cot heads and 16 simultaneous transport assignments. Cots beyond
that registration cap are unavailable until they load again with room in the
index. There is no block-entity ticker or saved block inventory. Cot occupancy is
tracked on the original patient and checked against nearby live entities when
reserving and during travel. The loaded occupancy index holds at most 256 cots.
Delivery transfers the reservation to that patient until safe exit. Proximity
to a cot alone never grants its treatment speed.

Native navigation is requested only after its surrounding search region is
loaded. Follow ranges above 64 blocks are declined; accepted paths have at most
128 nodes. Carrying uses a padded 1.4-block-wide, 3.8-block-high clearance box,
checks upcoming nodes, and abandons movement after 80 ticks without progress.
This conservative clearance can reject narrow routes that a lone guard can use.

A resting patient's `villagerguardgunners:cot_rest` version-1 record stores
`Version`, `Head`, `Facing`, and `Dimension`. On loading, its cot must be intact,
in the same loaded dimension, and within a quarter block of the expected
mattress position. Conflicting claims use UUID order. Invalid locations release
the relationship locally; the mod does not move patients across the world.
The synchronized anchor also controls the cot pose and label location. Client
ticks align the patient to that loaded cot, including after a stale spawn or
interpolation position. Missing furniture or distant anchors are not used to
move the client entity.

The patient's `villagerguardgunners:transport` version-1 compound contains only
`Version`, `Carrier`, `Dimension`, and finite `X`, `Y`, `Z` recovery coordinates.
It contains no copied entity, inventory, destination, or lease. Unknown and
malformed compounds are retained and prevent new transport. Recovery considers
only nearby loaded positions in the current dimension, with a maximum saved
position distance of eight blocks. If no safe nearby position remains, ordinary
dismount positioning applies; destroyed terrain is not rebuilt.

Other mods can cancel mounting and dismounting. A canceled pickup leaves the
original patient in place. A canceled dismount retains transport ownership and
the recovery hint, stops the carrier's AI, and retries every 20 loaded ticks.
Manual treatment returns busy while release is refused. A mod that continually
refuses dismounts therefore prevents release until that refusal ends. Direct
entity replacement, forced movement, or health changes outside the normal
lifecycle require separate compatibility checks.

## Verification

Cot-care regression tests check exact position and facing, displacement,
occupancy after reload, blocked exits, cot destruction before and after revival,
field treatment duration, alternate destinations, urgent fallback, and timed
manual treatment from the hand and pouch. The playground's normal and
`--one-cot` verification runs record delivery, treatment, revival, and return to
duty separately for each original casualty.

Native tests cover physical travel, exclusive reservations, passenger identity
and items, ordinary passenger controls, canceled mount/dismount events, cot
loot, interruption, dimension travel, and nested save/load. Integration probes
check that missing-terrain clearance and orphan recovery request no chunks or
POI data. An isolated client scenario exercises automatic first aid, carrying,
cot delivery, and second-dose revival with synchronized labels and finite doses.

The combined performance matrix includes 3,200 claim, pickup, safety-check, and
release cycles over 16 concurrent carriers and 16 patients. The harness supplies
loaded terrain and resets retry deadlines outside the measured batch; it checks
that production transport adds no chunk tickets and changes no items. It measures
stationary transport bookkeeping and safety work. Live navigation and rendering
are verified separately. Run `verifyTransportPerformanceReport` after the matrix
to validate the JSON/JFR evidence against the artifact and harness. Timings are
hardware-specific observations, not a universal server tick-rate guarantee.
