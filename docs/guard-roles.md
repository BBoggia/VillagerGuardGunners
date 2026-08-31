# Datapack-Defined Guard Roles

Guard roles are named, reusable sets of behavioral defaults. They do not grant,
create, remove, or move weapons, ammunition, armor, or any other item. A guard
without a role behaves exactly as it did before this feature.

Role files live at `data/<namespace>/guard_roles/<path>.json`. For example,
`data/example/guard_roles/village/sentry.json` defines role ID
`example:village/sentry`.

## Format 1

```json
{
  "format": 1,
  "inherits": ["villagerguardgunners:rifleman"],
  "orders": {
    "engagement": "hostile_mobs_only",
    "tactics": "marksman",
    "explosives": "disabled",
    "ammo_pickup": "enabled",
    "ammo_sharing": "enabled",
    "movement": "hold_position",
    "maximum_range": "blocks_48"
  }
}
```

`inherits` and `orders` are optional. A role may have at most eight parents,
and inheritance may be at most 16 roles deep. Every order field is optional;
an omitted or explicit `inherit` value contributes no default.

| Field | Values |
| --- | --- |
| `engagement` | `inherit`, `guard_targets`, `hostile_mobs_only`, `defensive`, `hold_fire` |
| `tactics` | `inherit`, `generalist`, `skirmisher`, `breacher`, `marksman`, `support`, `demolitions` |
| `explosives` | `inherit`, `enabled`, `disabled` |
| `ammo_pickup` | `inherit`, `enabled`, `disabled` |
| `ammo_sharing` | `inherit`, `enabled`, `disabled` |
| `movement` | `inherit`, `tactical`, `hold_position`, `patrol_bound` |
| `maximum_range` | `inherit`, `blocks_8`, `blocks_16`, `blocks_24`, `blocks_32`, `blocks_48`, `blocks_64` |

The parser is strict. Unknown fields, future formats, noncanonical IDs,
duplicate parents, missing parents, inheritance cycles, and contradictory
values inherited from multiple parents reject the complete role publication.
The last successfully loaded generation remains active. Removing every role
file intentionally publishes an empty generation; guards keep their assigned
IDs but receive no defaults until those definitions return. At most 256 role
documents are accepted in one reload.

The add-on supplies five unassigned, overrideable roles: `rifleman`,
`marksman`, `support`, `breacher`, and `demolitions` in the
`villagerguardgunners` namespace. Merely installing the mod does not assign
them or change an existing guard.

## Assignment

Natural-loadout format 4 adds an optional canonical `role` ID to an entry:

```json
{
  "format": 4,
  "entries": [
    {
      "gun": "tacz:ak47",
      "sidearm": "tacz:glock_17",
      "role": "villagerguardgunners:rifleman",
      "weight": 1
    }
  ]
}
```

A missing role definition fails the one-time natural issuance before any item
changes. Formats 1 through 3 retain their exact prior meaning and reject the
new field.

Permission-level-2 operators can assign, inspect, or clear roles directly:

```text
/vgg roles list [page]
/vgg roles inspect <role>
/vgg roles assign <guard> <role>
/vgg roles clear <guard>
```

These commands mutate only the selected guard's role ID. `/vgg inspect
<guard>` reports the assigned ID, whether its definition is loaded, and the
effective order layer.

Orders Ledger version 2 captures the role ID alongside the seven raw orders.
Applying it copies both behavior layers but no items. Version-1 ledgers remain
valid and deliberately leave the destination guard's role unchanged.

## Precedence and safety

Effective behavior is resolved in this order:

1. Server-wide safety and master restrictions remain authoritative.
2. Temporary squad commands retain their existing bounded movement priority.
3. Explicit values in the guard's Guard Orders panel override role defaults.
4. The loaded role supplies only fields still set to `inherit`.
5. Existing server defaults apply when neither layer specifies a value.

Consequently, a role cannot relax global Hold Fire, ammunition master
switches, maximum engagement range, ally protection, target exclusions,
explosive standoff, corridor safety, or TaCZ's native weapon state.

## Performance

Roles add no entity scan, world scan, tick listener, path request, task, or
chunk load. Reload resolves and validates the bounded inheritance graph once,
then publishes immutable definitions with one volatile write. Live behavior
uses one persisted role ID and one constant-time map lookup. The seven-enum
merge is cached and recomputed only after an explicit order change, role
assignment, or successful role reload. A reload generation lazily invalidates
each guard's existing low-frequency weapon metadata cache when that guard next
updates.
