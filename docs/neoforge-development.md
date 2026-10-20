# NeoForge development

This branch targets Minecraft 1.21.1, NeoForge 21.1.233, and Java 21. The
current candidate is `1.9-neoforge-alpha.1`, an alpha test build, not a stable release.
The 1.20.1 Forge implementation remains on `v1.9`.

## Build and dependency contracts

`gradle.properties` defines versions and loader ranges. ModDevGradle replaces
ForgeGradle. The Gradle 8.8 wrapper supports the Java 21 toolchain. Output goes
under `build/neoforge`, so a port build or `clean` does not remove Forge release
artifacts in the parent build directory.

`gradle/neoforge-mods.json` pins the four installed dependency JARs to specific
Modrinth version IDs and published SHA-512 hashes. `verifyPinnedMods` checks
those files and their agreement with the version properties. Kotlin's official
NeoForge Maven module supplies the development classpath; installed testing uses
the published combined Kotlin JAR. TaCZ's embedded Lua libraries are extracted
for compilation only and are not bundled into the add-on.

`META-INF/neoforge.mods.toml` declares the gameplay mod's dependencies and mixin
configuration. Mixins use Java 21 and named Minecraft classes. The old Forge
refmap and reobfuscation tasks do not apply to this build.

## Alpha packaging

`./gradlew prepareNeoForgeAlpha` runs the complete build checks and stages player
files under `build/neoforge/distributions/alpha/<version>/`. The directory contains
only the gameplay JAR, player copy, license, checksums, upload settings, and an
optional Minecraft 1.21.1 universal companion ZIP. The task accepts alpha version
names only. Run the installed QA suites against the exact staged gameplay JAR
before distribution. See [CurseForge upload process](curseforge-upload.md).

## Foundation checks

Use JDK 21 from the repository root:

```sh
./gradlew checkNeoForgeFoundation stageBootstrapMods
```

This resolves and verifies the dependencies, expands production metadata, and
compiles a separate probe in `src/bootstrap`. The probe has its own mod ID,
`vgg_port_bootstrap`, and does not include production code, assets, or mixins.
It checks loaded dependency versions, Java 21, the Guard Villagers entity
registration, and the TaCZ gun item API after a server starts.

The staged normal mod JARs and probe are in `build/neoforge/bootstrap/mods`.
Use them in an isolated, normally installed NeoForge 21.1.233 server with a new
world. Look for `VGG_PORT_BOOTSTRAP_PASS` after startup. Stop the server normally
and retain its log. A successful probe proves that the dependency foundation
loads; it does not certify guard combat or other gameplay.

For development debugging, `runBootstrapServer` loads only the probe, while
`runClient` and `runServer` target the production source set. Installed JAR
testing remains required for compatibility claims.

## Core gun checks

`compileJava` now compiles every production file, and `jar` produces the actual
add-on. No gameplay sources or required mixins are excluded to make the build
pass. Registration, event listeners, payloads, inventories, and serialization
have been adapted where needed to build and load the core gun integration.

The guard exposes six equipment slots plus three reserve slots through
`Capabilities.ItemHandler.ENTITY`. Medical supplies and sidearm storage remain
separate. Entity removal invalidates previously returned inventory views.
ItemStack serialization uses the world registry provider and preserves TaCZ
components. Add-on item metadata uses copied `CUSTOM_DATA` components.

The TaCZ discharge hook validates each native round before ammo consumption.
It uses NeoForge's cancellable event return value and intercepts both typed and
string bullet-property calls. Cancellation preserves ammo. The native TaCZ
scripts still own reloads, chambering, projectile creation, and fire cadence.

Run the focused unit tests with Java 21:

```sh
./gradlew test --tests '*combat.*' --tests '*inventory.*' --tests '*ammunition.*'
```

For installed-server acceptance, build the separate test mod and stage the
normal dependency JARs:

```sh
./gradlew stageCoreQaMods
python3 tools/run_neoforge_core_qa.py --accept-eula --fresh
```

