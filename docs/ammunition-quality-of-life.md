# Ammunition Quality-of-Life Guide

Villager Guard Gunners keeps TaCZ authoritative for ammunition compatibility,
magazine and chamber behavior, reloads, feed types, attachments, and finite or
infinite reserve rules. The add-on supplies three persistent guard ammunition
slots and three optional ways to put physical ammunition into those slots. It
does not synthesize ammunition or change a weapon's native capacity.

## Supplying a guard

### Inventory stocking

Open an authorized guard inventory and place compatible loose TaCZ ammunition
or ammo boxes in the three ammunition slots. This remains the most explicit
way to equip a guard and works even when both convenience systems are disabled.
The inventory tooltip reports magazine, chamber, feed type, compatible reserve
units, and any incompatible or unreadable stacks.

When TaCZ empties a finite ammo box, the guard preserves the reusable box by
dropping it beside itself the next time it needs room for a reload or an
automated restocking action. The same maintenance runs before an authorized
ammo shift-click. It is event-driven, scans only the three dedicated slots,
and never deletes the box or adds a per-guard tick task.

### Crouch-right-click handoff

A guard can receive compatible loose ammunition or an ammo box for either its
effective primary or configured sidearm from the player's held stack. Crouch
and right-click the guard. The server validates both weapon roles, inserts only
what fits, and leaves the remainder in the player's hand. Creative-mode
players retain their held stack.

This action follows Guard Villagers' `giveGuardStuffHOTV` policy. Permission to
give supplies does not grant permission to open the guard inventory or remove
equipment. Spectators, players targeted by the guard, unauthorized players,
inactive guards, unarmed guards, incompatible or unreadable ammunition, and
guns using dummy or native-infinite reserves are rejected without moving items.

### Passive dropped-ammo pickup

When `ammunition.pickupDroppedAmmo` is enabled, eligible dropped TaCZ
ammunition checks for a nearby guard every ten ticks, staggered by item entity
ID. Guards do not scan for items and do not pathfind toward drops. The nearest
eligible active guard within `ammunition.droppedAmmoPickupRadius` receives only
the portion that fits. Vanilla pickup delays and player-targeted item entities
remain authoritative. Passive pickup also follows Minecraft's `mobGriefing`
game rule; when the rule is false, dropped ammunition performs no guard query.

Passive pickup accepts loose ammunition and non-empty ammo boxes compatible
with either stable weapon role. It rejects empty finite boxes before the guard
lookup, ordinary items, calibers incompatible with both guns, unreadable
third-party ammo, full inventories, inactive or unarmed guards, and loadouts
that do not consume a physical reserve. Disable `pickupDroppedAmmo` to roll
this system back without disabling gun combat, manual stocking, or emergency
sharing.

### Emergency sharing

When `ammunition.shareAmmoBetweenGuards` is enabled, a guard requests supplies
only after TaCZ proves its loaded gun is dry and has no compatible physical
reserve. The recipient performs one radius-bounded search, chooses the nearest
eligible donor deterministically, and can receive at most one of its own
attachment-adjusted magazines. A failed request enters the existing dry retry
window instead of searching every tick.

Sharing transfers loose ammunition only. It must be compatible with the
recipient's currently active gun and with at least one stable donor weapon.
Ammo boxes are deliberately not opened or transferred. The donor's physical
and boxed reserves still count toward its retained floor, and
`ammunition.donorReserveMagazines` keeps the configured number of whole
attachment-adjusted magazines for every donor weapon that can consume the
shared caliber. With `ammunition.requireMatchingOwnerForSharing` enabled,
owner UUIDs must match; two unowned guards are considered a match. Disable
`shareAmmoBetweenGuards` to roll sharing back independently of passive pickup
and manual stocking.

The normal dry-primary flow asks for emergency ammunition before requesting a
sidearm transition. If the sidearm later runs dry, it can use this same sharing
path because TaCZ binds it as the active main-hand gun. A dry-primary fallback
does not return to that known-dry primary during the same engagement, avoiding
weapon-switch loops.

## Supported reserve behavior

