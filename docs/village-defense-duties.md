# Village Defense Duties

Village Defense Buildings can organize nearby loaded guards around saved duty
positions. The system reacts to events that already occur around the village;
it does not search unloaded chunks or create a separate village population
manager.

## Readiness

Each connected group of Village Defense Buildings keeps one readiness state:

- **Peace** uses routine sentry and training shifts.
- **Watch** begins when a nearby bell rings and opens ranged overwatch
  positions.
- **Alert** begins when a guard confirms a hostile target or a defended entity
  is attacked. Muster positions remain staffed until the threat ends.
- **Siege** remains active while the village has an active raid.
- **Recovery** keeps additional guards ready for a short period before the
  network returns to Peace.

The Watch, Alert, and Recovery durations are synchronized server settings.
Readiness is saved with the world, while active facts such as a raid are
rechecked before a timed state can relax.

## Automatic Staffing

When `villageDefense.automaticDuties` is enabled, the server assigns nearby
loaded guards to open positions in their defense network. Ranged-only slots
prefer guards carrying a TaCZ gun or crossbow. Assignment is limited by fixed
guard, position, and travel-distance caps.

An assigned guard travels to its position only while combat, a field command,
following, Armory resupply, and other higher-priority behavior do not need its
movement. Disabling automatic duties releases the temporary assignments but
does not delete buildings, readiness, Guard Orders, or patrol routes.

The server validates a position before staffing it. A blocked, flooded,
unsupported, or out-of-bounds position remains vacant while its chunk is
loaded. The position is checked again periodically and returns to normal use
after the obstruction is repaired. Unloaded positions remain saved without
loading their chunks.

## Squad Patrol Routes

A bound Squad Command Ledger can switch to Patrol Route Editor mode:

1. Use the ledger on an open block face to add a waypoint.
2. Add at least two waypoints to make the route ready.
3. Crouch-use the ledger on a block to remove the last waypoint.
4. Use the ledger in the air to inspect the current route.

Waypoints must be loaded, within the world border, close enough to the player,
and no more than 256 blocks from the previous point. A route can contain up to
64 waypoints in one dimension.

Patrol routes take precedence over automatic defense posts, but combat and
higher-priority squad commands still interrupt them. A guard pauses at each
waypoint and skips one after the configured number of failed path attempts. If
every waypoint chunk unloads, the route yields movement without loading those
chunks; the guard can return to a loaded defense post or ordinary Guard
Villagers behavior until part of the route is loaded again.

## Pack-Authored Positions

Village-building datapacks can provide up to four duty positions for a custom
building definition. Each position includes a unique key, rotation-aware local
offset, horizontal facing, duty type, and either an `any` or `ranged` weapon
requirement. See the copyable example in
`docs/examples/village-buildings-datapack` for the supported format.

## Operator Diagnostics

Permission-level-2 operators can use `/vgg defense status`, `nearest`,
`inspect`, `networks`, and `events` to review readiness and staffing without
locating structures or loading chunks. Squad routes can be reviewed with
`/vgg squads routes`.