Pass `--accept-eula` only after accepting Minecraft's EULA. The runner installs
NeoForge into `build/neoforge/installed-core-qa`, binds to localhost, and uses a
new test world. `--fresh` resets only that runner's world and expected record;
it refuses an existing runtime without its ownership marker. `--java-home`
can select a different Java 21 installation. The test mod is never included in
the production JAR.

The suite drives real guard goals with published TaCZ and Guard Villagers
JARs. It checks semi-auto and burst fire, target damage, reloading from loose
ammo and a reusable ammo box, dry guns, canceled fire, and exact round
conservation. It also checks malformed reserve slot recovery, item component
copying, capability teardown, and offer identity serialization. Finally, it
stops and restarts the server and verifies the saved gun, loose ammo, and ammo
box. Results and artifact hashes are written under
`build/neoforge/core-qa/results/core`. After retaining results, the isolated runtime
can be deleted to reclaim its libraries and world.

## Combat and command checks

Run the migrated unit, source-contract, and artifact checks with Java 21:

```sh
./gradlew checkNeoForgeFoundation stageCoreQaMods combatTest
python3 tools/run_neoforge_core_qa.py --accept-eula --fresh --suite behavior
```

`combatTest` covers combat, inventory/ammo, aiming settings, roles, orders,
sidearms, squads, and patrol policies. Packaging assertions inspect the built
production JAR. The full TOML round-trip test runs inside the installed server:
Fzzy Config 0.7.7 initializes Minecraft item registries while reading restart
annotations, so that check requires the game to be bootstrapped.

The behavior suite uses the same isolated NeoForge installation as the core
suite, with a separate `behavior-world` and expected save record. It keeps the
guards' production goal lists intact. Its prepared terrain and stationary
targets make failures reproducible; this is not a test of generated villages.

| Area | Installed-server checks |
| --- | --- |
| Tactical movement | Dry guards withdraw from a group without melee takeover; guards reload while withdrawing and resume fire |
| Firing safety | A shield does not starve gun use; a friendly villager blocks fire until the lane clears; Hold Fire cancels a pending round or the remainder of a burst |
| Accuracy | Zero-error mob aim, bounded general error, separate player error and center-mass aim, and native round/ammo accounting |
| Sidearms | Dry and unsafe-close explosive primaries switch to a pistol; Hold Fire restores the original primary; complete gun components survive active-swap serialization |
| Orders and squads | Role loading and copying, ledger capture/apply, idempotent apply, spectator/hostile-member rejection, Rally, Fall Back, Defend, Focus Target, and Resume Orders |
| Patrols | Both waypoints are reached with the upstream checkpoint goal present; Hold pauses and Resume restarts the route; an obstacle requires a detour; unloaded waypoints do not load chunks |
| Restart | Persistent orders, membership, route, unexpired command, ledger components, and both active/stowed guns survive a normal shutdown and restart; expired commands clear |
| Resources/config | Ally and engagement tags load; both ledgers craft; old TOML values and the new accuracy settings round-trip through Fzzy Config |

Player-target and command checks use server-side FakePlayers. They exercise the
real permissions, dispatch, and aim policy. Client input, rendering, networking,
and multiplayer synchronization are exercised separately by the installed
connected-client suite described below.

Results and exact JAR hashes go under
`build/neoforge/core-qa/results/behavior`. A run fails on a test failure marker,
an ERROR/FATAL log, a timeout, changed JAR hashes, or an unsuccessful shutdown.
The separate QA mod is never included in the gameplay JAR.

Combat tags now use `data/villagerguardgunners/tags/entity_type/`, and the two
ledger recipes use `data/villagerguardgunners/recipe/` with component-era result
stacks. These changes follow the
[Minecraft 1.21 directory renames](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21).
Worldgen and companion resources use the migration described below.

### Native inventory command authorization

