# Village Defense Buildings validation matrix

## Current candidate

- Step 14 candidate version: `1.7`
- Step 14 candidate JAR SHA-256: `9f81a8fbc0c738c0514de4253de8edbd764ddf3eea0d71b649047f2b33b843a8`
- Current status: candidate build and artifact checks passed; manual rows and
  full release evidence remain pending.
  Historical results below do not certify version 1.7. Repeat the applicable
  natural-terrain, upgrade, modpack, and multiplayer rows on this candidate.

## Historical 1.5 baseline and coverage

This matrix is the retained gate for the five village-attached defense
buildings. “Pass” requires evidence against the exact candidate; source review
alone is recorded as “Partial.” Step 5 adds automated visual-template evidence;
natural terrain, gameplay, and mixed-mod generation rows remain incomplete.

## Baseline

- Branch: `v1.5-support`
- Release candidate version: `1.5`
- Inherited 1.4 commit: `be63f09fc9697666aa67a8eb66055214fac2d568`
- Inherited 1.4 JAR SHA-256:
  `2ebf9920e7916bc9d7e81e85a55c522020e7149eaabf74d2354bfe6c838e59ac`
- Functional VGG village behavior active: bounded garrisons and public village Armories when the default-off master switch is enabled
- Step 5 visual templates packaged: 25 (five types x five village palettes)
- Step 5 generation default: disabled pending manual visual review
- Step 2 compatibility probe default: disabled
- Step 2 JVM/source-contract tests: 464 passed
- Step 2 Forge GameTests: all 97 required tests passed
- Step 2 development artifact: `villagerguardgunners-1.5-dev.jar`
- Step 2 artifact size: 954,554 bytes
- Step 2 artifact SHA-256:
  `2c973bbc32ac7baf870fb355826e48e2a488b8cf4e6090e031d2f5cc7b02322d`
- Step 3 JVM/source-contract tests: 476 passed; 0 failed, errored, or skipped
- Step 3 Forge GameTests: all 97 required tests passed
- Step 3 development artifact: `villagerguardgunners-1.5-dev.jar`
- Step 3 artifact size: 975,797 bytes
- Step 3 artifact SHA-256:
  `4dbb920dcb030f8b650f38fdd58a2072e8d6ba0c5856364c56b66b8d3f253cbc`
- Step 4 JVM/source-contract tests: 487 passed; 0 failed, errored, or skipped
- Step 4 Forge GameTests: all 97 required tests passed
- Step 4 development artifact: `villagerguardgunners-1.5-dev.jar`
- Step 4 artifact size: 997,182 bytes
- Step 4 artifact SHA-256:
  `1724bfd38d8099cf36f1cc3afce86725c71a080f7c6461ad3d1072d851bc87c4`
- Post-audit JVM/source-contract tests: 488 passed; 0 failed or errored
- Post-audit Forge GameTests: all 99 required tests passed
- Post-audit development artifact: `villagerguardgunners-1.5-dev.jar`
- Post-audit artifact size: 1,003,497 bytes
- Post-audit artifact SHA-256:
  `2833e99a8234e020c84496b94f96d5780686687c7a51f5e5a971c50ac7c4168d`
- Step 5 JVM/source-contract tests: 490 passed; 0 failed or errored
- Step 5 Forge GameTests: all 100 required tests passed
- Step 5 development artifact: `villagerguardgunners-1.5-dev.jar`
- Step 5 artifact size: 1,048,711 bytes
- Step 5 artifact SHA-256:
  `29f5af34e1539bbce5ddcfcade0ff593ec880ba98046ac399ec2b3e2c1f621a3`
- Step 6 JVM/source-contract tests: 497 passed; 0 failed, errored, or skipped
- Step 6 Forge GameTests: all 101 required tests passed
- Step 6 development artifact: `villagerguardgunners-1.5-dev.jar`
- Step 6 artifact size: 1,066,498 bytes
- Step 6 artifact SHA-256:
  `8650e519f1d573f2a82e0072ef29b6418560531eae187464d65ed6de5d55b9b0`
