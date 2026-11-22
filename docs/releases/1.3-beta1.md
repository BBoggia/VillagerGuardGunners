# Villager Guard Gunners 1.3 Beta 1

Automatic Village Armory resupply and datapack-defined guard roles for
Minecraft 1.20.1 Forge.

<br>

## Added in 1.3 Beta 1

<ul>
  <li>
    <strong>Automatic Village Armory resupply</strong><br>
    Guards running low on ammunition can locate a compatible loaded Village
    Armory, reserve its supply, travel to it, and withdraw ammunition. The
    complete process is server-authoritative, transactional, interruptible,
    and subordinate to combat, squad commands, follow and patrol behavior,
    sidearm recovery, and TaCZ's native weapon actions.<br><br>
  </li>
  <li>
    <strong>Bounded logistics</strong><br>
    Armory searches use an event-maintained loaded-chunk index with strict
    candidate and inventory-slot limits. Movement has bounded path attempts,
    progress checks, expiring reservations, and retry backoff. The system does
    not scan the world, tick Armory block entities, or force chunks.<br><br>
  </li>
  <li>
    <strong>Datapack-defined guard roles</strong><br>
    Modpack authors can create reusable role definitions that provide defaults
    for all seven per-guard orders. The mod includes optional Rifleman,
    Marksman, Support, Breacher, and Demolitions definitions. Roles change
    behavior only and never grant, remove, or move equipment.<br><br>
  </li>
  <li>
    <strong>Role-aware loadouts and ledgers</strong><br>
    Natural-loadout format 4 can assign a role during the existing atomic
    issuance transaction. Orders Ledger format 2 can copy a role together with
    the seven raw orders, while existing format-1 through format-3 pools and
    version-1 ledgers retain their previous behavior.<br><br>
  </li>
  <li>
    <strong>New operator controls</strong><br>
    Added <code>/vgg roles list</code>, <code>inspect</code>,
    <code>assign</code>, and <code>clear</code>. The existing
    <code>/vgg inspect &lt;guard&gt;</code> command now reports role resolution and
    the live Armory-resupply gate, need, state, source, transfer result, work
    counters, and next action without initiating logistics work.<br><br>
  </li>
  <li>
    <strong>Armory-resupply configuration</strong><br>
    Fzzy Config now exposes bounded controls for enablement, reserve trigger
    and target levels, assessment interval, search radius, candidate limit,
    movement speed, request and reservation lifetimes, cooldown, and retry
    timing.<br><br>
  </li>
</ul>

<br>

## Improved in 1.3 Beta 1

<ul>
  <li>
    Resupply understands the guard's active primary or sidearm, the gun's real
    TaCZ magazine capacity, compatible loose ammunition, finite ammunition
    boxes, inventory space, and public or matching-squad Armory access.<br><br>
  </li>
  <li>
    Role inheritance is resolved once per datapack reload and cached per guard.
    Explicit per-guard orders override role defaults, while global safety,
    rules of engagement, ally protection, explosive restrictions, and squad
    command precedence remain authoritative.<br><br>
  </li>
  <li>
    The performance suite now covers 20 workloads, including 100 idle
    Armories, 100 adequately supplied armed guards, and 50 simultaneous scarce
    or unreachable resupply requests.<br><br>
  </li>
</ul>

<br>

## Fixed in 1.3 Beta 1

<ul>
  <li>
    Fixed stale loaded-Armory cleanup incorrectly removing a newer Armory
    registered at the same position.<br><br>
  </li>
  <li>
    Made the 16-definition role inheritance limit deterministic regardless of
    datapack order or earlier cache population.<br><br>
  </li>
  <li>
    Hardened reservations, lifecycle cleanup, source replacement, feature
    disablement, combat interruption, malformed role data, inheritance cycles,
    missing parents, and conflicting inherited values so they fail safely
    without item loss, duplication, stuck paths, or partial role publication.<br><br>
  </li>
</ul>

<br>

## Beta Notice

This is a beta build intended for testing. Back up worlds before installing it
and report reproducible issues with the relevant <code>latest.log</code>, mod
versions, and TaCZ content-pack list. The planned squad visualization and
automatic target-coordination features are not included in this build.

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

Install the mod and every dependency on both the client and server. This beta
is intentionally locked to the reviewed versions above.
