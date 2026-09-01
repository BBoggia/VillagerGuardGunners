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

`/reload` validates and publishes a new last-known-good definition snapshot,
but live template pools are not rebuilt or destructively edited. Restart the
server after changing definitions so the accepted snapshot can be applied
during startup. Invalid documents reject the whole publication and keep the
previous accepted snapshot.

The server config is one-way: the master switch and individual building-type
switches may suppress datapack entries, but config cannot create entries or
override their pool, template, processor, projection, or weight.
