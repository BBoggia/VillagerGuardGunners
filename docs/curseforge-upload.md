# CurseForge upload process — Villager Guard Gunners

Use this checklist for every CurseForge release. Read the current version from
`gradle.properties`, identify the matching Minecraft version and loader, and use `docs/releases/<version>.md` as the file
changelog.

## Project

- Name: `Villager Guard Gunners`
- Class: `Mods`
- Suggested main category: `Mobs`
- Suggested additional categories: `Armor, Tools, and Weapons`,
  `Server Utility`, `Technology`, and `World Gen` when applicable
- License: `Custom License` using the packaged `LICENSE.txt`
- Environment: required on both client and server

Use `docs/curseforge-description.md` for the project description whenever the
evergreen page copy changes.

## File

- Display name: `Villager Guard Gunners <version>`; include NeoForge and the
  Alpha number for NeoForge alpha files.
- Release type: choose Release, Beta, or Alpha to match the version and tested
  release status
- Game version and loader: Minecraft 1.20.1 with Forge, or Minecraft 1.21.1
  with NeoForge. Read the candidate metadata; do not tag one JAR for both.
- Java: 17 for Forge 1.20.1; 21 for NeoForge 1.21.1.
- Changelog: `docs/releases/<version>.md`

Upload only the verified gameplay JAR as the mod file. NeoForge files are named
`villagerguardgunners-<minecraft>-neoforge-<version>.jar`. Do not upload bootstrap,
QA, sources, or GameTest JARs, and do not bundle the dependency mods.

## NeoForge alpha preparation

Run `./gradlew prepareNeoForgeAlpha stageCoreQaMods stageMultiplayerQaMods` with
Java 21. This runs the complete JUnit suite, verifies pinned dependencies and
companion packs, and stages the named alpha under
`build/neoforge/distributions/alpha/<version>/`. It includes the gameplay JAR,
copy-ready description and change notes, license, upload settings, SHA-256
checksums, and the optional Minecraft 1.21.1 universal datapack.

Test that exact JAR through the installed-server and connected-client runners
in [NeoForge development](neoforge-development.md). Keep the matching JAR hashes
and reports. Use **Alpha** for this candidate; completing the alpha packaging
and gameplay checks does not certify the remaining stable-release/performance
matrix. The current exact dependency versions are in `gradle.properties` and
`META-INF/neoforge.mods.toml`.

## Forge release preparation

The local `prepareReleaseCandidate` task also stages optional player building
packs and [installation instructions](building-pack-installation.md). Offer
the universal `villager-guard-gunners-village-buildings.zip` as the main download
on a single companion datapack project, separate from the mod JAR. The four
village providers are optional for this universal pack. Provider-specific ZIPs
remain selective alternatives and require their matching village mods. Preview and QA packs
are excluded from this collection. Candidate preparation does not complete
the manual publication checks below.

## Required relations

Mark each project as a required dependency on the CurseForge file:

- Guard Villagers
- Timeless and Classics Zero for Forge files, or
  **[UNOFFICIAL] TaCZ NeoForge Port** for NeoForge files. Use the
  [NeoForge port project](https://www.curseforge.com/minecraft/mc-mods/tacz-1-21-1)
  for the latter relation.
- Fzzy Config
- Kotlin for Forge

The dependencies are separate downloads and are not bundled in the mod JAR.

## Final review

- For stable releases, complete every applicable row in `docs/release-checklist.md`
  against the exact candidate JAR. Keep outstanding alpha limitations explicit.
- Confirm the Minecraft and loader tags agree with the packaged metadata.
- Confirm the release type matches the intended release channel.
- Confirm all four required relations are present.
- Confirm the project license matches `LICENSE.txt`.
- Confirm the uploaded JAR matches the recorded SHA-256 checksum.
- Confirm the public description and release note match the uploaded version.
- Add or update the project icon and representative screenshots when needed.
