# Changelog

This changelog records player-, operator-, pack-author-, and API-visible
changes. Internal implementation history and temporary validation evidence stay
in local development notes.

## Unreleased

### Added

- Server operators can configure guards’ aim error and choose eye-level or
  center-mass targeting, with separate optional settings against players.
  Defaults retain zero added error and eye-level targeting. Player overrides
  start disabled; 2° with center-mass player aim is the recommended starting
  adjustment.
- A universal Village Buildings datapack includes all five building families
  for vanilla villages and all four supported village providers in one ZIP.
  Optional village mods are not required; existing building IDs are preserved
  when replacing the separate packs.

### Changed

- Building integration skips known absent village providers without missing-pool
  warnings. Missing pools in installed providers and unknown custom pools still
  appear in startup diagnostics.

### Fixed

- Guards preserve SPAS-12 ammo when a shot is blocked or canceled.
- Guard shot checks account for ally and block changes made during firing
  events. Scripted bullet properties remain consistent with the checked shot
  even when ammo or heat changes during firing.
- Guard status messages explain unsupported guns and safety-check limits,
  and clear outdated refusals after target, weapon, or aim-setting changes.
- Guards stop firing at creative and spectator players, including when a
  player changes game mode during a burst or another mod assigns the target.

## 1.7 - 2026-09-11

### Added

- Optional all-buildings datapacks bundle the five building families into one
  download per provider: vanilla villages, CTOV, Tidal Towns, Moog's Missing
  Villages, or Sky Villages. Individual family packs remain available.
- Optional regional packs add furnished Barracks, Village Armories, Sentry Posts,
  Watchtowers, and Training Yards, with two layouts per region. Separate packs
  cover vanilla villages, Tidal Towns, Moog's Missing Villages, six CTOV themes,
  and Sky Villages. Sky buildings include railed floating islands.
- Structure authors can generate separate regional preview packs and define
  layouts with their own geometry, palettes, pool weights, lighting, and duties.
- Building datapacks support up to 192 entries and 24 target pools. Pack authors
  can tune `selection_chance` separately from pool weight and opt rigid entries
  into `terrain_safety` screening.
- Added settlement profiles, resource-provider and entrance inspection, and
  traversal-limit diagnostics for operators and pack authors.

### Changed

- Guards with empty or unusable guns seek safer reachable positions. Reloading
  guards make space before enemies reach them. Injured or overwhelmed guards
  can withdraw or hold, avoid hazardous routes, and retry blocked exits.
- Guards keep a usable sidearm active across target changes and brief contact
  gaps when the primary is out of ammo, then restore the primary after combat.
- Guards stop pursuing when their enemy observations and attack evidence expire.
  Operator inspection explains tactical decisions.
- Vanilla regional packs favor redesigned layouts, reduce matching small
  built-in buildings' frequency, and screen some flooded or deeply buried sites.
  Base-mod-only generation keeps its existing settings.
- Guards can find longer noncombat routes between floors in nearby buildings.
- Automatic village patrols support dry, unlit campfire decks and transitions
  across waterlogged top slabs and closed top trapdoors, checking surface height.
- Village discovery supports more than 64 village structure types and shares
  layout work across chunks to reduce delays in large villages.

### Fixed

- Fixed packaged startup crashes involving Recovery Cot name tags and guard
  checkpoint movement.
- Fixed managed village generation suppressing ordinary guards at Guard
  Villagers' default spawn marker. Existing frozen allowances remain unchanged.
- Guards stop advancing when combat requires holding or repositioning, release
  aim during retreat, and preserve horizontal Defend limits after terrain changes.
- Guards retain combat control through brief contact gaps, yield to medical
  interruptions, and clear combat movement after changing dimensions. Removing
  a gun during an action does not start duty or equipment service prematurely.
- Guards remain at valid defense posts instead of returning toward vanilla
  village points of interest. Armory blocks rotate and mirror with templates.
- Quartermaster service stops when the station's job-site claim is released.
- Medics try alternate cot approaches and recheck floor support and passenger
  clearance. Carriers retain their destination and screen remaining routes
  after replanning without rejecting completed segments.
- Automatic patrols recheck support and headroom before and during travel.
  Unchanged terrain surveys and new home observations no longer restart patrols.
- Village membership, home associations, bells, and damage alarms respect local
  elevation. Nearby courtyard homes remain recognized; uncertain observations
  preserve existing home records. Existing guard affiliations remain unchanged.

### Compatibility

- Added settlement and building classifications for the tested village mods,
  including CTOV size families and exclusions for hostile Witch settlements.
  Friendly defense buildings are also excluded from their road, decoration,
  and entity pools.
- Residential demand uses verified bed-bearing fragments where available.
  Better Villages and Lios replacements use the winning resource's metadata;
  unknown replacements do not inherit stale hints.
- Added a version-gated Integrated API piece reader and a targeted correction
  for Integrated Villages 1.3.2's broken village tag on Forge 47.4.10, preserving
  other packs' contributions.

## 1.6 - 2026-09-05

### Added

