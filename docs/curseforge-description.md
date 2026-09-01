# Villager Guard Gunners

Give **Guard Villagers** guards native access to **Timeless and Classics Zero**
firearms.

Villager Guard Gunners lets guards equip and operate TaCZ weapons using TaCZ's
own firearm system. Guards can aim, move, fire, reload, cycle manual actions,
manage ammunition, protect allies, and adjust their tactics to the weapon they
are carrying without replacing TaCZ's projectiles or weapon behavior.

<br>

## How It Works

<ol>
  <li>Create a guard through normal Guard Villagers gameplay, or allow an
      eligible natural guard to receive a configured loadout.</li>
  <li>Open the guard inventory and place a TaCZ gun in its main-hand slot. You
      can also give it an optional eligible pistol in the sidearm slot.</li>
  <li>Add compatible loose ammunition or ammo boxes to the three ammunition
      slots.</li>
  <li>Optionally specialize the guard with per-guard orders or assign it to a
      squad with a Squad Command Ledger.</li>
  <li>Give the guard a valid target through normal Guard Villagers behavior.</li>
  <li>The guard uses TaCZ's native aiming, firing, reload, projectile, sound,
      and synchronization systems during combat.</li>
</ol>

<br>

## Features

<ul>
  <li>
    <strong>Native TaCZ weapon handling</strong><br>
    Guards use TaCZ's firearm API for projectiles, damage, accuracy, recoil,
    fire modes, reloads, bolt actions, heat, charge behavior, attachments,
    sounds, and synchronization.<br><br>
  </li>
  <li>
    <strong>Automatic sidearms</strong><br>
    Give a guard an eligible non-explosive pistol in its dedicated sidearm
    slot. The guard can draw it when its primary is confirmed dry, when orders
    prohibit its explosive primary, or when a target is too close to use that
    explosive weapon safely. Native-action gates and return hysteresis prevent
    unsafe or rapid weapon swapping, while TaCZ remains responsible for every
    shot and reload.<br><br>
  </li>
  <li>
    <strong>Difficulty-scaled natural guard loadouts</strong><br>
    Eligible naturally generated guards can replace their starting crossbow
    with a loaded TaCZ gun and one complete stack of compatible ammunition.
    Opt-in format-3 datapack entries can also issue an eligible pistol sidearm
    with its own full ammunition stack.
    The defaults progress from pistols on Easy, through pistols and SMGs on
    Normal, to occasional rifles on Hard, with configurable source, chance,
    replacement, and explosive-safety controls. Persistent local caps remain
    accurate when nearby guard chunks are unloaded. Conditional datapack
    entries can respond to dimension, biome, structure, difficulty, spawn
    source, raid state, and guard scoreboard tags.<br><br>
  </li>
  <li>
    <strong>Naturally generated Village Watchtowers</strong><br>
    Rare standalone Watchtowers generate on suitable dry terrain in vanilla
    village-compatible Overworld biomes. Each compact stone-and-oak tower can
    create a difficulty-scaled squad of up to four persistent guards, assign
    role-appropriate TaCZ loadouts with a configurable higher gun chance, and
    place a stocked squad Armory. Towers are datapack-controlled, rotate with
    the terrain, and add no recurring tower scan, ticker, forced chunk, or
    repair system. They use
    village-compatible biomes but are not injected into vanilla village roads
    or building pools.<br><br>
  </li>
  <li>
    <strong>Weapon-specific tactical profiles</strong><br>
    Pistols, SMGs, rifles, shotguns, sniper rifles, machine guns, and launchers
    can use different approach distances, retreat behavior, aim requirements,
    firing ranges, and visibility timing. Exact gun overrides are also
    supported.<br><br>
  </li>
  <li>
    <strong>Guard ammunition inventory</strong><br>
    Every guard receives three persistent slots for compatible TaCZ loose
    ammunition and ammo boxes. Ammunition remains attached to the guard across
    saves, chunk reloads, and normal guard lifecycle events.<br><br>
  </li>
  <li>
    <strong>Ammunition quality-of-life</strong><br>
    Authorized players can crouch-right-click a guard to deposit ammunition.
    Guards can collect compatible nearby drops and request emergency loose
    ammunition from nearby allied guards while preserving donor reserves.
    These systems understand both the configured primary and sidearm.<br><br>
  </li>
  <li>
    <strong>Village Armory storage</strong><br>
    Store TaCZ loose ammunition and ammo boxes in a craftable nine-slot
    ammunition-only container. Village Armories support hoppers, Forge item
    pipes, comparator output, persistent contents, and optional Squad Command
    Ledger association. Low-ammunition guards can automatically locate a
    compatible loaded Armory, reserve its supply, travel to it, and withdraw
    ammunition through bounded, transactional, interruption-safe logistics
    that never force chunks.<br><br>
  </li>
  <li>
    <strong>Reusable ammo-box preservation</strong><br>
    Depleted finite TaCZ ammo boxes are dropped intact when a guard needs to
    reload or receive new supplies, preventing empty boxes from permanently
    blocking the ammunition slots.<br><br>
  </li>
  <li>
    <strong>Friendly-fire and ally protection</strong><br>
    Guards avoid firing through guards, villagers, iron golems, owners, tamed
    or owned entities, scoreboard allies, and additional protected entity
    types supplied by a modpack.<br><br>
  </li>
  <li>
    <strong>Rules of engagement</strong><br>
    Choose normal Guard Villagers targets, hostile mobs only, defensive
    retaliation, or hold fire. Datapacks and configuration can add or exclude
    exact entity types.<br><br>
  </li>
  <li>
    <strong>Per-guard orders</strong><br>
    Specialize individual guards with their own rules of engagement, tactical
    profile, explosive permission, ammunition logistics, combat movement, and
    maximum firing range while preserving server safety limits. Craft a
    reusable Orders Ledger to copy those behavior settings between guards
    without copying their equipment, ammunition, ownership, or patrol data.<br><br>
  </li>
  <li>
    <strong>Datapack-defined guard roles</strong><br>
    Create reusable Rifleman, Marksman, Support, Breacher, Demolitions, or
    modpack-specific behavior profiles that supply defaults beneath a guard's
    explicit orders. Roles can be assigned by natural loadouts, Orders
    Ledgers, or operator commands, but never grant or move equipment.<br><br>
  </li>
  <li>
    <strong>Squads and temporary field commands</strong><br>
    Use a reusable Squad Command Ledger to organize guards into stable named
    squads and issue Rally, Defend, Hold, Focus Target, Fall Back, or Resume
    Orders commands. Dispatch is limited to nearby loaded members and remains
    subordinate to every rule-of-engagement, ally, ammunition, and explosive
    safety check.<br><br>
  </li>
  <li>
    <strong>Explosive weapon safety</strong><br>
    Launcher users maintain a blast-aware minimum distance, avoid unsafe shots,
    and preserve blocks from guard-owned TaCZ explosions by default while
    keeping TaCZ's native effects against hostile entities. Runtime gun scripts
    that enable or alter explosions receive the same safety handling.<br><br>
  </li>
  <li>
    <strong>Modpack customization</strong><br>
    Fzzy Config controls combat, movement, tactical profiles, rules of
    engagement, ally protection, explosive safety, ammunition logistics,
    squad-command limits, and performance. Reloadable datapack weapon pools
    with contextual entries,
    entity tags, a copy-ready example datapack, migration guidance, and a
    compatibility API provide additional integration points.<br><br>
  </li>
  <li>
    <strong>Performance-conscious behavior</strong><br>
    Visibility checks, path updates, gun-data refreshes, dropped-ammo pickup,
    and sharing use bounded or staggered scheduling. Guards do not continuously
    scan for supplies or pathfind toward dropped ammunition.<br><br>
  </li>
  <li>
    <strong>Operator diagnostics</strong><br>
    <code>/vgg inspect &lt;guard&gt;</code> reports the guard's target, tactical
    profile, firing range, aiming state, safety decision, ammunition state, and
    current TaCZ weapon state. Naturally issued equipment also reports its
    selection result and authenticated provenance. Read-only
    <code>/vgg loadouts validate</code>, <code>inspect</code>, and
    <code>preview</code> commands validate compatibility and show contextual
    pool behavior before rollout. Permission-gated squad registry commands
    help operators inspect and remove abandoned squad records.<br><br>
  </li>
