# Villager Guard Gunners 1.0.0

Initial public release for Minecraft 1.20.1 Forge.

## Features

<ul>
  <li>
    <strong>Native TaCZ firearm handling</strong><br>
    Guards use TaCZ's own aiming, firing, projectile, reload, manual-action,
    heat, charge, attachment, sound, and synchronization systems.
  </li>
  <li>
    <strong>Difficulty-scaled natural guard loadouts</strong><br>
    Eligible guards can spawn with a loaded gun and one full compatible ammunition stack.
    Built-in Easy, Normal, and Hard pools can be replaced by datapacks, and
    persistent local caps remain accurate across unloaded chunks.
  </li>
  <li>
    <strong>Weapon-specific tactics</strong><br>
    Configurable profiles control preferred distance, retreat behavior, firing
    limits, aim precision, and visibility requirements without changing TaCZ
    weapon statistics.
  </li>
  <li>
    <strong>Ammunition quality-of-life</strong><br>
    Guards receive three persistent ammunition slots and support authorized
    quick deposit, bounded dropped-ammo pickup, emergency sharing, and depleted
    finite ammo-box preservation.
  </li>
  <li>
    <strong>Ally and explosive protection</strong><br>
    Guards avoid unsafe firing lanes and protect guards, villagers, golems,
    owners, owned entities, scoreboard allies, and modpack-defined allies.
    Launchers use blast-aware standoff and optional terrain preservation,
    including when runtime scripts enable or alter explosions.
  </li>
  <li>
    <strong>Rules and per-guard orders</strong><br>
    Configure rules of engagement, retaliation, ammunition logistics, combat
    movement, maximum range, and explosive permission globally or per guard.
    A reusable Orders Ledger safely copies behavior settings without copying
    equipment or ownership.
  </li>
  <li>
    <strong>Modpack and operator support</strong><br>
    Includes server-authoritative Fzzy Config settings, reloadable datapack
    pools and entity tags, compatibility hooks, and
    <code>/vgg inspect &lt;guard&gt;</code> diagnostics.
  </li>
  <li>
    <strong>Performance-conscious implementation</strong><br>
    Combat, navigation, pickup, sharing, metadata, and population work use
    bounded, cached, staggered, or event-driven paths.
  </li>
</ul>

## Required Dependencies

<ul>
  <li>Minecraft 1.20.1</li>
  <li>Forge 47.4.23</li>
  <li>Guard Villagers 1.20.1-1.6.19</li>
  <li>Timeless and Classics Zero 1.1.8-hotfix</li>
  <li>Fzzy Config 0.7.6 for Minecraft 1.20.1</li>
  <li>Kotlin for Forge 4.12.0</li>
</ul>

Install the mod and every dependency on both the client and server. This release
is intentionally locked to the reviewed versions above.
