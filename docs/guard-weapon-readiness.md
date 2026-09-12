# Guard weapon readiness

Gun-equipped guards retain combat MOVE/LOOK ownership while a live target exists
or bounded combat continuation remains,
including when the gun is empty, unreadable, or has no valid firing window. Life
state, casualty transport, medical retreat, equipment recovery, feature disablement,
and rules of engagement keep their existing priority and cleanup rules. A real
non-gun equipment change can release ownership to ordinary Guard Villagers goals.

## Readiness and native retries

`NativeGunController.captureReadiness()` reads the bound weapon and synchronized
native action state. It performs no reserve scan, entity search, property resolution,
or path construction. Its result explains the weapon's movement restriction:

| State | Combat movement |
| --- | --- |
| `READY` | Tactical movement can run. Normal shot cooldown and charging remain native firing concerns. |
| `NEEDS_RELOAD`, `RELOADING`, `CYCLING_ACTION`, `DRAWING` | Use a screened defensive route when danger warrants it; otherwise hold while the weapon becomes usable. |
| `DRY` | Seek a screened defensive route and retry native reload/supply checks on the existing 20-tick cadence. |
| `UNBOUND`, `SUSPENDED`, `INVALID_FIRING_WINDOW` | Hold while normal metadata synchronization or refresh resolves the weapon. |

Readiness is evaluated after the native weapon tick and any sidearm activation,
before issuing tactical movement. A spent final round, failed reload, or new draw
therefore stops an existing offensive path in that tick. Magazine and chamber
rules follow TaCZ's bolt type; an empty magazine can still contain a usable
chambered round. Inventory-fed guns retain TaCZ's native ammo check.

Confirmed exhaustion is separate from the retry deadline. Reaching that deadline
permits another native attempt; it does not make the weapon ready or erase the dry
state. A successful reload start, confirmed usable reserves after a temporary
refusal, successful ammo sharing, a loaded in-place update observed at retry, or a
successful shot can clear exhaustion. Weapon identity or definition replacement
resets old evidence. A reload that starts still blocks pursuit until readiness
recovers. Script/metadata failures retain their bounded refresh and suspension.

Explicit Squad HOLD, RALLY, FALL_BACK, and DEFEND boundary handling runs before
tactical readiness restrictions. A command path is an intentional movement order,
so it can remain active with a dry gun. Ordinary tactical pursuit cannot. [Tactical intent](guard-tactical-intent.md) uses bounded threat observations to
choose defensive movement. [Route screening](guard-tactical-routes.md) permits
escape with an unavailable gun; it does not permit offensive pursuit. A live
target is never cleared to bypass a supply-service gate.

## Sidearms and diagnostics

A backup activated for a dry primary remains selected across live permitted target
changes and brief target gaps with continuing combat evidence. True combat
end, denied targets, disabled features, and invalid backup definitions still settle
through native swap safety. If both weapons are dry, the guard holds and retries
with the active weapon. Stored-primary ammo arriving does not trigger mid-combat
swap oscillation. No storage schema or stack transaction format changes.

Active weapon ammo diagnostics include `readiness`, `offensiveMovementBlocked`,
`dryConfirmed`, and `dryRetryTicks`. The blocked flag describes the weapon gate;
it does not prohibit an explicit command path. The ordinary diagnostic ammo scan
remains an explicit inspection operation and is not used by the combat loop.
