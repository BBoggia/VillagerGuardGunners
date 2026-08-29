# Villager Guard Gunners 1.1

Feature update for Minecraft 1.20.1 Forge.

## Added in 1.1

<ul>
  <li>
    <strong>Guard-inventory combat status</strong><br>
    A color-coded, hoverable status explains aiming, visibility, range,
    friendly-lane, movement, reload, action, cooldown, and dry-ammunition
    states.<br><br>
  </li>
  <li>
    <strong>Per-guard orders</strong><br>
    Individual guards can override engagement policy, tactical profile,
    explosive permission, ammunition logistics, combat movement, and maximum
    firing range without bypassing server safety limits.<br><br>
  </li>
  <li>
    <strong>Orders Ledger</strong><br>
    A reusable Orders Ledger safely copies all seven behavior orders between
    guards without transferring equipment, ammunition, ownership, or patrol
    state.<br><br>
  </li>
  <li>
    <strong>Difficulty-scaled natural guard loadouts</strong><br>
    Eligible guards can receive a loaded TaCZ gun and one full compatible ammunition stack.
    Easy, Normal, and Hard use conservative built-in pools, while datapacks can
    replace the weighted gun pools for modpacks.<br><br>
  </li>
  <li>
    <strong>Natural-loadout controls and diagnostics</strong><br>
    Configure eligible spawn sources, difficulty chances, nearby caps,
    crossbow replacement, explosives, and datapack pool IDs. Issued guard/gun
    provenance and selection results are available through
    <code>/vgg inspect &lt;guard&gt;</code>.<br><br>
  </li>
</ul>

<br>

## Improved in 1.1

<ul>
  <li>
    Natural-gunner caps now remain accurate across unloaded chunks without a
    recurring entity scan.<br><br>
  </li>
  <li>
    Runtime gun scripts that enable or alter explosions now receive the same
    explosive-order, blast-standoff, and shot-safety handling as guns with a
    base explosion definition.<br><br>
  </li>
  <li>
    Unavailable TaCZ gun IDs selected from datapack loadout pools now produce a
    deduplicated actionable warning after each reload.<br><br>
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
