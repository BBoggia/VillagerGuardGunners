# Villager Guard Gunners 1.2

Sidearms, squads, field commands, Village Armories, and expanded natural
loadouts for Minecraft 1.20.1 Forge.

<br>

## Added in 1.2

<ul>
  <li>
    <strong>Automatic sidearms</strong><br>
    Guards now have a dedicated slot for an eligible non-explosive pistol. A
    guard can switch to it when its primary is confirmed dry, when its orders
    prohibit an explosive primary, or when a target is inside that weapon's
    safe blast distance. It returns to the primary through bounded native-state
    checks and hysteresis.<br><br>
  </li>
  <li>
    <strong>Sidearm-aware ammunition logistics</strong><br>
    Manual ammunition handoff, passive dropped-ammo pickup, emergency sharing,
    reserve protection, and operator diagnostics now understand both the
    configured primary and sidearm.<br><br>
  </li>
  <li>
    <strong>Natural-loadout format 3</strong><br>
    Datapack authors can opt individual entries into an eligible pistol
    sidearm. Successful issuance gives the primary and sidearm one full
    compatible ammunition stack each, while invalid definitions or insufficient
    space leave the guard unchanged.<br><br>
  </li>
  <li>
    <strong>Stable guard squads</strong><br>
    A craftable, reusable Squad Command Ledger creates named squads and assigns
    authorized guards without replacing their equipment, ownership, patrol, or
    persistent per-guard orders.<br><br>
  </li>
  <li>
    <strong>Temporary field commands</strong><br>
    Squads can receive Rally, Defend, Hold, Focus Target, Fall Back, and Resume
    Orders commands. Commands affect only bounded nearby loaded members and
    never bypass rules of engagement, ally protection, explosive safety, or
    TaCZ weapon state.<br><br>
  </li>
  <li>
    <strong>Village Armory</strong><br>
    Added a craftable nine-slot ammunition-only storage block with a compact
    screen, hopper and Forge item-pipe support, comparator output, persistent
    contents, safe drops, and optional Squad Command Ledger association.
    Automatic guard travel and withdrawal are not included in 1.2.<br><br>
  </li>
  <li>
    <strong>Squad registry maintenance</strong><br>
    Added permission-gated <code>/vgg squads list</code> and
    <code>/vgg squads unregister</code> commands for inspecting and reclaiming
    abandoned squad metadata.<br><br>
  </li>
</ul>

<br>

## Improved in 1.2

<ul>
  <li>
    Sidearm swaps preserve the complete TaCZ weapon stack, including magazine,
    chamber, attachments, fire mode, durability, and other weapon data.<br><br>
  </li>
  <li>
    Sidearm, squad-command, and Armory behavior is server-authoritative,
    bounded, cache-backed, and event-driven. Commands do not force chunks, and
    Village Armories do not use a block-entity ticker.<br><br>
  </li>
  <li>
    The guard inventory now exposes the authorized sidearm slot and a visible
    Recover Weapons action for contradictory or unreadable stored-weapon
    states.<br><br>
  </li>
</ul>

<br>

## Fixed in 1.2

<ul>
  <li>
    Natural loadout assignment now waits until Guard Villagers finishes its
    native starting-equipment pass, fixing structure guards that tracked
    enemies but spawned without the configured TaCZ weapon.<br><br>
  </li>
  <li>
    Fixed sidearm rollback, save/load, death, conversion, shield, target-change,
    external weapon-replacement, and both-weapons-dry edge cases so failures
    preserve equipment or enter explicit recovery instead of silently losing
    or overwriting a weapon.<br><br>
  </li>
  <li>
    Fixed stale squad commands when membership changes, unsafe Focus Target
    retention, damaged Armory lock persistence, and Armory menus remaining open
    after their block was replaced.<br><br>
  </li>
</ul>

<br>

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
