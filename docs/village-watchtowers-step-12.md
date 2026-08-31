# Village Watchtowers Step 12: guard-post group composition

Step 12 adds the immutable data layer that describes a watchtower's possible
occupants. It deliberately stops before marker processing, entity creation,
role assignment, or natural-loadout issuance. The only runtime operation is a
bounded deterministic selection that turns one group ID, difficulty, and seed
into a list of at most four identifier-only member requests.

## Resource path and format

Group pools live at
`data/<namespace>/guard_post_groups/<path>.json` and use the resource ID
`<namespace>:<path>`. Format 1 has this shape:

```json
{
  "format": 1,
  "inherits": ["example:base_post"],
  "loadout_pool": "villagerguardgunners:normal",
  "variants": [
    {
      "name": "mixed_patrol",
      "weight": 3,
      "difficulties": ["normal", "hard"],
      "members": [
        {
          "role": "villagerguardgunners:rifleman"
        },
        {
          "loadout_pool": "example:specialists",
          "role": "villagerguardgunners:marksman",
          "chance": 50
        }
      ]
    }
  ]
}
```

All fields are strict; unknown or duplicate JSON fields, noncanonical resource
IDs, fractional integers, duplicate values, and unsupported versions reject
the complete guard-post publication.

- `inherits` is optional and merges parent variants in declared order. Parent
  and child variant names must be unique. Missing parents, cycles, excessive
  depth, and conflicting inherited defaults or variant names are errors.
- `loadout_pool` is an optional group default. A variant may override it and a
  member may override the variant. Every member must resolve a pool through
  that chain.
- `name` is a stable lowercase path key. A later persisted post can identify a
  selection with the group resource ID and this name without depending on JSON
  array order.
- `weight` selects among variants eligible for the current difficulty.
- `difficulties` accepts `easy`, `normal`, and `hard`. Peaceful population is
  intentionally disabled and cannot be enabled by a document.
- `role` is optional. It records a canonical Guard Roles ID for later
  integration but does not resolve or assign it during this reload.
- `chance` is an integer percentage from 1 through 100 and defaults to 100.
  At least one member in every variant must be required at 100 percent, so a
  successful selection can never collapse to an empty group.

Role and loadout IDs are syntax-checked but intentionally not existence-checked
here. Their reload listeners publish independently, and Step 14's one-time
spawn transaction remains responsible for resolving them and failing closed
without partial equipment.

## Built-in watchtower groups

The bundled `villagerguardgunners:watchtower` pool keeps normal populations
below the four-guard ceiling while allowing uncommon larger compositions.

| Difficulty | Weighted variants | Possible occupants |
| --- | --- | --- |
| Peaceful | none | 0 |
| Easy | `easy_sentry` 4, `easy_pair` 1 | 1-2 |
| Normal | `normal_patrol` 3, `normal_support` 1 | 2-3 |
| Hard | `hard_squad` 3, `hard_breach` 1 | 3-4 |

The selected member records reference the existing difficulty pools and the
built-in rifleman, marksman, support, breacher, and demolitions roles. No gun,
ammunition, or other item is created by group selection.

## Determinism and performance

One later structure marker will provide one 64-bit seed. Selection mixes that
seed with the group ID and difficulty, performs one weighted pass over at most
64 variants, and derives optional-member decisions from the same seed over at
most four members. It retains no random source and performs no world lookup.

The reload and runtime ceilings are:

- 128 group documents;
- 65,536 characters per document before JSON parsing;
- 32 nested JSON containers;
- 8 direct parents and 16 documents in an inheritance path;
- 64 resolved variants per group;
- 4 members per variant; and
- a signed 32-bit total eligible weight per difficulty.

Reload compilation is atomic. If reading or resolving any group fails, the
previous immutable group map and generation number remain published while a
bounded diagnostic records the failed attempt. Other datapack listeners can
continue normally because the error is contained rather than thrown from the
apply phase.

## Deliberately deferred

Step 12 does not add a template marker, spawn a Guard Villager, persist a post
identity, assign a role, select a TaCZ weapon, issue ammunition, create a squad
or Armory, or evaluate loot. It adds no scan, tick, task, chunk acquisition, or
retro-generation behavior. Those responsibilities remain isolated in Steps
13 and 14.

## Validation

The clean Java 17 Step 12 gate passes 412 plain JUnit/source/artifact tests,
both development source sets compile, and all 89 required Forge correctness
GameTests pass. The coverage includes strict field and numeric parsing, JSON
size and nesting, all inheritance failure modes, every hard collection bound,
last-known-good publication, Peaceful behavior, weighted difficulty
eligibility, optional members, determinism over repeated seeds, built-in
resource publication, and production-JAR contents.

The verified development artifact is `villagerguardgunners-1.4-dev.jar`, size
857651 bytes, SHA-256
`6946f3e5fa735fab740e1ab33bd82ca01cf6e277ae979032722112b3267fc4ba`.
