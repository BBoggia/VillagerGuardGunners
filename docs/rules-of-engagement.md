# Rules of Engagement

Rules of engagement (ROE) decide whether a Guard Villager may use its TaCZ gun
against the target already selected by Guard Villagers. The add-on does not
replace Guard Villagers' target acquisition, reputation, village-defense, or
retaliation goals. An allow entry therefore permits a selected target; it does
not make guards search for that entity type.

## Modes

The synchronized `engagement.mode` setting supports four policies:

| Mode | Behavior |
| --- | --- |
| `GUARD_TARGETS` | Compatibility default. Engage every living, non-allied target selected by Guard Villagers. |
| `HOSTILE_MOBS_ONLY` | Engage targets implementing Minecraft's `Enemy` contract. Neutral mobs and players require an explicit allow entry. |
| `DEFENSIVE` | Engage only a recent attacker of the guard or one of the guard's currently protected allies. |
| `HOLD_FIRE` | Keep tracking an otherwise permitted current target, but stop engagement movement and never trigger the gun. |

`engagement.defensiveMemoryTicks` controls the defensive memory window and
defaults to 200 ticks (10 seconds). A target counts as an ally attacker when
its current vanilla last-hurt victim is protected by the central modpack ally
resolver. This intentionally uses Minecraft's existing combat memory rather
than maintaining another per-guard history or scanning nearby entities.

## Datapack tags

Modpacks can add entity types to either additive tag:

- `#villagerguardgunners:always_engage`
- `#villagerguardgunners:never_engage`

For example, to allow a faction NPC in restricted modes:

```json
{
  "replace": false,
  "values": [
    "examplemod:raider"
  ]
}
```

Save this as
`data/villagerguardgunners/tags/entity_types/always_engage.json` in a datapack.
The packaged tags are intentionally empty so the default remains compatible
with Guard Villagers' own target configuration.

The config provides equivalent synchronized sets when shipping a datapack is
not convenient:

- `engagement.additionalTargets`
- `engagement.excludedEntities`

## Precedence

The resolver applies one deterministic order:

1. Missing or dead target, or creative/spectator player: deny.
2. Central ally protection: deny.
3. `engagement.excludedEntities`: deny.
4. `#villagerguardgunners:never_engage`: deny.
5. `HOLD_FIRE`: deny.
6. `engagement.additionalTargets`: allow.
7. `#villagerguardgunners:always_engage`: allow.
8. The selected mode's normal rule.

An allow entry can broaden `HOSTILE_MOBS_ONLY` or `DEFENSIVE`, but cannot
override creative/spectator protection, ally protection, an exclusion, the
never-engage tag, or `HOLD_FIRE`.
Ally exclusions are configured separately under `safety.allies`; another guard
and the shooter's owner remain protected even there.

## Target release and native firing

A target denied because it is a creative/spectator player, allied, excluded,
non-hostile, or lacks recent
aggression is released back to Guard Villagers' existing target selector. This
prevents a disallowed target from monopolizing the target slot while a valid
threat is nearby. `HOLD_FIRE` is different by design: the guard retains and
tracks an otherwise permitted current target while holding position; protected
allies and explicit deny entries are still released by the earlier rules.

The policy gates the initial trigger and is re-evaluated from TaCZ's
per-round `GunFireEvent` callback for every scheduled burst round. A live
config change, target change, expired defensive window, or newly protected
relationship therefore closes an already scheduled burst before the next
round consumes ammunition or creates a projectile. Friendly-fire direct-hit
and explosion backstops remain independent final protections.

## Performance and diagnostics

ROE adds no world scan, target search, tick handler, reflection, or logging.
For a guard already in gun combat it performs constant-time config, registry,
tag, type, and recent-combat-memory reads. The central ally check is shared
with the existing firing-corridor and damage protection.

`/vgg inspect <guard>` reports both the active mode and exact decision reason,
including explicit additions/exclusions, hostile classification, recent guard
or ally aggression, hold fire, and protected allies.
