# Natural Guard Loadout Migration Guide

This guide covers moving an existing format-1 natural Guard Villager weapon
pool to format 2 without unintentionally changing its balance, adding
contextual rules in controlled stages, and opting selected entries into
format-3 sidearms.

## Before changing a pool

Record the current pool ID, entries, weights, and the difficulty settings that
reference it. A pool's resource path determines its ID:

`data/<namespace>/natural_guard_loadouts/<path>.json`

becomes `<namespace>:<path>`. A higher-priority datapack file with the same ID
replaces the complete lower-priority pool; entries are never merged.

Keep the original file available for rollback. Changing or disabling a pool
affects future one-shot spawn attempts only. It does not revisit existing guards
or replace their current equipment.

## Lossless format-1 to format-2 conversion

Change only the root `format` value from `1` to `2`. Leave every `gun` and
`weight` unchanged and do not add `conditions` yet:

```json
{
  "format": 2,
  "entries": [
    {"gun": "tacz:glock_17", "weight": 3},
    {"gun": "tacz:m1911", "weight": 2},
    {"gun": "tacz:uzi", "weight": 2},
    {"gun": "tacz:hk_mp5a5", "weight": 1}
  ]
}
```

This preserves the total weight of 8 and the original probabilities: 37.5%,
25%, 25%, and 12.5%. In format 2, an omitted `conditions` field and an explicit
empty `conditions` object are both unconditional and normalize to the same
identity. Prefer omission because it is clearer; do not try to use one of each
to repeat the same gun, because the pool will correctly reject that duplicate.

The copy-ready
[`vgg_example:migrated_normal`](examples/natural-loadout-datapack/data/vgg_example/natural_guard_loadouts/migrated_normal.json)
pool is this exact conversion.

## Add conditions incrementally

Add one rule category at a time and preview it before adding the next. Different
categories use AND, values inside an include list use OR, and any matching
exclusion vetoes the entry. An omitted category is unrestricted.

Keep at least one unconditional fallback entry unless intentionally allowing a
context where no gun can be selected. Without a matching entry, the attempt
returns `NO_MATCHING_ENTRIES`; it does not use the old pool, another difficulty's
pool, or a random fallback.

Format 2 may repeat a gun only when the normalized conditions differ. If two
distinct entries for the same gun match one context, both weights participate
and their effective probability is additive. An exact duplicate gun and
condition pair is rejected even if the weights differ.

Biome and structure selectors may be exact IDs or tag selectors beginning with
`#`. Dimension conditions accept exact IDs only. Referenced biome and structure
tags belong in the normal Minecraft datapack tag directories, as demonstrated
by the [example pack](examples/natural-loadout-datapack/README.md).

Keep each pool within the enforced runtime bounds: at most 256 entries, at most
64 values in any individual condition collection, and at most 128 distinct
biome and structure selectors across the complete pool. Split a larger design
into smaller difficulty-specific pools rather than relying on an oversized
single document.

Guard conditions use scoreboard tags, not datapack registry tags. They are most
useful when another mod, structure, script, or spawn integration puts the tag on
the entity before the one-shot loadout decision. Applying a scoreboard tag
afterward does not retry selection.

## Opt in to format-3 sidearms

Do this only after the format-2 pool is stable. Change `format` from `2` to `3`
without changing the existing entries; this is behavior-preserving because an
omitted `sidearm` still issues only the primary. Then add `sidearm` to one entry
at a time:

```json
{
  "format": 3,
  "entries": [
    {
      "gun": "tacz:m4a1",
      "sidearm": "tacz:glock_17",
      "weight": 1,
      "conditions": {"difficulties": ["hard"]}
    }
  ]
}
```

Each sidearm must pass the strict pistol, physical-ammunition, native
compatibility, and non-explosive checks. Successful issuance requires room for
one complete ammo stack per weapon. If either stack, weapon, private slot, or
provenance commit fails, neither loadout is partially issued. Formats 1 and 2
reject the `sidearm` field.

See [the format-3 contract](natural-loadout-v3-sidearms.md) and the copy-ready
[`vgg_example:rifle_sidearms`](examples/natural-loadout-datapack/data/vgg_example/natural_guard_loadouts/rifle_sidearms.json)
pool.

## Install and validate

Install a folder or archive whose root contains `pack.mcmeta`, then run:

```text
/reload
/vgg loadouts validate <namespace:pool>
/vgg loadouts inspect <namespace:pool> 1
/vgg loadouts preview <difficulty> <spawn_source> <namespace:pool>
```

`validate` resolves every gun and ammunition definition against the currently
loaded TaCZ data without issuing items. `inspect` exposes the parsed weights and
conditions. `preview` uses the command source's current dimension and position;
use vanilla `/execute in` and `/execute positioned` to exercise another
location. Use `/execute as` a Guard Villager guard when scoreboard tags or a
guard-dependent TaCZ script must be evaluated against a real guard.

After validation, assign the pool ID to the intended
`naturalLoadouts.easy.poolId`, `naturalLoadouts.normal.poolId`, or
`naturalLoadouts.hard.poolId` setting in Fzzy Config. Test each configured
difficulty and enabled spawn source. A valid pool may still reject an individual
spawn because of chance, the nearby cap, a protected existing weapon,
incompatible ammunition, inventory capacity, or explosive-weapon policy.

## Rollout and rollback

For a production server:

1. Add the new pool under a new ID instead of overwriting the active pool.
2. Reload and run `validate`, `inspect`, and location-specific `preview` checks.
3. Point one difficulty at the new ID and observe newly spawned guards.
4. Move the remaining difficulties only after the first group behaves as
   expected.
5. Roll back by restoring the previous pool ID. For an immediate global pause,
   set `naturalLoadouts.enabled = false`.

Do not enable explosive entries merely because a pool validates. Natural
explosive issuance also requires `naturalLoadouts.allowExplosiveWeapons = true`,
and should be tested with ally protection, standoff, rules of engagement, and
terrain-preservation settings in the target modpack.

## Common failures

- A pool missing after `/reload` was rejected as a complete document. Run
  `/vgg loadouts validate` without a pool to confirm the `rejectedDocuments`
  count, then check the server log for that pool's bounded rejection reason. A
  targeted command cannot inspect a pool that was not published.
- `NO_MATCHING_ENTRIES` means the file parsed but every entry was excluded by
  the previewed or spawned context. Inspect AND combinations, exclusions, raid
  state, and scoreboard-tag timing.
- An unavailable or incompatible gun means the identifier parsed correctly but
  did not resolve into a usable physical-ammunition TaCZ gun after reload.
- An ineligible sidearm may be a non-pistol, explosive, unreadable, or otherwise
  incompatible TaCZ definition. `inspect` reports its role and eligibility.
- `AMMO_CAPACITY_UNAVAILABLE` for format 3 means the bounded three-slot reserve
  inventory could not accept both complete stacks without a partial commit.
- A structure or biome tag that resolves to no holders simply cannot match; it
  does not make the entry unconditional.
- Datapack priority replaces a whole pool. If expected entries disappear,
  verify which datapack owns the winning file for that exact pool ID.
