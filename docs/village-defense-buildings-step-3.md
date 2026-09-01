# Village Defense Buildings Step 3: datapack definitions

Step 3 replaces the hard-coded compatibility-probe declaration with a strict,
bounded datapack registry. It defines where a village piece may be selected;
it does not yet add functional VGG building templates, markers, guards,
Armories, loot, or recurring behavior.

## File location and identity

Definitions are read from:

`data/<namespace>/village_buildings/<path>.json`

The namespace and path become the stable entry ID. For example,
`data/example/village_buildings/plains/post.json` is
`example:plains/post`. Modpacks should use their own namespaces. The built-in
inert probe remains `villagerguardgunners:integration_probe/plains`.

## Format 1

```json
{
  "format": 1,
  "building_type": "sentry_post",
  "target_pool": "minecraft:village/plains/houses",
  "template": "example:village/plains/sentry_post",
  "processors": "minecraft:empty",
  "projection": "rigid",
  "weight": 1
}
```

| Field | Required | Allowed value |
| --- | --- | --- |
| `format` | yes | exactly `1` |
| `building_type` | yes | `barracks`, `armory`, `sentry_post`, `watchtower`, or `training_yard` |
| `target_pool` | yes | canonical namespaced template-pool ID |
| `template` | yes | canonical namespaced structure-template ID |
| `processors` | no | canonical namespaced processor-list ID; defaults to `minecraft:empty` |
| `projection` | no | `rigid` or `terrain_matching`; defaults to `rigid` |
| `weight` | yes | integer from 1 through 5 |

Unknown fields, unknown building types, noncanonical IDs, future format
versions, fractional weights, duplicate JSON keys, trailing JSON, and invalid
projections reject publication. The schema deliberately has no command,
entity, item, SNBT, marker, loot, or arbitrary registry-mutation field.

## Bounds and atomic publication

One publication accepts at most 25 documents targeting at most five distinct
pools. Each source document is limited to 16,384 characters and JSON depth 16.
Two IDs may not describe the same building-type, target-pool, template,
processor-list, and projection tuple.

Resources are compiled in stable ID order. If any document or publication-wide
bound fails, the entire candidate is rejected and the prior accepted snapshot
is retained. Errors are normalized and capped at 512 characters. A bad pack
cannot partially replace a valid definition set.

## Config precedence

The datapack owns entry identity, type, target pool, template, processor list,
projection, and weight. The synchronized Fzzy Config section is one-way:

- `villageBuildings.enabled` suppresses the complete integration when false;
- `barracks`, `armories`, `sentryPosts`, `integratedWatchtowers`, and
  `trainingYards` may suppress only their matching types; and
- config cannot invent or rewrite a datapack entry.

The master switch remains `false` during this inert compatibility stage. The
five type switches default to `true`, so enabling the master later does not
silently omit a reviewed building unless the server owner chooses to do so.
The integrated Watchtower switch never changes the standalone 1.4 Watchtower.

## Restart required

Minecraft's initial resource reload publishes the accepted snapshot before the
server-startup pool integration. `/reload` validates and stores later changes,
but the mod does not remove, reorder, or rebuild a live template pool. Restart
the server after editing village-building definitions so the accepted snapshot
is installed during the next startup.

This split is intentional: a reload never risks invalidating already-selected
jigsaw pieces or destructively replacing entries supplied by another mod. Pool
installation remains a bounded startup action with no per-tick work, village
scan, chunk load, or retained registry reference.

See the copyable but intentionally template-free example in
[`docs/examples/village-buildings-datapack`](examples/village-buildings-datapack/README.md).