- Added craftable Recovery Cots and optional casualty transport. Supplied medics
  can carry stabilized allies to nearby cots before continuing treatment. Enable
  `medical.transport` alongside downed guards to try it; it starts off.

- Added optional downed guards, supply-based stabilization and revival, casualty
  status labels, and medic rescue priorities. Enable `medical.downed` to try it;
  it starts off. Disabling it lets existing casualties recover at low health.

- Added opt-in medic duty, finite-supply healing for nearby allied guards, and
  separate medical retreat controls. Medics respect player orders and pause
  treatment when interrupted; supplied guards can resume automatic duties.

- Added the Quartermaster profession and opt-in guard equipment service during
  work hours. Guards can restock their current loadout or maintain a selected
  profile using physical guns, armor, ammo, and medical supplies.
- Added separate guard service permissions, links to nearby Village Armories,
  equipment replacement controls, and supply status in the guard inventory.
  Existing equipment stays protected until replacement is enabled.
- Added datapack equipment profiles and operator commands for inspecting and
  recovering inventories whose service ownership needs reconciliation.

- Added craftable bandages, direct treatment with bandages and drinkable healing
  potions, and a separate three-slot medical pouch in each guard inventory.
  Canceled or ineffective treatment costs no supplies, and consumed potions
  return their bottle to the source slot.
- Added medical pouch packing and recovery on death or conversion, validated
  datapack treatment profiles, and dedicated medical-supply automation access.

- Added a craftable Quartermaster station with 27 stock slots, nine return slots,
  private, squad-creator, and public inventory access, and optional hopper/pipe
  transport. Players can withdraw supplies to equip guards manually.
- Quartermaster stations retain their supplies and access settings when moved
  as packed items, and retry canceled item drops after saving and loading.
- Added optional size-based initial guard generation for new villages. Ordinary
  guards and Village Defense Building garrisons share one allowance; deaths do
  not trigger replacements. Enable `villagePopulation.managedGeneration` to try
  it; it starts off.
- Added persistent counts of known village residents, including unloaded and
  unarmed guards, with `/vgg defense population` diagnostics and a Squad Command
  Ledger summary. Existing villages keep their guards without automatic additions.

- Added saved village records for ordinary villages, with `/vgg villages`
  diagnostics for discovery progress, observed homes, and guard affiliations.
  Village records preserve separate identities when defense networks merge.
- Added optional automatic outdoor village coverage for eligible residents,
  including ordinary villages. Guards rotate destinations, resume after combat,
  and release blocked paths. Enable `villageDefense.automaticCoverage` to try it;
  it starts off.
- Added `/vgg defense coverage` area diagnostics and a local village summary
  when inspecting a route with the Squad Command Ledger.
- Added a Java coverage-planning API with bounded village areas, guard
  eligibility, fair assignment rounds, and exclusive temporary work reservations.
  The optional live coverage layer supplies terrain validation and movement.
- Added persistent village defense readiness. Bells place nearby defense
  networks on Watch, confirmed threats raise an Alert, active raids begin a
  Siege, and quiet villages move through Recovery before returning to Peace.
- Added automatic defense duties for loaded guards. Garrisons rotate through
  sentry, drill, overwatch, and muster positions according to the village's
  current readiness and the weapon each position requires.
- Added saved squad patrol routes. A bound Squad Command Ledger can add,
  inspect, remove, and clear waypoints, and assigned guards follow completed
  routes while ordinary combat and higher-priority commands retain control.
- Added datapack-defined duty positions for Village Defense Buildings, with
  rotation-aware offsets, facing, duty type, and optional ranged-weapon
  requirements.
- Added permission-level-2 `/vgg defense` diagnostics for readiness, defense
  networks, duty positions, staffing, and event activity.
- Added synchronized settings for readiness timing, automatic duties, patrol
  speed, waypoint pauses, and unreachable-waypoint limits.

### Changed

- Recovery Cots provide faster casualty revival and rest. When transport is
  enabled, emergency field revival takes eight seconds followed by twenty
  seconds of rest; cot revival takes two seconds followed by five seconds of
  rest. Both use the same supplies and restore up to two hearts.
- Manual revival follows the same treatment timing, with progress feedback and
  no supply cost when interrupted. Server operators can adjust the durations.

### Fixed

- Medics deliver casualties onto their Recovery Cots and align them with the
  mattress. Resting patients stop their walking animation, keep their cot through
  recovery, and get up beside it.
- Medics can try another cot after a failed route and leave time for emergency
  field care. Broken cots preserve successful revival and time already rested.

- Medic work now respects follow orders even when the guard's owner is offline.

- Fixed medics repeatedly overlooking nearby patients in crowded villages.
- Downed guards immediately release automatic building-duty positions for other
  guards to cover.
- Earlier healing cooldowns no longer block emergency stabilization. Treatment
  profiles with long cooldowns leave time to administer the second revival dose.


- Guards joining a village now wait briefly for Minecraft to finish registering
  them, so early join events do not permanently lose their village affiliation.
- Village discovery now expands an initial population footprint into the full
  referenced village layout when that layout is available.
