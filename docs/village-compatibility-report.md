# Village compatibility qualification

This candidate is not release-qualified. Loading and recognition checks are
separate from guard staffing, patrol quality, medical behavior and multiplayer
compatibility.

The recorded production matrix uses Villager Guard Gunners 1.6 with SHA-256
`20b45dbc27942cc9837961a685b3841250191971a8c5a4599b0b3592f4088764`.
Its configuration enables managed spawning, automatic coverage and Village
Defense Buildings. Each case observes one naturally generated settlement for
500 ticks after location and loading. It uses normal Forge 47.4.10 with
Minecraft 1.20.1, Guard Villagers 1.6.19 and TaCZ 1.1.8-hotfix.

Subsequent navigation, alarm and reporting fixes require qualification against
their own jar hash. The results below apply only to the recorded candidate.

## Recorded outcomes

| Cases | Result on this candidate |
| --- | --- |
| Baseline and nine individual mods | Configured production assertions passed |
| CTOV/Liberty, MMV/Liberty, Tidal/Liberty, Sky/Liberty, CTOV/Integrated, Better/Lios | Configured production assertions passed |
| All-nine dedicated server | Loading, profile publication, resource reload and clean exit passed; no client joins tested |
| Full nine-mod client installation | Timed out after 420 seconds during natural vanilla plains village location; village behavior and claim checks did not complete |

The report checker verified 16 completed cases against the candidate hash and
retained the incomplete full-installation case. Its overall client matrix result is
not passing. The separate dedicated-server check used the same candidate and
mod jars without the observer, with a fresh default server configuration. These are single-seed samples, not comprehensive per-mod approval.

## Scope and unresolved behavior

The matrix checks production code sources, loaded dependency versions, profile
reload errors and settlement intent. Liberty cases also run a controlled native
Quartermaster claim lifecycle. Witch cases require no friendly village record
or observed guards. These checks do not establish the following behavior:

- Distributed initial staffing for MMV and Sky settlements, or populated
  districts throughout large villages.
- Continuous coverage through Lios terrain refreshes, settlements requiring
  more than the current region limits, or measured higher population caps.
- Fractional deck surfaces, safe native navigation shortcuts, water recovery,
  bridge crowding or carrier travel through generated buildings.
- Mod-specific defense buildings, district supply searches, full evacuation,
  Liberty schedule options or overlapping healing systems.
- Bounded hostile pursuit near a friendly settlement and player-led assaults
  with ammo depletion and withdrawal.
- Client joins, late joins, multiplayer permissions, upgrades, optional-mod
  removal, or performance under sustained combat and service queues.

The CTOV sample contains 11 guards accounted for by its frozen village plan
and three additional guards with one standalone Watchtower post identity.
Those three have no village affiliation. The raw nearby-guard census therefore
is not a count of managed village issuance, and this case does not prove a
shared village cap for overlapping standalone posts.

Existing observations include unresolved Lios refresh continuity and villages
with no native guard requests. A successful matrix case does not resolve those
findings. The public population limit remains unchanged; no larger supported
cap follows from these tests.

## Reproduction and evidence

Use `tools/village-compatibility/combined-installations.json` as the reusable
matrix and pin the candidate and optional jars for a recorded run. The local
candidate run is under `build/village-compatibility-m10-matrix/`. Each run retains
its exact configuration, jar hashes, world evidence, logs, resource providers,
loaded versions and assertions. The report checker described in
[village-compatibility-testing.md](village-compatibility-testing.md) verifies
those records against the candidate hash.

Effective resource providers must be inspected when installing Better Villages
and Lios together. Their replacement resources are not merged into a new village
layout. Only the winning resources and the resulting observed layout can be
assessed.

In the sampled Better Villages/Lios installation, 32 observed village structure
resources came from Lios and nine from vanilla; none of the observed structure
resources came from Better Villages. This statement applies to the recorded
resource sample, not every file in either mod. Its plan allowed 10 guards, while
nine were accounted for after the observation period; issuance completion was
not established.

The full installation logs missing CTOV pool references for its Vampirism,
Bountiful and Waystones integrations. Those optional integrations are not part
of this installation and are not qualified by this matrix.
