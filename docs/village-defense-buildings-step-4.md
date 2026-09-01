# Village Defense Buildings Step 4: anchors and one-shot lifecycle

Step 4 adds the shared identity and persistence layer that later Barracks,
Armory, Sentry Post, integrated Watchtower, and Training Yard behavior can use.
It deliberately adds no guards, inventories, loot, resupply points, training
logic, or other functional building action.

## Template anchor contract

A village-building template may contain zero or one structure data marker with
this closed grammar:

`village_anchor:<building_type>`

The five accepted type values are `barracks`, `armory`, `sentry_post`,
`watchtower`, and `training_yard`. Metadata is limited to 64 characters and is
case-sensitive. The marker type must equal the `building_type` in the
definition that selected the template.

No anchor remains valid when the template contains a second anchor, malformed
anchor metadata, an unknown type, or a type mismatch. Validation occurs before
the template is placed. A template with no anchor remains legal for purely
decorative compatibility pieces, including the default-off plains probe.

When a valid anchor is inside the active placement bounds, its marker cell is
replaced with air and one immutable identity is derived from:

- the world seed;
- the dimension ID;
- the definition's stable entry ID;
- the stable building type; and
- the transformed world position of the anchor.

The inputs use a versioned domain before `UUID.nameUUIDFromBytes`, so the same
placement produces the same ID across chunk clipping, reloads, and restarts.
Changing any ownership input produces a different identity.

## Persistent record

Each dimension stores passive records in
`villagerguardgunners_village_buildings.dat`. The reusable strict record root
is `VggVillageBuilding`, schema `1`, containing only:

- the building UUID;
- definition entry ID;
- building type;
- dimension ID;
- anchor block position; and
- a bounded bit mask of claimed one-shot actions.

The stable action surface is `ANCHOR`, `GARRISON`, `ARMORY`, and
`INITIAL_STOCK`. Step 4 claims only `ANCHOR`; the remaining claims reserve
at-most-once boundaries for later building-specific steps. Initial stock also
requires the Armory claim, preventing impossible partial state. Claims are written
before later side effects so interruption fails toward missing optional
content rather than duplicate guards, Armories, or loot.

An existing UUID with different provenance or an existing physical anchor with
a different UUID is a conflict. It does not create a replacement record. This
prevents a changed datapack entry from treating a previously generated
building as a new one if the marker is ever revisited.

The per-dimension file has an explicit schema and a hard ceiling of 262,144
records. Unknown fields, wrong tag types, invalid IDs/types/actions, duplicate
UUIDs, duplicate physical anchors, future versions, and over-limit files
disable the complete index. An unreadable/future root is retained opaquely and
written back unchanged instead of being normalized or erased. Disabled state
cannot claim an action.

## Threading and performance

Structure marker handling may originate from a world-generation worker. The
marker creates only immutable identity data there. The SavedData claim runs
immediately when already on the server thread or is queued once onto that
thread through Minecraft's server executor.

The queued task resolves the already-loaded dimension and performs an O(1)
UUID/anchor-map claim. It does not capture a chunk, request a ticket, load a
chunk, scan a village, enumerate entities, or schedule recurring work. Saved
records have no ticker or repair pass. Removing a later generated guard or
Armory will not clear its claim and will not cause regeneration.

## No functional building actions

The built-in probe still has no anchor and the master integration switch still
defaults to `false`. Step 4 provides only the shared marker, identity, record,
codec, and at-most-once claim system. Functional templates and the code that
claims `GARRISON`, `ARMORY`, or `INITIAL_STOCK` remain later steps.
