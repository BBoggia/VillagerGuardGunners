# Villager Guard Gunners

Villager Guard Gunners is an add-on for **Guard Villagers** and **Timeless and
Classics Zero (TaCZ)** that lets guards use TaCZ guns. It adds configurable
combat behavior, squad commands, patrol routes, ammo resupply, Quartermaster
services, and medical care.

Equip guards through their inventory, give them compatible ammo, and adjust
their orders. Village Armories and Quartermasters provide supplies, while
medic guards can treat injured allies. Optional generation settings and a
companion datapack add defense buildings to newly generated villages.

**Available for Minecraft 1.20.1 with Forge and for Minecraft
1.21.1 with NeoForge.** Choose the file and dependencies for your Minecraft
version. Both versions use the official TaCZ project, with the file for the
matching Minecraft version and loader.

## Features

- **TaCZ guns and combat.** Guards can use pistols, SMGs, rifles, shotguns,
  sniper rifles, machine guns, and launchers. They aim, fire, reload, and adjust
  their movement to their weapon. Guards avoid firing through protected allies
  and can withdraw when injured, overwhelmed, or unable to use their gun.
  Their explosions preserve blocks by default.
- **Configurable accuracy.** Adjust aim error and the point guards aim at.
  Optional settings let you reduce accuracy against players separately from
  accuracy against mobs.
- **Ammo and backup sidearms.** Each guard has three dedicated ammo slots for loose
  ammo and ammo boxes. Equip a backup pistol for situations where the primary
  gun is empty or unsafe to use. Guards can collect nearby dropped ammo and
  share spare loose ammo with other guards.
- **Guard Orders and squads.** Set rules of engagement, tactics, movement,
  firing range, and explosive permission. Copy behavior with an Orders Ledger,
  or use a Squad Command Ledger to organize squads, create patrol routes, and issue
  Rally, Defend, Hold, Focus Target, Fall Back, and Resume Orders commands.
- **Village Armories.** Store ammo in craftable Armories that guards can visit
  when supplies run low. Armories support hoppers, pipes, comparators, and
  access restrictions for squads.
- **Quartermasters.** Let an adult unemployed villager claim a stocked
  Quartermaster station. Enrolled guards can restock or maintain a selected
  equipment loadout during work hours. Review shortages, deliveries, and guard
  service settings through the overview, or manage several guards together.
  Quartermasters also sell supplies through villager trades. Gun sales are
  optional and start disabled. Deposit purchases into storage for guards to
  use them.
- **Medical care.** Supply a guard's medical pouch and enable Medic Duty to
  treat injured allies. Medical Retreat lets wounded guards seek help. With
  downed guards enabled, casualties can be stabilized and revived. Optional
  cot care and transport let supplied medics carry casualties to Recovery Cots.
- **Village defense.** Optional initial staffing scales guard counts with
  village size. Automatic patrols cover village areas and resume after combat.
  Standalone Village Watchtowers and optional village buildings provide guard
  positions and supplies. Initial staffing and building garrisons do not
  replace killed guards.
- **Natural gun loadouts.** Newly spawned guards can receive guns and matching
  ammo based on difficulty, including Peaceful. Server settings and datapacks control the available loadouts,
  spawn sources, chances, and nearby automatic-gunner limits. Guard counts
  and gun loadouts have separate settings.

## Getting Started

1. Install the mod and its four required dependencies on both client and server.
2. Create a guard through normal Guard Villagers gameplay.
3. Open its inventory, equip a TaCZ gun, and add compatible ammo.
4. Adjust Guard Orders, add a sidearm, or assign the guard to a squad.
5. For automatic supplies, stock a Village Armory or opt the guard into Restock
   or Loadout service at a staffed Quartermaster station.

Use Fzzy Config to change settings. The guard inventory shows ammo, combat,
medical, and equipment-service status.

## Optional Village Buildings Datapack

The companion Village Buildings datapack includes **Barracks, Village Armories,
Sentry Posts, Village Watchtowers, and Training Yards** in one universal ZIP.

It includes designs for vanilla villages and selected regions from
**ChoiceTheorem's Overhauled Village (CTOV), Moog's Missing Villages, Tidal
Towns, and Sky Villages**. Integrations for village mods you do not have remain
inactive. Use the pack made for your Minecraft version; the Forge and NeoForge
downloads are separate.

Place the ZIP in the world's `datapacks` folder, enable **Village Defense
Building generation** in Fzzy Config, and restart the world or server. Explore
newly generated villages to find the buildings. `/reload` alone is not enough,
and existing villages are not rebuilt. On servers, joining players do not need
a local copy of the datapack.

The companion pack expands the base mod’s smaller built-in buildings.
Large structures need space, and occasional awkward terrain placement is possible.

## Infinite Ammo

Both versions have four options under **Ammo Quality of Life → Infinite Ammo**:

- **Off (default):** normal ammo use.
- **Infinite Ammo with Reloading:** all guards have unlimited reserves and keep normal reload delays.
- **Infinite Ammo without Reloading:** all guards keep their magazines supplied. Firing delays, action cycling, heat, and friendly-fire checks still apply.
- **Infinite Ammo with Creative Ammo Box:** new TaCZ gun loadouts include compatible creative ammo boxes. Other supported gun mods keep ordinary ammo. Existing guards can receive boxes manually.

Virtual modes leave carried supplies untouched. Changing the setting does not
remove boxes already issued. Boxes can be removed and transferred like other
TaCZ items. This setting does not change the chance of a guard spawning with a gun.

## Requirements

| Component | Minecraft 1.20.1 — Forge | Minecraft 1.21.1 — NeoForge |
| --- | --- | --- |
| Loader | Forge 47.4.10 or newer 47.x | NeoForge 21.1.233 or newer 21.1.x |
| Java | 17 | 21 |
| Guard Villagers | 1.20.1-1.6.19 | 2.4.12 |
| TaCZ | Original TaCZ, 1.1.8 or newer for 1.20.1 | Official NeoForge release, 1.2.0 |
| Fzzy Config | 0.7.6 or 0.7.7 for 1.20.1 | 0.7.7+1.21+neoforge or 0.7.7+fix1+1.21+neoforge |
| Kotlin for Forge | 4.12.0 | 5.12.0 |

**Use the listed Guard Villagers, TaCZ, and Kotlin versions for NeoForge. Fzzy Config accepts either listed build.**
Download the Minecraft 1.21.1 NeoForge file from the
[official TaCZ project](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero).
Replace the unofficial port rather than installing both. TaCZ 1.2.0's NeoForge
file is currently marked Beta. Older custom gun packs can need conversion;
TaCZ provides `/tacz convert universal` to generate Minecraft 1.21.1 overlays.
Keep a world backup when changing dependencies. Gun-pack conversion is separate
from upgrading an entire world between Minecraft versions.

## Servers, Modpacks, and Support

Downed guards, cot care and transport, size-based initial staffing, automatic
village patrols, and Village Defense Building generation start disabled.
Enable the features you want through Fzzy Config. Individual guards start with
Medic Duty and Medical Retreat off.

Datapacks can customize gun loadouts, guard roles, treatment supplies,
Quartermaster equipment and trades, ally protection, and village buildings.
Operators can use `/vgg` commands to inspect guards and village systems.

Report issues with your Minecraft, loader,
and mod versions, relevant logs, gun-pack list, and reproduction steps.

Villager Guard Gunners is an unofficial add-on and is not affiliated with or
endorsed by the Guard Villagers or TaCZ development teams.
