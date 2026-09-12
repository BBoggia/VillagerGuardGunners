# Guard walking detours

Guards can retry an incomplete walking path to a nearby destination on another
level. This helps with Watchtower stairs: a downstairs work position can be
close in a straight line while the actual walk around the deck and down the
stairs exceeds the native route allowance.

The retry changes the path request, not the guard's follow-range attribute.
Detection and engagement distances keep their existing settings.

## Scope and limits

The retry runs on the server for living, grounded guards using ground
navigation. It requires one destination, two to eight blocks above or below
the guard, within a straight-line distance of 24 blocks. The destination's
chunk must be loaded. A complete normal path is used immediately.

If the normal path is missing or incomplete, one additional search can use up
to twice the current follow range, capped at 40 blocks of route length. Follow
ranges below four blocks or at least 40 blocks receive no retry. The additional
search keeps the original search region and native node-visit limit. It does
not expand the chunk area requested by normal navigation.

Each guard can attempt this additional search once every 20 game ticks, shared
across destinations. Changing destinations does not reset that budget. If the
additional search cannot reach the destination, the caller receives the
original result. An active partial path is temporarily excluded from the
native planning cache, then restored; the caller still decides whether to
follow the returned path.

The retry excludes combat targets, active gun-combat goals, owned tactical
paths, screened combat approaches, casualty transport participants, passengers,
and guards carrying passengers. Those movement systems retain their existing
planning and safety checks. Other entity types and explicit path requests
with their own route allowance keep their existing behavior.

## Implementation

[GuardDetourPathNavigationMixin](../src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardDetourPathNavigationMixin.java)
hooks the ordinary native path request after it produces a result. Its retry
calls the explicit-range overload, which avoids recursion.
[GuardDetourPathPolicy](../src/main/java/com/gamergaming/villagerguardgunners/ai/navigation/GuardDetourPathPolicy.java)
contains the distance, range, search-region, and retry-interval limits.

Minecraft constructs the search region from route range plus padding. The
retry reduces that padding by the amount added to route range, preserving
the original integer region radius. For the default range of 20 and padding
of eight, the radius remains 28: the retry uses range 40 and padding −12.
The search can therefore consider a longer walk inside the same nearby area.

The [Plains Watchtower preview guide](watchtower-preview.md) records packaged
Forge movement trials. The templates remain separate manual-placement
previews; this movement change does not add them to natural village pools.
