# Phase 8: Validation and Performance Release Gate

Phase 8 turns the behavior and performance requirements from Phases 0–7 into repeatable release evidence. Correctness GameTests and the opt-in scalability suite now live in development-only source sets, so none of the test code is packaged in the production mod.

## Automated release boundary

`./gradlew check` now runs the unit/source contracts, compiles both development GameTest suites, builds and reobfuscates the release JAR, and verifies the artifact itself. The artifact check requires the runtime classes, metadata, mixin configuration, refmap, inventory texture, and supported Fzzy Config integration. It rejects GameTest classes, the old projectile and combat simulation, the behavior tree/reload systems, legacy config classes, `.DS_Store`, a timestamped manifest, unexpected dependency declarations, and missing common-server mixin mappings.

The suites are deliberately isolated:

- `src/gametest` contains 73 correctness GameTests and is selected by the normal `runGameTestServer` task.
- `src/perftest` contains 17 sequential scalability workloads and is selected only with `-Pphase8Performance=true`.
- `src/main` contains production code only.
- correctness tests use a generated 40×26×26 arena so fixtures outside the old 1×1×1 marker cannot overlap neighboring tests.
- sequential performance workloads use distinct world regions 512 blocks apart, as well as entity/projectile/chunk cleanup.
- the performance run uses `run-phase8-performance`, deletes its generated world and old reports before each matrix, and writes fresh evidence to `build/reports/phase8-performance`.

`PhaseEightReleaseGateContractTest` prevents the source-set separation, matrix, evidence fields, config-profile reset, and artifact gate from silently being removed. `verifyPhase8PerformanceReports` then checks that all 17 JSON/JFR pairs are present, internally consistent, percentile ordered, captured with the complete default-v8 config profile, and backed by accepted native TaCZ shots, ammunition-work evidence, and exact or bounded sidearm state evidence wherever required. It parses every JFR and requires real execution samples rather than accepting a merely nonempty file.

## Correctness coverage

The automated layer now covers every correctness item that can be made deterministic with the reviewed default gun pack:

| Requirement | Evidence |
| --- | --- |
| Ammo slots accept only loose TaCZ ammunition and ammo boxes | plain JUnit handler tests |
| Crouch handoff is give-policy authorized, caliber-compatible, partial, atomic, creative-safe, inactive-AI safe, and rejects nonphysical TaCZ reserve sources | transformed interaction/transaction GameTest plus source contracts |
| Passive dropped-ammo pickup is staggered, radius-bounded, caliber-compatible, partial, delay/target-safe, config-controlled, and never adds guard item-seeking | transformed item-tick/transaction GameTest plus source contract |
| Emergency sharing is native-dry-triggered, radius/owner bounded, loose-ammo-only, donor-reserve-safe, transactionally conservative, and has no balancing tick | transformed dry-reload/sharing GameTest plus arithmetic and source contracts |
| Ammo save/load, old slot migration, and rollback-format deduplication | migration unit tests plus real Guard save/load GameTests |
| Zombie/lightning guard conversion preserves isolated ammo and pending overflow | transformed conversion GameTest covering both upstream discard paths |
| Natural-loadout identity survives save/load, reconstructs cap state, rejects replacement/tampering, and enforces the live nearby cap after reload | strict codec/diagnostic unit tests plus three transformed Guard/ItemStack/spawn-policy GameTests |
| Gun binding changes only with the held weapon | controller contracts plus real reload/charge/bolt weapon-swap GameTests |
| Every TaCZ `ShootResult` has an explicit transition | source contract over the complete result switch |
| AUTO, HOLD, and DELAY charge/reset progression | deterministic trigger-state unit tests |
| Corridor/spread/movement intersection geometry | geometry unit tests |
| Direct friendly hits and projectile explosions are protected | transformed Forge/TaCZ GameTests |
| Swap during reload, charge, or manual bolt | real operator/weapon GameTests |
| Target death during a native burst | real M16 burst GameTest plus cancellable per-round `GunFireEvent` coverage |
| Permanently dry gun yields to Guard Villagers melee | real free-will Guard goal GameTest |
| Native fire mutates the bound gun without redrawing it per shot | real Glock firing GameTest |
| Preferred TaCZ range does not incorrectly cap firing | real guard-goal TaCZ test beyond preferred range and within the configured maximum |
| Tactical profiles alter movement | real Glock/Skirmisher approach and M95/Marksman retreat GameTest |
| Tactical profiles alter target-acquisition and aim gates | real M95/Marksman test proving its eight-tick visibility requirement and 6.5-degree tolerance before native fire |
| Tactical profiles impose a maximum firing cap beneath the global cap | real Glock/Skirmisher test refusing a 30-block shot and resuming native fire after entering its cached window |
| Explosive weapons enforce their calculated minimum standoff | vertically isolated real RPG-7 test refusing an eight-block shot and resuming native fire beyond ten blocks |
| Sidearms switch only for reviewed tactical reasons and return with lifecycle/time/distance hysteresis | real dry-primary and RPG-7 transformed GameTests plus policy, runtime-ordering, persistence, and migration contracts |
| Sidearm ammunition handoff, pickup, sharing, and summaries recognize both stable weapon roles | real two-caliber AK-47/Glock GameTest plus bounded source contracts |
| Sidearm configuration and recovery preserve authorization, shield/offhand state, target changes, active save/death ownership, and external main-hand replacement | transformed menu/lifecycle GameTests plus schema-3 token and source contracts |
| Format-3 natural sidearms issue atomically with one full ammo stack per role, preserve primary provenance while stowed, and never displace an existing sidearm | strict parser/diagnostic contracts plus published-pool, capacity, occupied-slot, activation, and return GameTests |
| Administrator inspection command is registered and executable | transformed command-dispatch GameTest against a real Guard |
| Lag does not create a firing backlog and per-tick work is capped | cadence unit tests and source contracts |
| A blocked lateral reposition cannot loop forever in one direction | progress-helper unit test and bounded reversal source contract |

