# Villager Guard Gunners 1.4

Naturally generated armed Village Watchtowers for Minecraft 1.20.1 Forge.

<br>

## Added in 1.4

<ul>
  <li>
    <strong>Naturally generated Village Watchtowers</strong><br>
    Rare standalone 9 x 14 x 9 stone-and-oak Watchtowers can now generate on
    suitable dry terrain in vanilla village-compatible Overworld biomes. The
    structure supports four persisted rotations, a lower guardroom, internal
    ladder, observation deck, railings, and roof. Watchtowers are independent
    structures and are not injected into vanilla village roads or building
    pools.<br><br>
  </li>
  <li>
    <strong>Difficulty-scaled tower squads</strong><br>
    New towers can create up to four persistent Guard Villagers. Easy favors
    small rifleman posts, Normal adds Marksman and Support combinations, and
    Hard can add Support, Breacher, and Demolitions positions. Peaceful creates
    no guards, and the configurable per-post ceiling can restrict the result
    from zero through four.<br><br>
  </li>
  <li>
    <strong>Role-appropriate generated loadouts</strong><br>
    Tower members use the existing native TaCZ issuance transaction with
    dedicated Rifleman, Marksman, Support, Breacher, and Demolitions pools.
    A configurable 1.5x tower-only multiplier raises their normal
    difficulty-based gun chance to 52.5% on Easy, 75% on Normal, and 97.5% on
    Hard by default. Nearby caps, inventory capacity, source controls, pool
    conditions, TaCZ compatibility, and global explosive restrictions remain
    authoritative.<br><br>
  </li>
  <li>
    <strong>Generated squad Armories</strong><br>
    Each enabled tower places a squad-locked Village Armory with deterministic
    datapack-defined ammunition for the built-in weapon classes. Generated
    Armories retain the existing storage, comparator, automation, locking, and
    automatic guard-resupply behavior.<br><br>
  </li>
  <li>
    <strong>Watchtower configuration</strong><br>
    Fzzy Config now provides server-authoritative controls for new Watchtower
    starts, guard spawning, the 0–4 guard ceiling, Armory placement, and
    initial Armory stock, plus a 0–3 tower gun-chance multiplier. These are
    one-way restrictions for newly processed structures and never retrofit
    completed towers.<br><br>
  </li>
  <li>
    <strong>Watchtower diagnostics</strong><br>
    Added permission-level-2 <code>/vgg watchtowers status</code>,
    <code>groups</code>, <code>inspect</code>, and <code>errors</code>
    commands. They expose bounded read-only configuration, group, generated
    guard, and rejected-datapack information without locating structures or
    loading chunks.<br><br>
  </li>
</ul>

<br>

## Modpack Support

<ul>
  <li>
    Watchtower biome selection, spacing, structure settings, physical
    template, guard groups, role-specific weapon pools, and initial Armory
    stock use standard namespaced datapack resources.<br><br>
  </li>
  <li>
    Datapack definitions remain authoritative. Server configuration can
    suppress new generation or marker actions but cannot silently add a
    structure back after a datapack removes it.<br><br>
  </li>
  <li>
    The add-on does not modify vanilla or third-party village jigsaw pools and
    performs no nearby-village, road, outpost, or structure search.<br><br>
  </li>
</ul>

<br>

## Improved in 1.4

<ul>
  <li>
    Placement uses a fixed nine-point terrain check and rejects water, frozen
    unsupported terrain, slopes greater than two blocks, and locations where
    the complete tower would exceed build height.<br><br>
  </li>
  <li>
    Generated guard and Armory integration is deferred to persisted one-shot
    server tasks. World-generation workers do not perform live entity scans,
    TaCZ definition work, or arbitrary inventory transactions.<br><br>
  </li>
  <li>
    Nearby natural-gunner counting now stops as soon as the configured cap is
    reached across loaded guards and the unloaded persistent index.<br><br>
  </li>
  <li>
    Completed towers add no manager, recurring scan, block-entity ticker,
    forced chunk, repair pass, or retro-generation system.<br><br>
  </li>
</ul>

<br>

## Fixed in 1.4

<ul>
  <li>
    The generated Armory now sits against the interior wall instead of
    blocking the Watchtower's central doorway aisle. The corrected placement
    is preserved under all four tower rotations.<br><br>
  </li>
  <li>
    Generated guard handoffs now retain their pending assignment and source
    until the queued server task confirms the same guard is still live.<br><br>
  </li>
  <li>
    Peaceful, guard-disabled, zero-occupant, and failed-spawn towers no longer
    leave an Armory locked to an unavailable squad. The Armory registers its
    deterministic squad once or safely becomes public if registry capacity is
    unavailable.<br><br>
  </li>
  <li>
    Removed guards are not respawned, manual squad reassignment is not
    reverted, broken Armories are not rebuilt, and generated loot is not
    rerolled when a completed structure piece is revisited.<br><br>
  </li>
  <li>
    Malformed and future-version Watchtower records remain preserved and
    inactive instead of being executed, normalized, or erased.<br><br>
  </li>
  <li>
    Structure pieces with missing or corrupt persisted rotations now remain
    disabled instead of placing in an incorrect default orientation.<br><br>
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

Install the mod and every dependency on both the client and server. Existing
worlds remain supported; Watchtowers generate only in newly generated chunks.