- Managed guard recovery now rejects copied or incomplete claims, validates saved
  plans against their journal, and blocks legacy spawning when a known managed
  building has lost its binding. Rejected plan changes leave the journal intact.

- Village Armory menu transfers now mark inventory changes for saving and
  update comparators, including partial shift-click withdrawals.
- Village Armories retain their ammo slot arrangement after saving and loading.
- Guards retain empty ammo boxes when another mod cancels their item spawn.
- Village Defense Building activation retries now wait between attempts,
  giving temporary placement and spawn failures time to clear.
- Patrol routes whose waypoints are all unloaded no longer keep control of a
  guard or suppress normal defense-post and Guard Villagers movement. Route
  checks never load waypoint chunks.
- Guards now skip an unloaded or unreachable waypoint after the configured
  number of attempts when another loaded patrol waypoint is available.
- Blocked, flooded, unsupported, or out-of-bounds defense positions are no
  longer staffed while their chunk is loaded. Repaired positions return to
  rotation automatically.

### Improved

- Clarified server settings, medical timing and dependencies, patrol choices,
  target protection, and Quartermaster controls. Nested loadout, tactical, and
  medical settings now use their intended labels and tooltips.
- Added restart notices for Village Defense Building layout changes and live
  previews of cot/field care times. Existing defaults and saved keys are kept.
- The settings editor rejects inverted village guard limits and Armory ammo
  thresholds with instructions for correcting them.

- Reduced repeated building-registry work during village defense updates.

### Compatibility

- Lowered the minimum supported Forge version from 47.4.23 to 47.4.10 while
  retaining support for newer Forge 47.x releases.

## 1.5 - 2026-09-01

### Added

- Added optional Village Defense Buildings for vanilla villages: Barracks,
  public Armories, Sentry Posts, integrated Watchtowers, and Training Yards.
  Each building has biome-aware plains, desert, savanna, snowy, and taiga
  variants.
- Added small guard groups to Barracks, Sentry Posts, integrated Watchtowers,
  and Training Yards. Peaceful villages remain unstaffed, and server settings
  control staffing, guard limits, and the chance that garrison guards receive
  guns.
- Added Training Yards with an open drill court, covered firing line, raised
  targets, reinforced backstop, and one or two Rifleman, Marksman, or Breacher
  guards. The targets are decorative and don't consume ammo.
- Added public Village Armories with configurable ammo-only starting stock.
  Guards can restock from them, and players can use hoppers, pipes,
  comparators, and the Armory inventory.
- Added server settings for each building type, garrisons, gun chances,
  Village Armories, and starting stock. The master switch is off by default.
- Added datapack support and a copyable example for custom village buildings,
  garrisons, roles, loadout pools, and Armory stock.
- Added permission-level-2 `/vgg villagebuildings` commands so operators can
  review the loaded settings, village-pool weights, and reported problems.

### Improved

- Made Sentry Posts twice as common as each of the other Village Defense
  Buildings while leaving vanilla and modded village entries unchanged.
- Invalid village-building datapacks now keep the last working settings. A
  server restart is still required before village-pool changes take effect.
- Completed buildings remember their guards, Armories, and starting stock, so
  revisiting them doesn't replace removed guards or refill supplies.
- Completed buildings don't force chunks, repair themselves, or repeatedly
  scan the world.
- Added an interior route to integrated Village Watchtowers that guards can
  follow between the ready room and upper deck.
- Reworked Sentry Posts with a three-block-wide checkpoint entrance, mixed
  solid and low cover, clearer raised firing lanes, and a dedicated Sentry
  role that stays bound to the post during ordinary combat.
- Reworked integrated Village Watchtowers with a roomier fortified ready room,
  compact interior stairs, biome-matched fence railings, a taller observation
  opening, and a more detailed roof.
  Guards with TaCZ guns or crossbows take deck duty, while melee and unarmed
  guards remain downstairs.

### Fixed

- Fixed fences, iron bars, walls, stairs, doors, and similar blocks not
  connecting correctly after a building changed biome style or rotation.
- Fixed building entrances becoming solid biome blocks and decorative lanterns
  losing their support.
- Fixed interrupted building setup duplicating guards, Village Armories,
  blocks, or starting supplies. Unfinished setup can resume after a restart
  without forcing chunks.
- Fixed failed Village Armory setup leaving behind an unusable block.
- Fixed definitions with a missing building type being treated as Barracks.
  The original legacy entry remains supported.
- Reduced repeated village-building warnings for intentionally empty
  difficulty pools and repeated unavailable-chunk or missing-group outcomes.
- Fixed some village buildings failing to set up their guards or Village
  Armories after generation. Missing or mismatched building templates are now
  rejected before they affect villages.
- Added one warning when a world reaches its generated-squad limit without
  preventing players from creating or recovering squads.

## 1.4 - 2026-08-31

### Added

- Added naturally generated Village Watchtowers in vanilla village-compatible
  Overworld biomes. Each standalone 9 x 14 x 9 tower has a stone guardroom,
  internal ladder, wooden observation deck, railings, and roof.
- Added difficulty-scaled Watchtower groups containing up to four persistent
  Guard Villagers with deterministic post, member, squad, role, and loadout
  identities. Peaceful towers deliberately create no guards.
