# Phase 7: Legacy Removal and Packaging Gate

Phase 7 finishes the transition from the original custom firearm simulation to a focused Guard Villagers/TaCZ integration. Most runtime deletions occurred when the native vertical slice replaced them in Phase 2; this phase audits the complete removal list, cleans the remaining build/bootstrap surface, and makes the boundary executable through regression contracts.

## Removed runtime surface

The project no longer contains:

- the behavior tree, its nodes, selectors, factory, helper API, or goal;
- the custom patrol state and NBT;
- custom reload, melee, movement, recoil, accuracy, fire-mode, or spawn handlers;
- custom gun helpers or general-purpose copied AI utilities;
- the custom bullet entity, renderer, entity registry, or projectile registration;
- the custom ammo slot implementation;
- the broad copied Guard renderer mixin;
- the Fzzy Config implementation, Kotlin for Forge bridge, rebalance registration, or config metadata; or
- commented-out implementations and debug logging from the old firing path.

There is no replacement balance configuration. Damage, projectile behavior, spread, recoil, sounds, magazine/chamber rules, reload duration, fire rate, burst behavior, heat, and synchronization come from TaCZ and the selected gun data. The remaining private timing/range constants are AI scheduling, bounded retry, movement, and friendly-fire safety limits; they do not alter a gun's native combat values.

## Retained ownership

The addon's runtime code now has five narrow responsibilities:

1. register and display the combined guard/ammo menu;
2. provide persistent ammo storage and migrate legacy ammo without item loss;
3. let a Guard drive TaCZ's native `IGunOperator` while making bounded targeting and movement decisions;
4. prevent native guard shots and explosions from harming protected entities; and
5. restore TaCZ's native pose after the two Guard-specific model animations.

`VillagerGuardGunners` registers the supported Fzzy configuration and the combined menu. `GuardMixin` owns the goal attachment, TaCZ operator initialization, ammo capability/persistence/migration lifecycle, death drops, authorized menu replacement, and transient native-burst safety state. The common mixin list contains only `GuardMixin` and `RaiseShieldGoalMixin`; the two model mixins remain in the client-only list.

## Build cleanup

The build retains ForgeGradle 6, MixinGradle, Java 17, Forge, Guard Villagers, TaCZ, Fzzy Config, Kotlin for Forge, and the JUnit test layer. Phase 7 removed the old Fzzy-backed rebalance configuration; the later supported configuration integration deliberately restored current Fzzy/Kotlin repositories and dependencies without restoring any rebalance code. The build also removes the redundant ForgeGradle 5 buildscript dependency, unused Parchment Librarian plugin, Maven publishing example, duplicate resource/JAR blocks, stale template commentary, and timestamped manifest entry. Archive ordering and timestamps are normalized for stable artifacts.

The metadata is reduced to the mod declaration plus exact reviewed dependencies for Minecraft 1.20.1, Forge 47.4.23, Guard Villagers 1.20.1-1.6.19, TaCZ 1.1.8-hotfix, Fzzy Config 0.7.6, and Kotlin for Forge 4.11.0.

## Regression and packaging gates

`PhaseSevenCleanupContractTest` enumerates every removed legacy path and rejects the old simulation/rebalance type names and mutation calls, build dependency regressions, broad mixin registration, unused bootstrap helpers, and production logging. This deliberately makes a future reintroduction require an explicit architectural decision instead of arriving accidentally.

Final verification is:

1. `./gradlew clean test build`
2. inspect the clean JAR for its exact class/resource/dependency surface and refmap;
3. verify the JAR contains no custom projectile/entity registration, Fzzy, Kotlin, or obsolete class;
4. `./gradlew runGameTestServer` to execute the integration suite and prove dedicated-server classloading remains client-safe.
