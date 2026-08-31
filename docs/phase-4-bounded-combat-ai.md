# Phase 4: Bounded Combat AI

Phase 4 replaces the fixed-range navigation loop with a small, explicit movement state machine. The gun goal still updates every server tick for aim, native TaCZ state, and trigger timing, while perception and pathfinding remain independently bounded and staggered.

## Movement states

| State | Entry condition | Work performed |
| --- | --- | --- |
| `APPROACH` | Target exceeds the resolved TaCZ effective range, or two visibility samples have failed. | Submit one normal navigation request on the guard's 20-tick path slot. |
| `HOLD` | Target is visible and within range, or a patrolling guard has no immediate safety movement to perform. | No path or strafe request. Navigation is stopped once when entering the state. |
| `RETREAT` | Target is inside the profile's close-range threshold, including while patrolling. | Use vanilla `MoveControl.strafe` to step backward without a path search. |
| `REPOSITION` | A due-shot safety query finds a protected entity in the firing envelope, including while patrolling. | Use a stable left/right vanilla strafe split by entity ID until a clear due-shot check succeeds. |

TaCZ's attachment cache and runtime property hook resolve the weapon's effective range. The result is cached on the controller's existing low-frequency gun-data refresh and bounded to 8–32 blocks for navigation. Missing or malformed third-party data falls back to 16 blocks; it cannot issue an extreme path request or crash the AI loop.

## Work cadence

| Work | Maximum steady cadence |
| --- | ---: |
| Target validity, look control, native state, and trigger timing | Every tick |
| Line-of-sight sample | Every 2 ticks |
| Movement-state decision | Every 5 ticks |
| Path submission while approaching | Every 20 ticks |
| Friendly-fire corridor | Only for an initial due shot or an actually scheduled native burst round |
| Gun definition/property refresh | Every 20 ticks while active |

The first visibility, movement, and path slots are phase-offset by entity ID. Later work preserves that cadence and discards missed intervals after lag instead of accumulating a backlog. Holding, retreating, repositioning, and patrolling submit no path request. Patrol suppresses ordinary approach navigation, but it does not prevent a short safety retreat or lateral friendly-fire reposition.

## Guard Villagers compatibility

The gun goal remains at priority 2: Guard Villagers' eating/run-to-eat survival goals keep their higher priority, while its melee, bow, crossbow, checkpoint, and ordinary movement goals remain lower. Target selectors are not modified.

Guard Villagers' priority-zero `RaiseShieldGoal` recognizes only bows and crossbows as ranged weapons. A targeted mixin makes that one goal yield while a TaCZ `IGun` is actively aimed and releases an already-raised offhand shield, including when Guard Villagers' always-shield option is enabled. Gun-goal startup also lowers an offhand shield that was raised before TaCZ could enter its aiming state, avoiding a circular wait between the two mods. Ordinary sword-and-shield behavior and dry-gun melee/shield fallback remain available when TaCZ is not aiming.

The gun goal also keeps the guard's vanilla aggressive flag clear. Guard Villagers applies a melee arm animation from that flag after TaCZ applies its humanoid gun pose; the target and TaCZ aiming state already carry the necessary combat state.

## Verification

Plain JUnit covers movement priorities, deterministic staggering, lateral split, and effective-range bounds. Source contracts lock the path/strafe budgets and shield integration. A Forge GameTest verifies that a real transformed `RaiseShieldGoal` still activates for an ordinary sword and a non-aiming gun, then raises the shield before confirming an engageable TaCZ gun lowers it and enters native aim without deadlocking.