- Added a generated squad-locked Village Armory with deterministic initial
  ammunition stock covering the built-in rifleman, marksman, support,
  breacher, and demolitions weapon pools.
- Added synchronized Fzzy Config controls for new Watchtower starts, guard
  spawning, the 0–4 guard ceiling, Armory placement, initial Armory stock, and
  a tower-only natural-gun chance multiplier.
- Added permission-level-2 `/vgg watchtowers status`, `groups`, `inspect`, and
  `errors` diagnostics with bounded pagination and no structure location or
  chunk-loading side effects.
- Added standard datapack resources for Watchtower placement, biome selection,
  physical templates, guard groups, role-specific loadout pools, and Armory
  loot so modpacks can replace each layer independently.

### Improved

- Added four persisted rotations and bounded nine-point terrain validation
  that rejects water, frozen unsupported terrain, slopes above two blocks, and
  placements outside the dimension build height.
- Deferred generated guard loadouts and Armory loot to one-shot live-server
  tasks. Generation workers persist only bounded identifiers and never perform
  TaCZ definition work, live-world scans, or arbitrary inventory transactions.
- Persisted the exact generation difficulty for each tower member and applied
  a configurable 1.5x tower-only gun chance while retaining source, nearby-cap,
  explosive, inventory, and TaCZ compatibility rules during issuance.
- Added dedicated Marksman, Support, Breacher, and Demolitions pools and
  expanded Watchtower Armory ammunition without bypassing global explosive
  restrictions.
- Stopped loaded and unloaded natural-gunner population queries immediately
  after the configured nearby cap is proven.
- Kept completed towers passive: there is no tower manager, block-entity
  ticker, recurring scan, forced chunk, repair pass, or retro-generation path.

### Fixed

- Moved the generated Armory against the inside wall so it no longer blocks
  the Watchtower's central doorway aisle in any structure rotation.
- Made queued generated-guard handoffs interruption-safe by retaining pending
  assignment and source data until the live server task revalidates the guard.
- Prevented Peaceful, disabled, zero-occupant, and failed-spawn towers from
  leaving an unusable Armory squad lock. The one-time resolver registers the
  squad or safely makes the Armory public if capacity is unavailable.
- Prevented tower registry exhaustion from creating unmanaged squad
  memberships while reserving capacity for player-created and recovered squads.
- Preserved malformed and future-version Watchtower guard, assignment, and
  generated-Armory records without executing, normalizing, or erasing them.
- Prevented completed towers from respawning removed guards, reverting manual
  squad changes, rebuilding broken Armories, or rerolling Armory contents.
- Disabled pieces with missing or corrupt persisted rotations instead of
  placing them in an incorrect default orientation.

## 1.3-beta1 - 2026-08-31

### Added

- Added automatic Village Armory resupply. Low-ammunition guards can locate a
  compatible loaded Armory, reserve its supply, travel to it, and withdraw
  ammunition through the same transactional TaCZ-native inventory rules used
  by the existing ammunition systems.
- Added a transient loaded-Armory index with deterministic, distance-ordered,
  hard-capped searches that never scan blocks, force chunks, or tick Armory
  block entities.
- Added expiring per-source reservations so multiple guards cannot claim the
  same ammunition. Every transaction revalidates the guard, weapon, active
  primary or sidearm role, Armory access, source item, distance, configuration,
  and current ammunition need immediately before moving an item.
- Added bounded and interruptible logistics movement with eight maximum path
  approaches, progress checks, native-action pauses, cancellation on combat or
  higher-priority orders, and retry backoff for missing or unreachable supply.
- Added synchronized Fzzy Config controls for Armory resupply thresholds,
  target reserves, search limits, movement, request lifetimes, reservations,
  cooldowns, and retry timing.
- Expanded `/vgg inspect <guard>` with the current Armory-resupply gate, need,
  runtime state, selected source, work counters, transfer result, and next
  action without causing an Armory query or path request.
- Added reloadable datapack-defined guard roles that provide reusable defaults
  for all seven per-guard orders without granting or moving equipment.
- Added five optional built-in role definitions: Rifleman, Marksman, Support,
  Breacher, and Demolitions. Existing guards remain unassigned and unchanged.
- Added natural-loadout format 4 role assignment, Orders Ledger format 2 role
  copying, and permission-level-2 `/vgg roles list`, `inspect`, `assign`, and
  `clear` commands. Formats 1 through 3 and version-1 ledgers retain their
  previous behavior.

### Improved

- Role inheritance is validated and resolved once per datapack reload, then
  served through constant-time lookups and revision-cached effective orders.
- Armory resupply remains below combat, squad commands, following, hold and
  patrol restrictions, sidearm recovery, and TaCZ native weapon actions.
- Resupply understands the currently active primary or sidearm, physical loose
  ammunition and finite ammo boxes, attachment-adjusted magazine capacity,
  squad-locked Armories, and partial destination space.
- Added automated coverage for Armory indexing, reservation contention,
  transactional withdrawal, lifecycle cleanup, path interruption, schema-9
  migration, diagnostics, role parsing/inheritance, assignment, migration, and
  item-neutral behavior.
