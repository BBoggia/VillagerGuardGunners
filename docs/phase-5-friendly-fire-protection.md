# Phase 5: Friendly-Fire Protection

Phase 5 completes the safety boundary around native TaCZ gunfire. The pre-fire check avoids unsafe trigger inputs, while two Forge event backstops prevent a protected entity from taking damage if movement, piercing, a scheduled burst, or an explosion still brings native gunfire into contact with it.

## Shared protection rule

`GuardFriendlyFireSafety.isProtected` delegates to `GuardAllyProtection`, the only ally resolver used before and after a shot. It protects:

- the guard's owner, matched by UUID rather than only a currently resolved entity reference;
- all `AbstractVillager` entities;
- all Guard Villagers guards;
- iron golems; and
- owned vanilla/modded living entities and Minecraft alliance relationships by default;
- entity types in `#villagerguardgunners:guard_allies` or the synchronized explicit addition set; and
- entities accepted by a registered positive-only compatibility provider.

Explicit type exclusions override additions, tags, the broad villager/golem,
owned-entity, alliance, and provider rules. They cannot bypass protection for
another guard or the shooter's owner. This precedence is shared unchanged by
the corridor and both native damage backstops.

Guard Villagers' configurable target blacklist is deliberately not part of this predicate. Refusing to select a mob as an AI target does not make that mob friendly, so hostile entities remain valid gun and explosion victims.

## Pre-fire corridor

When a shot is actually due, the goal performs one living-entity query over the axis-aligned union of the safety envelopes from TaCZ's projectile origin (the guard eye position minus 0.1 blocks) to the target eye position. The envelopes use:

- runtime attachment/script inaccuracy;
- current heat inaccuracy;
- explosive radius; and
- protected-entity movement during a TaCZ-owned burst.

Only protected candidates are returned. Each candidate's real bounding box is checked against three progressive sections: fixed base/burst clearance at the muzzle, full distance-aware spread clearance farther forward, and full blast clearance downrange. A widened section cannot block an entity whose own bounds have not reached that forward section. This removes the former six-block-scale box around an explosive shooter while preserving the direct muzzle line and the complete forward/impact envelope. It also retains the bounding-box coverage that avoids missing the upper or lower part of a tall entity. No block ray trace is issued for any friendly, and the hot path still performs only one entity query per due shot.

A protected target fails closed before the entity query. Extreme or malformed padding continues to fail closed before a potentially oversized query.

## Cache and scheduled-burst behavior

A safe corridor result for a new trigger is reused only within the same server tick and only while the shooter and target endpoints are unchanged. An unsafe result may be retained for two ticks to reduce repeated trigger attempts while a friendly is still crossing. Once TaCZ accepts a burst, every later scheduled round invokes a fresh fail-closed validator from TaCZ's cancellable `GunFireEvent`, before ammunition, heat, projectiles, sound, or recoil are produced. This avoids relying on the previous guard-goal tick when TaCZ executes a round at the next server-tick boundary.

The damage event backstops below make the cache a performance optimization rather than the final safety authority. A protected entity that moves into the line after a cached result is still protected on contact.

## Native direct-hit backstop

`GuardFriendlyFireEvents` listens to TaCZ's cancellable `EntityHurtByGunEvent.Pre` at Forge's lowest normal priority. On the logical server it resolves the guard from the event attacker, with the native projectile owner as a defensive fallback, and cancels the event when the hit entity matches the shared protection rule.

TaCZ fires this event before ignition, headshot multiplication, damage, knockback, direct-hit explosion creation, kill events, and observer hit messages. One handler therefore protects ordinary rounds, every shotgun pellet, and every impact in a piercing projectile without replacing any TaCZ projectile or damage code.

## Native explosion backstop

TaCZ's `ProjectileExplosion` calls Forge's normal explosion detonation hook before it traces exposure or applies damage and knockback. For only that explosion type, Phase 5 uses Minecraft's public `Explosion.getIndirectSourceEntity()` to identify a Guard-owned native projectile, then removes protected entities from the mutable affected-entity list.

No reflection or accessor mixin is required: TaCZ supplies its kinetic projectile as the explosion's direct source, and Minecraft resolves that projectile's owner as the indirect source. Hostile entities remain in the original affected list. By default, the synchronized `safety.preserveBlocksFromGuardExplosions` setting also clears Forge's already-computed mutable block list for a Guard-owned TaCZ explosion. This retains the native explosion, particles, sound, hostile damage, and knockback while preventing guards from dismantling the terrain they defend. Disabling the setting restores TaCZ's normal block effects; player-owned and non-TaCZ explosions are never altered.

The preservation handler performs no block or point-of-interest search. It runs only after TaCZ and Forge have computed an explosion's affected lists and clears the existing block list, so it adds no guard tick cost and avoids building a second village-boundary system.

## Verification

The unit and source-contract suite checks bounding-box corridor geometry, progressive muzzle/spread/blast clearance, conservative burst padding, direct and explosive event wiring, configurable block preservation, cache limits, and the absence of added block ray tracing or reflection.

At the end of Phase 5, eight Forge GameTests verified the existing inventory/native integration and Phase 5 behavior with real loaded Guard Villagers and TaCZ classes. The Phase 5 tests cover the shared owner/villager/guard/golem/team predicate, hostile exclusion, direct-hit cancellation including projectile-owner fallback, selective removal from a TaCZ projectile explosion, enabled/disabled block preservation, and isolation from player-owned TaCZ explosions. Later coverage adds explicit modpack additions/exclusions, non-bypassable guard/owner protection, owned entities, scoreboard alliances, compatibility providers, rules of engagement, a real RPG transition from blocked forward blast clearance to permitted near-shooter side clearance, and the shared TaCZ-native ammunition model and transaction layer. Phase 8, the post-review regressions, Fzzy Config integration, administrator-command coverage, tactical profiles, modpack ally protection, ROE, ammunition quality-of-life work, natural-loadout issuance, contextual selection and diagnostics, persistence and live cap validation, squad and armory hardening, and player-facing combat status expand the current integration suite to 52 tests.

Run:

1. `./gradlew test`
2. `./gradlew build`
3. `./gradlew runGameTestServer`

Manual validation remains appropriate for representative third-party piercing, shotgun, burst, and explosive datapacks, especially when collecting the performance scenarios defined in the Phase 0 baseline.
