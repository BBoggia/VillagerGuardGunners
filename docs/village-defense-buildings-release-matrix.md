# Village Defense Buildings 1.5 validation matrix

This matrix is the retained gate for the five village-attached defense
buildings. “Pass” requires evidence against the exact candidate; source review
alone is recorded as “Partial.” Steps 1 through 3 intentionally leave visual,
marker, lifecycle, gameplay, and mixed-mod generation rows incomplete.

## Baseline

- Branch: `v1.5-support`
- Development version: `1.5-dev`
- Inherited 1.4 commit: `be63f09fc9697666aa67a8eb66055214fac2d568`
- Inherited 1.4 JAR SHA-256:
  `2ebf9920e7916bc9d7e81e85a55c522020e7149eaabf74d2354bfe6c838e59ac`
- Functional VGG village templates active: no
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

## Automated architecture and integration cases

| ID | Scenario | Required result | Step 3 status |
| --- | --- | --- | --- |
| VBUILD-BASE-01 | Inspect version, branch contract, inherited commit, and checksum. | Every identity matches the recorded baseline. | Pass |
| VBUILD-SCHEMA-01 | Enumerate building types and reserved paths. | Exactly the five reviewed stable building IDs are exposed; the probe is not a building type. | Pass |
| VBUILD-LIMIT-01 | Submit invalid weight, duplicate IDs, more than five pools, or more than 25 entries. | Publication rejects the batch before touching a pool. | Pass |
| VBUILD-CFG-01 | Start with `villageBuildings.enabled=false`. | No pool registry inspection or mutation occurs. | Pass |
| VBUILD-CFG-02 | Disable any one schema-13 building-type switch. | Matching datapack entries are suppressed without rewriting the accepted snapshot or affecting other types. | Pass — JVM config/filter tests |
| VBUILD-DATA-01 | Parse format-1 definitions, including pack-owned namespaces. | Only the seven reviewed fields and five stable types compile in deterministic entry-ID order. | Pass — JVM parser tests |
| VBUILD-DATA-02 | Submit duplicate keys, trailing JSON, deep/oversized documents, future formats, bad IDs/projections, or fractional/out-of-range weights. | The strict bounded reader rejects the candidate before pool mutation. | Pass — JVM parser tests |
| VBUILD-DATA-03 | Reload one invalid document after a valid publication. | The complete invalid candidate is rejected; generation and last-known-good definitions remain unchanged. | Pass — JVM publication tests |
| VBUILD-DATA-04 | Run `/reload` after startup. | The accepted snapshot updates, but live template pools are not removed, reordered, or rebuilt; restart is documented as required. | Pass — architecture contract; live restart test remains manual |
| VBUILD-POOL-01 | Inject the plains probe into the vanilla plains houses pool. | Existing raw and flattened entries retain identity/order; one weight-1 VGG element is appended. | Pass — live Forge registry test |
| VBUILD-POOL-02 | Repeat injection in the same server. | Pool sizes do not change; the report records one already-present entry. | Pass — live Forge registry test |
| VBUILD-POOL-03 | Target a missing pool or processor list. | The entry is skipped, other entries remain eligible, and one bounded report records the omission. | Pass — live Forge registry test |
| VBUILD-POOL-04 | Inspect the probe template's jigsaw metadata. | The existing inert template has a connector to the plains street pool. | Pass — live Forge template test |
| VBUILD-CODEC-01 | Encode and decode the custom pool element. | Entry ID, building type, template, processors, and projection round-trip. | Pass — live polymorphic codec test |
| VBUILD-RESTART-01 | Stop and start a second integrated server process. | The new registry receives one entry; no static prior-server state suppresses it. | Partial — implementation is content-based; lifecycle run pending |
| VBUILD-OLDWORLD-01 | Load an existing 1.4 world with integration enabled. | Existing starts/chunks are unchanged; only newly selected pieces can use appended entries. | Manual test required |

## Later functional and compatibility cases

| ID | Scenario | Required result | Current status |
| --- | --- | --- | --- |
| VBUILD-PLACE-01 | Generate each building in every supported village biome and rotation. | Road connection, terrain fit, bounds, palettes, and interiors are correct. | Not implemented |
| VBUILD-MARKER-01 | Revisit, clip, save, and reload a building anchor. | One persistent identity; no duplicate guard, Armory, loot, or post action. | Not implemented |
| VBUILD-BARRACKS-01 | Generate Barracks across all difficulties. | Bounded role/loadout-aware garrison and no partial issuance. | Not implemented |
| VBUILD-ARMORY-01 | Generate the village Armory/Quartermaster. | Existing lock, loot, ammo, and resupply contracts remain authoritative. | Not implemented |
| VBUILD-SENTRY-01 | Generate roadside Sentry Posts. | Small footprint, bounded staffing, and safe road clearance. | Not implemented |
| VBUILD-TOWER-01 | Generate integrated and standalone towers in one world. | Independent controls/identity; no double processing or shared start state. | Not implemented |
| VBUILD-YARD-01 | Generate Training Yards. | No unsafe combat target or recurring training controller is introduced. | Not implemented |
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
