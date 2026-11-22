# Village expansion validation

Use this procedure to validate village population, automatic coverage,
Quartermaster service, medical care, casualty recovery, and transport together.
A passing automated matrix is development evidence. Public release also requires
the client, dedicated-server, compatibility, and balance checks below.

## Reproduce the automated matrix

Use Python 3.9 or newer on macOS/Linux, Java 17, and the dependency versions in `gradle.properties`. Once Gradle's
dependencies are cached, run from the repository root:

```sh
python3 -m unittest discover -s tools/tests -v
python3 tools/validate_village_expansion.py
```

The runner invokes four separate Gradle processes, in order:

1. `cleanTest check runGameTestServer` for fresh JVM results, packaged-artifact
   checks, and native correctness tests.
2. `runGameTestServer -PvillageIntegrationProbes=true` for instrumented world,
   POI, population, and movement checks.
3. `runGameTestServer -Pstep13Performance=true` for the combined combat,
   logistics, medical, and transport workloads.
4. All seven performance report gates, including medic AI and transport.

The Build workflow also offers a `village_validation` checkbox for manual CI
runs. It warms dependencies before the offline matrix and retains evidence even
on failure. Ordinary CI runs the evidence parser tests alongside `check`.

Each matrix process uses `--offline --no-daemon`. Do not run another Gradle build,
client, or server alongside this matrix: the variants share generated resources
and test worlds. The runner excludes concurrent copies of itself. It does not
lock separately launched Gradle commands.

Each stage has a 900-second wall-clock limit. On timeout or interruption, the
runner terminates its descendant process groups, including Gradle's single-use
daemon and the test server, and marks the run failed. Independently launched
clients and servers are outside that process tree.

Evidence goes into a new directory under `build/reports/village-expansion/`.
The manifest records the JAR SHA-256, source hashes, dependency versions,
commands, exit codes, test counts, and hashes of retained logs, JVM XML results,
performance JSON, and JFR recordings. A nonzero exit, missing native completion
marker, failed or skipped JVM test, or changed source/JAR fails the run. Failed
runs retain their logs and do not replace `latest-passed.txt`. Available performance
reports and JFR recordings are copied immediately after the performance stage,
including on failure, so a later report-gate failure or rerun cannot erase them.

Verify a retained run before using it:

```sh
python3 tools/validate_village_expansion.py --verify build/reports/village-expansion/<run>/manifest.json
```

Verification rejects changed sources, changed evidence, and a different JAR.
It never changes a report's original artifact identity. `latest-passed.txt`
identifies the most recent successful run; it is not evidence that the current
working tree still matches that run. Archive the run directory outside `build/`
before cleaning the project, then use its new manifest path to verify it.

## Reproduce the integrated client scenario

After the automated matrix exits, run this on a machine with a graphical display:

```sh
python3 tools/run_village_client_qa.py
```

The harness copies the generated `run-step13-performance/world` into
`build/village-client-qa/saves/VillageValidation`, opens that isolated world,
constructs a test scene, and exits after an explicit pass or failure. It replaces
only its disposable copy, never a normal player save. Do not use the test scene
as a persistent world. A fresh performance run is required after cleaning the project.

The scenario transfers four real bandages from a Quartermaster station to a
medic, downs an ally holding a loaded TaCZ AK-47, observes automatic first aid
and physical cot transport, and waits for automatic revival. It checks exact
gun/pouch contents, the original patient, two consumed doses, released transport
ownership, and synchronized client states. Four screenshots, the client log,
and a manifest with artifact/evidence/harness hashes remain under
`build/village-client-qa`. Inspect the screenshots for rendering problems;
an exit code alone cannot certify this scenario. Archive this directory before
rerunning because each client run replaces its disposable scene and evidence.

## Combined regression coverage

| Boundary | Automated evidence | Scope |
| --- | --- | --- |
| Ordinary village coverage and casualty recovery | `ordinaryVillageCoverageExcludesCasualtiesAndResumesAfterRecovery` | Lethal injury removes active coverage; disabling downing preserves low-health recovery; later healing restores eligibility without changing village membership, gun, or orders. |
| Quartermaster supplies and revival | `stationSuppliesMedicWhoRevivesOriginalArmedPatient` | Physical station stock supplies two medic doses; stabilization and revival preserve the original armed patient through an entity save/load round trip. |
| Operator loss and medical shortages | `operatorLossInvalidatesMedicalRestockWithoutMovingDoses` | A prepared medical transfer fails after profession loss, conserves exact stock, and succeeds after service is restored. |
| Ordinary village travel | Coverage integration suite | Visits both sides, resumes after combat, honors player orders, rejects missing terrain, and handles blocked/repaired routes. |
| Population and migration | Registry/population JVM and native suites | Versioned records, future-data preservation, 10,000-record indexed lookup, generation credits, cancellation, and save/load caps. |
| Downing and transport | Casualty and transport native suites | Original passenger identity, loaded-only recovery, death and totem ordering, canceled healing/mount/dismount events, finite supplies, cot delivery, and feature-disable recovery. |

