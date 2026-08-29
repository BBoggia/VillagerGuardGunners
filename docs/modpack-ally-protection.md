# Modpack-Friendly Ally Protection

Villager Guard Gunners uses one server-authoritative ally resolver for the
initial firing corridor, every scheduled native TaCZ burst round, direct-hit
damage cancellation, and guard-owned TaCZ explosions. A modpack therefore
cannot accidentally classify an entity differently at separate safety layers.

## Datapack tag

The preferred static integration is the entity-type tag:

`#villagerguardgunners:guard_allies`

For example, place this at
`data/villagerguardgunners/tags/entity_types/guard_allies.json` in a datapack:

```json
{
  "replace": false,
  "values": [
    "examplemod:village_guard",
    "examplemod:merchant_companion"
  ]
}
```

Using `replace: false` lets several mods and datapacks contribute safely. The
add-on includes the vanilla snow golem as a conservative defender default but
does not guess which third-party entities belong to a village faction.

## Configuration

The synchronized Fzzy Config section `safety.allies` provides:

- `protectOwnedEntities`: protects living entities that implement Minecraft's
  `OwnableEntity` contract and currently have an owner UUID;
- `protectAlliedEntities`: protects Minecraft alliance relationships,
  including a shared scoreboard team;
- `additionalProtectedEntities`: explicit namespaced entity-type IDs to
  protect without a datapack; and
- `excludedEntities`: explicit type IDs that should remain valid targets even
  if a tag or broader classification would otherwise protect them.

Exclusions are evaluated before additions, tags, villagers, iron golems,
ownership, alliances, and compatibility providers. They cannot disable
protection for another Guard Villagers guard or the shooter's owner. This
retains the two non-bypassable safety relationships while allowing packs to
handle unusual hostile villager subclasses or faction-dependent NPC types.

## Resolution order

For a living candidate, the resolver uses this deterministic order:

1. The shooter itself, every Guard Villagers guard, and the shooter's owner.
2. `safety.allies.excludedEntities` (unprotected result).
3. `safety.allies.additionalProtectedEntities`.
4. `#villagerguardgunners:guard_allies`.
5. Vanilla/modded `AbstractVillager` types and iron golems.
6. Owned entities when enabled.
7. Same-team and other Minecraft alliance relationships when enabled.
8. Registered compatibility providers.
9. Unprotected fallback.

Non-living entities are not ally candidates. A provider exception fails closed
for that candidate and appears as `PROVIDER_FAILURE` in on-demand diagnostics;
it does not log from the combat path or crash the server tick.

## Java compatibility provider

Relationships that cannot be represented by an entity-type tag can register a
positive-only provider during common setup:

```java
GuardAllyProtection.registerProvider(
        ResourceLocation.fromNamespaceAndPath("examplemod", "village_faction"),
        (guard, candidate) -> ExampleFactions.areAllies(guard, candidate));
```

Provider IDs must be unique. Providers cannot make an entity hostile, override
an explicit exclusion, or bypass core guard/owner protection. They should be
fast, allocation-free, and side-effect-free: they run only for a living entity
that has already reached a due-shot safety query or damage backstop, but may be
called many times during a large battle.

For a bounded fixture or integration whose lifetime is shorter than the
server, use `registerScopedProvider` and close its `ProviderRegistration` when
finished. Closing is idempotent and removes only that exact registration. This
prevents test or lifecycle-scoped relationships from leaking into later work;
normal common-setup integrations should continue using `registerProvider`.

## Diagnostics and performance

`/vgg inspect <guard>` appends `allyReason` when its target or firing corridor
is protected. Reasons distinguish explicit additions/exclusions, tags,
villagers, golems, ownership, scoreboard teams, vanilla alliances, provider
matches, and provider failures.

Ally classification runs before rules of engagement and cannot be broadened by
an ROE allow entry. Target-side allow/deny tags and restricted firing modes are
documented separately in [rules-of-engagement.md](rules-of-engagement.md).

The normal hot path performs direct type checks, two small set lookups, one tag
membership test, and optional relationship checks. It does not scan registries,
nearby entities, scoreboard entries, datapack files, or the full provider list
unless earlier rules leave the candidate unresolved. Provider registrations
are copied and sorted once during setup, then published as an immutable list.