- Step 7 JVM/source-contract tests: 503 passed; 0 failed, errored, or skipped
- Step 7 Forge GameTests: all 102 required tests passed
- Step 7 development artifact: `villagerguardgunners-1.5-dev.jar`
- Step 7 artifact size: 1,075,732 bytes
- Step 7 artifact SHA-256:
  `ab4dd30f530edd2a6f83c699d30b353bd91d71236a45eecd897346f57d84c1a7`
- Step 8 JVM/source-contract tests: 505 passed; 0 failed, errored, or skipped
- Step 8 Forge correctness GameTests: all 102 required tests passed
- Step 8 isolated performance GameTests: 1 passed; 100 placements and 200 idle ticks
- Step 8 startup publication: 25 injected; repeated publication found all 25
- Step 8 development artifact: `villagerguardgunners-1.5-dev.jar`
- Step 8 artifact size: 1,075,732 bytes
- Step 8 artifact SHA-256:
  `ab4dd30f530edd2a6f83c699d30b353bd91d71236a45eecd897346f57d84c1a7`
- Step 8 village-building JFR SHA-256:
  `88476a177aa0005d24f30b1e3b7cca940eb1e154eb00329d9e122a3c178a2820`
- Post-audit JVM/source-contract tests: 508 passed; 0 failed, errored, or skipped
- Post-audit Forge correctness GameTests: all 104 required tests passed
- Step 9 integrated tower matrix: five biome variants x four rotations passed
- Step 9 isolated performance GameTest: 1 passed; 100 placements and 200 idle ticks
- Step 9 development artifact: `villagerguardgunners-1.5-dev.jar`
- Step 9 artifact size: 1,082,704 bytes
- Step 9 artifact SHA-256:
  `37f57710dc2a836f6497ff017de44136ac66ed4a964951b756c90f741bc03da5`
- Step 9 village-building JFR SHA-256:
  `76c2ad83eaafcb37196f6090ad3f0fa29f3dcae571888fd1db1eab417c48724c`
- Step 10 JVM/source-contract tests: 511 passed; 0 failed, errored, or skipped
- Step 10 Forge correctness GameTests: all 106 required tests passed
- Step 10 Training Yard matrix: five biome variants x four rotations passed
- Step 10 isolated performance GameTest: 1 passed; 100 placements and 200 idle ticks
- Step 10 development artifact: `villagerguardgunners-1.5-dev.jar`
- Step 10 artifact size: 1,083,342 bytes
- Step 10 artifact SHA-256:
  `f8e81cb8591d977346112a6f3a65f155cec605f762daefcf59f4b89fd9037d7f`
- Step 10 village-building JFR SHA-256:
  `b8d3d9cf70d4456a6713469161f4a02ec903a8a93aea2f683829e3982111cd93`
- Balance-pass JVM/source-contract tests: 511 passed; 0 failed, errored, or
  skipped
- Balance-pass Forge correctness GameTests: all 106 required tests passed
- Balance-pass isolated performance GameTest: 1 passed; 100 placements and 200
  idle ticks
- Balance-pass development artifact: `villagerguardgunners-1.5-dev.jar`
- Balance-pass artifact size: 1,083,337 bytes
- Balance-pass artifact SHA-256:
  `451cd5ce9aad6e39cc5e808f73b44277e256f79e64a6e4b16b34822f0334a265`
- Balance-pass village-building JFR SHA-256:
  `2b1a6239cfe4a72c553390dfecf9d4e355d067e6912f7124906477d434557fc9`
- Step 12 JVM/source-contract tests: 515 passed; 0 failed, errored, or skipped
- Step 12 Forge correctness GameTests: all 107 required tests passed
- Step 12 isolated performance GameTest: 1 passed; 100 placements and 200
  idle ticks
- Step 12 development artifact: `villagerguardgunners-1.5-dev.jar`
- Step 12 artifact size: 1,096,850 bytes
- Step 12 artifact SHA-256:
  `566c984ddaba10da028e39ed73a3dbac4793e89719f25de8f0232d42c3b727e8`