| Reserve or feed behavior | Inventory/handoff | Passive pickup | Emergency sharing |
| --- | --- | --- | --- |
| Compatible loose TaCZ ammunition | Supported | Supported | Supported |
| Compatible finite ammo box | Supported as a whole stack; dropped intact after depletion | Supported as a whole stack | Not transferred |
| Compatible creative/unlimited ammo box | Accepted when TaCZ marks it compatible | Accepted when TaCZ marks it compatible | Not transferred |
| Magazine-fed, manual, fuel, or inventory-fed gun | Uses TaCZ's resolved compatibility and feed behavior | Same | Loose physical reserve only |
| Attachment-adjusted capacity | Reported and used by TaCZ | No capacity rewrite | Bounds recipient request and donor reserve |
| Open-/closed-bolt chamber | Reported using TaCZ semantics | No chamber mutation | No chamber mutation |
| Dummy or native-infinite gun reserve | Physical stocking rejected by quick paths | Rejected | Recipient/donor rejected |
| Unknown or malformed third-party definition | Fails without moving items | Fails without moving items | Fails without moving items |

Third-party gun packs remain responsible for exposing correct TaCZ
compatibility and reserve metadata. A manual release pass should include the
exact gun packs and attachments distributed with the modpack.

## Server configuration and synchronization

The server owns `config/villagerguardgunners/guard_gunners.toml`, synchronizes
the active Fzzy Config values to clients, and requires permission level 4 for
edits. The ammunition controls are:

| Setting | Default | Effect |
| --- | ---: | --- |
| `ammunition.pickupDroppedAmmo` | `true` | Enables the item-driven passive pickup hook. |
| `ammunition.droppedAmmoPickupRadius` | `1.5` blocks | Bounds the spherical guard lookup around a drop. |
| `ammunition.shareAmmoBetweenGuards` | `true` | Enables recipient-driven emergency sharing. |
| `ammunition.ammoSharingRadius` | `8` blocks | Bounds the dry recipient's donor lookup. |
| `ammunition.donorReserveMagazines` | `1` | Whole attachment-adjusted magazines retained by a donor. |
| `ammunition.requireMatchingOwnerForSharing` | `true` | Requires equal owner UUIDs; two unowned guards still match. |

Pickup and sharing are independent rollback controls. Changing either toggle
does not alter ammunition already stored in a guard. Radius and donor settings
apply to the next eligible operation; they do not start a recurring inventory
scan.

## Diagnostics and troubleshooting

Use `/vgg inspect <guard>` as an operator with permission level 2. The report
shows separate primary and sidearm gun IDs, feed types, magazine and chamber
states, physical reserve units, and scan completeness. Native dry-retry and
last-sharing details are attached only to the currently active weapon role.

If a guard will not reload or share:

1. Confirm the gun and reserve stacks are compatible in the guard tooltip.
2. Confirm the gun uses a physical reserve rather than dummy/native-infinite
   ammunition.
3. Confirm the recipient is genuinely dry; sharing is not proactive balancing.
4. Check donor owner UUIDs, distance, compatible loose ammunition, and retained
   magazine floor.
5. Inspect the guard after its next native reload attempt.

If a guard will not collect a drop, confirm the pickup toggle, radius, vanilla
`mobGriefing` rule, pickup delay, player-target UUID, guard AI state, gun
compatibility, and free space in the three ammunition slots. Depleted finite
boxes should be lying beside the guard after its next reload or restocking
attempt; they are intentionally not collected again as ammunition.

## Intentionally deferred

The first release deliberately does not include:

- a custom reload animation or replacement reload implementation;
- tactical early reloads or ammunition-conservation fire modes;
- pathfinding to dropped ammunition, donors, chests, or supply blocks;
- continuous redistribution or squad-wide inventory balancing;
- transfers out of finite ammo boxes or ammo-box sharing;
- custom caliber conversion or repair of malformed third-party definitions;
- a village armory or automatic supply-point block.

These boundaries keep reload behavior native to TaCZ and prevent ammunition
convenience from adding continuous scans or all-guard pairwise work.

## Release validation

The complete recordable multiplayer worksheet is in
[release-checklist.md](release-checklist.md). Run its ammunition cases against
the exact candidate JAR, server configuration, gun packs, and attachments being
distributed. Automated release validation is run with:

```bash
./gradlew verifyAmmunitionReleaseCandidate
```

Generate the opt-in performance evidence first in its required separate Gradle
invocation. The aggregate task then runs all unit/source/artifact gates, the
normal correctness GameTest server, verifies the exact 14-report/14-JFR set,
and rebuilds the deterministic release manifest. It does not mark the client,
multiplayer, restart, or third-party gun-pack rows as manually passed.