- Expanded the opt-in scalability matrix to 20 workloads, including 100 idle
  Armories, 100 adequately supplied armed guards, and 50 simultaneous scarce
  or unreachable resupply requests.

### Fixed

- Prevented stale loaded-Armory index entries from removing a newer block
  entity registered at the same position.
- Made the role inheritance ceiling deterministic and limited to 16 total
  definitions including the assigned role, independent of datapack order or
  earlier cache population.
- Hardened role publication against malformed documents, missing parents,
  cycles, conflicting inherited values, future formats, excessive parents,
  excessive depth, and oversized reload sets while retaining the last known
  good generation.
- Prevented satisfied or blocked guards from performing Armory candidate,
  inventory-slot, reservation, or path work.
- Expanded the release artifact gate to require the complete role runtime and
  built-in datapack resources while continuing to exclude all GameTest and
  performance-only code from the distributable JAR.

### Beta notes

- This is a beta build. Automated unit, source, compilation, and packaged-JAR
  gates are required before distribution, but the complete manual client,
  dedicated-server, multiplayer, third-party gun-pack, and Armory integration
  matrix is still required before the final 1.3 release.

## 1.2 - 2026-08-30

### Added

- Added the isolated, allocation-free sidearm eligibility and transition
  contracts, including non-bypassable explosive rejection, native-state gates,
  return hysteresis, rollback postconditions, and lossless recovery states.
- Added private versioned sidearm and stowed-primary storage with active switch
  context, read-only preservation of corrupt or future data, normal death-drop
  handling, and lossless zombie and lightning conversion recovery. The storage
  is deliberately excluded from TaCZ's equipment/ammunition capability.
- Added server-authoritative primary/sidearm transactions with complete-stack
  verification, native-action safety gating, exact rollback snapshots,
  persistent lossless recovery, and natural-loadout provenance preservation.
- Added bounded automatic sidearm switching for confirmed-dry primaries,
  prohibited explosive primaries, and explosive standoff violations, with
  native-action deferral, time-and-distance return hysteresis, lifecycle
  cleanup, revision-cached readiness assessment, and version-1 save migration.
- Extended manual ammunition handoff and item-driven dropped-ammo pickup to
  both the effective primary and configured sidearm. Emergency sharing now
  supports an active sidearm while retaining the configured reserve floor for
  every compatible donor weapon, and diagnostics report primary and sidearm
  reserves separately.
- Added opt-in natural-loadout format 3 with explicit pistol sidearms, strict
  non-explosive/native compatibility checks, one full ammo stack per weapon,
  transactional primary/sidearm/ammo/provenance issuance, role-aware nearby-cap
  authentication, and sidearm-aware loadout diagnostics.
- Added an authorized sidearm slot to the Guard Villagers inventory with
  eligibility-aware placement, safe shift-click routing, and active-transaction
  equipment locks while keeping the internal stowed primary private.
- Added schema-3 active-sidearm ownership tokens and integration coverage for
  shield preservation, target replacement, both-weapons-dry behavior, active
  save/death ownership, and player/mod main-hand replacement.
- Stable named guard squads managed through a reusable Squad Command Ledger.
- Temporary Rally, Defend, Hold, Focus Target, Fall Back, and Resume Orders
  with bounded loaded-member dispatch, persistent expiry, deterministic caps,
  and independent per-guard anchors.
- Command-aware guard movement that retains native TaCZ firing, constrains
  Defend navigation, safety-validates Focus Target, and restores unchanged
  Guard Villagers patrol/follow behavior after completion or expiry.
- A craftable Village Armory block with nine ammunition-only slots, a compact
  inventory screen, hopper/pipe-compatible Forge item access, comparator
  output, safe content drops, and optional Squad Command Ledger locking.
- Permission-gated `/vgg squads list` and `/vgg squads unregister` maintenance
  commands for inspecting and safely reclaiming abandoned registry metadata.

### Fixed

- Deferred natural-loadout eligibility until after Guard Villagers completes
  its native one-time sword/crossbow equipment pass, so structure-spawned
  guards no longer reject valid upgrades merely because their main hand was
  still empty during the entity-join event.
- Added an authorized, explicit **Recover Weapons** action for contradictory,
  corrupt, and future-version sidearm records. Recovery keeps the equipped item
  equipped, returns every recognized private weapon through vanilla inventory
  insertion/overflow handling, removes only this mod's internal ownership token,
  and clears the combat lock only after the storage reaches a stable state.
- Prevented malformed or empty configured sidearms from reaching active-token
  generation before their complete-stack TaCZ gun validation.
- Extended natural-loadout issuance rollback across ammunition insertion,
  sidearm configuration, main-hand replacement, and provenance mutation so an
  unexpected runtime failure cannot leave a partially issued loadout.
- Bound every sidearm release-checklist identity to the automated release gate,
  including recovery, lifecycle, ammunition, configuration, and pack-reload
  cases.
- Prevented Forge's modifiable-handler slot assumption from crashing the
  deliberately non-modifiable public sidearm view.