The native firing assertion checks TaCZ's gun state, ammunition consumption, shot timestamp, and the identity of the bound stack. It also confirms that continued fire does not change `drawTimestamp`, guarding against an expensive draw/property rebuild per shot.

## Performance harness

Run the complete sequence in this order:

1. `./gradlew clean test build`
2. `./gradlew runGameTestServer`
3. `./gradlew runGameTestServer -Pphase8Performance=true`
4. `./gradlew verifyPhase8PerformanceReports`
5. `./gradlew assembleReleaseCandidateEvidence`
6. `./gradlew verifyAmmunitionReleaseCandidate`

The final task requires the artifact and performance-report gates to pass and
creates `build/reports/release-candidate/release-candidate.json` plus a
conventional SHA-256 file. The manifest is deterministic: it contains no
timestamp or machine-specific absolute path. It records the exact runtime
versions, artifact name, byte size and digest, evidence counts, the sorted name,
byte size, and SHA-256 digest of every JSON/JFR evidence file, and the
separate correctness/manual validation commands. Every performance JSON also
records the SHA-256 of the production JAR built before its run; verification
rebuilds that JAR and requires an exact match. The verifier rejects unexpected
scenario names, stale captures from another artifact, and malformed JFRs so
unrelated evidence cannot be silently bundled with a release candidate.

The final aggregate task is run without `-Pphase8Performance`. It consumes the
separately generated performance evidence, executes the normal 73-case
correctness GameTest server, rebuilds the deterministic manifest, verifies that
pickup and sharing still have independent rollback controls, and confirms that
common production bytecode has no Minecraft client-class references. The
schema-4 manifest links the ammunition guide and manual worksheet and records
the path, size, and SHA-256 of the migration guide and every copy-ready example
datapack file. Neither the task nor manifest claims that hands-on multiplayer
rows have passed.

The public `prepareCurseForgeRelease` packaging task also runs
`verifyManualReleaseChecklist`. It fails while the checklist contains a
`Not run`/`Failed` table row or an unchecked checklist item, preventing the
automated evidence boundary from being mistaken for completed hands-on release
validation.

Each performance workload creates real Guard Villagers, native TaCZ guns, targets, projectiles, safety queries, goal selectors, and entity ticks. It warms for 200 ticks and samples 1,000 ticks. The longer warmup prevents whichever unordered Forge batch runs first from measuring one-time class loading and JIT work as steady-state guard cost. Every scenario produces p50/p95/p99/max MSPT, aggregate and per-guard post-warmup native shot/ammunition evidence, live TaCZ bullets, GC count, heap delta, the complete active guard-gunner config snapshot, and a focused JFR recording using the default preset plus explicit 5 ms execution sampling. The narrower preset avoids a JDK 17 JFR metadata race seen with the broad profile preset while retaining CPU attribution. Total ammunition includes the gun and dedicated guard inventory so native reloads cannot hide consumption. Every firing workload must exercise at least 80% of its guards with evidence from each active guard; this prevents one guard from satisfying a many-guard scenario without making intended safety suppression a flaky failure. An end-of-sample state for every guard records its goal decision, aim state, line of sight, safety result, held items, and last TaCZ result after timing has ended, making intermittent participation failures actionable without allocating in the measured combat loop. Each scenario discards its owned native bullets after recording the end-of-sample count so later batches remain isolated. The test task removes its generated config before launch, and report verification rejects any scenario that did not use the default-v8 values, including every natural-loadout policy value, tactical mapping, profile value, rules-of-engagement control, explosive-standoff control, modpack ally default, and ammunition quality-of-life control.

