# Per-Guard Orders

Per-guard orders let an authorized player specialize one guard without
duplicating the server's Fzzy Config. Open the guard inventory and use the
**Guard Orders** panel on its right side. Each button cycles through its
available values; **Inherit** (or **Auto** for tactics) preserves the existing
server-configured behavior.

Orders are server-authoritative, synchronized through the open container, and
saved on the guard. A guard with all defaults writes no additional order data,
so existing guards and worlds retain their previous behavior.

## Copying orders with an Orders Ledger

The **Orders Ledger** is a reusable, single-stack item that copies the seven
behavior orders shown in the Guard Orders panel plus the guard's optional
datapack role. Craft one shapelessly from a
**Book and Quill** and an **Emerald**.

1. Use a blank Orders Ledger on a guard to capture that guard's current raw
   orders.
2. Use the written ledger on another guard to apply all seven stored orders at
   once.
3. Sneak-use the ledger on a guard to replace its stored template with that
   guard's current orders.

The tooltip lists every stored value, and a written ledger has the enchanted
glint so it is easy to distinguish from a blank one. Applying a template that
already matches is a mutation-free no-op. A ledger copies behavior parameters
only: it never copies or transfers weapons, ammunition, armor, inventory,
ownership, patrol positions, names, targets, health, or any other guard data.

The normal Guard Villagers interaction permission is checked on the server for
every capture, apply, and overwrite. The player and living guard must be in the
same server level and within eight blocks; spectators and a player currently
targeted by that guard cannot use the ledger. An offhand ledger works when the
main hand is empty, but it never takes priority over a non-empty main-hand
interaction.

Version-2 templates include the role assignment. Existing version-1 ledgers
remain valid and leave the destination guard's role unchanged.

Templates use a strict versioned format. Missing fields, unknown values,
damaged data, and templates written by a newer unsupported format fail closed
without partially changing the guard. Sneak-use can deliberately replace a
damaged or unsupported template with the selected guard's current orders.

## Available orders

| Order | Values | Effect |
| --- | --- | --- |
| Rules of engagement | Inherit, Normal, Hostiles, Defensive, Hold Fire | Selects this guard's target policy. Normal follows Guard Villagers' selected targets; Hostiles permits proactive fire only at hostile mobs; Defensive requires recent aggression; Hold Fire tracks without engaging. |
| Tactical profile | Auto, Generalist, Skirmisher, Breacher, Marksman, Support, Demolitions | Selects the guard's movement and firing envelope. It does not alter TaCZ weapon statistics or remove structural explosive and multi-projectile safety traits. |
| Explosive weapons | Inherit, Allow, Deny | Allows or prohibits firing a weapon TaCZ identifies as explosive. Deny makes the gun goal retain the target safely without firing or falling through to melee. |
| Dropped-ammo pickup | Inherit, Allow, Deny | Enables or disables passive pickup for this guard, subject to the server master switch. |
| Ammunition sharing | Inherit, Allow, Deny | Enables or disables both donating and receiving emergency loose ammunition for this guard, subject to the server master switch. |
| Combat movement | Inherit, Tactical, Hold, Patrol Bound | Inherit follows the guard's current patrol state. Tactical allows the full approach/retreat/reposition loop. Hold suppresses all add-on combat movement. Patrol Bound suppresses ordinary approach while retaining immediate retreat and friendly-lane repositioning. |
| Maximum range | Inherit, 8, 16, 24, 32, 48, or 64 blocks | Narrows this guard's firing cap. It can never widen the server maximum or a narrower weapon-profile cap. |

## Precedence and safety

Per-guard orders are behavior overrides, not administrator permissions. The
following controls remain authoritative:

- Server-wide `HOLD_FIRE` cannot be relaxed by a guard order.
- Ally protection, target exclusions, and deny tags cannot be bypassed.
- `ammunition.pickupDroppedAmmo=false` and
  `ammunition.shareAmmoBetweenGuards=false` disable those systems globally.
- The configured maximum engagement range remains a hard upper bound.
- Explosive standoff, firing-corridor checks, direct-hit protection, native
  burst validation, and explosion protection always remain active.

Within those boundaries, per-guard tactical selection precedes an exact gun-ID
override, which precedes the configured gun-type mapping and structural
fallback. Structural traits are still retained: choosing Marksman for an RPG,
for example, does not make the rocket non-explosive.

## Authorization and networking

The same Guard Villagers permission used to edit the guard inventory is used
for order changes. The server rechecks that permission, menu validity, living
guard state, and distance for every button request. A client cannot change an
order by hiding or forging a button.

The implementation adds no custom packet channel and no global order tick
handler. It uses Minecraft's existing container-button packet and seven small
change-detected data values while the inventory is open. During combat, the
hot path reads immutable enum values; a single revision integer causes the
existing low-frequency TaCZ metadata cache to refresh immediately when a
range or tactical order changes.

## Diagnostics and troubleshooting

`/vgg inspect <guard>` reports the raw per-guard orders and the effective rules
of engagement after server precedence is applied.

If a button appears to do nothing:

1. Confirm the player is allowed to edit that guard under Guard Villagers'
   inventory/reputation rules and remains within eight blocks.
2. Check whether a server master switch is disabled or server-wide Hold Fire
   is active.
3. Use `/vgg inspect <guard>` to distinguish an order from range, ammunition,
   aim, visibility, or friendly-fire safety.
4. For launchers, remember that allowing explosives does not bypass the
   blast-aware minimum standoff or protected-entity corridor.

If an Orders Ledger does not behave as expected:

1. Read its tooltip to confirm whether it is blank, written, damaged, or from
   an unsupported newer format.
2. Keep the main hand empty when using a ledger from the offhand.
3. Move within eight blocks and confirm Guard Villagers allows that player to
   interact with the guard.
4. Remember that the ledger stores raw orders. The effective result can still
   be narrowed by global Hold Fire, maximum range, ammunition master switches,
   ally protection, and explosive safety.