- Prevented another gun placed into the main hand during an active sidearm
  transaction from being mistaken for the original sidearm and overwriting a
  weapon on return.
- Kept combat and positional squad movement suppressed for every contradictory
  recovery layout rather than allowing whichever weapon remained visible to
  continue acting.
- Prevented active sidearm returns from being starved by new shots or
  higher-priority positional squad movement after an engagement ends.
- Prevented guards that had already raised an offhand shield from becoming stuck tracking a target without ever entering TaCZ's native aiming and firing state.
- Preserved damaged and future-version armory locks across saves so fail-closed
  access cannot silently become public after a restart.
- Cleared temporary commands and Focus-owned targets whenever a guard leaves or
  changes squads.
- Applied Focus Target safely to unarmed and conventional Guard Villagers as
  well as TaCZ gunners.
- Invalidated menus that still reference an armory block entity after the block
  is replaced.

### Safety and performance

- Kept every command subordinate to rules of engagement, ally and per-round
  friendly-fire protection, explosive restrictions, ammunition policy, and
  TaCZ's native weapon state.
- Added constant-time active-command validation, one loaded-entity UUID lookup
  for Focus Target, loaded-chunk-only navigation, the existing staggered path
  cadence, and a fail-closed runtime distance limit without recurring scans or
  chunk forcing.
- Hard-bounded squad candidate collection and sorting to 128 loaded members in
  addition to the existing configurable 64-guard mutation cap.
- Kept armories entirely unticked. Their fixed-size inventory rejects
  non-ammunition, malformed squad locks fail closed for guard access, and block
  break, persistence, capability, and comparator updates are event driven.
- Removed redundant armory block-entity packets from ordinary inventory
  mutations and cleaned transient reassignment confirmations on expiry, player
  logout, and server shutdown.
- Doubled the performance-harness warmup while retaining its existing timing
  budgets, preventing first-batch JVM startup work from masquerading as steady-
  state guard cost.
- Anchored invulnerable performance targets inside their prepared firing lanes
  and added post-sample per-guard diagnostics, removing displacement-driven
  participation flakes without weakening any evidence threshold.
- Switched performance captures to JFR's default preset with explicit 5 ms
  execution sampling, retaining CPU attribution while avoiding an intermittent
  JDK 17 recording-finalization race in the broader profile preset.
- Expanded the release performance matrix from 14 to 17 workloads with a
  simultaneous 50-guard dry-primary switch, an exact mixed RPG-standoff split,
  and 100 armed-idle guards carrying configured sidearms.
- Added artifact-bound sidearm evidence for configured loadouts, active-state
  ranges, observed transition timing, exact per-role weapon mapping, per-role
  firing participation, and each guard's final sidearm state while retaining
  every existing performance threshold.
- Compared the 100-guard configured-sidearm idle workload directly with the
  otherwise identical Glock-armed idle workload under a tighter 1.25x latency
  budget, instead of relying on the broader unarmed baseline.
- Kept sidearm performance validation event-driven and cache-backed without
  introducing guard scans, schedulers, worker threads, or pathfinding work.

## 1.1 - 2026-08-29

### Added

- A color-coded, hoverable guard-inventory combat status for quickly diagnosing
  aiming, range, friendly-lane, movement, reload, action, cooldown, and dry-
  ammunition states.
- Persistent per-guard orders for engagement policy, tactical profile,
  explosive permission, ammunition logistics, combat movement, and maximum
  firing range.
- A reusable Orders Ledger that safely copies all seven behavior orders without
  transferring equipment, ammunition, ownership, or patrol state.
- Difficulty-scaled natural guard loadouts with one full compatible ammunition
  stack, reloadable datapack pools, configurable spawn sources and caps, and
  authenticated guard/gun provenance.
- Conditional format-2 loadout entries for dimension, biome, structure,
  difficulty, spawn source, raid-state, and guard-tag contexts, including a
  copy-ready example datapack and lossless format-1 migration guide.
- Read-only `/vgg loadouts validate`, `/vgg loadouts inspect`, and
  `/vgg loadouts preview` administrator diagnostics.

### Changed

- Made nearby natural-gunner caps remain accurate across unloaded chunks
  without adding a recurring entity scan.
- Matched TaCZ runtime script-only explosion behavior for loadout filtering,
  per-guard orders, blast standoff, and shot-time safety.
- Added deduplicated warnings for unavailable TaCZ gun IDs selected from
  datapack loadout pools.
- Bounded datapack pool sizes and contextual selector collections so malformed
  modpack data cannot create unbounded spawn-selection or validation work.
- Reported guard-dependent explosive compatibility separately from actionable
  loadout warnings, reducing false-positive administrator noise.
- Avoided biome and guard-tag reads for conditional pools that do not reference
  those context categories.
- Expanded `/vgg inspect <guard>` with per-guard orders, combat status, and
  natural-loadout selection and provenance details.

## 1.0.0

### Added

- Native TaCZ firearm operation for Guard Villagers guards.
- Three persistent guard ammunition slots supporting loose TaCZ ammunition and
  ammo boxes.
