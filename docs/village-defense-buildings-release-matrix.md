# Village Defense Buildings 1.5 validation matrix

This matrix is the retained gate for the five village-attached defense
buildings. “Pass” requires evidence against the exact candidate; source review
alone is recorded as “Partial.” Step 5 adds automated visual-template evidence;
natural terrain, gameplay, and mixed-mod generation rows remain incomplete.

## Baseline

- Branch: `v1.5-support`
- Development version: `1.5-dev`
- Inherited 1.4 commit: `be63f09fc9697666aa67a8eb66055214fac2d568`
- Inherited 1.4 JAR SHA-256:
  `2ebf9920e7916bc9d7e81e85a55c522020e7149eaabf74d2354bfe6c838e59ac`
- Functional VGG village behavior active: bounded garrisons when the default-off master switch is enabled
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

## Automated architecture and integration cases

| ID | Scenario | Required result | Current status |
| --- | --- | --- | --- |
| VBUILD-BASE-01 | Inspect version, branch contract, inherited commit, and checksum. | Every identity matches the recorded baseline. | Pass |
| VBUILD-SCHEMA-01 | Enumerate building types and reserved paths. | Exactly the five reviewed stable building IDs are exposed; the probe is not a building type. | Pass |
| VBUILD-LIMIT-01 | Submit invalid weight, duplicate IDs, more than five pools, or more than 50 entries. | Publication rejects the batch before touching a pool. | Pass |
| VBUILD-CFG-01 | Start with `villageBuildings.enabled=false`. | No pool registry inspection or mutation occurs. | Pass |
| VBUILD-CFG-02 | Disable any one schema-13 building-type switch. | Matching datapack entries are suppressed without rewriting the accepted snapshot or affecting other types. | Pass — JVM config/filter tests |
| VBUILD-DATA-01 | Parse format-1 definitions, including pack-owned namespaces. | Only the seven reviewed fields and five stable types compile in deterministic entry-ID order. | Pass — JVM parser tests |
| VBUILD-DATA-02 | Submit duplicate keys, trailing JSON, deep/oversized documents, future formats, bad IDs/projections, or fractional/out-of-range weights. | The strict bounded reader rejects the candidate before pool mutation. | Pass — JVM parser tests |
| VBUILD-DATA-03 | Reload one invalid document after a valid publication. | The complete invalid candidate is rejected; generation and last-known-good definitions remain unchanged. | Pass — JVM publication tests |
| VBUILD-DATA-04 | Run `/reload` after startup. | The accepted snapshot updates, but live template pools are not removed, reordered, or rebuilt; restart is documented as required. | Pass — architecture contract; live restart test remains manual |
| VBUILD-POOL-01 | Inject the plains probe into the vanilla plains houses pool. | Existing raw and flattened entries retain identity/order; one weight-1 VGG element is appended. | Pass — live Forge registry test |
| VBUILD-POOL-02 | Repeat injection in the same server. | Pool sizes do not change; the report records one already-present entry. | Pass — live Forge registry test |
| VBUILD-POOL-03 | Target a missing pool, processor list, template, or invalid anchor template. | The entry is skipped before any pool commit, other entries remain eligible, and one bounded report records its ID. | Pass — live Forge registry/template tests |
| VBUILD-POOL-04 | Inspect the probe template's jigsaw metadata. | The existing inert template has a connector to the plains street pool. | Pass — live Forge template test |
| VBUILD-CODEC-01 | Encode and decode the custom pool element. | Entry ID, building type, template, processors, and projection round-trip. | Pass — live polymorphic codec test |
| VBUILD-ANCHOR-01 | Place one real template with a valid, type-matching anchor marker. | Blocks place, the marker cell becomes air, and one deterministic schema-1 building record is claimed. | Pass — live Forge template-placement/SavedData test |
| VBUILD-ANCHOR-02 | Supply duplicate, malformed, unknown, oversized, or type-mismatched anchors. | Template validation fails before placement; zero anchors remains valid for decorative pieces. | Pass — JVM marker tests |
| VBUILD-ANCHOR-03 | Repeat an anchor or reuse its UUID/position with different provenance. | Exact repeats are no-ops; UUID and physical-anchor conflicts fail closed without another record. | Pass — JVM and live idempotence tests |
| VBUILD-ANCHOR-04 | Save/load valid, malformed, duplicate, wrong-dimension, wrong-seed identity, wrong-type, and future-version building data. | Valid state round-trips; invalid state disables claims and is retained opaquely without normalization. | Pass — JVM persistence tests |
| VBUILD-LIFECYCLE-01 | Process an anchor from a world-generation worker. | Only immutable identity crosses to one server-thread task; no chunk load, scan, ticket, ticker, or repair manager exists. | Pass — architecture plus dedicated worker-thread Forge GameTest |
| VBUILD-RESTART-01 | Stop and start a second integrated server process. | The new registry receives one entry; no static prior-server definitions suppress or contaminate it. | Partial — explicit stopped-server reset and JVM session-isolation test pass; live two-server lifecycle run pending |
| VBUILD-LEGACY-01 | Decode a Step 2 compatibility-probe structure start without `building_type`. | It loads as the legacy Barracks probe, while every new encoding retains the explicit type. | Pass — live polymorphic codec test |
| VBUILD-OLDWORLD-01 | Load an existing 1.4 world with integration enabled. | Existing starts/chunks are unchanged; only newly selected pieces can use appended entries. | Manual test required |

