# Changelog

All notable Villager Guard Gunners changes are recorded here.

## 1.0.0 - Unreleased

### Added

- Native TaCZ firearm operation for Guard Villagers guards.
- Three persistent guard ammunition slots supporting loose TaCZ ammunition and
  ammo boxes.
- Authorized guard inventory integration, shift-click handling, save/load,
  legacy-slot migration, conversion handling, death drops, and Forge item
  capability exposure.
- Bounded guard movement using TaCZ effective range as a preferred distance and
  the configured maximum engagement range as the hard firing cap.
- Native support for semi-auto, automatic, burst, manual-action, reload,
  inventory-fed, heat, and charged weapon state transitions.
- Pre-fire corridor safety plus direct-hit, explosion, and per-burst-round
  protection for guards, villagers, iron golems, owners, and allies.
- Guard-specific third-person TaCZ pose compatibility for both Guard Villagers
  model variants.
- Server-authoritative Fzzy Config settings for combat, movement, safety, and
  bounded scheduling.
- Permission-level-2 `/vgg inspect <guard>` diagnostics with no recurring debug
  tick or logging cost.
- Plain JUnit contracts, 19 transformed Forge correctness GameTests, a 12-case
  opt-in performance/JFR matrix, reproducible artifacts, and a CI release gate.

### Changed

- Updated the reviewed runtime to Minecraft 1.20.1, Forge 47.4.23, Guard
  Villagers 1.6.19, TaCZ 1.1.8-hotfix, Fzzy Config 0.7.6, and Kotlin for Forge
  4.11.0.
- Delegated projectiles, damage, spread, recoil, fire modes, reloads, heat,
  charge behavior, attachments, sound, and synchronization to TaCZ.

### Removed

- The custom guard bullet, renderer, weapon simulation, firing-mode strategies,
  reload implementation, recoil/inaccuracy logic, and hand-authored rebalance.
- The unused behavior tree, custom melee/movement experiments, gunsmith,
  blueprint, debug, and legacy configuration systems from this add-on.