- Authorized guard inventory integration, shift-click handling, save/load,
  legacy-slot migration, conversion handling, death drops, and Forge item
  capability exposure.
- Bounded guard movement using TaCZ effective range as a preferred distance,
  the configured maximum engagement range as the global cap, and optional
  narrower profile-specific firing caps.
- Native support for semi-auto, automatic, burst, manual-action, reload,
  inventory-fed, heat, and charged weapon state transitions.
- Pre-fire corridor safety plus direct-hit, explosion, and per-burst-round
  protection for guards, villagers, iron golems, owners, and allies.
- Configurable block preservation for guard-owned TaCZ projectile explosions,
  enabled by default without replacing native hostile-entity effects.
- Guard-specific third-person TaCZ pose compatibility for both Guard Villagers
  model variants.
- Server-authoritative Fzzy Config settings for combat, movement, safety, and
  bounded scheduling.
- Version-3 Fzzy Config schema for six weapon-specific tactical profiles,
  TaCZ type mappings, exact gun-ID overrides, and bounded explosive standoff.
- Version-4 modpack ally protection with an entity-type datapack tag, explicit
  additions/exclusions, owned-entity and Minecraft-alliance controls, and a
  deterministic positive-only compatibility provider API.
- Version-5 rules of engagement with Guard-target compatibility, hostile-only,
  defensive-retaliation, and hold-fire modes; synchronized target overrides;
  datapack allow/deny tags; target release; and per-round native burst gating.
- Version-6 Fzzy Config schema groundwork for independently switchable passive
  ammunition pickup and emergency guard sharing, with bounded radii, donor
  reserves, and owner-safe defaults.
- Version-7 Fzzy Config schema groundwork for natural guard loadouts, with a
  dedicated master switch, explicit spawn-source eligibility, protective
  crossbow-only replacement, explosive opt-in, a bounded one-time population
  radius, and independent Easy/Normal/Hard chance, cap, and datapack-pool
  settings.
- Strict, reloadable, datapack-defined weighted natural-loadout pools with
  atomic immutable publication, isolated semantic-error reporting, full-pack
  replacement semantics, and conservative built-in pistol/SMG/occasional-rifle
  Easy, Normal, and Hard progressions.
- A shared TaCZ-native ammunition model for fail-soft compatibility, actual
  ammo-box units, creative infinity, dummy/native reserves, attachment-aware
  capacity, open-bolt chamber rules, and simulate-before-commit transactions.
- A cached, color-coded guard-inventory ammunition tooltip and expanded
  `/vgg inspect` ammunition report using TaCZ's combined inventory view.
- A color-coded, hoverable guard-inventory combat status that distinguishes
  idle, aiming, visibility, range, friendly-lane, movement, reload, action,
  charge, cooldown, weapon-error, and dry-ammunition states through one
  change-detected menu data value with no recurring work while the menu is closed.
- Persistent, server-authoritative per-guard orders for rules of engagement,
  tactical profile, explosive permission, ammunition pickup and sharing,
  combat movement, and a narrower maximum range. The expanded inventory uses
  the existing button/data-slot protocol, preserves global safety limits, and
  adds no idle entity scan or custom packet channel.
- A reusable Orders Ledger crafted from a Book and Quill and an Emerald. It
  atomically captures, applies, and deliberately overwrites complete seven-
  order templates without copying equipment, ownership, or other guard state;
  strict versioning and server-side authorization make damaged, future, or
  unauthorized requests fail closed.
- Authorized crouch-right-click ammunition handoff with TaCZ-native caliber
  checks, transactional partial insertion, creative-stack preservation, and
  action-bar feedback without recurring tick work.
- Quick-handoff safeguards for inactive guards and TaCZ dummy/native-infinite
  guns, preventing successful deposits of physical ammunition the gun cannot
  consume.
- Passive dropped-ammunition pickup for active armed guards, using staggered
  item-driven checks, a bounded configurable radius, TaCZ-native caliber and
  reserve validation, transactional partial insertion, vanilla pickup-delay
  and target-UUID protection, and no item-seeking guard pathfinding.
- Recipient-driven emergency sharing after a TaCZ-native reload proves a guard
  is dry, with a bounded nearest-donor query, configurable owner matching and
  donor magazine reserve, loose-ammo-only transactional transfers, a one-
  recipient-magazine request cap, and no continuous balancing or pathfinding.
- Event-driven depleted finite ammo-box preservation: reload and restocking
  actions eject each reusable empty box intact, freeing the three dedicated
  slots without a recurring guard scan.
- Correct open-/closed-bolt capacity reporting, physical-stack diagnostics for
  dummy reserves, and retry-safe exact conversion-drop verification.
- Low-frequency, attachment/script-aware caching of TaCZ gun type, projectile
  and explosion traits, resolved tactical profile, and normalized envelope.
- Profile-specific preferred approach and retreat distances consumed directly
  from the cached tactical envelope with Generalist compatibility fallbacks.
- Profile-specific continuous-visibility and aim-precision requirements used
  by initial-trigger and native burst-continuation gates without per-tick
  profile resolution.
