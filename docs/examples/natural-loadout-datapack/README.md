# Natural-loadout example datapack

This directory is a complete Minecraft 1.20.1 server datapack. Copy the
directory into a world's `datapacks` directory, or zip its contents so
`pack.mcmeta` is at the root of the archive. It adds three pools but does not
replace the mod's built-in pools or alter configuration by itself.

- `vgg_example:migrated_normal` is a format-2 copy of the built-in Normal pool.
  Its entries remain unconditional, so its weights and probabilities are
  exactly the same as the format-1 source.
- `vgg_example:contextual_guards` demonstrates a safe unconditional fallback,
  dimension, biome, and structure selectors, difficulty and spawn-source
  filters, raid state, and integration-owned guard scoreboard tags.
- `vgg_example:rifle_sidearms` demonstrates format 3 with a validated pistol
  sidearm and a complete ammunition stack for each weapon. It is Hard-only.

After installing the pack, run:

```text
/reload
/vgg loadouts validate vgg_example:migrated_normal
/vgg loadouts validate vgg_example:contextual_guards
/vgg loadouts validate vgg_example:rifle_sidearms
/vgg loadouts inspect vgg_example:contextual_guards 1
/vgg loadouts inspect vgg_example:rifle_sidearms 1
/vgg loadouts preview hard structure vgg_example:contextual_guards
```

The pools affect natural issuance only after their IDs are assigned to one or
more `naturalLoadouts.<difficulty>.poolId` settings in Fzzy Config. The example
uses only guns from the base TaCZ distribution, but `validate` remains the
authoritative compatibility check for the exact TaCZ version and gun packs on
the server. Natural sidearms must be non-explosive pistols and must use physical
loose ammunition; the live spawned guard remains authoritative for any
context-dependent TaCZ script.

The `vgg_elite` and `vgg_unarmed` scoreboard tags are examples for another mod,
script, structure, or spawn system that applies tags before the guard's one-shot
natural-loadout decision. Adding a tag after that decision does not retry or
rewrite the guard's equipment. The tags are still useful for inspecting a
preview context with `/execute as <guard> run vgg loadouts preview ...`.

See [the migration guide](../../natural-loadout-migration.md) before adapting
these files for a production modpack.