The two ammunition pressure workloads target the bounded query paths directly.
One keeps 100 compatible dropped-ammunition entities beside guards whose three
ammo slots are full, so the staggered item-driven rejection path remains active
through the complete sample. The other places 50 empty, armed guards in compact
rows with targets, requiring every guard to record a native-dry sharing attempt
while the existing 20-tick retry window prevents a per-tick search. Both have
same-run baseline-relative p95/p99 budgets and explicit report evidence gates.

Three sidearm workloads cover the final runtime boundary without adding a
synthetic scheduler or recurring scan. Fifty guards receive dry primaries and
acquire their targets together at the warmup/sample boundary, proving a bounded
simultaneous transition into native TaCZ sidearm fire. A mixed group places ten
RPG guards inside explosive standoff and ten outside it, requiring exactly the
near half to use sidearms while the far half retain their launchers. The idle
case equips 100 guards with both weapon roles but no target, requiring all 100
loadouts to remain present with zero active sidearms for the complete sample.
Reports record the configured-loadout count, active-sidearm count, accepted
range, observed first activation tick, active gun identity, expected tactical
role, accepted shots, and final sidearm state for every guard. Verification
requires the mixed near/far group to finish with the correct Glock/RPG mapping
and at least 80% firing participation independently in both roles. The dry
group's observed activations must occur after target release and within a
bounded 200-tick window. The idle sidearm case now uses the same Glock primary
as `idle-armed-100` and is compared directly to that scenario with a tighter
p95/p99 budget, isolating configured-sidearm overhead from weapon-type cost.

Forge's GameTest server runs as fast as possible, while TaCZ fire and reload timing use wall-clock milliseconds. The harness therefore advances TaCZ's public cooldown timestamps at a nominal 20 TPS weapon cadence and advances an already-active native reload timestamp. It does not start or complete reloads itself, create projectiles, deal damage, emulate scripts, or bypass the addon controller. The explosive gate additionally requires at least twice as many rounds as guards, proving the RPG group reloaded from the dedicated inventory instead of ending after its preloaded shots. Results are appropriate for same-machine regression comparisons and profiler attribution, not as an absolute public-server capacity claim. Reports therefore retain `absoluteTimingGateApplied: false`, while `relativeTimingGateApplied: true` records that each workload's p95 and p99 must stay within a deliberately generous multiplier-plus-allowance budget derived from the 100-guard unarmed baseline in the same run. This catches major regressions without pretending different hardware has one portable MSPT ceiling.

All hostile targets are invulnerable, gravity-free, position-anchored, and knockback-resistant inside their prepared lanes so sustained native fire cannot push a fixture behind uncleared terrain and turn participation into a random geometry failure. The intentionally stationary clear-lane guards also receive fixture-only knockback resistance inside widened prepared corridors; movement speed was already zero there, and movement-heavy obstacle/protected-lane workloads remain free to navigate. Scenario-owned warmup bullets are discarded before timing begins so the measured sample starts clean without bypassing or emulating any firing path. The protected-lane workload moves 25 invulnerable, gravity-free villagers back and forth across their corresponding guard-to-target corridors. Each deterministic cycle dwells outside the corridor on both sides before crossing, guaranteeing repeated open windows without removing the moving-friendly workload. Making the fixtures invulnerable keeps all lanes present for the full sample; the normal native pre-fire safety logic still decides whether the guard may shoot. Its widened floors retain real lateral repositioning, and the 80% active-guard floor proves many independent lanes reach an open portion instead of allowing one active guard to satisfy the whole workload.

## Post-sidearm-hardening reference run: 2026-08-30

All 17 workloads passed after the completed sidearm, ammunition deposit, dropped-ammo
pickup, emergency-sharing, tactical-profile, modpack-ally, rules-of-engagement,
natural-loadout, fixture-isolation, and evidence-provenance integration. Every
report is bound to production artifact
`40677abe8084621a8ea29ef80d37359edcaf37c3d3583a00d89ef2f3d9dd8ab0`. The run
used an Apple-silicon development machine and Java 17.0.12. Times below are
total GameTest server work, not add-on-only time.