The add-on intercepts both original Guard Villagers follow/patrol handlers.
On the server thread, each request must reference the guard in the sender's
currently open `GuardGunnerContainer`. Its live validity check enforces the
same level, an alive nonspectator player, an available guard, and a distance
under eight blocks. `GuardOrderPermissions.mayManage` and the relevant upstream
Hero requirement are checked again when the action executes. Patrol requests
also require their previous-state flag to match the current server state.
The upstream packet IDs and authorized button behavior are preserved.

## Supply and medical checks

```sh
./gradlew checkNeoForgeFoundation stageCoreQaMods supplyMedicalTest
python3 tools/run_neoforge_core_qa.py --accept-eula --fresh --suite workflow
```

`supplyMedicalTest` covers Armory resupply policies and reservations,
Quartermaster service settings, ownership receipts, management protocols,
trade definitions, treatment profiles, casualty state, and cot/transport state.
Artifact checks verify the supply recipes, block loot tables, and mining tag
in the actual gameplay JAR. The retained Forge performance harness is checked
as reference; its historical results do not certify NeoForge performance.

The installed workflow suite uses the production event handlers, goals,
capabilities, merchant menus, and published dependency JARs. A test player
exercises server-side permissions and item interactions. Normal villager AI
must claim a Quartermaster station, and normal guard AI must travel for
supplies and treatment.

| Area | Installed-server checks |
| --- | --- |
| Resources | Craft a Village Armory, Quartermaster station, bandages, and Recovery Cot; verify block loot and axe tags |
| Station storage | Automation settings, simulated insertion, copied stacks, stock/return restrictions, removed capability rejection, one packed drop, and normal packed-block placement |
| Equipment service | Manual opt-in, unique stock reservations, exact gun/sidearm/armor transfers, replay rejection, protected equipment, full returns, changed stock, and work hours |
| Management | Authorized settings, rejection of strangers and stale station contexts, read-only needs previews, assigned service, linked Armory squad locks, and interrupted ownership receipts |
| Trading | Natural job claiming, offer population without duplicates, ordinary merchant payment, disabled-trade rejection, and saved offer identities/uses |
| Treatment | Bandage consumption, cooldowns, canceled healing, potion components and returned bottles, opaque payload preservation, sparse pouch slots, packing, and capability teardown |
| Medical AI | Medic approach and healing; lethal damage creates a casualty; interrupted player revival preserves supplies; field-care fallback when carry routes are rejected; transport, cot treatment, and recovery release |
| Restart | Station contents and permissions, committed ownership receipts, guard supplies and cooldowns, service assignment, Quartermaster profession, and casualty/cot relationship |

Results and exact JAR hashes are retained under
`build/neoforge/core-qa/results/workflow`. The runtime installation is shared
with the other suites; a process lock prevents simultaneous use. Each suite
has its own world and expected save record. After all runners have stopped,
the test-owned `build/neoforge/installed-core-qa` directory can be deleted while
retaining the staged JARs and result directories.

Supply recipes use `recipe/`, the three block loot tables use `loot_table/`,
and the axe tag uses `tags/block/` in this branch. Custom medical, trade, and
loadout definitions retain their existing addon-specific directory names.
Worldgen resources and companion packs use the same current-game resource conventions.

The carrying route screen is conservative: a rejected candidate path can lead
to field care even when a wider detour might exist. The suite checks this
fallback as well as transport along a clear route; it does not claim exhaustive
route finding in generated villages.

These checks cover server behavior on prepared terrain. They do not certify
connected-client screens, inventory synchronization, multiplayer trading, or
rendered casualty/cot poses. Those are covered by the separate installed-client
acceptance pass.

### Damage and absorption

Ordinary medical interruption and village damage notifications observe
`LivingDamageEvent.Post`, which reports damage after absorption. Downed guards
must intercept the remaining damage before health loss. `CasualtyDamageMixin`
therefore intercepts the final damage read in `LivingEntity.actuallyHurt`, after
native absorption and its reduction callbacks, and passes that value to the
casualty timer. Absorbed damage is neither refunded nor counted against the
timer. Bypass damage keeps the normal death path. The required mixin and exact
NeoForge pin prevent an incompatible damage hook from silently going unused.

