# Downed guards and revival

Enable `medical.downed` to give guards a chance to survive lethal injuries. It
is **off by default**. This option requires `medical.enabled` and at least one
valid treatment profile. It works in ordinary villages and with manually
recruited guards.

## Treating a casualty

A downed guard lies on its side and displays a status label. Use a bandage or
supported drinkable healing potion on it, or let a supplied guard with Medic
duty enabled approach it. Sneak-use still stocks its medical pouch.

| State | Treatment result | Time available |
| --- | --- | --- |
| Downed | One dose stabilizes the guard at one health point | 60 seconds |
| Stabilized | A second dose revives the guard at up to four health points | 60 seconds after the first dose's cooldown |
| Recovering | The guard rests before resuming its saved orders | 5 seconds |

Downing clears any earlier healing cooldown so first aid can begin immediately.
Each dose then follows the treatment profile's cooldown. A medic needs an
uninterrupted two-second treatment channel for each dose when transport is off.
With [transport enabled](casualty-transport.md), revival at a cot takes two
seconds followed by five seconds of rest; field revival takes eight seconds
followed by twenty seconds of rest. Manual revival uses the same channels. Canceled treatment,
changed supplies, injury during the channel, and death before completion cost
no dose. Healing potions return their bottle to the slot they came from.

Medics prioritize downed guards, then stabilized guards, then other injured
allies. A casualty's saved follow, patrol, or hold order does not prevent
in-place care; the medic's own orders still take priority. A casualty keeps its
original identity, role, squad, orders, guns, ammo, armor, and medical pouch.
The normal guard hitbox stays selectable while the model lies down.

During these three states, guards cannot fight, walk their routes, reload,
share ammo, or accept equipment service. Downing immediately releases any
automatic building-duty reservation so another guard can cover the position. Their equipment menu is unavailable.
Food, regeneration, and cleric healing cannot stabilize or revive a casualty.
After revival, normal healing can help the guard recover. The guard cannot be
downed again for two minutes after revival; another lethal injury during that
period causes ordinary death.

## Damage, time, and configuration

Bleed-out uses **loaded simulation ticks**. Unloading the guard or stopping the
server pauses its timer. Casualties never keep chunks loaded. Both the downed
and stabilized windows expire in true death if treatment does not arrive.

Further accepted damage shortens the remaining window by 20 ticks per damage
point, capped at 200 ticks for one simulation tick. Damage in the tick that
first downs a guard does not shorten the window, so TaCZ's split damage for one
bullet cannot immediately finish the casualty. Normal damage cancellation and
invulnerability frames still apply.

Void damage, `/kill`, damage tagged `minecraft:bypasses_invulnerability`, and
the datapack tag `villagerguardgunners:bypasses_downing` bypass this protection.
The supplied tag includes `minecraft:out_of_world` and `minecraft:generic_kill`.
Pack authors can add other damage types at
`data/<namespace>/tags/damage_type/bypasses_downing.json`, using the
`villagerguardgunners` namespace to extend this mod's tag.

Totems get their normal opportunity before a guard becomes downed. Zombie
conversion is deferred until true death and then follows Guard Villagers'
settings. Lightning conversion keeps its ordinary behavior, including recovery
of the original guard's medical supplies. Downing itself produces no death,
loot, XP, or population-death signal. Actual death uses the ordinary pipeline
once, including its loot rules and mod events.

Turning off either `medical.downed` or `medical.enabled` resolves loaded and
subsequently loaded casualties into the five-second recovery state at up to
four health points. Removing every valid treatment profile has the same
result. Invalid profile reloads retain the last valid profiles. Physical
medical supplies remain recoverable when treatment is disabled.

## Persistence and integration

`villagerguardgunners:life` is a versioned compound stored on the guard. Legacy
version 1 records `Stage`, `Remaining`, `Cooldown`, `DamageType`, and optional `Attacker`
UUID, together with `Version`. Timers are remaining loaded ticks. `Remaining`
is at most 1200 while downed, 2400 while stabilized (including up to 1200 ticks
of treatment cooldown), and 100 while recovering. Version 2 retains those fields and adds `RecoveryTotal`, `RecoveryElapsed`,
`FieldRecovery`, and `CotRecovery`. Recovery can last up to 2400 loaded ticks;
elapsed rest also counts while waiting for a cot exit. Existing version-1
records preserve their remaining timers, including short recovery already in
progress. Transient channels, leases, and per-tick damage suppression do not
persist. Unknown or
malformed records are preserved and cannot enable new downing or immobilize a
guard. The mod never changes the guard's saved `NoAI` choice.

The original damage source is retained while the casualty remains loaded.
After reload, its damage type and attacker are resolved using only the current
level's loaded entities. If the attacker is unavailable, true death still
runs but attacker-dependent credit or zombie conversion cannot be restored.
For a resolved attacker, bleed-out restores vanilla recent-attacker credit
before invoking death. Direct damage-type registry removal falls back to
generic damage.

Death interception runs at the start of `Guard.die`, after totems and before
Guard Villagers' conversion code. Forge `LivingDeathEvent` cancellation alone
cannot prevent that upstream conversion. Canceled damage never enters downing;
revival dispatches the cancellable heal event and rechecks the life-state
revision and exact dose before consuming supplies. Ordinary `heal` calls cannot
bypass the state transition, including calls made recursively by a heal-event
listener. Other mods that directly replace entities or write health outside
these boundaries need their own compatibility checks.

Care starts where the guard falls. Optional [casualty transport](casualty-transport.md)
lets supplied medics move stabilized guards to dedicated Recovery Cots before
continuing treatment.