| Scenario | Guards | Active | Avg ms | p95 ms | p99 ms | Max ms | Native shots / ammo | Sidearms configured / active | Live bullets | GC |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Unarmed idle baseline | 100 | 0 | 0.994 | 1.071 | 1.174 | 19.631 | 0 / 0 | 0 / 0 | 0 | 0 |
| Armed idle | 100 | 0 | 1.073 | 1.405 | 1.745 | 9.519 | 0 / 0 | 0 / 0 | 0 | 1 |
| Armed idle with sidearms | 100 | 0 | 1.284 | 1.439 | 1.648 | 6.361 | 0 / 0 | 100 / 0 | 0 | 1 |
| Semi-auto | 10 | 9 | 1.021 | 1.122 | 1.421 | 5.929 | 2,993 / 2,993 | 0 / 0 | 0 | 2 |
| Semi-auto | 25 | 24 | 1.008 | 1.285 | 1.762 | 6.448 | 7,972 / 7,972 | 0 / 0 | 0 | 1 |
| Semi-auto | 50 | 45 | 1.683 | 2.527 | 3.433 | 42.495 | 14,947 / 14,947 | 0 / 0 | 0 | 1 |
| Automatic | 10 | 9 | 0.884 | 1.027 | 1.756 | 8.466 | 4,482 / 4,482 | 0 / 0 | 9 | 1 |
| Automatic | 25 | 23 | 1.443 | 1.729 | 2.136 | 6.402 | 11,462 / 11,462 | 0 / 0 | 22 | 2 |
| Automatic | 50 | 49 | 1.711 | 1.952 | 2.275 | 7.672 | 24,422 / 24,422 | 0 / 0 | 49 | 3 |
| Automatic shotgun | 10 | 10 | 1.009 | 1.300 | 1.574 | 6.000 | 2,490 / 2,490 | 0 / 0 | 0 | 1 |
| Explosive | 10 | 9 | 2.062 | 6.665 | 6.843 | 18.060 | 1,123 / 1,123 | 0 / 0 | 0 | 2 |
| Mixed explosive standoff | 20 | 17 | 1.977 | 5.937 | 6.315 | 11.837 | 2,122 / 997 | 20 / 10 | 0 | 2 |
| Simultaneous dry-primary switch | 50 | 43 | 1.915 | 2.124 | 2.345 | 6.733 | 21,344 / 0 | 50 / 48 | 43 | 4 |
| Obstacle navigation | 25 | 0 | 1.288 | 1.674 | 2.281 | 5.969 | 0 / 0 | 0 / 0 | 0 | 1 |
| Crossing friendlies | 25 | 24 | 1.389 | 1.806 | 2.372 | 7.970 | 11,941 / 11,941 | 0 / 0 | 24 | 1 |
| Persistent dropped ammo | 100 | 0 | 1.437 | 1.641 | 2.263 | 14.321 | 0 / 0 | 0 / 0 | 0 | 2 |
| Dry sharing pressure | 50 | 0 | 1.607 | 2.008 | 4.107 | 8.109 | 0 / 0 | 0 / 0 | 0 | 2 |

The native-shot column is sourced from completed, non-cancelled server-side
TaCZ `GunShootEvent` instances per guard. This avoids TaCZ's mutable operator
`shootCount`, which can reset during reloads and previously made an actively
firing guard appear idle. The gate also retains ammunition consumption as an
independent cross-check; shotgun ammunition remains shell-level while its native
shoot events are trigger-level. The dropped-ammo workload retained all 100
compatible item entities beside full guards for the complete sample. The dry
sharing workload recorded an attempted sharing search from all 50 guards. A
sidearm transition deliberately leaves the report's ammunition-delta reference
bound to the original primary stack: the dry-switch case therefore reports zero
primary ammunition consumed, and the mixed case's ammunition column counts only
RPG rounds. Accepted server `GunShootEvent` counts, per-guard shot evidence, and
the verified role mapping and final sidearm states establish native sidearm
participation. The dry group activated 48 sidearms between observed test ticks
201 and 221. The mixed group held the exact ten/ten Glock/RPG split and recorded
accepted shots from nine sidearm-role guards and eight primary-role guards. A
negative heap delta in the raw JSON means a GC reclaimed more pre-existing heap
than the scenario allocated; it is not negative allocation. Armed idle averaged
0.079 ms above the 100-guard unarmed baseline, including TaCZ operator and held-
gun work as well as this add-on. The otherwise identical 100-guard configured-
sidearm case recorded zero activations, added 0.211 ms average and 0.034 ms p95,
and remained 0.097 ms lower at p99. It stayed well inside the tighter direct
armed-idle budget; the percentile inversion is ordinary run-order/JVM noise.

