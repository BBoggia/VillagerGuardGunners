# Release Candidate Checklist

This checklist records the work that cannot be inferred from a successful
compile alone. Do not mark a public release ready until the automated gates and
the relevant manual rows pass against the exact files being distributed.

## Candidate identity

- Version: read `mod_version` in `gradle.properties`. It currently remains `1.7`;
  the accuracy work is Unreleased and needs a new public version before upload.
- Minecraft: `1.20.1`
- Forge: `47.4.10` minimum; compatible 47.x releases
- Guard Villagers: `1.20.1-1.6.19`
- TaCZ: `1.1.8-hotfix`
- Fzzy Config: `0.7.6+1.20.1`
- Kotlin for Forge: `4.12.0`
- Automated baseline gun pack: TaCZ Default Gun Pack bundled with TaCZ
  `1.1.8-hotfix`. Record additional tested pack versions with candidate evidence.
- Candidate JAR SHA-256: use the checksum shipped with the candidate being tested.
  The earlier 1.7 preparation hash does not identify the current Unreleased build.

## Guard accuracy candidate

Use the [accuracy configuration guide](configuration.md#guard-accuracy-and-player-combat)
and [validation results](guard-accuracy-validation.md) for the completed accuracy
work. Its local review bundle contains the mod JAR, the aiming-section example,
documentation, and checksums. It retains the current build version for local
testing; it is not a replacement upload for published 1.7 release files.

Confirm zero-error/eye-level defaults and disabled player overrides, the 2°
center-mass starting example, current dependency metadata, no observer classes
in the production JAR, and packaged client/server smoke evidence. Keep artifact
hashes with each result. Text-only candidate updates do not require repeating
unchanged balance trials; identify which artifact produced those measurements.

## Candidate evidence

Use the checksum in the prepared candidate bundle's `SHA256SUMS.txt` to identify
its exact files. Earlier development reports do not certify changed artifacts.
The checked rows below are historical preparation results and apply only to
their recorded artifact, not automatically to the current Unreleased JAR.
Current preparation results are recorded with the candidate review evidence;
manual, multiplayer, upgrade, performance, and remote CI checks still require
matching evidence before public release.

Build a local review bundle with `./gradlew prepareReleaseCandidate`. It contains
the mod JAR, 25 individual player datapacks, five all-buildings alternatives,
release notes, documentation, and checksums under `build/distributions/release-candidate/<version>/`. This task
runs `check` and release-identity verification. It does not mark pending manual
rows complete or bypass the public `prepareCurseForgeRelease` gates.

See [building pack installation](building-pack-installation.md) for selecting
provider packs. Preview and QA packs are excluded from the review bundle.

## Automated gates

- [x] Combined packs match all individual resources exactly. A copied 1.7
  world passed migration from 25 individual packs to five combined packs,
  CTOV lookup, reload, and full restart on ordinary packaged Forge.

- [x] Packaged dedicated Forge startup, all nine village mods and 25 player
  packs, CTOV structure lookup, datapack reload, and full process restart.
  This covers loading and persistence, not multiplayer playthrough.

- [x] `./gradlew cleanTest prepareReleaseCandidate` — Java tests, resource and
  packaged-artifact checks, release identity, 25 individual pack builds, and
  five combined pack builds.

Run the [village expansion validation](village-expansion-validation.md) matrix.
Its manifest records fresh counts and artifact identity; do not carry checked
boxes forward from an older candidate.

- [x] `python3 -m unittest discover -s tools/tests -v`
- [ ] `python3 tools/validate_village_expansion.py` — JVM and artifact checks,
  native correctness tests, integration probes, combined performance matrix,
  and all seven report gates.
- [ ] Verify the retained manifest against the current source tree and JAR.
- [ ] Complete the combined village compatibility and playthrough matrix.
- [ ] `./gradlew assembleVillageDefenseBuildingEvidence` — regenerate aggregate
  release manifests for the final distribution candidate.
- [ ] `./gradlew verifyAmmunitionReleaseCandidate verifyVillageDefenseBuildingsReleaseCandidate`
  — validate aggregate release evidence for that candidate.
- [x] Build the final versioned JAR and record its SHA-256.
- [ ] Confirm the CI build passes from a clean checkout.

The normal GameTest suite includes a native Glock firing case with a target
beyond its profile-adjusted preferred range but within the configured maximum
range, real Skirmisher approach and Marksman retreat coverage, and a live
Marksman firing case that exercises its profile-adjusted visibility and aim
requirements. It also includes strict natural-loadout issuance/provenance
failures and an end-to-end save/reload population-cap scenario.
Format-3 coverage additionally issues an AK-47/Glock pair with one complete
ammo stack per role, activates the sidearm without losing natural-primary
authentication, and proves a two-stack capacity failure leaves every state
unchanged.
The suite also covers dry-primary sidearm activation and engagement-end return,
explosive time/distance hysteresis, conservative sidearm schema migration, and
two-caliber primary/sidearm deposit, pickup, sharing, and reserve summaries.
Step 7 additionally covers the authorized sidearm menu slot, sharing before
fallback, explosive-order fallback, shield preservation, target replacement,
both weapons dry, active save/death ownership, and fail-closed external weapon
replacement.
Step 8 adds a simultaneous 50-guard dry-primary switch, an exact 10/10
RPG-standoff/sidearm split, and a 100-guard configured-sidearm idle workload.
All three are event-driven fixtures; no production scan, scheduler, thread, or
pathfinding behavior was added.

## Client and weapon behavior

Record pass/fail, the tested gun ID, attachments, and observations for each row.

| Check | Result | Gun/config and notes |
| --- | --- | --- |
| Semi-auto pistol sustained combat and reload | Not run | |
| Semi-auto rifle sustained combat and reload | Not run | |
| Full-auto sustained combat | Not run | |
| Complete native burst | Not run | |
| Target death or weapon swap during a burst | Not run | |
| Shotgun pellet impacts, sound, and animation | Not run | |
| Manual bolt action over repeated cycles | Not run | |
| Lever action over repeated cycles | Not run | |
| Pump action over repeated cycles | Not run | |
| Heat-limited gun through overheat and recovery | Not run | |
| Inventory-fed gun and ammo box | Not run | |
| Charged AUTO gun | Not run | Third-party definition required |
| Charged HOLD gun | Not run | Third-party definition required |
| Charged DELAY gun | Not run | Third-party definition required |
| Explosive shot with an open lane | Not run | Repeat with block preservation enabled and disabled |
| Explosive shot near villagers, guards, golems, and owner | Not run | Confirm hostile effects remain while allies and configured terrain are preserved |
| Attachments changing ammo, spread, range, or explosion | Not run | |
| Datapack reload while guards retain their weapons | Not run | |

## Rendering and synchronization

Test both Guard Villagers model choices where available.

| Check | Result | Notes |
| --- | --- | --- |
| Idle gun hold | Not run | |
| Partial and full aim | Not run | |
| Firing | Not run | |
| Reload | Not run | |
| Bolt/lever/pump action | Not run | |
| Walking and retreating | Not run | |
| Crouching, swimming, eating, and death | Not run | |
| Player Animator absent | Not run | |
| Player Animator present | Not run | |
| One observing client | Not run | |
| Multiple observing clients | Not run | |
| Sound, animation, ammo, and target state agree for observers | Not run | |
| Inventory combat status follows idle, aim, range, unsafe-lane, reload, action, charge, cooldown, and dry states | Not run | Hover each state and confirm its explanation; repeat with a second observing client |

## Sidearm end-to-end pass

Use the exact candidate JAR first with TaCZ's default Glock and then with at
least one eligible third-party pistol. Repeat synchronization-sensitive rows on
a dedicated server with one operating client and one observer.

| ID | Setup and action | Expected result | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| SIDEARM-VISUAL-01 | Deplete a primary during observed combat, allow the guard to draw and fire its sidearm, then end the engagement and wait for return hysteresis. | Both clients see the same main-hand weapon, draw sound, aim/fire/reload animation, ammunition state, and eventual primary restoration; no stale or duplicate model remains. | Not run | |
| SIDEARM-RELOAD-01 | Exercise a dry primary with a loaded sidearm, a partially loaded sidearm with compatible reserve, and both weapons dry. | The guard switches only when the fallback is ready, TaCZ performs the sidearm reload natively, and both-dry state remains bounded without switch/reload thrashing. | Not run | |
| SIDEARM-SHIELD-01 | Repeat dry-primary and explosive-standoff switches while the guard has a shield down and raised. | Offhand contents and use state remain synchronized; the shield never moves into private storage, duplicates, or prevents a later safe weapon return. | Not run | |
| SIDEARM-LIFE-01 | Save once with the sidearm configured and again while it is active; unload/reload the chunk, reconnect both clients, stop cleanly, and restart. | Configured sidearm, stowed primary, active ownership, TaCZ weapon state, and natural-loadout provenance restore exactly once or enter documented fail-closed recovery without item loss. | Not run | |
| SIDEARM-AMMO-02 | Equip a primary and sidearm using different calibers, then fire, reload, hand off, pick up, and share both ammo types before and after a switch. | Each caliber reaches only its compatible weapon; summaries and donor floors remain role-correct with no loss, duplication, or wrong-caliber reload. | Not run | |
| SIDEARM-PACK-01 | Repeat activation/return with at least one third-party pistol, attachment-modified capacity, and deliberately invalid, explosive, or script-only definitions. | Eligible physical pistols use native TaCZ behavior; unreadable, incompatible, and explosive sidearms fail closed without disturbing either weapon. | Not run | |
| SIDEARM-CONFIG-01 | During an active engagement, change global Hold Fire, explosive permission, RPG standoff controls, per-guard explosive/profile/range orders, and ammunition pickup/sharing settings. | The next bounded decision reflects the authoritative setting; no change bypasses ally/ROE safety, strands a weapon, restarts a burst, or adds a recurring scan. | Not run | |
| SIDEARM-RECOVERY-01 | Create a recovery-required layout using an active-sidearm save followed by an external main-hand replacement, open the guard inventory as an authorized player, and select **Recover Weapons** with both empty and full player inventory space. Repeat with a future-version record containing one recognizable gun. | Combat status shows **Recovery Required**; normal weapon slots remain locked; the action keeps the current main-hand item, removes only the add-on ownership marker, returns or safely drops every recognizable private gun exactly once, clears the unreadable/contradictory record, and restores normal slot and AI operation. | Not run | |

## Per-guard orders

Use the exact candidate JAR on an integrated server and a dedicated server.
Repeat synchronization-sensitive rows with a second observing client.

| ID | Setup and action | Expected result | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| ORDER-UI-01 | Open a new guard and an existing pre-feature guard at the smallest supported window and several GUI scales. Cycle every order through all values. | Both guards begin at Inherit/Auto; the panel, translated values, and tooltips fit without covering slots or Guard Villagers' follow/patrol controls. | Not run | |
| ORDER-AUTH-01 | Attempt changes as an authorized nearby player, an unauthorized player, a player beyond eight blocks, and with a stale/closed menu. | Only the authorized nearby request changes persistent server state. Forged or stale button requests are rejected. | Not run | |
| ORDER-LIFE-01 | Set every non-default order, unload/reload the chunk, reconnect, and restart the server. | Exact orders persist once and synchronize when the inventory reopens; resetting every value to Inherit/Auto restores inherited behavior. | Not run | |
| ORDER-ROE-01 | Give separate guards Normal, Hostiles, Defensive, and Hold Fire orders; then enable server-wide Hold Fire and exercise exclusions/allies. | Individual modes apply, but no guard order bypasses global Hold Fire, exclusions, deny tags, or ally protection. A live Hold Fire change cancels the next burst round. | Not run | |
| ORDER-WEAPON-01 | Cycle tactical profiles, explosive Allow/Deny, and maximum-range presets during semi-auto and burst combat, including an RPG. | The existing burst window closes immediately on an order revision; the next AI synchronization uses the new profile/range. Explosive Deny suppresses fire and melee fallback, while Allow retains blast standoff and corridor safety. | Not run | |
| ORDER-AMMO-01 | Give donors and recipients different pickup/sharing orders while toggling each global ammunition master switch. | Denied guards neither pick up nor donate/receive; allowed/inherited guards operate only while the matching global switch is enabled. | Not run | |
| ORDER-MOVE-01 | Compare Inherit, Tactical, Hold, and Patrol Bound at long range, unsafe lanes, and inside the retreat distance. | Tactical permits all bounded movement; Hold does not move; Patrol Bound suppresses approach but retains urgent retreat/reposition; Inherit follows the guard's patrol state. | Not run | |
| ORDER-LEDGER-01 | Craft the shapeless Book and Quill plus Emerald recipe. Use a blank ledger on a configured guard, inspect its tooltip, and apply it to a guard with different orders. | The item stacks to one; capture stores and displays exactly seven raw values; apply changes all seven atomically without moving equipment, ammunition, armor, ownership, name, patrol state, or target. | Not run | |
| ORDER-LEDGER-02 | Normal-use a written ledger on a matching guard, then sneak-use it on a differently configured guard and apply the updated template elsewhere. Repeat from the offhand with an empty and then non-empty main hand. | A matching apply is a no-op; sneak-use deliberately replaces the template; the offhand fallback works only with an empty main hand and never steals another main-hand interaction. | Not run | |
| ORDER-LEDGER-03 | Attempt capture/apply while unauthorized, beyond eight blocks, in spectator mode, while targeted by the guard, and after the guard is removed. Then test deliberately damaged and newer-version template NBT on a disposable copy. | Every request fails closed with feedback and no partial order change. Sneak-use by an authorized nearby player can repair the damaged/unsupported disposable ledger. | Not run | |
| ORDER-LEDGER-04 | Store a written ledger in player inventory, a chest, and an item-handling mod across chunk unload, reconnect, and server restart; then apply it while global Hold Fire, an 8-block global cap, and ammunition master switches are restrictive. | The exact template persists without duplication or loss; applying it cannot relax any global restriction, ally rule, burst gate, or explosive safety boundary. | Not run | |

## Squads and temporary commands

Use the exact candidate JAR on an integrated server and a dedicated server.
Repeat synchronization-sensitive rows with a second observing client.

| ID | Setup and action | Expected result | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| SQUAD-LIFE-01 | Create and name a squad, assign several authorized guards, copy the bound ledger, unload/reload the guard chunks, reconnect, and restart the server. | Every ledger copy retains one squad UUID and display name; membership and temporary-command expiry survive exactly once without changing equipment, ownership, patrol, or follow state. | Not run | |
| SQUAD-AUTH-01 | Add, remove, replace, and command guards as an owner, a reputation-authorized player, an unauthorized player, a spectator, and from outside the interaction/dispatch ranges. | Only authorized loaded guards inside the bounded dispatch radius change; applied, unauthorized, and capped feedback matches the affected guards. | Not run | |
| SQUAD-MOVE-01 | Issue Rally, Defend, Hold, and Fall Back before and during pistol, automatic, burst, sniper, and RPG combat. | Rally clears at arrival; Defend remains inside its radius; Hold returns to each unique anchor; Fall Back reaches and retains its point; every permitted guard continues using native TaCZ firing safely. | Not run | |
| SQUAD-FOCUS-01 | Focus a hostile, protected villager, ally, excluded entity, dead/removed target, target that unloads, and target while Hold Fire is active. | Only the permitted loaded hostile is prioritized. Every denied or unavailable target clears the command without a shot, forced chunk, unsafe fallback, or ally damage. | Not run | |
| SQUAD-RESUME-01 | Issue each positional command to patrolling, following, and stationary guards, then use Resume Orders and separately let each command expire across a chunk unload. | Navigation owned by the command stops and the exact pre-command Guard Villagers patrol/follow state resumes; no permanent patrol position is rewritten. | Not run | |
| SQUAD-PATROL-01 | Bind a Squad Command Ledger, select Patrol Route Editor, add two or more valid waypoints, inspect the route, remove the last point, and clear it. Repeat invalid, distant, cross-dimension, blocked, and unloaded placements. | Valid edits persist and report the route state; invalid edits leave the previous route unchanged. Guards begin only after the second point and do not change equipment, orders, ownership, or Guard Villagers patrol data. | Not run | |
| SQUAD-PATROL-02 | Observe a routed squad through combat, Rally, Hold, following, waypoint obstruction, and chunk unload/reload. | Higher-priority behavior interrupts safely; unreachable points are skipped after the configured attempts; a fully unloaded route yields movement without loading chunks and resumes when a route chunk returns. | Not run | |
| SQUAD-PERF-01 | Dispatch to more guards than the configured and hard caps, then observe 10/25/50 commanded guards in mixed idle/combat states. | One bounded loaded-member query occurs per dispatch; active commands add no cohort scan or chunk load and path recalculations remain staggered. | Not run | |

## Village defense readiness and duties

Test a newly generated village containing several Village Defense Buildings.
Repeat the lifecycle rows after a save, chunk unload, and server restart.

| ID | Setup and action | Expected result | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| DUTY-STATE-01 | Ring a nearby bell, let Watch expire, damage and kill a defended entity with an external attacker, start and finish a raid, then wait through Recovery. | The connected defense network progresses through Watch, Alert, Siege, Recovery, and Peace without a lower-severity event clearing a current threat. | Not run | |
| DUTY-STAFF-01 | Observe Peace, Watch, Alert, Siege, and Recovery with ranged, melee, unarmed, following, commanded, resupplying, and actively fighting guards. | Sentry, drill, overwatch, and muster positions activate at the documented readiness levels; ranged-only positions use ranged guards; higher-priority behavior retains movement. | Not run | |
| DUTY-REPAIR-01 | Block a position's feet or head, remove its floor, add fluid, repair each case, and repeat while its chunk is unloaded. | A loaded invalid position releases its guard and returns after repair; an unloaded position remains saved and never loads its chunk. | Not run | |
| DUTY-DATA-01 | Load valid custom duty positions, reload invalid and future-format definitions, rotate the building through every direction, and revisit an existing building after changing the datapack. | New buildings use the accepted rotation-aware positions; invalid data is rejected; existing buildings keep their saved layout. | Not run | |
| DUTY-CONFIG-01 | Disable automatic duties and patrol execution independently, change readiness durations and movement settings, then restore both systems. | Each setting affects only its documented behavior; routes, buildings, Guard Orders, and saved readiness remain intact while execution is disabled. | Not run | |
| DUTY-PERF-01 | Run the 100-guard automatic-duty performance workload with a maximum-size duty layout and unloaded patrol route. | Eligibility checks remain within the fixed guard, position, and waypoint bounds and do not add forced chunks. | Pass | 20,000 unloaded-route guard checks and 51,200 loaded-slot checks completed across 200 ticks; no route chunk was loaded. |

## Village armories

Use representative TaCZ loose ammunition and finite/creative ammunition boxes.
Repeat automation rows with at least one vanilla hopper and one Forge pipe or
storage integration used by the target modpack.

| ID | Setup and action | Expected result | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| ARMORY-UI-01 | Craft and place the armory facing each direction, then open it at several GUI scales. Shift-click ammunition and unrelated items. | The model faces correctly; the 3x3 screen fits; compatible ammunition enters; unrelated items remain in player inventory. | Not run | |
| ARMORY-AUTO-01 | Insert and extract from every face with a hopper and representative Forge item pipe. Try non-ammunition and simulate before commit. | All faces expose one nine-slot handler; compatible items follow simulation results; non-ammunition is rejected without loss or duplication. | Not run | |
| ARMORY-LOCK-01 | Use a valid bound ledger, the same ledger again, another squad ledger, and blank/corrupt/future/orphaned ledgers. | The armory locks, returns public, and reassigns as requested; invalid ledgers make no change. The screen reports the authoritative state. | Not run | |
| ARMORY-LIFE-01 | Fill all slots, set a squad lock, unload/reload, restart, then break the block normally and by explosion. | Exact stacks and lock identity persist once; contents and the block drop without duplication; malformed locks deny guard eligibility. | Not run | |
| ARMORY-REDSTONE-01 | Measure empty, partially filled, and completely filled inventories while inserting/extracting through automation. | Comparator output changes from 0 through 15 and neighboring comparators update promptly. | Not run | |
| ARMORY-PERF-01 | Place many empty and stocked armories across loaded and unloaded chunks with no guards resupplying. | Armories perform no tick, entity scan, block scan, pathfinding, or forced chunk load. | Not run | |

## Natural loadout lifecycle

| ID | Setup and action | Expected result | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| NATURAL-LIFE-01 | Allow a structure guard to receive a natural gun, then unload/reload its chunk and restart the server before spawning another eligible guard inside the cap radius. | The original guard keeps its gun, ammunition, and identity; it still consumes exactly one nearby-cap slot after both reload boundaries. | Not run | |
| NATURAL-LIFE-02 | Replace an issued gun with an unmarked copy of the same TaCZ gun, move the marked original to another guard, save, and reload both. | Neither guard is counted as a natural gunner: the source record is cleared and an item marker without the matching guard record is inert. No equipment is deleted. | Not run | |
| NATURAL-DROP-01 | Exercise ordinary death, zombie conversion, and lightning conversion with naturally issued guns and partially used dedicated ammunition. | The gun follows Guard Villagers/Minecraft equipment behavior and appears at most once; dedicated ammunition follows the documented add-on path without loss or duplication. | Not run | |
| NATURAL-DIFF-01 | At each difficulty, generate a documented sample of otherwise eligible structure guards with an isolated village and default config. | Peaceful issues none; Easy remains pistol-only and locally capped at one; Normal introduces SMGs and caps at two; Hard permits occasional rifles while retaining a cap of two. Results are treated as a random sample, not exact percentages. | Not run | |
| NATURAL-POOL-01 | Add a custom namespaced weighted pool, select it for one difficulty, run `/reload`, then replace it with an invalid document and reload again. | Valid entries are selected according to their integer weights; invalid pool data is reported and that pool becomes unavailable without retaining a stale pre-reload copy or affecting other pools. | Not run | |
| NATURAL-SOURCE-01 | Exercise structure, villager conversion, spawn egg, command, and another finalized source with each source disabled and enabled. Repeat with a crossbow and non-crossbow starting weapon. | Only configured sources are eligible; crossbow-only replacement protects all other starting equipment unless explicitly disabled; each entity is attempted at most once. | Not run | |
| NATURAL-AMMO-01 | Issue representative pistol, SMG, rifle, attachment-modified, dummy/infinite-reserve, and physical-ammo custom definitions. Fill all dedicated slots before one attempt. | Eligible physical guns receive a loaded native gun plus exactly one TaCZ-defined full loose-ammo stack; unsupported reserve models and insufficient capacity leave the original weapon and every slot unchanged. | Not run | |
| NATURAL-EXPLOSIVE-01 | Put an RPG, a script-only explosive with no base explosion block, and a non-explosive gun in a custom pool. Test explosive issuance disabled and enabled, runtime explosion toggling, then engage targets near allies and terrain. | Disabled explosive issuance fails without mutation. Runtime changes immediately update explosive orders and blast standoff. Enabled issuance still obeys ally protection, rules of engagement, and block-preservation policy. | Not run | |
| NATURAL-CAP-01 | Place authentic issued guards just inside/outside the spherical cap radius, unload the inside guard's chunk, and spawn another eligible guard from a neighboring loaded chunk. Reload both chunks, then repeat with manually equipped and mismatched-provenance guards. | The unloaded authentic guard continues to consume one persistent slot and both chunks never converge above the cap; manual, mismatched, dead, removed, or out-of-radius guards do not count. | Not run | |
| NATURAL-SIDEARM-01 | Install the format-3 example, spawn its rifle/pistol guard, deplete and activate the sidearm, unload/reload while it is active, then fill all but one reserve slot and retry on a new guard. Repeat with a preconfigured sidearm. | Both weapons receive one full compatible ammo stack and switch through native TaCZ behavior; the stowed primary remains the one counted natural weapon. Insufficient dual-stack capacity or an occupied sidearm slot preserves the crossbow, existing sidearm, ammo, and provenance without a partial issue. | Not run | |

## Dedicated-server and modpack pass

Local `./gradlew runServer` preflight on 2026-08-27 reached Minecraft's normal
EULA gate after Forge dependency discovery and common-server mixin remapping,
without a client-only classloading failure. `run/eula.txt` remains `false`;
accepting the EULA and completing a full `Done` startup is a user action, so the
first row below remains unchecked.

### Ammunition quality-of-life multiplayer matrix

Use the exact candidate JAR and record the server type, player count, gun ID,
ammo ID, attachments, relevant settings, and evidence. `Pass` means the
expected result occurred without item loss, duplication, client disagreement,
or a server error. Keep screenshots, logs, and save backups with the release
notes. Detailed behavior and troubleshooting are documented in
[ammunition-quality-of-life.md](ammunition-quality-of-life.md).

| ID | Setup and action | Expected result | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| AMMO-ENV-01 | Integrated server: one player stocks, hands off, drops, and shares compatible ammunition. | Server-authoritative counts and client tooltip agree after every operation. | Not run | |
| AMMO-ENV-02 | Dedicated server: repeat the core stocking, handoff, pickup, native reload, and sharing flow. | Server reaches `Done`; no client-only classloading error, disconnect, or desynchronization occurs. | Not run | |
| AMMO-ENV-03 | Dedicated server: one player operates guards while a second client observes pickup, sharing, and native reload. | Both clients agree on held gun, reserve counts, sounds, and subsequent shots. | Not run | |
| AMMO-OWN-01 | Use same-owner, different-owner, and two unowned guard pairs with owner matching enabled; then disable owner matching. | Only equal owner UUIDs and two unowned guards share by default; disabling the restriction permits another eligible pair. | Not run | |
| AMMO-FEED-01 | Test representative magazine-fed, manual, fuel, inventory-fed, and explosive weapons with their native compatible reserves. | Tooltip, native reload, and remaining reserve agree with TaCZ; unsupported/dummy/native-infinite reserves fail without moving physical ammo. | Not run | |
| AMMO-FEED-02 | Repeat with attachment-modified capacity plus finite, typed creative, and universal creative ammo boxes where the pack supports them. Fully deplete at least four finite boxes across repeated restocks. | Capacity/chamber display and native consumption remain correct; each depleted finite box drops intact, its slot accepts the next supply, and boxes are never transferred by guard sharing. | Not run | |
| AMMO-GIVE-01 | Try compatible, incompatible, partial, full, unauthorized, inactive-guard, spectator, dummy-reserve, native-infinite, and creative-player handoffs. | Only authorized compatible physical ammunition is inserted; partial remainder and creative held stack are preserved exactly. | Not run | |
| AMMO-PICK-01 | Drop compatible, depleted-box, and incompatible ammunition at the radius boundary with empty, partial, and full guard inventories. Also test pickup delay, a player-targeted item, and `mobGriefing=false`. | Only eligible non-empty compatible capacity is collected; remainder, depleted boxes, delayed, targeted, incompatible, out-of-radius, and gamerule-blocked items remain unchanged. No guard query runs while `mobGriefing` is false. | Not run | |
| AMMO-SHARE-01 | During simultaneous combat, dry several guards near nearer/farther donors with surplus, exact reserve-floor, wrong-caliber, and ammo-box-only inventories. | Each dry recipient performs bounded emergency sharing; nearest eligible loose-ammo donor wins and retains its configured reserve. | Not run | |
| AMMO-LIFE-01 | Save with ammunition in all three slots, unload/reload the chunk, stop cleanly, and restart the server. | Exact stacks/components and guard gun state persist once, with no duplication or migration overflow. | Not run | |
| AMMO-LIFE-02 | Test guard death, zombie conversion, and lightning conversion with partially filled slots and pending overflow where applicable. | Ammunition follows the documented Guard Villagers outcome and is neither lost nor duplicated. | Not run | |
| AMMO-CFG-01 | Change pickup and sharing toggles independently through the permission-4 Fzzy Config UI, then change radii, reserve magazines, and owner matching. | Server values synchronize; the next eligible operation uses them and stored ammo is unchanged. Each disabled toggle suppresses only its own system. | Not run | |
| SIDEARM-AMMO-01 | Equip different-caliber primary and sidearm guns, then hand off and drop each caliber; activate the sidearm and trigger emergency sharing. | Both convenience stocking paths accept either configured caliber, summaries remain role-correct while switched, and donors retain the configured magazine floor for every compatible weapon. | Not run | |

- [ ] Start a dedicated server without client-only classloading failures.
- [ ] Join with the exact client mod list and resource/gun packs.
- [ ] Confirm `/vgg inspect <guard>` is unavailable below permission level 2.
- [ ] Inspect ready, out-of-range, obstructed, reloading, bolting, and dry guards.
- [ ] Exercise authorized and unauthorized guard inventory interaction.
- [ ] Complete every `AMMO-*` row above and retain its evidence with this candidate.
- [ ] Complete every `SIDEARM-*` row above and retain its evidence with this candidate.
- [ ] Run 10/25/50-guard observer scenarios with fixed view distance and JVM settings.
- [ ] Record server p95/p99 MSPT, client frame time, network traffic, live projectiles,
  GC/allocation, and a long profiler/JFR capture.
- [ ] Verify a datapack entry in `#villagerguardgunners:guard_allies` is protected by direct, burst, and explosive fire.
- [ ] Verify explicit ally additions and exclusions against representative modded NPCs.
- [ ] Verify owned/tamed modded entities and scoreboard-team allies with both config controls enabled and disabled.
- [ ] Exercise all four ROE modes against hostile mobs, neutral mobs, hostile players, and protected allies.
- [ ] Verify datapack additions to `#villagerguardgunners:always_engage` and `#villagerguardgunners:never_engage`, including deny precedence.
- [ ] Change ROE during a native burst and confirm the next scheduled round is canceled without consuming ammunition.
- [ ] Complete every `ORDER-*` row above and retain its evidence with this candidate.
- [ ] Complete every `SQUAD-*` row above and retain its evidence with this candidate.
- [ ] Complete every `NATURAL-*` row above and retain its evidence with this candidate.

## Packaging

- [ ] Complete every `VBUILD-MANUAL-*` row and resolve every Partial or Manual
  row in `docs/village-defense-buildings-release-matrix.md` against the exact
  candidate JAR.
- [ ] Run `./gradlew verifyVillageDefenseBuildingManualChecklist` after
  recording evidence for the natural-terrain, old-world, dedicated-server,
  frequency, and mixed-mod checks.
- [x] Set the `1.7` candidate identity and add matching platform-neutral
  release notes. This does not certify the pending gameplay checks.
- [x] Finalize `gradle.properties`, `CHANGELOG.md`, and the evergreen mod-page description.
- [x] Review all publication copy against the shared writing standard and run
  `../Content&DocumentationStandards/scripts/check-writing.sh .` from this
  repository.
- [x] Confirm `META-INF/mods.toml` contains the intended dependency ranges.
- [x] Confirm the release JAR contains no GameTests, legacy projectile/rebalance
  classes, local configuration, generated worlds, logs, or `.DS_Store` files.
- [ ] Install the built JAR into an otherwise clean client and dedicated server.
- [ ] Tag the exact tested commit only after every required row passes.

- Run `verifyMedicAiPerformanceReport` with the other performance report gates.
  Check Medic and Retreat controls, interrupted treatment, finite doses, and
  Quartermaster medical supply service in the candidate build.
- Run `verifyTransportPerformanceReport` after the combined performance matrix.
  Exercise opt-in transport on the candidate's client and dedicated server:
  clear and blocked cot routes, two observers, finite-dose revival, carrier
  interruption, and saving during a carry. Confirm original patient identity,
  equipment, and passenger visibility. See [casualty transport](casualty-transport.md).