The installed workflow suite verifies active and downed guards with full and
partial absorption, canceled damage, and an absorption reduction callback. It
also calls the original installed follow/patrol handlers to check permissions,
menu identity, range, Hero requirements, stale patrol requests, and authorized
toggles. Fzzy restart annotations are checked through reflection inside
Minecraft, where the required item registries are initialized.

## Worldgen and companion resources

Build and validate the Minecraft 1.21.1 resources with Java 21 and Python 3.11+:

```sh
./gradlew checkNeoForgeFoundation stageCoreQaMods worldgenTest verifyNeoForgeCompanionPacks
python3 tools/run_neoforge_core_qa.py --accept-eula --fresh --suite worldgen --worldgen-scenario builtin
python3 tools/run_neoforge_core_qa.py --accept-eula --fresh --suite worldgen --worldgen-scenario universal
# Diagnostic reproduction of the known Sky Villages error; expected nonzero exit.
python3 tools/run_neoforge_core_qa.py --accept-eula --fresh --suite worldgen --worldgen-scenario providers-baseline
python3 tools/run_neoforge_core_qa.py --accept-eula --fresh --suite worldgen --worldgen-scenario providers
```

Run the installed scenarios sequentially. The worldgen suite uses a 4 GB heap
for its prepared building areas and generated village samples. They share a locked installation,
with a separate world and results directory for each scenario. The provider
scenario downloads the published JARs pinned in
`gradle/neoforge-village-mods.json`, verifies SHA-512 hashes, and installs their
actual dependencies. It does not use a development runtime. Results are retained
under `build/neoforge/core-qa/results/worldgen/`.

| Scenario | Installed content and checks |
| --- | --- |
| Built-in | Complete gameplay JAR and required dependencies; 25 definitions and standalone Watchtower template; native village jigsaw generation; save/restart |
| Universal | Combined companion ZIP with no optional village mods; 175 definitions load, 75 entries activate, and 100 provider entries remain inactive |
| Providers | Combined ZIP with CTOV, MMV, Tidal Towns, Sky Villages, and their dependencies; all 175 entries activate; fingerprinted discovery and native provider jigsaw generation |

The suite loads every template and checks its anchor in every rotation. It
round-trips installed pool-element codecs and verifies weights, duplicate
prevention, resource reloads, generated ammo IDs, Armory stock, consumed-stock
persistence, garrison activation, and saved building claims. Selected regional
layouts are placed through the production pool element on prepared support.
Standalone Watchtower pieces are placed in four rotations and their marker
claims are serialized. Native village samples use the registered structure's
normal jigsaw generation and per-chunk placement path on generated terrain.
These samples do not measure natural frequency or certify every terrain/seed
combination. Connected-client visual review remains separate.

The current provider pins are CTOV 3.6.3, MMV 2.2.0, Tidal Towns 2.0, and
Sky Villages 1.0.6. Their libraries are Lithostitched 1.8.1, Moog's Structure Lib
3.4.2, and Architectury 13.0.11. These pins describe the checked configurations;
they are not new mandatory dependencies for Villager Guard Gunners. The
Forge-era provider matrix does not establish NeoForge coverage for Better
Villages, Lios, Villages&Pillages, Integrated Villages, or Liberty's Villagers.

### Known provider issue

Sky Villages 1.0.6 ships two item frames in `skyvillages:house_1` with saved
attachment positions `(124, -23, 87)` and `(114, -23, 87)`. Minecraft logs
`Block-attached entity at invalid position` while loading that template. The
provider-only diagnostic places the unmodified template without Villager
Guard Gunners or its datapack and reproduces both messages. This is an upstream
decoration issue; the add-on does not rewrite the provider's template.