- Step 12 village-building JFR SHA-256:
  `3276ff10c73ffa7f6699e2767002e98badd7c30964d5065af1e00e8551e57ac3`
- Step 13 JVM/source-contract tests: 517 passed; 0 failed, errored, or skipped
- Step 13 Forge correctness GameTests: all 107 required tests passed
- Step 13 combined performance GameTests: all 21 required tests passed (20
  combat/logistics scenarios plus one village-building scenario)
- Step 13 village-building workload: 100 disabled selections, 100 selected
  and simultaneous initializations, 100 completed records, and 200 idle ticks
- Step 13 pool matrix: all 25 entries installed idempotently across five
  vanilla pools while preserving every pre-existing raw entry and weight
- Step 13 performance evidence: 20 JSON reports, 20 JFR profiles, one
  village-building JSON report, and one village-building JFR profile verified
- Step 13 development artifact: `villagerguardgunners-1.5-dev.jar`
- Step 13 artifact size: 1,096,850 bytes
- Step 13 artifact SHA-256:
  `566c984ddaba10da028e39ed73a3dbac4793e89719f25de8f0232d42c3b727e8`
- Step 13 combined release manifest SHA-256:
  `2beee870d7561961bbcf21a548d61e6f05863a81aeab03c093f9ad33c7158bd4`
- Step 13 village-building JFR SHA-256:
  `989073676a0adb8ef893d06657c4fef164c6df03b31b1cd69242985f122f53c2`
- Step 14 candidate version: `1.5`
- Step 14 candidate JAR SHA-256: `7845193de710fc5f8e4c5248451a352456d31855794f26711738516b1016933b`
- Step 14 status: manual validation pending; this is not a public release

## Automated architecture and integration cases

