# Natural Loadout Format 3: Sidearms

Format 3 is an opt-in extension of format 2. It keeps every conditional field
and adds one optional `sidearm` TaCZ gun ID per entry:

```json
{
  "format": 3,
  "entries": [
    {
      "gun": "tacz:m4a1",
      "sidearm": "tacz:glock_17",
      "weight": 1,
      "conditions": {
        "difficulties": ["hard"]
      }
    }
  ]
}
```

The root still accepts only `format` and `entries`. A format-3 entry accepts
only `gun`, `sidearm`, `weight`, and `conditions`. `sidearm` is optional, but
when present it must be a canonical namespaced ID. Formats 1 and 2 reject the
field, so an accidental version mismatch cannot silently change a loadout.

Format 3 normalizes duplicate identity as primary gun, optional sidearm, and
conditions. Two entries may use the same primary with different sidearms, but
an exact duplicate of all three parts is rejected even when its weight differs.

## Eligibility and issuance

The primary follows the existing natural-loadout explosive setting. A natural
sidearm is stricter: it must resolve to a complete single TaCZ gun, use physical
loose ammunition, pass native ammunition compatibility, match the reviewed
sidearm type contract (a pistol in the current release), and resolve as
non-explosive against the spawned guard. Exact-ID or type expansion is not
inferred from a gun pack.

Issuance supplies one complete compatible ammunition stack for the primary and
one complete compatible stack for the sidearm. This remains true when both
weapons use the same caliber; each requested stack must fit in the guard's
three dedicated ammo slots.

Before changing the guard, the add-on preflights both stacks against a scratch
copy of the inventory. The real commit covers ammunition, the private
configured-sidearm slot, the primary hand, and natural provenance. A failure
restores the prior state and never displaces a sidearm another player or mod
already configured. A failed one-time natural attempt is not retried.

Only the primary carries natural issuance provenance and counts toward the
nearby natural-gunner cap. The authentication helper resolves the primary role
rather than assuming it is in the main hand, so the guard remains authentic
while its naturally issued sidearm is active and the primary is safely stowed.

## Compatibility and diagnostics

`/vgg loadouts validate`, `inspect`, and `preview` display the sidearm ID,
weapon compatibility, eligibility result, ammunition, and explosive state.
Context-free validation marks a sidearm's runtime explosive script as
context-dependent; live issuance always reevaluates it against the actual
guard and fails closed.

Formats 1 and 2 keep their existing parsing, weighting, selection, and issuance
behavior. The bundled Easy, Normal, and Hard pools remain format 1 and issue no
natural sidearms. Modpack authors must deliberately select a format-3 pool to
enable this feature.
