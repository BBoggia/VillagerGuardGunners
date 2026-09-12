# Village Defense Buildings datapack example

Place this directory in a world's `datapacks` folder and supply the structure
template referenced by the example definition at:

`data/vgg_example/structures/village/plains/sentry_post.nbt`

Format-1 documents belong under
`data/<namespace>/village_buildings/<path>.json`. The path becomes the stable
entry ID, so this example is `vgg_example:plains_sentry_post`. Third-party
namespaces are supported deliberately; packs should own their IDs and
templates instead of writing into `minecraft` or `villagerguardgunners`.

The example is authoring material only and does not include a structure NBT.
It therefore must not be installed unchanged in a playable world. Its template
needs a valid jigsaw connector for the selected village pool.

`duty_slots` is optional. When omitted, the selected building type uses the
mod's built-in reviewed positions. When present, it must contain one to four
slots. Each slot has a unique lowercase `key`, an `[x, y, z]` `offset` from
the template's `village_anchor:<building_type>` marker, a horizontal `facing`,
a tactical `kind`, and a `weapon_requirement`.

`selection_chance` is an optional number from 0 through 1, defaulting to 1.
It controls admission to each shuffled child-piece candidate list, before fit
checks or placement. For example, `0.25` considers the building in roughly one
quarter of those lists. All weighted copies of the same element share one
decision per list, so weight does not multiply admission attempts. Rejected
entries leave the remaining provider candidates and fallback available. This is
not a per-village probability or cap. Zero prevents child-piece selection;
direct template placement is unaffected. Omitted values preserve the previous
selection behavior. Restart after changing this field, as with pool weights.

Supported values are:

- `facing`: `north`, `south`, `east`, or `west`;
- `kind`: `muster`, `sentry`, `overwatch`, or `drill`; and
- `weapon_requirement`: `any` or `ranged`.

Every offset component is limited to -16 through 16. Offsets and facings use
the unrotated template's local coordinates; the mod rotates them with the
building. Positions must be distinct and should provide solid, reachable
standing space with enough headroom. `ranged` accepts a TaCZ gun or crossbow.
These definitions select destinations only: they do not place blocks, give
items, force chunks, or bypass the normal duty and route priority rules.

`/reload` validates and publishes a new last-known-good definition snapshot,
but live template pools are not rebuilt or destructively edited. Restart the
server after changing definitions so the accepted snapshot can be applied
during startup. Invalid documents reject the whole publication and keep the
previous accepted snapshot.

A building stores its exact resolved duty layout when it generates. Later
datapack changes therefore affect only buildings generated after the server is
restarted with the new accepted definition; they do not move existing
buildings' saved duty positions.

The server config is one-way: the master switch and individual building-type
switches may suppress datapack entries, but config cannot create entries or
override their pool, template, processor, projection, or weight.
`/vgg villagebuildings entries` reports `duties=built-in` or the accepted
custom slot count for each definition.