The diagnostic deliberately reports a nonzero exit because it captures those
errors. A subsequent provider acceptance run recognizes only those exact two
messages, and only when the saved baseline proves they occurred without the
add-on and used the same Sky Villages JAR hash. They remain visible in
`runtimeErrors` and `knownProviderErrors`; all other errors still fail the run.
The gameplay checks and normal shutdown/restart must also pass. This exception
does not apply to the built-in, universal-without-providers, combat, or medical
suites.

### Pack installation and formats

`combinedBuildingIntegration` builds 31 ZIPs under
`build/neoforge/distributions/companion-packs/`: one universal pack, five provider
packs, and 25 family/provider alternatives. The universal file is
`villager-guard-gunners-village-buildings.zip`. Install that file into the world's
`datapacks` folder, or select the narrower alternatives. Avoid installing the
universal pack together with its constituent packs. Enable
`villageBuildings.enabled` and restart the world/server. Generation changes
apply to new chunks; `/reload` does not rewrite live village pools.

These ZIPs use data pack format 48 and `data/<namespace>/structure/`. Custom
addon directories such as `village_buildings`, `village_compatibility`, and
`guard_post_groups` keep their existing names. They add definitions rather than
replacing provider pools. Missing supported providers are skipped, while an
installed provider with a broken/missing pool still produces diagnostics.

The main JAR includes 25 built-in building templates plus the standalone
Watchtower. Mod `pack.mcmeta` lets NeoForge supply the correct format for each
pack type (resource format 34 and data format 48). Structure NBT retains its
actual source DataVersion 3465 so Minecraft's data fixer upgrades vanilla
states and block-entity schemas when reading it; relabeling old NBT as current
would bypass that conversion. Generated inventories come from runtime loot,
using `minecraft:set_custom_data` for TaCZ's ammo ID.

The geometry builders remain shared with the retained Forge tools. The
NeoForge pack builder calls their geometry functions and writes only into the
isolated NeoForge output directory. It does not call their Forge-era export
routines or reuse previously generated Forge files. The old Forge release
artifact and companion outputs remain separate.

