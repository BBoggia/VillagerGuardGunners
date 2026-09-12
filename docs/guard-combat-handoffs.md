# Guard combat handoffs

The gun goal owns movement during gun combat and brief gaps backed by recent
observations. Clearing the current target does not by itself authorize a supply
trip, automatic duty movement, or a second command movement owner. Direct sight
and the nearby threat scan both contribute expiring evidence; neither can retain
an engagement indefinitely without new evidence.

## Commands and native actions

While combat continues, the gun goal applies positional commands through its
command adapter. HOLD, RALLY, FALL_BACK, and the return portion of DEFEND take
precedence over tactical withdrawal. Once combat and sidearm restoration finish,
the ordinary command goal can resume. Revoked, expired, and cross-dimension
commands still pass through the existing command validation.

Combat and positional-command adapters share one transient path budget per
guard. Replacing a command or changing goals cannot reset the deadline. The weak
budget registry stores no guard references in its values and writes no save data.

Sidearm restoration retains TaCZ's native safety checks. A pending draw, reload,
bolt, shot, melee action, or burst can delay the stack transaction. Disablement
stops movement and trigger work immediately while allowing restoration to wait
for a safe native state. Native callback exceptions suspend weapon actions;
they do not authorize a swap or offensive movement.

## Medical and lifecycle boundaries

Downing, transport, medical retreat, and unavailable equipment access prevent
combat from starting and interrupt an active combat tick. The positional-command
goal also yields to casualty, transport, and medical retreat states. Existing
medical and transport leases remain responsible for their own paths and supplies.

Goal stop clears navigation, tactical observations, cached support, and firing
permission. Capability invalidation and the server's entity-leave event invoke
the same cleanup, including dimension transfers that do not invalidate capabilities
at the expected boundary. A reloaded or transferred guard starts with fresh
transient combat state; saved orders, inventory ownership, and sidearm recovery
records retain their existing formats.

The equipment-service and automatic-duty gates remain closed while combat owns
the guard or recent observations justify continuation. They reopen after combat
ends and native actions settle. Existing armory, quartermaster, and duty eligibility
checks still decide whether any particular service or assignment can start.

## Inspection

`/vgg inspect` includes the running movement-goal names, retained combat ownership,
and the last interruption reason. Tactical intent, readiness, threat assessment,
and route status explain the underlying decision. This snapshot is collected only
for inspection; ordinary play does not emit a per-tick transition log.


Removing a gun does not bypass a pending native reload or bolt action. Duty and
equipment service remain gated until the synchronized action state is idle,
including when the main hand is empty or holds a non-gun item.
