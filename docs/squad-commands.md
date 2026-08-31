# Squad Commands

Squad commands are temporary, server-authoritative movement and targeting
instructions. They do not replace Guard Villagers' ownership, follow mode,
patrol position, equipment, or the persistent policy stored in Guard Orders.

## Creating and managing a squad

Craft a Squad Command Ledger shapelessly from a Book and Quill, a Goat Horn,
and an Emerald. The item stacks to one.

1. Give a blank ledger a custom anvil name if desired.
2. Crouch-use it on a guard you are authorized to manage. This creates a stable
   squad and assigns that guard.
3. Crouch-use the bound ledger on other authorized guards to add or remove them.
4. If a guard already belongs to another squad, repeat the authorized
   interaction to confirm replacement.

The squad UUID, not the physical ledger stack, is the identity. Copies and
recovered ledgers can therefore refer to the same squad safely.

Changing or removing a guard's squad membership immediately clears its active
temporary command. A guard therefore cannot continue following instructions
issued by its former squad.

## Issuing commands

Crouch-right-click in the air to cycle the selected mode. Normal right-click in
the air or on a block issues positional modes. Select Focus Target and use the
ledger directly on a living entity. Resume Orders clears the active temporary
command from the loaded members reached by that dispatch.

| Mode | Behavior |
| --- | --- |
| Rally | Move toward the command point while retaining safe TaCZ fire, then clear the command after arriving. |
| Defend | Return inside the configured radius, constrain combat approach to that area, and otherwise retain weapon-specific tactics. |
| Hold | Return to the separate anchor captured for each guard and remain there while firing when permitted. |
| Focus Target | Prioritize the selected loaded entity only when the guard's current rules of engagement and ally protection permit it. |
| Fall Back | Prioritize reaching the command point while retaining immediate, permitted self-defense; hold there until expiry or Resume Orders. |
| Resume Orders | Remove the temporary command so unchanged Guard Villagers follow, patrol, and persistent per-guard behavior resumes. |

## Precedence and safety

Temporary positional commands override ordinary combat movement when needed.
Defend deliberately ignores a permanent patrol-bound movement preference while
inside its command area so the guard can use bounded weapon tactics there.
Commands never override:

- rules of engagement or Hold Fire;
- ally classification and per-round friendly-fire protection;
- explosive permissions or blast standoff;
- TaCZ weapon, ammunition, reload, or cooldown state;
- native TaCZ rate of fire and projectile behavior.

Focus Target uses the same rules-of-engagement classifier as ordinary combat
before changing a target, including for squad guards that do not currently
hold a TaCZ gun. Protected, excluded, dead, removed, unloaded, cross-dimension,
excessively distant, and otherwise denied targets clear the temporary command
without falling back to an unsafe target.

## Lifetime and persistence

Commands contain their issuing player, issue and expiry times, dimension, and
the required position, radius, or target UUID. They persist with the guard so
their absolute game-time expiry remains correct across chunk unload and server
restart. Rally clears on arrival. Other modes clear when invalid or expired, or
when Resume Orders is issued.

Clearing a command does not rewrite the guard's patrol position or follow flag.
The normal Guard Villagers goal scheduler resumes the already stored behavior.

## Performance boundaries

- Squad members are queried only when a player issues a command.
- Only loaded guards within the configured dispatch radius are considered.
- Each dispatch has a configurable cap and an absolute 64-guard hard limit.
- Candidate collection and sorting are independently hard-bounded to 128
  matching loaded guards, preventing pathological entity counts from creating
  an unbounded one-click allocation or sort.
- Active command validation is constant-time per guard and performs no entity
  scan. Focus uses one loaded-entity UUID lookup.
- Positional navigation reuses the existing staggered path-recalculation
  interval.
- Navigation checks loaded chunk presence and never force-loads a destination.
- A 256-block runtime sanity limit rejects corrupted or stale destinations and
  runaway Focus targets.

The relevant server settings are documented in
[configuration.md](configuration.md).

## Registry maintenance

The stable world registry is bounded to 4,096 squads. Permission-level-2
administrators can inspect it with `/vgg squads list [page]` and remove an
abandoned metadata entry with `/vgg squads unregister <uuid>`. Unregistering is
deliberately non-destructive: it does not scan chunks or rewrite guards,
armories, or ledger items. A surviving bound ledger can recover that stable
identity if it is deliberately used again.
