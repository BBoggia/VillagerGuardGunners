# Villager Guard Gunners

Villager Guard Gunners is an add-on for **Guard Villagers** and **Timeless and
Classics Zero (TaCZ)** that lets guards use TaCZ guns. It also adds configurable
combat behavior, squad commands, patrol routes, ammo resupply, and medical care.

Equip guards through their inventory, adjust orders, and assign squads. Village Armories and Quartermasters provide supplies, while
medic guards can treat injured allies. Optional packs add regional defense buildings to new villages.

## Features

- **TaCZ guns and combat.** Guards can use pistols, SMGs, rifles, shotguns,
  sniper rifles, machine guns, and launchers. They aim, fire, reload, and adjust
  their movement to the gun they carry. They use TaCZ gun attachments, sounds,
  and effects. Guards avoid firing through protected allies,
  and their explosions preserve blocks by default.
  Guards with empty or unusable guns seek safer positions and can withdraw
  when injured or overwhelmed.
- **Adjustable guard accuracy.** Configure aim error and target point. For player
  combat, enable the optional player override and start with 2 degrees and
  Center Mass. Groups remain dangerous; low cover can block center-mass shots.
- **Ammo and backup sidearms.** Each guard has three dedicated ammo slots for
  loose ammo and ammo boxes. Equip a backup pistol for situations where the
  primary gun is empty, restricted by orders, or unsafe to use. Guards can also
  collect nearby dropped ammo and share spare loose ammo with guards who need it.
- **Guard Orders and squads.** Choose rules of engagement, tactics, movement,
  firing range, and explosive permission for individual guards. Copy behavior
  with an Orders Ledger, or use a Squad Command Ledger to organize squads,
  create saved patrol routes, and issue Rally, Defend, Hold, Focus Target,
  Fall Back, and Resume Orders commands.
- **Village Armories and Quartermasters.** Store ammo in craftable Village
  Armories with hopper, pipe, comparator, and squad-access support. Stock a
  Quartermaster station and let an adult unemployed villager claim it. Guards
  you enroll can visit during work hours to restock supplies or maintain a
  selected equipment loadout. Existing gear stays protected unless you allow
  replacement, and linked Armories provide additional ammo.
- **Medic guards and casualty recovery.** Give a guard medical supplies and
  enable Medic Duty to treat nearby injured allies. Medical Retreat lets
  wounded guards seek help. With optional downed guards enabled, one dose
  stabilizes a casualty and another revives them. Enable cot care to let medics
  carry casualties to Recovery Cots for faster treatment and rest. Emergency
  treatment away from a cot takes longer under the default settings.
- **Village staffing and automatic patrols.** Optional initial guard counts
  scale with village size. Automatic patrols rotate guards through village
  areas and resume after interruptions such as combat. Both features support
  ordinary villages without Village Defense Buildings. Initial staffing affects
  newly generated villages; deaths don't trigger replacement guards.
- **Natural gun loadouts.** Newly generated guards can receive guns and matching
  ammo based on difficulty. Server settings and datapacks control the available
  loadouts, their chances, and nearby automatic-gunner limits. Guard counts and
  automatic gun loadouts have separate settings.
- **Village Watchtowers and Defense Buildings.** Standalone Village Watchtowers
  can generate with guard squads and stocked Armories. You can also enable Barracks,
  Village Armories, Sentry Posts, integrated Watchtowers, and Training Yards in
  newly generated villages. Bells, threats, and raids change village readiness,
  while guards can take sentry, drill, overwatch, and muster duties.
- **Status and settings.** The guard inventory explains conditions such
  as empty ammo, reloading, blocked shots, and supply shortages.
  Configure the mod through Fzzy Config; tooltips explain each setting.

## Getting Started