These assertions exercise production methods in a transformed Minecraft runtime.
Some scenarios advance a treatment deadline or call a transfer directly to test
its boundary. They do not substitute for automatic selection, physical travel,
or observer synchronization; those have separate movement and client checks.

## Compatibility and playthrough matrix

Record the exact candidate hash, world seed/save, config, dependency and gun-pack
versions, observer count, result, and evidence for each row. Leave unexecuted
rows pending. Historical screenshots and logs are useful for diagnosis but do
not certify a changed candidate.

| ID | Environment and action | Required outcome | Status |
| --- | --- | --- | --- |
| VEXP-CLIENT | Integrated client: stock a station, designate a medic, down an armed ally, stabilize, carry to a cot, and revive. | Visible states and items agree with server state; exactly two doses, one original patient, and no copied gun. | Passed on `1.6`; retained client manifest and four reviewed screenshots in `build/village-client-qa` |
| VEXP-SERVER | Dedicated server with an operator and a second observing client; repeat treatment, transport, equipment service, and orders. | Both observers agree on equipment, animation, labels, ammo, and passenger state; no client-only classloading failure. | Pending |
| VEXP-TERRAIN | Small ordinary village with buildings disabled; then a large village with buildings enabled, broken routes, and a raid. | Staffing stays bounded; shortages are visible; medics can leave duty; casualties stop covering posts; surviving guards resume reachable coverage. | Pending |
| VEXP-RESTART | Upgrade a backed-up old world; save/restart during equipment service, downing, and carrying; disable each automatic feature. | No retroactive population credits, copied items, missing inventories, stranded passenger, or overwritten player orders. | Pending |
| VEXP-PACK | Default TaCZ pack plus a named third-party gun pack; inventory automation, Player Animator both absent/present, and representative NPC/death/healing mods. | Exact stock, native gun behavior, cancellation, ally policy, rendering, and true-death outcomes remain correct. Record unsupported combinations explicitly. | Pending |
| VEXP-SCALE | Same-machine/seed baseline and candidate: 4/8/16/32 guards per village, 100/400 across villages, 1,000/10,000 historical records, crowded care, blocked paths, and chunk bursts. | No feature-caused chunk loads; bounded queues/paths/queries; no repeated idle saved-data writes; measured p95/p99, allocation, queue age, service latency, packet volume, and baseline deltas. | Pending full scale comparison |

Native GameTests use a dedicated test server, but that does not establish a
normal dedicated-server startup with two authenticated clients. An entity NBT
round trip does not establish an operating-system restart or old-world upgrade.
The performance matrix includes stationary transaction batches and real combat;
its timings do not establish 400-guard village capacity or a universal TPS claim.

## Defaults and release decision

Keep the current opt-in defaults until the playthrough and scale rows
justify release tuning. Automatic coverage and managed generation currently
start disabled. Players can opt in to each independently; ordinary villages do
not require Village Defense Buildings. Equipment replacement requires the
guard's service policy and replacement permission. Medic duty is explicit.
Medical treatment and medic AI are enabled, while downing and transport remain
disabled by default.

When selecting public defaults, enable automatic coverage for eligible
uncommanded residents only after the terrain and scale checks pass. Preserve
existing saved opt-outs and do not infer opt-in from missing old configuration
fields. Managed generation must remain limited to newly generated villages.
Downing and transport remain separate opt-ins. Retain finite physical supplies,
35% retreat / 75% recovery hysteresis, and the current bounded work limits unless
recorded gameplay and profiling evidence support a change.

The automated manifest deliberately reports `release_ready: false` and lists
outstanding release checks. Complete this matrix and the existing
[release checklist](release-checklist.md), then choose the public version, rebuild,
rerun against that exact artifact, and complete the existing publication gates.

## Interactive combat playground

Use the [Village Defense Playground](village-defense-playground.md) for a
resettable superflat village with a firing line, an empty-ammo guard, two
casualties, medics, a staffed Quartermaster, and attacks from the east. Its
control book lets a player start and replay the exercise.
