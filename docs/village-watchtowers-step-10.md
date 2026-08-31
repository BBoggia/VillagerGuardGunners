# Village Watchtowers Step 10: registry and datapack skeleton

Step 10 introduces the smallest complete Minecraft 1.20.1 structure registry
surface without enabling generation. The server can decode and bind every
built-in watchtower reference now, while two independent gates keep the feature
dormant until Step 11 supplies the first reviewed template and terrain logic.

## Registered identities

| Registry or resource | ID |
| --- | --- |
| Structure type | `villagerguardgunners:watchtower` |
| Structure | `villagerguardgunners:watchtower` |
| Structure set | `villagerguardgunners:watchtowers` |
| Allowed-biome tag | `#villagerguardgunners:has_structure/watchtower` |

`ModStructures` registers exactly one `StructureType` through Forge's mod event
bus. `VillageWatchtowerStructure` uses Minecraft's standard
`Structure.simpleCodec`, so biome holders, spawn overrides, generation step,
and terrain adaptation remain ordinary datapack-owned structure settings.

## Dormant generation contract

The built-in allowed-biome tag is intentionally empty and
`findGenerationPoint` returns `Optional.empty()`. Either condition independently
prevents a valid start. This protects worlds if a modpack references the type
or structure set before the later template is present.

Step 10 does not include a structure template, template pool, processor list,
loot table, data marker handler, entity creation path, terrain query, or chunk
access. It adds no event listener, tick handler, world scan, entity scan,
scheduled task, path request, or chunk ticket.

The built-in structure set reserves a deterministic random-spread placement of
40 chunks with 12 chunks separation, linear spread, weight 1, and salt
`17290527`. Those settings cannot produce a structure while both dormant gates
remain closed, but they are decoded during server datapack startup and provide
the stable placement identity that Step 11 will activate.

## Datapack behavior

The resources follow vanilla dynamic-registry paths. A datapack can replace the
structure settings, structure set, or biome tag through normal priority rules.
The Java codec contains no copied biome list or placement values.

During Step 10, adding biomes through a third-party datapack still cannot create
a tower because the Java generation point remains empty. This is intentional;
activation requires the Step 11 code and template together. Removing all
resources leaves existing worlds unaffected because no Step 10 start can be
valid.

## Validation

Automated contracts require the single bootstrap registration, codec, canonical
resource IDs, exact settings keys, valid spacing/separation relation, empty
biome tag, empty generation point, absence of placement/entity/chunk logic, and
the complete packaged-JAR surface. A Forge GameTest server startup is the final
Step 10 check that the dynamic registries bind the custom codec and all three
JSON resources without a datapack error.

The completed Step 10 checkpoint passes 393 plain JUnit/source/artifact tests
and all 87 required Forge correctness GameTests. The server loaded the custom
structure type, structure, structure set, and empty biome tag without a dynamic
registry error. The exact development artifact is
`villagerguardgunners-1.4-dev.jar` with SHA-256
`f4d01c87387d637262537051b8155e1e289859c55c32bed94efe700b3b84268b`.