| ID | Scenario | Required result | Current status |
| --- | --- | --- | --- |
| VBUILD-BASE-01 | Inspect version, branch contract, inherited commit, and checksum. | Every identity matches the recorded baseline. | Pass |
| VBUILD-SCHEMA-01 | Enumerate building types and reserved paths. | Exactly the five reviewed stable building IDs are exposed; the probe is not a building type. | Pass |
| VBUILD-LIMIT-01 | Submit invalid weight, duplicate IDs, more than twenty-four pools, or more than 128 entries. | Publication rejects the batch before touching a pool. | Pass |
| VBUILD-CFG-01 | Start with `villageBuildings.enabled=false`. | No pool registry inspection or mutation occurs. | Pass |
| VBUILD-CFG-02 | Disable any one schema-13 building-type switch. | Matching datapack entries are suppressed without rewriting the accepted snapshot or affecting other types. | Pass — JVM config/filter tests |
| VBUILD-CFG-03 | Disable village Armory placement or only initial stock. | Placement-off preserves the air anchor without a claim; stock-off places one complete public empty Armory. | Pass — JVM policy tests and live Forge GameTest |
| VBUILD-DATA-01 | Parse format-1 definitions, including pack-owned namespaces. | Only the seven reviewed fields and five stable types compile in deterministic entry-ID order. | Pass — JVM parser tests |
| VBUILD-DATA-02 | Submit duplicate keys, trailing JSON, deep/oversized documents, future formats, bad IDs/projections, or fractional/out-of-range weights. | The strict bounded reader rejects the candidate before pool mutation. | Pass — JVM parser tests |
| VBUILD-DATA-03 | Reload one invalid document after a valid publication. | The complete invalid candidate is rejected; generation and last-known-good definitions remain unchanged. | Pass — JVM publication tests |
| VBUILD-DATA-04 | Run `/reload` after startup. | The accepted snapshot updates, but live template pools are not removed, reordered, or rebuilt; restart is documented as required. | Pass — architecture contract; live restart test remains manual |
| VBUILD-POOL-01 | Inject the plains probe into the vanilla plains houses pool. | Existing raw and flattened entries retain identity/order; one weight-1 VGG element is appended. | Pass — live Forge registry test |
| VBUILD-POOL-02 | Repeat injection in the same server. | Pool sizes do not change; the report records one already-present entry. | Pass — live Forge registry test |
| VBUILD-POOL-03 | Target a missing pool, processor list, template, or invalid anchor template. | The entry is skipped before any pool commit, other entries remain eligible, and one bounded report records its ID. | Pass — live Forge registry/template tests |
| VBUILD-POOL-04 | Inspect the probe template's jigsaw metadata. | The existing inert template has a connector to the plains street pool. | Pass — live Forge template test |
| VBUILD-BALANCE-01 | Inspect the complete built-in matrix in each supported houses pool. | Five raw entries are appended with relative weights 1/1/2/1/1, the flattened list grows by six, and the original vanilla weight remains greater than the complete VGG addition. | Pass — source contract and live Forge registry test |
| VBUILD-DIAG-01 | Run `status`, `entries`, `pools`, and `errors` as an operator and below permission level 2. | Commands are permission-gated, paginated to eight items, distinguish accepted-next-restart definitions from live pool weights, and change no registry or publication state. | Pass — source contract and live Forge command test |
| VBUILD-AUTO-01 | Exercise every built-in entrance through Minecraft's attachment predicate and place all five building types together. | All 100 biome/building/rotation connector cases attach only to a matching road target, and five independent records are added. | Pass — live Forge matrix |
| VBUILD-PERF-01 | Compare 100 disabled candidates with 100 same-tick passive building initializations, then retain the completed records for 200 idle ticks. | Disabled candidates select no VGG entry; selected candidates add exactly 100 records; idle time adds no record or forced chunk. | Pass — artifact-bound JSON/JFR workload |
| VBUILD-PERF-02 | Run the existing 20-scenario guard, ammunition, sidearm, and Armory matrix beside the village workload in one server process. | One artifact checksum binds all scenarios; 100 armed idle guards remain bounded and scarce/unreachable Armory pressure produces bounded no-supply/backoff evidence without forced chunks. | Pass — artifact-bound combined Step 13 run |
| VBUILD-CODEC-01 | Encode and decode the custom pool element. | Entry ID, building type, template, processors, and projection round-trip. | Pass — live polymorphic codec test |
| VBUILD-ANCHOR-01 | Place one real template with a valid, type-matching anchor marker. | Blocks place, the marker cell becomes air, and one deterministic schema-1 building record is claimed. | Pass — live Forge template-placement/SavedData test |
| VBUILD-ANCHOR-02 | Supply duplicate, malformed, unknown, oversized, or type-mismatched anchors. | Template validation fails before placement; zero anchors remains valid for decorative pieces. | Pass — JVM marker tests |
| VBUILD-ANCHOR-03 | Repeat an anchor or reuse its UUID/position with different provenance. | Exact repeats are no-ops; UUID and physical-anchor conflicts fail closed without another record. | Pass — JVM and live idempotence tests |
| VBUILD-ANCHOR-04 | Save/load valid, malformed, duplicate, wrong-dimension, wrong-seed identity, wrong-type, and future-version building data. | Valid state round-trips; invalid state disables claims and is retained opaquely without normalization. | Pass — JVM persistence tests |
| VBUILD-LIFECYCLE-01 | Process an anchor from a world-generation worker. | Only immutable identity crosses to the server thread; recovery observes relevant chunk-load events but never loads chunks, scans unrelated records, adds tickets, ticks continuously, or repairs completed buildings. | Pass — architecture plus dedicated worker-thread Forge GameTest |
| VBUILD-LIFECYCLE-02 | Persist an unfinished building, then make its anchor or guard-cell chunk available. | Rotation, generation difficulty, and the required action survive serialization; one indexed chunk event resumes the action exactly once and removes its pending state. | Pass — JVM persistence/index tests and live Forge recovery GameTest |
| VBUILD-RESTART-01 | Stop and start a second integrated server process. | The new registry receives one entry; no static prior-server definitions suppress or contaminate it. | Partial — explicit stopped-server reset and JVM session-isolation test pass; live two-server lifecycle run pending |
| VBUILD-LEGACY-01 | Decode a Step 2 compatibility-probe structure start without `building_type`, then repeat with every non-probe type. | Only the exact legacy plains probe loads as Barracks; every current or pack-authored entry requires and retains its explicit type. | Pass — live positive and five-case negative polymorphic codec test |
| VBUILD-OLDWORLD-01 | Load an existing 1.4 world with integration enabled. | Existing starts/chunks are unchanged; only newly selected pieces can use appended entries. | Manual test required |

