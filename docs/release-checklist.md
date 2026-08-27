# Release Candidate Checklist

This checklist records the work that cannot be inferred from a successful
compile alone. Do not mark a public release ready until the automated gates and
the relevant manual rows pass against the exact files being distributed.

## Candidate identity

- Version: `1.0.0`
- Minecraft: `1.20.1`
- Forge: `47.4.23`
- Guard Villagers: `1.20.1-1.6.19`
- TaCZ: `1.1.8-hotfix`
- Fzzy Config: `0.7.6+1.20.1`
- Kotlin for Forge: `4.11.0`
- Gun pack(s): record exact names and versions in the release notes
- Candidate JAR SHA-256: `593477320311b5987c008a5998bc9ad7059d3c0e1bd4ec1acb946be61284f6f5`

## Automated gates

- [x] `./gradlew clean check`
- [x] `./gradlew runGameTestServer`
- [x] `./gradlew runGameTestServer -Pphase8Performance=true`
- [x] `./gradlew verifyPhase8PerformanceReports`
- [x] Rebuild the JAR and confirm its SHA-256 is reproducible
- [ ] Confirm the CI build passes from a clean checkout

The normal GameTest suite includes a native Glock firing case with a target
beyond its preferred TaCZ range but within the configured maximum range.

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
| Explosive shot with an open lane | Not run | |
| Explosive shot near villagers, guards, golems, and owner | Not run | |
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

## Dedicated-server and modpack pass

- [ ] Start a dedicated server without client-only classloading failures.
- [ ] Join with the exact client mod list and resource/gun packs.
- [ ] Confirm `/vgg inspect <guard>` is unavailable below permission level 2.
- [ ] Inspect ready, out-of-range, obstructed, reloading, bolting, and dry guards.
- [ ] Exercise authorized and unauthorized guard inventory interaction.
- [ ] Save/restart with ammunition in all three dedicated slots.
- [ ] Exercise guard death, zombie conversion, and lightning conversion.
- [ ] Run 10/25/50-guard observer scenarios with fixed view distance and JVM settings.
- [ ] Record server p95/p99 MSPT, client frame time, network traffic, live projectiles,
  GC/allocation, and a long profiler/JFR capture.

## Packaging

- [ ] Update `gradle.properties`, `CHANGELOG.md`, and the mod-page release notes.
- [ ] Confirm `META-INF/mods.toml` contains the intended exact dependency ranges.
- [x] Confirm the release JAR contains no GameTests, legacy projectile/rebalance
  classes, local configuration, generated worlds, logs, or `.DS_Store` files.
- [ ] Install the built JAR into an otherwise clean client and dedicated server.
- [ ] Tag the exact tested commit only after every required row passes.