## Later functional and compatibility cases

| ID | Scenario | Required result | Current status |
| --- | --- | --- | --- |
| VBUILD-PLACE-01 | Generate each building in every supported village biome and rotation. | Road connection, terrain fit, bounds, palettes, and interiors are correct. | Partial — all 25 templates load and all five types pass rotated real-placement tests; natural-village visual review remains manual |
| VBUILD-MARKER-01 | Revisit, clip, save, and reload a building anchor. | One persistent identity; no duplicate guard, Armory, loot, or post action. | Partial — shared anchor identity, strict persistence, and at-most-once garrison claims implemented; Armory and stock actions remain later steps |
| VBUILD-BARRACKS-01 | Generate Barracks across all difficulties. | Bounded role/loadout-aware garrison and no partial issuance. | Partial — bounded Easy/Normal/Hard datapack group, atomic entity creation, and one-shot claim implemented; natural terrain gameplay review remains manual |
| VBUILD-ARMORY-01 | Generate the village Armory/Quartermaster. | Existing lock, loot, ammo, and resupply contracts remain authoritative. | Partial — biome-aware shell and anchor implemented; Armory placement/stock is a later step |
| VBUILD-SENTRY-01 | Generate roadside Sentry Posts. | Small footprint, bounded staffing, and safe road clearance. | Partial — one-guard difficulty-aware staffing and safe-cell validation implemented; natural road-clearance review remains manual |
| VBUILD-TOWER-01 | Generate integrated and standalone towers in one world. | Independent controls/identity; no double processing or shared start state. | Partial — independent integrated upper-platform staffing and standalone controls implemented; natural mixed-generation review remains manual |
| VBUILD-YARD-01 | Generate Training Yards. | No unsafe combat target or recurring training controller is introduced. | Partial — passive shell and anchor implemented; no entity or controller is present |
| VBUILD-COMPAT-01 | Run with common village-overhaul datapacks/mods. | Third-party entries and weights remain intact; missing/replaced pools skip safely. | Manual test required |
| VBUILD-PERF-01 | Generate at least 100 villages and profile startup/generation. | Startup work is bounded and generation adds no recurring server-tick cost. | Performance test required |

## Manual visual checklist

- [ ] Every palette matches its vanilla village biome.
- [ ] Every connector meets a road without a blocked doorway or floating path.
- [ ] All rotations preserve beds, doors, workstations, fences, ladders, and
  collision clearance.
- [ ] Barracks, Armory, Sentry Post, integrated Watchtower, and Training Yard
  are visually distinct and readable.
- [ ] Defense-building frequency feels useful without overwhelming normal
  homes and professions.
- [ ] Standalone 1.4 Watchtowers remain rare and visually/behaviorally
  independent.