Natural-loadout selection is deliberately absent as a recurring workload: it
runs once at an eligible spawn, with authentication at load/save and inside the
already-lazy cap predicate. Deterministic unit tests and transformed GameTests
cover that event path, including query avoidance, atomic issuance, reload
authentication, and live cap enforcement. Adding a synthetic per-tick spawn
loop here would measure a workload the released mod does not perform.

All 17 retained JFR captures contain server-thread execution samples. These
short profiles are directional evidence for later inspection, not a substitute
for a long production-server capture. Every workload remained below 6.9 ms p99;
the 100-guard sidearm-idle case remained below 1.7 ms p99, the simultaneous
50-guard switch case remained below 2.4 ms p99, and the exact-split mixed
explosive case remained below 6.4 ms p99. The 100-entity dropped-ammo pressure
case remained below 2.3 ms p99, and the 50-guard dry-sharing pressure case
remained below 4.2 ms p99. The verifier's
same-run, workload-specific budgets retain a regression ceiling without treating
one development-machine run as a portable server-capacity guarantee.
Navigation-heavy real-server profiling therefore remains worthwhile before
calling a large battle configuration production-ready.

## Remaining manual compatibility matrix

The default TaCZ pack does not provide a representative gun for every extension point, and headless GameTests cannot validate client animation or observer synchronization. These checks remain required before a public release:

The executable pass/fail worksheet is maintained in `docs/release-checklist.md`.

| Case | Automated coverage | Manual release check still needed |
| --- | --- | --- |
| Semi-auto rifle/pistol | native Glock firing | observe sustained combat, reload, sound, and ammo UI |
| Full-auto rifle | AK-47 workloads at 10/25/50 | observe animation and synchronization under sustained fire |
| Native burst | real M16 burst and target-death cleanup | observe a complete burst and mid-burst target/swap transitions |
| Shotgun/multiple projectile | native AA-12 workload | verify pellet impacts, sound, and animation with a client |
| Manual bolt/lever/pump | real bolt state and swap cancellation | run one representative gun of each action type through repeated cycles |
| Heat-limited weapon | `OVERHEATED` transition and bounded retry contracts | test a real heat-enabled gun through overheat and recovery |
| Inventory-fed gun | shared ammo capability and native reload delegation | test a representative minigun/inventory-fed datapack gun and ammo box |
| Explosive weapon | RPG workload and explosion backstop | observe safe and unsafe blast placement around villagers, guards, golems, and owner |
| Sidearm lifecycle | dry-primary, explosive-standoff, storage, recovery, and two-caliber GameTests plus three performance workloads | verify main-hand synchronization, draw sound, native reload/animation, shield state, reconnect/restart, third-party pistols, and live config changes |
| Charged AUTO/HOLD/DELAY gun | deterministic TaCZ charge semantics | test representative third-party definitions because the default pack lacks all three |
| Attachments altering ammo/spread/explosion | native property lookup and conservative safety bounds | test representative attachment combinations and datapack reloads |
| Client models | narrow normal/Steve model mixins | verify idle, aim, fire, reload, bolt, movement, crouch, swim, eat, and death poses |
| Optional animation mod | client-only compatibility boundary | repeat models with Player Animator present and absent |
| Observers/network | server-authoritative native path, no addon shot packet | repeat with one and multiple observing clients and watch animation, sound, ammo, bandwidth, and lag-back |

For the observer/performance pass, use a normal dedicated server at a fixed view distance and fixed JVM settings, warm each population before capture, and compare 10/25/50 guard runs with the same world and targets. Record server p95/p99 MSPT, client frame time, network traffic, live projectiles, GC/allocation, and a long JFR or profiler capture. Do not compare results from different gun packs, attachment sets, worlds, or hardware as though they were the same benchmark.

## Release interpretation

The automated gate establishes that the addon remains a narrow bridge to TaCZ: no custom packets or projectiles, no per-shot draw/property rebuild, no firing backlog, bounded visibility/path/safety work, no combat-path logging spike, development tests excluded from the JAR, a reproducible default config profile, and low sampled add-on leaf time in the reference matrix. A release candidate is ready only after the remaining manual client, third-party gun, and multi-observer rows above are checked against the exact modpack being shipped.
