# Phase 5: Friendly-Fire Protection

Phase 5 completes the safety boundary around native TaCZ gunfire. The pre-fire check avoids unsafe trigger inputs, while two Forge event backstops prevent a protected entity from taking damage if movement, piercing, a scheduled burst, or an explosion still brings native gunfire into contact with it.

## Shared protection rule

`GuardFriendlyFireSafety.isProtected` is the only ally predicate used before and after a shot. It protects:

- the guard's owner, matched by UUID rather than only a currently resolved entity reference;
- all `AbstractVillager` entities;
- all Guard Villagers guards;
- iron golems; and
- living entities allied to the guard through Minecraft's team relationship.

Guard Villagers' configurable target blacklist is deliberately not part of this predicate. Refusing to select a mob as an AI target does not make that mob friendly, so hostile entities remain valid gun and explosion victims.

## Pre-fire corridor

When a shot is actually due, the goal performs one living-entity query in the axis-aligned box from TaCZ's projectile origin (the guard eye position minus 0.1 blocks) to the target eye position. The box is inflated once for the complete native risk envelope calculated in Phase 3:

- runtime attachment/script inaccuracy;
- current heat inaccuracy;
- explosive radius; and
- protected-entity movement during a TaCZ-owned burst.

Only protected candidates are returned. Each candidate's real bounding box is inflated by the same safety padding and clipped against the shot segment. This replaces the earlier center-point/sphere approximation, which could miss the upper or lower part of a tall entity. No block ray trace is issued for any friendly.

A protected target fails closed before the entity query. Extreme or malformed padding continues to fail closed before a potentially oversized query.

## Cache and scheduled-burst behavior

A safe corridor result for a new trigger is reused only within the same server tick and only while the shooter and target endpoints are unchanged. An unsafe result may be retained for two ticks to reduce repeated trigger attempts while a friendly is still crossing. Once TaCZ accepts a burst, every later scheduled round invokes a fresh fail-closed validator from TaCZ's cancellable `GunFireEvent`, before ammunition, heat, projectiles, sound, or recoil are produced. This avoids relying on the previous guard-goal tick when TaCZ executes a round at the next server-tick boundary.

The damage event backstops below make the cache a performance optimization rather than the final safety authority. A protected entity that moves into the line after a cached result is still protected on contact.

## Native direct-hit backstop

`GuardFriendlyFireEvents` listens to TaCZ's cancellable `EntityHurtByGunEvent.Pre` at Forge's lowest normal priority. On the logical server it resolves the guard from the event attacker, with the native projectile owner as a defensive fallback, and cancels the event when the hit entity matches the shared protection rule.

TaCZ fires this event before ignition, headshot multiplication, damage, knockback, direct-hit explosion creation, kill events, and observer hit messages. One handler therefore protects ordinary rounds, every shotgun pellet, and every impact in a piercing projectile without replacing any TaCZ projectile or damage code.

## Native explosion backstop

TaCZ's `ProjectileExplosion` calls Forge's normal explosion detonation hook before it traces exposure or applies damage and knockback. For only that explosion type, Phase 5 uses Minecraft's public `Explosion.getIndirectSourceEntity()` to identify a Guard-owned native projectile, then removes protected entities from the mutable affected-entity list.

No reflection or accessor mixin is required: TaCZ supplies its kinetic projectile as the explosion's direct source, and Minecraft resolves that projectile's owner as the indirect source. Blocks and hostile entities remain in their original affected lists. Removing protected entities before TaCZ's fifteen-point exposure calculation also avoids that ray-tracing cost for each protected entity in the blast.

## Verification

The unit and source-contract suite checks bounding-box corridor geometry, conservative spread/burst padding, direct and explosive event wiring, cache limits, and the absence of added block ray tracing or reflection.

At the end of Phase 5, eight Forge GameTests verified the existing inventory/native integration and Phase 5 behavior with real loaded Guard Villagers and TaCZ classes. The Phase 5 tests cover the shared owner/villager/guard/golem/team predicate, hostile exclusion, direct-hit cancellation including projectile-owner fallback, and selective removal from a TaCZ projectile explosion. Phase 8, the post-review regressions, Fzzy Config integration, maximum-range regression, and administrator-command coverage expand the current integration suite to 19 tests.

Run:

1. `./gradlew test`
2. `./gradlew build`
3. `./gradlew runGameTestServer`

Manual validation remains appropriate for representative third-party piercing, shotgun, burst, and explosive datapacks, especially when collecting the performance scenarios defined in the Phase 0 baseline.
