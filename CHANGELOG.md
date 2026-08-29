# Changelog

All notable Villager Guard Gunners changes are recorded here.

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

### Changed

- Made nearby natural-gunner caps remain accurate across unloaded chunks
  without adding a recurring entity scan.
- Matched TaCZ runtime script-only explosion behavior for loadout filtering,
  per-guard orders, blast standoff, and shot-time safety.
- Added deduplicated warnings for unavailable TaCZ gun IDs selected from
  datapack loadout pools.
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
- Plain JUnit contracts, 44 transformed Forge correctness GameTests, a 14-case
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