## Later functional and compatibility cases

| ID | Scenario | Required result | Current status |
| --- | --- | --- | --- |
| VBUILD-PLACE-01 | Generate each building in every supported village biome and rotation. | Road connection, terrain fit, bounds, palettes, and interiors are correct. | Partial — all 25 templates load; open entrances and supported lanterns are source-validated; all five types pass rotated real-placement checks; natural-village visual review remains manual |
| VBUILD-MARKER-01 | Revisit, clip, save, and reload a building anchor. | One persistent identity; no duplicate guard, Armory, loot, or post action. | Partial — shared identity and at-most-once garrison, Armory, and initial-stock claims implemented; natural chunk-boundary review remains manual |
| VBUILD-BARRACKS-01 | Generate Barracks across all difficulties. | Bounded role/loadout-aware garrison and no partial issuance. | Partial — bounded Easy/Normal/Hard datapack group, atomic entity creation, and one-shot claim implemented; natural terrain gameplay review remains manual |
| VBUILD-ARMORY-01 | Generate the village Armory/Quartermaster. | Existing lock, loot, ammo, and resupply contracts remain authoritative. | Partial — public Armory placement, bounded ammo-only stock, persistent identity, and existing resupply integration implemented; natural gameplay review remains manual |
| VBUILD-SENTRY-01 | Generate roadside Sentry Posts. | Three-wide entrance, mixed solid/low cover, clear horizontal and raised firing lanes, one position-bound guard, and safe road access. | Partial — all 20 biome/rotation layouts, the Sentry role, safe cell, navigation, cover, and firing lanes pass automatically; natural road-clearance and combat review remain manual |
| VBUILD-TOWER-01 | Generate integrated and standalone towers in one world. | Independent controls/identity; integrated TaCZ-gun and crossbow guards move once to the deck while melee or unarmed guards remain in the ready room. | Partial — bounded ranged-only deck placement and independent standalone controls are implemented; natural mixed-generation review remains manual |
| VBUILD-TOWER-02 | Place all five integrated tower palettes under all four rotations. | Reviewed bounds, matching road connector, safe ready-room and deck cells, navigable supported stairs, connected biome-matched fence railing, two-block-high observation clearance, clear firing lanes, and no Armory. | Pass — 20-case live Forge placement/pathfinder matrix plus build-time source validation |
| VBUILD-YARD-01 | Generate Training Yards. | At most two role/loadout-aware guards use safe cells; targets remain decorative and no recurring training controller is introduced. | Partial — bounded datapack staffing and the 20-case biome/rotation layout matrix pass automatically; natural terrain gameplay review remains manual |
| VBUILD-COMPAT-01 | Run with common village-overhaul datapacks/mods. | Third-party entries and weights remain intact; missing/replaced pools skip safely. | Manual test required |
| VBUILD-PERF-01 | Publish the complete built-in matrix, place one passive defense piece for each of 100 simulated village candidates, then profile 200 idle ticks. | Startup and placement work are bounded, JFR contains execution samples, records remain stable, and the forced-chunk set is unchanged. | Pass — 25 entries injected, 100/100 placements, records stable at 100, forced chunks 25→25, and JFR verifier passed |

## Manual visual checklist

Natural terrain and mixed-mod review remains separate from the compact
performance fixture and is required before a public release.

