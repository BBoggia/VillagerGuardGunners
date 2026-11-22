# Villager Guard Gunners

> **New in the 1.12 beta** — Guards can now use guns from [MrCrayfish's Gun Mod \[Unofficial\]](https://www.curseforge.com/minecraft/mc-mods/mrcrayfishs-gun-mod-unofficial), [Vic's Point Blank](https://www.curseforge.com/minecraft/mc-mods/vics-point-blank), and [PointBlank: Jelly](https://www.curseforge.com/minecraft/mc-mods/pointblank-jelly) on Forge 1.20.1 and NeoForge 1.21.1. TaCZ is optional in the beta. If you use Point Blank, choose one of the two variants.

Villager Guard Gunners is an add-on for **Guard Villagers** that lets guards use guns from **Timeless and Classics Zero (TaCZ)** and other supported gun mods. It adds configurable combat behavior, squad commands, patrol routes, ammo resupply, Quartermaster services, and medical care.

Equip guards through their inventory, give them compatible ammo, and adjust their orders. Village Armories and Quartermasters provide supplies, while medic guards can treat injured allies. Optional generation settings and a companion datapack add defense buildings to newly generated villages.

**Available for Minecraft 1.20.1 with Forge and for Minecraft 1.21.1 with NeoForge.** Choose the file and dependencies for your Minecraft version. If you use TaCZ, choose the official file for your Minecraft version and loader.

I started developing this mod because powerful mobs in my modpack were wiping out villages. I wanted to give the guards a way to defend them without making the guards too powerful.

## Features

*   **Guns and combat.** Guards can use pistols, SMGs, rifles, shotguns, sniper rifles, machine guns, and supported launchers. With either Point Blank variant, guards use guns that hit their target directly and hold fire with launchers. They aim, fire, reload, and adjust their movement to their weapon. Guards avoid firing through protected allies and can withdraw when injured, overwhelmed, or unable to use their gun. Their explosions preserve blocks by default.
*   **Configurable accuracy.** Adjust aim error and the point guards aim at. Optional settings let you reduce accuracy against players separately from accuracy against mobs.
*   **Ammo and backup sidearms.** Each guard has three dedicated ammo slots for loose ammo and ammo boxes. Equip a backup pistol for situations where the primary gun is empty or unsafe to use. Guards can collect nearby dropped ammo and share spare loose ammo with other guards.
*   **Guard Orders and squads.** Set rules of engagement, tactics, movement, firing range, and explosive permission. Copy behavior with an Orders Ledger, or use a Squad Command Ledger to organize squads, create patrol routes, and issue Rally, Defend, Hold, Focus Target, Fall Back, and Resume Orders commands.
*   **Village Armories.** Store ammo in craftable Armories that guards can visit when supplies run low. Armories support hoppers, pipes, comparators, and access restrictions for squads.
*   **Quartermasters.** Let an adult unemployed villager claim a stocked Quartermaster station. Enrolled guards can restock or maintain a selected equipment loadout during work hours. Review shortages, deliveries, and guard service settings through the overview, or manage several guards together. Quartermasters also sell supplies through villager trades. Gun sales are optional and start disabled. Deposit purchases into storage for guards to use them.
*   **Medical care.** Supply a guard's medical pouch and enable Medic Duty to treat injured allies. Medical Retreat lets wounded guards seek help. With downed guards enabled, casualties can be stabilized and revived. Optional cot care and transport let supplied medics carry casualties to Recovery Cots.
*   **Village defense.** Optional initial staffing scales guard counts with village size. Automatic patrols cover village areas and resume after combat. Standalone Village Watchtowers and optional village buildings provide guard positions and supplies. Initial staffing and building garrisons do not replace killed guards.
*   **Natural gun loadouts.** Newly spawned guards can receive guns and matching ammo based on difficulty, including Peaceful. Server settings and datapacks control the available loadouts, spawn sources, chances, and nearby automatic-gunner limits. Guard counts and gun loadouts have separate settings.

## Getting Started

1.  Install Villager Guard Gunners, Guard Villagers, Fzzy Config, and Kotlin for Forge on both client and server. Install your chosen gun mods and their dependencies on both sides. Earlier releases also require TaCZ.
2.  Create a guard through normal Guard Villagers gameplay.
3.  Open its inventory, equip a supported gun in its main hand, and add compatible ammo.
4.  Adjust Guard Orders, add a sidearm, or assign the guard to a squad.
5.  For automatic supplies, stock a Village Armory or opt the guard into Restock or Loadout service at a staffed Quartermaster station.

Use Fzzy Config to change settings. The guard inventory shows ammo, combat, medical, and equipment-service status.

## Optional Village Buildings Datapack

The [companion Village Buildings datapack](https://www.curseforge.com/minecraft/data-packs/villager-guard-gunners-village-buildings) includes **Barracks, Village Armories, Sentry Posts, Village Watchtowers, and Training Yards** in one universal ZIP.

It includes designs for vanilla villages and selected regions from **ChoiceTheorem's Overhauled Village (CTOV), Moog's Missing Villages, Tidal Towns, and Sky Villages**. Integrations for village mods you do not have remain inactive. Choose the Forge or NeoForge download for your Minecraft version.

Place the ZIP in the world's `datapacks` folder, enable **Village Defense Building generation** in Fzzy Config, and restart the world or server. Explore newly generated villages to find the buildings. `/reload` alone is not enough, and existing villages are not rebuilt. On servers, joining players do not need a local copy of the datapack.

The companion pack expands the base mod’s smaller built-in buildings. Large structures need space, and occasional awkward terrain placement is possible.

## Infinite Ammo

Both versions have four options under **Ammo Quality of Life → Infinite Ammo**:

*   **Off (default):** normal ammo use.
*   **Infinite Ammo with Reloading:** all guards have unlimited reserves and keep normal reload delays.
*   **Infinite Ammo without Reloading:** all guards keep their magazines supplied. Firing delays, action cycling, heat, and friendly-fire checks still apply.
*   **Infinite Ammo with Creative Ammo Box:** new TaCZ loadouts include a compatible creative ammo box. You can give a box to an existing TaCZ guard manually. Guards with MrCrayfish’s or Point Blank guns receive ordinary matching ammo.

Virtual modes leave carried supplies untouched. Changing the setting does not remove boxes already issued. Boxes can be removed and transferred like other TaCZ items. This setting does not change the chance of a guard spawning with a gun.

## Requirements

| Component        | Minecraft 1.20.1 — Forge                 | Minecraft 1.21.1 — NeoForge                     |
| ---------------- | ---------------------------------------- | ----------------------------------------------- |
| Loader           | Forge 47.4.10 or newer 47.x              | NeoForge 21.1.233 or newer 21.1.x               |
| Java             | 17                                       | 21                                              |
| Guard Villagers  | 1.20.1-1.6.19                            | 2.4.12                                          |
| TaCZ (optional in the beta) | Official Forge release, 1.1.8 or newer for 1.20.1 | Official NeoForge release, 1.2.0 |
| Fzzy Config      | 0.7.6 or newer 0.7.x for 1.20.1                | 0.7.7+1.21+neoforge or 0.7.7+fix1+1.21+neoforge |
| Kotlin for Forge | 4.12.0 (tested)                          | 5.12.0                                          |

Use the listed Guard Villagers and Kotlin for Forge versions on NeoForge. Either listed NeoForge build of Fzzy Config is supported.

*   **TaCZ.** Use the [official TaCZ project](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero) for both loaders. On NeoForge, replace the unofficial port with the official 1.2.0 file. Older custom gun packs can need conversion. TaCZ provides `/tacz convert universal` to generate Minecraft 1.21.1 overlays. Keep a world backup when changing dependencies.
*   **MrCrayfish's Gun Mod \[Unofficial\].** Install Framework alongside it. On Minecraft 1.21.1, version 1.4.21 of the gun mod requires NeoForge 21.1.249 or newer and Framework 0.13.11 or newer.
*   **Point Blank.** Install GeckoLib and either Vic's Point Blank or PointBlank: Jelly. Vic's Point Blank 2.2.0 and PointBlank: Jelly 2.1.0 were tested on both loaders.

## Servers, Modpacks, and Support

Downed guards, cot care and transport, size-based initial staffing, automatic village patrols, and Village Defense Building generation start disabled. Enable the features you want through Fzzy Config. Individual guards start with Medic Duty and Medical Retreat off.

Datapacks can customize gun loadouts, guard roles, treatment supplies, Quartermaster equipment and trades, ally protection, and village buildings. Operators can use `/vgg` commands to inspect guards and village systems.

Report issues with your Minecraft, loader, and mod versions, relevant logs, gun-pack list, and reproduction steps.

Villager Guard Gunners is an unofficial add-on and is not affiliated with or endorsed by the Guard Villagers or TaCZ development teams.