The directory and pack-version changes are documented in the
[Minecraft 1.21 release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21).
The per-type mod metadata behavior is implemented in NeoForge's
[ResourcePackLoader](https://github.com/neoforged/NeoForge/blob/1.21.1/src/main/java/net/neoforged/neoforge/resource/ResourcePackLoader.java).

### Updating detailed village profiles

The runtime reads fingerprint evidence from `structure/`. Built-in detailed
profiles are regenerated from the Minecraft and provider JARs, with exact mod
versions and template hashes. Unknown or replaced templates do not inherit
stale bed/entrance coordinates. Do not widen those version ranges without
reviewing the actual templates.

After populating the pinned village-mod cache, refresh the profiles with:

```sh
python3 tools/refresh_neoforge_village_profiles.py --minecraft-jar build/neoforge/installed-core-qa/libraries/net/minecraft/server/1.21.1-20240808.144430/server-1.21.1-20240808.144430-extra.jar
```

Review the source diff and the audit under
`build/neoforge/reports/village-profiles/`. This maintenance command is not a
normal build dependency. The resulting JSON files are committed resources;
normal builds need neither an installed test world nor local agent notes.

## Connected clients and multiplayer

Build the dedicated acceptance mod separately from the gameplay JAR:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./gradlew --no-daemon stageMultiplayerQaMods
python3 tools/run_neoforge_multiplayer_qa.py --prepare-only
python3 tools/run_neoforge_multiplayer_qa.py --accept-eula --fresh
```

Only use `--accept-eula` after accepting Minecraft's EULA. The current client
launcher supports macOS ARM64 with Java 21 and an active graphical desktop.
`--java-home` selects another Java 21 installation. It installs the published
Minecraft and NeoForge client/server files under
`build/neoforge/installed-multiplayer-qa`, using official download checksums and
verified cached assets where available. It never edits the normal Minecraft
installation, launcher accounts, or existing worlds.

The runner launches two real graphical clients and a dedicated server using
installed launch metadata and packaged JARs. It uses local test identities on
a loopback-only offline server. Client A uses the normal guard model; client B
uses Guard Villagers' Steve model. The separate `vgg-multiplayer-qa` JAR builds
the test fixtures, drives production client interactions, and checks the server
state. Files coordinate scenario boundaries; inventory clicks, button actions,
item interactions, configuration, entity state, and custom payloads cross the
actual network connection. These are not FakePlayer tests.

The suite covers:

- Guard inventory, medical pouch, sidearm transfer, service settings, Guard
  Orders, and the upstream follow/patrol buttons. Server accuracy settings must
  override the clients' initial defaults.
- Armory and Quartermaster stock changes seen by two viewers, private stock
  restrictions, live permission revocation, concurrent equipment previews, and
  rejection of stale actions.
- Direct upstream follow/patrol packets from an unauthorized peer, an owner
  without an open guard inventory, and an owner whose Hero effect has expired.
  Rejected requests must preserve ownership, following, and patrol state.
- Merchant selection, payment and output transfer, competing traders,
  disconnect cleanup, conserved items after reconnect, and rejection of an old
  management-session payload.
- Treatment initiated by a client, casualty state on both clients, Recovery Cot
  alignment and labels, and native TaCZ aim, firing, and reload events.
- A valid TaCZ common-data cache larger than the single custom-payload limit,
  delivered through the add-on's production fragment codec and client handler.
  This fixture pads valid JSON with whitespace and adds a test-only cache entry;
  it does not certify every external gun pack or the automatic overflow trigger
  for every pack combination.
- Orders Ledger capture/apply and Squad Command Ledger binding, including item
  component synchronization and denial of a copied ledger used without command
  permission.

Results, mod hashes, launch commands, console logs, and PNG captures are written
to `build/neoforge/multiplayer-qa/results/`. The runner requires successful
server assertions, clean exits from all three processes, unchanged mod JARs,
and no logged errors, fatal messages, or missing textures. Inspect the captures
as well as the result JSON. Automated state checks alone do not establish visual
quality.

`--fresh` resets only this runner's world, configurations, and scenario results.
An ownership marker and exclusive lock prevent unrelated directories or a live
run from being reused. Keep the reports before another run. After all test
processes exit, the owned `build/neoforge/installed-multiplayer-qa` directory can
be deleted to recover space; the separate results and staged JARs remain.
Never distribute the acceptance JARs with the gameplay mod.

## Remaining validation

The installed server and two-client suites cover the migrated workflows, but
do not certify full feature parity across every environment. Combat in generated
terrain, wider provider combinations, long-running servers, network latency,
authenticated internet servers, and other client operating systems remain
separate validation work. Custom shaders and resource packs also need their own
visual checks.

The existing GameTest, integration, and performance suites retain additional
tests awaiting migration. The complete JUnit suite runs against the port.
Artifact contracts inspect the built NeoForge JAR, and resource contracts use
the current singular directory names. Tests of retained Forge release gates
are reference checks, not evidence that those gates ran on NeoForge. The original Forge build and
release validators remain in `gradle/legacy-forge.gradle` as reference and are
not applied. Full release validation must be adapted before stable publication. Historical
1.9 release evidence applies only to the Forge artifact.

CI runs `./gradlew --no-daemon build stageCoreQaMods stageMultiplayerQaMods`.
The ordinary `build`/`check` path runs the complete JUnit suite, dependency and
metadata validation, and `verifyNeoForgeCompanionPacks`. The filtered
`combatTest`, `supplyMedicalTest`, and `worldgenTest` tasks remain available for
focused work. CI retains the full test report. It does not silently accept the
Minecraft EULA or run the graphical acceptance suite on its headless Linux
worker.

Use fresh worlds for installed tests. The unofficial TaCZ port documents
incompatible 1.20.1 world data and separate gun-pack conversion requirements:
[TaCZ NeoForge port](https://www.curseforge.com/minecraft/mc-mods/tacz-1-21-1).