</ul>

<br>

## Requirements

Villager Guard Gunners `1.4` requires:

<ul>
  <li><strong>Minecraft 1.20.1</strong></li>
  <li><strong>Forge 47.4.23</strong></li>
  <li><a href="https://www.curseforge.com/minecraft/mc-mods/guard-villagers"><strong>Guard Villagers 1.20.1-1.6.19</strong></a></li>
  <li><a href="https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero"><strong>Timeless and Classics Zero 1.1.8-hotfix</strong></a></li>
  <li><a href="https://www.curseforge.com/minecraft/mc-mods/fzzy-config"><strong>Fzzy Config 0.7.6 for Minecraft 1.20.1</strong></a></li>
  <li><a href="https://www.curseforge.com/minecraft/mc-mods/kotlin-for-forge"><strong>Kotlin for Forge 4.12.0</strong></a></li>
</ul>

Install the mod and all required dependencies on both the client and server.
This release is deliberately locked to this reviewed version set.

<br>

## Configuration

The server-authoritative Fzzy Config screen includes controls for:

<ul>
  <li>Global guard gun behavior</li>
  <li>Engagement and close-range distances</li>
  <li>Guard movement speeds</li>
  <li>Weapon tactical profiles and exact gun overrides</li>
  <li>Rules of engagement and defensive memory</li>
  <li>Ally and entity-type protection</li>
  <li>Explosive standoff and block preservation</li>
  <li>Dropped-ammunition pickup</li>
  <li>Ammunition sharing, owner matching, and donor reserves</li>
  <li>Bounded performance scheduling</li>
  <li>Persistent per-guard combat and logistics orders</li>
  <li>Squad-command range, duration, radius, and affected-guard limits</li>
  <li>Automatic Armory-resupply thresholds, search bounds, target reserves,
      movement, reservations, cooldowns, and retry timing</li>
  <li>Natural-loadout spawn sources, difficulty chances, local caps, pool IDs,
      unloaded-chunk accounting, crossbow replacement, and explosive
      eligibility</li>
  <li>New Watchtower generation, guard count, tower gun-chance multiplier,
      Armory placement, and initial Armory stock</li>
