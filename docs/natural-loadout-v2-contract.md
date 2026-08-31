# Conditional Natural Loadouts and Diagnostic Commands — Frozen Contract

This document freezes the contract for the natural-loadout expansion. Format 2
is accepted by the strict reload parser, retained in the immutable publication
snapshot, and eligible for contextual natural spawn selection. The commands
below are registered as bounded, read-only administrator diagnostics.

The current format-1 pool remains valid without modification. Existing built-in
Easy, Normal, and Hard weights and natural-spawn behavior must not change when
format 2 becomes active.

A copy-ready [example datapack](examples/natural-loadout-datapack/README.md) and
the [format-1 migration guide](natural-loadout-migration.md) are maintained
against this contract. Automated tests pass every documented pool through the
same strict production parser used during `/reload`.

## Format 2

Format 2 retains the same pool root and adds an optional `conditions` object to
an entry:

```json
{
  "format": 2,
  "entries": [
    {
      "gun": "tacz:glock_17",
      "weight": 4
    },
    {
      "gun": "example:desert_rifle",
      "weight": 2,
      "conditions": {
        "dimensions": {
          "include": ["minecraft:overworld"],
          "exclude": ["example:void_dimension"]
        },
        "biomes": {
          "include": ["#example:is_desert"],
          "exclude": ["example:desert_oasis"]
        },
        "structures": {
          "include": ["#example:villages"]
        },
        "difficulties": ["hard"],
        "spawn_sources": ["structure"],
        "raid": "any",
        "required_guard_tags": ["vgg_elite"],
        "excluded_guard_tags": ["vgg_unarmed"]
      }
    }
  ]
}
```

The root accepts only `format` and `entries`. A format-1 entry accepts only
`gun` and `weight`; a format-2 entry also accepts `conditions`. The condition
object accepts only the fields shown above.

`dimensions` uses canonical dimension IDs. `biomes` and `structures` accept
canonical registry IDs or tag selectors prefixed with `#`. Their selector-group
objects accept only `include` and `exclude`.

`difficulties` accepts `peaceful`, `easy`, `normal`, and `hard`.
`spawn_sources` accepts the existing natural-loadout source names. `raid`
accepts `any`, `active`, or `inactive`. Guard tags are exact, nonblank, trimmed
Minecraft entity scoreboard tags.

An omitted condition category is unrestricted. An explicitly present list must
contain at least one value. A selector group may contain only `include`, only
`exclude`, or both, but an explicitly empty group is invalid.

Duplicate values inside one list are invalid rather than being silently
collapsed. A value cannot appear in both `include` and `exclude`, and one guard
tag cannot be both required and excluded. Enum values are exact lowercase
tokens. An explicitly empty `conditions` object is valid and normalizes to the
same unconditional condition as omitting the field.

A pool contains at most 256 entries. Each individual condition collection
contains at most 64 values, and a pool contains at most 128 distinct biome and
structure selectors in total. The strict parser rejects oversized documents,
and the immutable pool and condition constructors enforce the same limits so a
programmatic caller cannot bypass the runtime boundary.

## Step 5 activation boundary

Formats 1 and 2 are parsed and published. Format 1 retains its existing spawn
behavior and never captures contextual data. Format 2 evaluates its conditions
against one immutable context captured lazily after the chance and pool gates.
If no entry matches, selection returns `NO_MATCHING_ENTRIES` before the
nearby-guard query or weighted roll. A failed or inconsistent context capture
returns `CONTEXT_UNAVAILABLE`; conditions are never ignored or treated as
unconditional.

Parsing performs no dimension, biome, structure, raid, entity, or TaCZ lookup.
Each pool precomputes the biome and structure selectors it references and
whether matching requires structure, raid, or guard-tag context. Runtime capture
therefore checks only relevant selectors and avoids optional lookups for pools
that do not use them.

## Matching and weighting

Different condition categories use AND. Values inside one include collection
use OR. Exclusions take precedence over inclusions and veto an otherwise
matching entry. Every required guard tag must be present; any matching excluded
guard tag vetoes the entry.

An entry without `conditions` always matches. If a pool has no matching entry,
selection returns `NO_MATCHING_ENTRIES` before the nearby-natural-gunner query
or weighted random roll.

Format 1 continues to reject duplicate gun IDs. Format 2 may repeat a gun when
the normalized conditions differ. Exact duplicate gun-and-condition entries
are invalid. If multiple distinct entries for one gun match the same context,
their weights contribute independently and therefore add to that gun's
effective probability.

