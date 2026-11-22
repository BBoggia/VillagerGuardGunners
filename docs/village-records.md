# Village records and discovery

Villager Guard Gunners records generated villages independently of Village
Defense Buildings. Operators can inspect ordinary villages, their observed
homes, and guard affiliations through `/vgg villages`.

These records provide the foundation for village-wide coverage and staffing.
They currently issue no guards, equipment, movement orders, or population
allowances. Player-built settlement registration will use the future
Quartermaster station; placing beds does not register a new village.

## Operator diagnostics

All commands require permission level 2 and report the command source's current
dimension. Use `/execute in <dimension> run vgg villages ...` to inspect another
dimension. They read recorded or cached facts and do not locate structures or
request chunks.

| Command | Result |
| --- | --- |
| `/vgg villages status` | Dimension record count, storage status, tracked chunks, pending affiliations, completed reviews, limit/rejection counter, and latest work budget usage. |
| `/vgg villages here` | Recorded footprints at the command position and local discovery progress, including unresolved start references and unknown POI sections. Overlapping villages appear separately. |
| `/vgg villages list [after]` | Up to eight records in UUID order. The response supplies the cursor for the next page. |
| `/vgg villages inspect <village UUID>` | Structure/start identity, footprint revision, planned bounds, piece count, observed homes, and affiliation counts. |
| `/vgg villages guard <guard>` | The selected loaded guard's recorded village affiliation. |

Discovery follows loaded chunks and can take several ticks or longer under a
large load backlog. A missing record is not proof that no village exists.
`reviewed=true` means a discovery pass finished; unresolved references and POI
sections still need later observations. Revisit the area to make its chunks
available normally. The commands do not force discovery work.

Affiliated-guard counts are durable membership counts, not a count of living or
currently available defenders. Unloading, player follow orders, and missing
entities do not remove membership. Confirmed-death accounting and population
issuance belong to the future population ledger.

## Identity and footprint

Generated identities use the tuple `(dimension ID, registered structure ID,
structure start chunk)`. A versioned name-based UUID derives from that tuple.
It does not depend on load order, beds, a changing centroid, mutable bounding
boxes, defense-network IDs, or squad IDs. Identity is local to a world's data;
copying a world retains its identities.

Discovery accepts structures in the `minecraft:village` structure tag, including
pack-defined structures with supported native metadata. It reads complete piece
lists from loaded structure starts before publishing a footprint. A layout that
is interrupted, changes its source start, exceeds supported limits, or loses
its start chunk is deferred; a partial list never replaces a complete record.

Each chunk reviews only its actual native starts and structure references.
Chunks referencing the same in-progress village advance a shared layout cursor.
Registering additional village types does not add empty per-type searches to
every chunk or disable discovery when a pack exceeds 64 village types.

Each piece retains its position, rotation, template ID when available, and
observed bounds. Unknown/non-single-pool templates use the diagnostic marker
`villagerguardgunners:unknown_piece`. The footprint is planned geometry, not
walkable terrain or a count of residences. The [coverage policy](village-coverage.md)
can group these observations using explicit template classifications and home
anchors. [Live patrol coverage](village-patrol-coverage.md) supplies loaded terrain
validation and movement when its independent setting is enabled.

Minecraft can rebuild piece bounding boxes when loading native structure data.
A changed observed footprint increments its revision while preserving the
village UUID and affiliations. Unchanged observations do not dirty saved data.
Footprint overlap never merges generated identities.

Home observations come from cached native HOME POI records. Discovery replaces
only a fully observed section, after the chunk's structure references have been
resolved. Missing or capped sections retain their previous observations. Homes
inside overlapping footprints are left unassigned until an unambiguous owner
exists. Homes alone never create a village or mint population credits.

## Membership and defense integration

A loaded guard receives an initial affiliation only when its chunk has finished
a structure review and its position has local piece evidence in exactly one
recorded footprint. The position must be inside an individual piece horizontally
and within its vertical bounds, with a two-block vertical tolerance for terrain
adaptation. The full village union box alone is insufficient. The candidate's structure must still be supported. Ambiguous or
incomplete observations defer assignment. Affiliation stays on the original
village when a guard follows a player or leaves the footprint. Discovery does
not change NoAI, follow/patrol flags, equipment, Guard Orders, or squad membership.

Existing and newly saved building anchors are looked up through a chunk index.
Discovery-based building association uses the same piece/elevation check.
HOME POIs can also use pieces within 16 blocks horizontally, inside the village's
overall horizontal bounds, so courtyard and player-built homes need not occupy
an original template rectangle. The elevation requirement still applies.
Unknown pieces and volumes taller than 24 blocks are unresolved until finer
floor evidence is available. An unresolved home-section scan preserves its
previous observations; a complete empty scan still removes destroyed beds.
Generated guards and buildings with a frozen population-plan binding retain
that authoritative association independently of geometric discovery. An unambiguously located building can acquire a village association independently
of its defense-network membership. Those associations persist by building UUID.
`VillageDefenseAssociation` resolves the building's current canonical network
through the existing network registry, so network merges and aliases cannot
merge village identities or redirect village affiliation.