- Profile-specific maximum firing caps and blast-aware explosive minimum
  standoff applied to initial triggers and every scheduled native burst round.
- Permission-level-2 `/vgg inspect <guard>` diagnostics with no recurring debug
  tick or logging cost.
- One-time, source-aware natural guard loadout selection for finalized Guard
  Villagers spawns and player conversions, including a protochunk-safe structure
  handoff, server-thread deferral, difficulty policy, and lazy bounded cap query.
- Fail-closed natural loadout issuance using TaCZ's native gun and ammunition
  builders, physical-reserve and caliber validation, attachment/script-aware
  explosive filtering, one full TaCZ-defined ammunition stack, full-capacity
  simulation, and atomic main-hand/inventory rollback.
- Versioned natural-loadout provenance shared by the issued guard and gun,
  strict reload authentication, stable nearby-cap accounting after reload, and
  stale/tampered/manual-replacement rejection without a duplicate gun-drop path.
- On-demand natural-loadout authentication and provenance in `/vgg inspect`,
  plus an end-to-end save/reload test proving restored guards enforce the live
  nearby population cap without mutating the rejected guard.
- Plain JUnit contracts, 47 transformed Forge correctness GameTests, a 14-case
  opt-in performance/JFR matrix, reproducible artifacts, and a CI release gate.
- A recordable ammunition multiplayer matrix, user-facing logistics and
  troubleshooting guide, dedicated-server bytecode boundary check, and
  aggregate ammunition release-candidate Gradle gate.
- A documented natural-loadout balance baseline and nine-case lifecycle,
  datapack, source, ammunition, explosive, and population-cap release matrix,
  all bound into the deterministic release-candidate evidence gate.

### Changed

- Matched TaCZ's runtime script-only explosion semantics for natural-loadout
  filtering, explosive orders, blast standoff, and per-shot safety refreshes.
- Made nearby natural-gunner caps persist across unloaded chunks through a
  dimension-local chunk-bucketed saved-data index with no recurring tick scan.
- Added one-per-reload warnings for unresolved datapack gun entries and made
  the public CurseForge packaging task require completed manual validation.
- Stabilized the protected-crossing performance fixture with deterministic
  off-corridor dwell windows while retaining moving-friendly crossings.
- Made passive dropped-ammunition pickup respect vanilla `mobGriefing` and
  reject depleted finite boxes before scheduling a nearby-guard query.
- Required a failed TaCZ reload to have a complete, independently usable
  reserve result before delaying sharing or the normal dry/melee fallback.
- Updated the reviewed runtime to Minecraft 1.20.1, Forge 47.4.23, Guard
  Villagers 1.6.19, TaCZ 1.1.8-hotfix, Fzzy Config 0.7.6, and Kotlin for Forge
  4.12.0.
- Delegated projectiles, damage, spread, recoil, fire modes, reloads, heat,
  charge behavior, attachments, sound, and synchronization to TaCZ.
- Hardened native burst cleanup against delayed server-phase execution, removed
  a redundant active-burst corridor query, normalized contradictory tactical
  envelopes, and made type-map alias conflicts deterministic.
- Allowed patrol guards to perform immediate close-range retreats and protected-
  lane repositioning while continuing to suppress ordinary approach paths.
- Strengthened performance-report verification with exact per-scenario guard,
  weapon, workload, config, timing, and evidence contracts, then regenerated
  the complete post-tactical-profile JSON/JFR reference matrix.
- Added deterministic release-candidate manifest and SHA-256 generation that
  binds the verified JAR to an exact, stale-file-free performance evidence set
  while preserving the separate manual compatibility boundary.
- Made contradictory or unreadable tactical windows yield to lower-priority
  Guard Villagers combat without per-tick goal restarts, and made unreadable
  effective-range properties fail closed for range-limited profiles.
- Isolated performance scenarios by discarding their native TaCZ bullets,
  required firing evidence from at least 80% of expected guards, and recorded per-guard
  evidence in each report.
- Kept HOLD_FIRE authoritative for dry or temporarily unreadable guns without
  issuing native reload attempts every tick, while retaining safe target release.
- Replaced uniform maximum-width friendly-fire padding at the muzzle with
  progressive base, spread, and explosive envelopes. Owners and allies can
  stand closer beside or behind a guard without weakening the direct firing
  line or full downrange blast protection.
- Isolated correctness fixtures in a padded generated arena and placed each
  sequential performance workload in a distinct world region.
- Bound performance reports to the exact production JAR, parsed JFR execution
  samples, and added same-run baseline-relative p95/p99 regression budgets.
- Matched Guard Villagers' client health/armor display option in the expanded
  gunner inventory and added reversible scoped ally-provider registrations.
- Finalized the natural-loadout tuning order and non-destructive rollback
  controls without changing TaCZ weapon statistics or adding recurring work.

### Removed

- The custom guard bullet, renderer, weapon simulation, firing-mode strategies,
  reload implementation, recoil/inaccuracy logic, and hand-authored rebalance.
- The unused behavior tree, custom melee/movement experiments, gunsmith,
  blueprint, debug, and legacy configuration systems from this add-on.