1. Install the mod and its required dependencies on both client and server.
2. Create a guard through normal Guard Villagers gameplay.
3. Open its inventory, equip a TaCZ gun, and add compatible ammo.
4. Adjust Guard Orders or add a sidearm, squad, or Village Armory.
5. For medical care, stock a guard's medical pouch and enable Medic Duty.
   For Quartermaster service, stock a staffed station and opt the guard into
   Restock or Loadout service.

## Optional Village Buildings Datapack

The optional Village Buildings datapack adds larger, furnished defense buildings with
materials and layouts suited to each village region. Layouts include courtyard
Barracks, supply yards, roadside shelters, gatehouse lookouts, and Training Yards.

**One universal download includes all five building families:** Barracks, Village
Armories, Sentry Posts, Village Watchtowers, and Training Yards, with two layouts
per family in each supported region.

- **Vanilla villages**
  — Plains, Desert, Savanna, Taiga, and Snowy designs. Also supports the vanilla
  village replacements used by Better Villages and Lios Overhauled Villages.
- **ChoiceTheorem's Overhauled Village (CTOV)**
  — Designs for ChoiceTheorem's Overhauled Village: Plains, Desert, Taiga,
  Snowy Igloo, Swamp, and Jungle. Other CTOV themes aren't included.
- **Moog's Missing Villages**
  — Jungle and Swamp designs for Moog's Missing Villages.
- **Tidal Towns**
  — Deep-ocean designs for Tidal Towns settlements.
- **Sky Villages**
  — Buildings on railed floating islands for Sky Villages.

The village mods above are optional. The pack uses the village content installed
in your world and skips integrations for absent providers. Replace the separate
building packs with the universal ZIP instead of keeping both enabled.

**To enable the buildings:**

1. Close the world or stop the server, then put the universal ZIP, still zipped,
   in the world's `datapacks` folder. For a new singleplayer world, use the
   creation screen's Data Packs option.
2. Enable **Village Defense Building generation** in Fzzy Config. It starts
   disabled.
3. Reopen the world or restart the server, then explore newly generated
   villages. **`/reload` alone isn't enough.** Existing villages aren't rebuilt.

On a dedicated server, install the ZIP in the server world's `datapacks`
folder. Joining players don't need a local copy of the datapack.

The base mod includes small built-in defense buildings; the datapack is an optional
download for the larger regional designs. Large buildings need room and can be
rare in crowded villages. Occasional awkward terrain placement is possible.

## Servers and Modpacks

Downed guards, cot care and transport, size-based initial staffing, automatic
village patrols, and Village Defense Building generation start **disabled by
default**. Enable the features you want through Fzzy Config. Cot care requires
Medical Treatment and Downed Guards; carrying also requires a supplied medic
and a reachable Recovery Cot. Individual guards start with Medic Duty and
Medical Retreat off.

Guards refuse unsupported scripted projectile behavior, including at zero added
aim error.

Enabling buildings doesn't automatically enable size-based staffing or patrol
coverage. Completed buildings don't replace killed guards, repair themselves,
or refill supplies that players remove.

Datapacks can customize gun loadouts, guard roles, treatment supplies,
Quartermaster equipment profiles, ally protection, village buildings, and
starting stock. Operators can use `/vgg` commands to inspect guards and review
village, squad, medical, and supply systems.

## Requirements

- **Minecraft:** 1.20.1
- **Forge:** 47.4.10 or newer 47.x
- **Java:** 17
- **Guard Villagers:** 1.20.1-1.6.19
- **Timeless and Classics Zero (TaCZ):** 1.1.8 or newer
- **Fzzy Config:** 0.7.6 for Minecraft 1.20.1
- **Kotlin for Forge:** 4.12.0

Install Villager Guard Gunners and all four required dependencies on both
client and server.

## Support

When reporting a problem, include your mod versions, relevant `latest.log`,
TaCZ gun-pack list, and steps to reproduce it.

Villager Guard Gunners is an unofficial add-on for Guard Villagers and Timeless
and Classics Zero. It isn't affiliated with or endorsed by either development
team.