- [ ] Every palette matches its vanilla village biome.
- [ ] Every connector meets a road without a blocked doorway or floating path.
- [ ] All rotations preserve beds, doors, workstations, fences, tower steps, and
  collision clearance.
- [ ] Barracks, Armory, Sentry Post, integrated Watchtower, and Training Yard
  are visually distinct and readable.
- [ ] Defense-building frequency feels useful without overwhelming normal
  homes and professions.
- [ ] At least 20 to 30 newly generated villages across several seeds confirm
  the Sentry Post, large-building, guard-population, and Armory-loot balance.
- [ ] Standalone 1.4 Watchtowers remain rare and visually/behaviorally
  independent.

## Step 14 manual release worksheet

Use the exact candidate identified in the baseline and follow
[`village-defense-buildings-manual-validation.md`](village-defense-buildings-manual-validation.md).
Every row requires retained evidence; `Not run`, `Failed`, an empty evidence
cell, or a different candidate checksum blocks public packaging.

| ID | Setup and action | Expected result | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| VBUILD-MANUAL-PALETTE-01 | Generate all five building types across plains, desert, savanna, snowy, and taiga villages, retaining representative rotations. | Every structure uses the matching palette and its doors, lanterns, beds, ladders, fences, work areas, and guard cells remain visually and physically usable. | Not run | |
| VBUILD-MANUAL-ROAD-01 | Inspect defense buildings on flat ground, slopes, water edges, and beside several ordinary village pieces. | Entrances meet roads without blocked doors, floating or buried paths, unsafe drops, or overlap with neighboring pieces. | Not run | |
| VBUILD-MANUAL-FUNCTION-01 | Exercise a Barracks, Village Armory, Sentry Post, integrated Watchtower, and Training Yard with their default enabled subfeatures. | Each performs only its documented one-time garrison or Armory work; guards receive valid roles, squads, native TaCZ loadouts, and ammunition, and Armory resupply works. | Not run | |
| VBUILD-MANUAL-DIFFICULTY-01 | Generate representative buildings on Peaceful, Easy, Normal, and Hard. | Peaceful creates no guards; other difficulties use bounded documented groups and never exceed the building or global guard ceiling. | Not run | |
| VBUILD-MANUAL-CONFIG-01 | Disable the master switch, each building type, garrisons, Armory placement, and initial stock separately, restarting before generating new chunks. | Each setting suppresses only new matching selections or actions and does not remove, rewrite, or rerun completed structures. | Not run | |
| VBUILD-MANUAL-LIFECYCLE-01 | Remove guards and supplies, break an Armory, unload/reload chunks, reconnect, save, and restart. | Completed actions remain completed exactly once; removed guards, Armories, and stock are not regenerated or duplicated. | Not run | |
| VBUILD-MANUAL-OLDWORLD-01 | Open a backed-up 1.4 world with the exact candidate, inspect old chunks, and generate new chunks. | Existing villages and structures remain unchanged; only new eligible village pieces can contain defense buildings. | Not run | |
| VBUILD-MANUAL-COEXIST-01 | Generate standalone and integrated Village Watchtowers in one world and exercise their guards, squads, Armories, settings, and diagnostics. | The two systems remain independent with no shared identity, duplicate processing, or setting crossover. | Not run | |
| VBUILD-MANUAL-COMPAT-01 | Generate representative villages with at least one named village-overhaul mod or datapack from the target modpack. | Existing third-party pool entries and weights remain intact, missing pools skip safely, and generated road connections remain usable. | Not run | |
| VBUILD-MANUAL-SERVER-01 | Repeat representative generation, combat, Armory use, chunk reload, reconnect, and restart on a dedicated server with an observer where practical. | Server and clients agree on structures, guards, weapons, sounds, Armory state, and persistence without relevant errors or client-only classloading. | Not run | |
| VBUILD-MANUAL-FREQUENCY-01 | Review at least 20 to 30 newly generated villages across multiple seeds and all supported styles. | Defense buildings are discoverable without overwhelming ordinary homes or professions; record per-type counts and any rejected terrain examples. | Not run | |