`/vgg villages guard <guard>` reports when a member is outside its recorded
region/elevation. This is read-only evidence: it can indicate travel or an older
horizontal association, and never automatically reassigns the guard. The check
uses planned geometry and does not prove that a route is walkable.

Existing networks remain the readiness owners for their buildings. No ordinary
village is represented by a fake building or added to a network to satisfy
validation. Ordinary-village readiness and coverage assignments are not enabled
by this record layer.

## Lifecycle and work limits

Chunk-load events enqueue positions, and chunk unload removes transient work.
Guard join/leave events maintain the pending affiliation queue. All publication
and membership changes run on the server thread. Reloading structure tags resets
transient observation cursors; saved records remain available. The global mod
`enabled` setting pauses discovery while preserving recorded data and queues.

Discovery reads only `ServerChunkCache.getChunkNow` and a remapped cache-only
accessor for `SectionStorage.get`. It never calls POI range queries, loads absent
sections, adds chunk tickets, or performs a startup scan of historical villages.
An absent start is retried on subsequent loaded-chunk reviews. Loading the start
chunk itself also makes it eligible for discovery.

| Limit | Current bound |
| --- | --- |
| Chunk work | Four round-robin chunks per dimension per tick, up to eight cursor advances each. |
| Cursor advance | One structure/reference check, one piece, or one POI record/section step; final publication and anchor association have their own finite limits below. |
| Idle review | At least 600 simulation ticks after a completed pass; queue size and work budgets can extend this. |
| Pending guard work | At most four guard checks per dimension per tick; unresolved guards wait at least 100 ticks between attempts. |
| Transient capacity | 32,768 tracked chunks, 16,384 pending guard UUIDs, and 1,024 shared in-progress layouts per dimension. |
| Supported structure types | Any current `minecraft:village` tag member with supported native metadata; no separate tag-entry ceiling. |
| Footprint | At most 512 pieces; at most 1,024 blocks of horizontal span on either axis. |
| Cached home section | At most 256 HOME records; oversized sections are retained as unresolved. |
| Building association | At most 64 anchors per reviewed chunk. |
| Spatial lookup | 256-block index cells; at most 64 candidate villages per point query. At most 4,096 piece comparisons per guard lookup, home-section publication, or building batch. Excess overlap, uncertain evidence, or exhausted work defers association. |
| Durable storage | At most 16,384 villages, 1,024 homes per village, and 262,144 entries in each affiliation table. |

Work queues retain progress between visits and rotate fairly across accepted
chunks. Overflow does not evict established facts or force world loading; it
increments the diagnostic rejection counter. A chunk or guard rejected at the
hard tracking limit needs a later load/join event after capacity becomes
available. Oversized layouts remain unsupported and visible through unresolved
counts. These bounds are resource limits, not hardware-independent TPS claims.

## Save format and compatibility

Each dimension stores `villagerguardgunners_villages.dat` through Minecraft
`SavedData`. Root format 1 contains `Version`, `Dimension`, `Records`, `Guards`,
and `Buildings`. Records contain the derived UUID, structure ID, start chunk,
revision, complete piece observations, and observed home positions. Membership
entries contain a member UUID and a village UUID. All records are immutable;
transient cursors, entity/chunk references, navigation, and readiness assignments
are excluded from the file.

Strict decoding checks types, field names, dimensions, coordinate/size limits,
UUID derivation, duplicate records/members, and dangling affiliation references.
An unsupported version or malformed publication disables mutation and preserves
the entire original NBT publication. Diagnostics expose disabled storage. The
loader never salvages a partial registry by dropping unknown records.

An existing world with no registry file starts with an empty format-1 registry
and discovers loaded villages incrementally. There is no earlier published
village-registry format to migrate. Existing building/network/squad files remain
authoritative for their own features; their IDs are not imported as village IDs.
The building chunk index is reconstructed from existing building records without
changing that save format.

## Verification

The unit suite exercises strict decoding, lossless rejection, stable identity,
overlap, membership persistence, section replacement, network aliases, building
index restoration, and spatial/cursor queries with 10,000 historical records.
The opt-in native integration suite exercises production discovery with no
instrumented absent-chunk/POI reads, bounded/interrupted layouts, ordinary
villages with buildings off, guard-order preservation, and command permissions.
It also reloads actual SavedData files through `DimensionDataStorage`, native
structure NBT, and guard NBT while replaying unload/reload lifecycle events.
This is a server-side lifecycle simulation, not a client terrain playthrough.

Run the existing `check`, normal GameTests, and the integration run documented
in [Village integration boundaries](village-integration-boundaries.md).

Population counts and initial generation allowances have separate persistence.
See [village population](village-population.md) for unloaded residents, confirmed
removals, existing-world behavior, and operator diagnostics.

Join observations can precede Minecraft's entity tracking. Discovery retries a
missing guard at 20-tick intervals, with at most ten checks before retiring a
never-inserted request. Loaded guards waiting for complete village observations
continue to use the ordinary discovery queue. Population admission can publish
a provisional footprint of classified sources; a loaded start reference with
a different native piece count triggers full footprint discovery.

Dense observations that repeatedly exceed the comparison limit remain deferred;
this limit bounds work but does not implement incremental continuation.