Context matching does not allocate a temporary filtered entry list. The
selector calculates the eligible total and selects with two bounded passes over
the immutable pool.

## Diagnostic severity

Diagnostics use three severities:

- `INFO` describes usable context or compatibility details.
- `WARNING` is reserved for actionable behavior that is not unconditionally
  broken.
- `ERROR` means the entry cannot produce a valid natural loadout in the
  evaluated context.

A compatible gun whose explosive-property script requires a living guard is
reported as informational `CONTEXT_REQUIRED` and included in the validation
summary's separate `contextDependent` count. It is not an actionable warning:
fail-closed issuance evaluates the same script against the actual spawned guard.

Syntax errors and semantic pool errors remain isolated per pool during reload.
TaCZ gun and ammunition resolution remains outside the datapack listener so
reload-listener ordering cannot decide whether a pool is published.

## Reload publication boundary

Every reload creates one immutable, generation-tagged snapshot containing the
usable pool map, deterministic metadata for each published pool, and bounded
rejection diagnostics for every omitted document. A single volatile publication
also replaces the unavailable-gun warning scope, so readers cannot combine the
pool map from one generation with diagnostic state from another.

Published metadata records the pool ID, format, entry count, and total weight.
Rejected metadata records the pool ID and a safe single-line reason of at most
512 characters. Retaining an older snapshot across `/reload` remains safe; its
maps, lists, pools, entries, and metadata do not change.

The listener parses each resource into a prepared result so malformed JSON and
semantic failures are both retained without aborting unrelated pools. Reload
still performs parsing only: it never queries TaCZ indexes, builds item stacks,
evaluates runtime scripts, scans entities, or performs compatibility analysis.

## Compatibility analyzer boundary

The analyzer constructs a proposed TaCZ gun and one full physical ammunition
stack, verifies their native IDs and compatibility, and can report or reject
explosive behavior. It never equips a guard, changes an ammunition inventory,
creates provenance, updates the population cap, or performs an entity query.

Analysis without a living guard reports runtime-script explosive behavior as
context-dependent. Fail-closed issuance evaluates those scripts against the
actual spawned guard. Issuance remains a separate transactional step and is the
only path that may commit the prepared defensive copies to a guard.

## Step 6 command surface

The read-only administrator subtree is frozen as:

```text
/vgg loadouts validate [pool]
/vgg loadouts inspect <pool> [page]
/vgg loadouts preview <difficulty> <spawn_source> [pool]
```

Every command requires permission level 2, sends no operator broadcast, and is
invoked explicitly. Pool arguments receive suggestions from the current
immutable snapshot.

Every invocation retains one reload generation for its entire calculation.
Compatibility report records contain only item-free status metadata; prepared
gun and ammunition stacks never enter the command output model. Preview output
is capped at 20 matching entries and individual detail lines are capped at 512
characters.

`validate` checks every published entry, or one requested pool, through the
same non-mutating TaCZ compatibility analysis later used by issuance. Its chat
summary shows at most 20 issues and directs the operator to paginated inspection
for complete details.

`inspect` displays a pool's format, total weight, entries, conditions, and
compatibility results. Output is bounded to 8 entries per page. Page numbers
are one-based; a missing page means page 1; a page outside the available range
fails without partial output.

`preview` evaluates the requested difficulty, source, optional pool, and the
command source's current world position. Vanilla `/execute in` and `/execute
positioned` provide alternate preview contexts. It reports matching entries and
their effective probabilities, but never consumes a random roll, marks a guard
attempted, constructs provenance, issues items, mutates the population index,
or performs the normal nearby-cap entity query.

Runtime scripts that require a real guard are reported as context-dependent
rather than being guessed from a player or console source. Actual issuance with
the spawned guard remains authoritative for those scripts. Using vanilla
`/execute as` with a Guard Villager guard supplies its scoreboard tags and live
entity context for a definitive guard-dependent preview.

## Performance boundary

These features add no tick listener, recurring guard scan, automatic command,
or combat-path work. Dimension context is captured once during the existing
one-time spawn decision. Biome, structure, raid, and guard-tag data are read
only when at least one entry requires them. Pool and condition limits bound the
two-pass matching work and explicit validation work. Commands perform work only
when an authorized administrator invokes them, and all chat output is bounded.