</ul>

The configuration file is stored at:

`config/villagerguardgunners/guard_gunners.toml`

Server configuration is synchronized to connected clients. Permission level 4
is required to edit synchronized values through Fzzy Config.

<br>

## Operator Commands

`/vgg inspect <guard>` and the read-only loadout diagnostics require permission
level 2.

The command displays the selected guard's current AI decision, movement state,
rules-of-engagement result, tactical profile, firing window, aim state,
friendly-fire safety result, ammunition reserves, reload state, and last TaCZ
shooting result. For natural loadouts it also distinguishes no record, an
authenticated issued gun, and a guard/gun provenance mismatch.

`/vgg loadouts validate [pool]`, `/vgg loadouts inspect <pool> [page]`, and
`/vgg loadouts preview <difficulty> <spawn_source> [pool]` validate and preview
datapack pools without issuing equipment.

`/vgg squads list [page]` and `/vgg squads unregister <squad>` provide
permission-gated maintenance for the persistent squad registry.

`/vgg roles list [page]`, `/vgg roles inspect <role>`,
`/vgg roles assign <guard> <role>`, and `/vgg roles clear <guard>` provide
permission-gated inspection and assignment of datapack-defined roles.

`/vgg watchtowers status`, `/vgg watchtowers groups [page]`,
`/vgg watchtowers inspect <guard>`, and `/vgg watchtowers errors [page]`
provide bounded, read-only diagnostics for Watchtower configuration, group
publication, generated guards, and rejected datapack definitions.

<br>

## Installation

<ol>
  <li>Install Minecraft 1.20.1 with Forge 47.4.23.</li>
  <li>Install Guard Villagers, TaCZ, Fzzy Config, and Kotlin for Forge using the
      versions listed above.</li>
  <li>Place Villager Guard Gunners in the <code>mods</code> folder on both the
      client and server.</li>
  <li>Add any desired TaCZ content packs normally.</li>
  <li>Launch the game. Equip a guard manually, or allow an eligible natural
      guard to receive a configured loadout, and let Guard Villagers select a
      target.</li>
  <li>Explore newly generated village-compatible Overworld biomes to find
      standalone Village Watchtowers and their generated guard squads.</li>
  <li>Optionally equip a pistol sidearm, copy persistent behavior with an
      Orders Ledger, or manage temporary group commands with a Squad Command
      Ledger.</li>
</ol>

<br>

## Documentation and Support

Configuration, sidearms, squads, Village Armories, automatic Armory resupply,
guard roles, natural-loadout datapack pools, rules-of-engagement behavior,
ammunition logistics, Village Watchtower customization, and operator guidance
are documented with the project source.

Please report reproducible problems through the CurseForge project page. Include
the Minecraft, Forge, Guard Villagers, TaCZ, Fzzy Config, Kotlin for Forge, and
Villager Guard Gunners versions, along with the relevant `latest.log` and TaCZ
content-pack list.

<br>

## Disclaimer

Villager Guard Gunners is an unofficial add-on for **Guard Villagers** and
**Timeless and Classics Zero**. It is not affiliated with or endorsed by either
development team. Minecraft, Minecraft Forge, Guard Villagers, TaCZ, Fzzy
Config, Kotlin for Forge, and all third-party content remain the property of
their respective owners.
