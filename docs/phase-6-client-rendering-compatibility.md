# Phase 6: Client Rendering Compatibility

Phase 6 restores TaCZ's native third-person gun pose after Guard Villagers finishes animating either of its Guard models. It does not replace Guard Villagers' renderer or reproduce TaCZ's pose selection.

## Render-order problem

TaCZ injects its third-person animation at the tail of `HumanoidModel.setupAnim`. Both Guard Villagers models then return to their own more-specific `setupAnim(Guard, ...)` method and apply additional Guard animation:

- `GuardModel` controls shoulder pads, quiver visibility, kicking, raised-weapon movement, and eating;
- `GuardSteveModel` controls kicking, melee movement, and eating.

Those later arm changes can overwrite the TaCZ hold or aim pose. The old addon attempted to solve this by replacing `GuardRenderer.getArmPose`, duplicating Guard Villagers' complete pose method and affecting guards that did not carry a gun. That renderer mixin was removed earlier and is not restored.

## Narrow client mixins

The mixin configuration now places two classes in its client-only list:

- `GuardModelMixin` targets only `GuardModel.setupAnim(Guard, ...)`;
- `GuardSteveModelMixin` targets only `GuardSteveModel.setupAnim(Guard, ...)`.

Each injection runs at the concrete method's tail. Client-only registration is important: neither Guard model nor any other Minecraft client class is referenced by the common/dedicated-server mixin list.

Both mixins pass the completed model parts to `GuardThirdPersonAnimation`. The helper immediately returns during TaCZ's age-zero render path or when the Guard's main-hand item is not an `IGun`. It otherwise delegates to TaCZ's own `InnerThirdPersonManager`, which remains authoritative for:

- the gun display's selected third-person animation;
- hold versus aim pose;
- synchronized native aiming progress;
- pause handling;
- swimming, sleeping, climbing, and fall-flying exclusions; and
- Player Animator compatibility selection.

The addon does not cache client gun displays or reproduce those rules.

## Minigun and custom-transform safety

TaCZ's standard hold/aim poses assign arm rotations and can safely be selected again. Its minigun pose additionally rotates each arm's model position. Because TaCZ has already applied one pass inside `HumanoidModel`, applying that position transform a second time would compound it and distort the model.

Before delegating, the helper stores the current positions of the arms, body, and head. It then lets TaCZ reapply the final pose rotations and restores those already-correct positions. This preserves the first native minigun/custom position transform without allocating a snapshot object or hand-copying TaCZ's animation implementation.

## Other animation states

Walking and crouching keep the leg and base-body work produced by Minecraft and Guard Villagers; the native manager reapplies only the selected gun pose parts. TaCZ deliberately skips its pose while swimming, sleeping, climbing, or fall-flying, so those animations remain untouched. Death rotation is still owned by the living-entity renderer, outside model arm setup.

Reload and bolt state remain fully native. For non-player living entities, TaCZ continues to show the current synchronized hold/aim pose while its gun model and operator state animate the actual reload or bolt action. No addon reload or bolt pose is introduced.

Player Animator's optional third-person system is designed by TaCZ for `AbstractClientPlayer`. A Guard therefore follows TaCZ's vanilla-model fallback whether Player Animator is installed or absent. Phase 6 calls only TaCZ's compatibility manager and never links directly to the optional mod's API.

## Performance boundary

For guards without guns, Phase 6 performs one main-hand item type check and returns. For rendered gun guards, it performs one ordinary TaCZ third-person selection pass using primitive local snapshots only. It adds no world/entity scan, collection, path request, network message, log entry, reflection, or per-frame heap object of its own.

## Verification

Automated source contracts verify both exact Guard model targets, tail injection, client-only registration, the early `IGun` guard, delegation to TaCZ, minigun position preservation, absence of the broad renderer mixin, and absence of direct Player Animator integration or world scans.

Run:

1. `./gradlew test`
2. `./gradlew build`
3. `./gradlew runGameTestServer`
4. `./gradlew runClient`

The dedicated GameTest launch verifies that the client-only mixins do not cause server classloading failures. Client startup verifies that both target mixins apply successfully. Final visual validation should exercise both Guard model configuration choices with idle hold, partial/full aim, reload, bolt, minigun, walking, crouching, eating, swimming, and death, with Player Animator both installed and absent.
