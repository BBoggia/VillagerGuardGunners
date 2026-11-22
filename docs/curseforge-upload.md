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
- Release type: use `mod_release_type` from `gradle.properties` for beta and stable files. Use Alpha for alpha builds.
- Game version and loader: Minecraft 1.20.1 with Forge, or Minecraft 1.21.1
  with NeoForge. Read the candidate metadata; do not tag one JAR for both.
- Java: 17 for Forge 1.20.1; 21 for NeoForge 1.21.1.
- Changelog: `docs/releases/<version>.md`

Upload only the verified gameplay JAR as the mod file. NeoForge files are named
`villagerguardgunners-<minecraft>-neoforge-<version>.jar`. Do not upload bootstrap,
QA, sources, or GameTest JARs, and do not bundle the dependency mods.

## NeoForge preparation

Run `./gradlew prepareNeoForgeRelease stageCoreQaMods stageMultiplayerQaMods` with
Java 21. This runs the complete JUnit suite, verifies pinned dependencies and
companion packs, and stages the beta or stable release under
`build/neoforge/distributions/release/<version>/`. It includes the gameplay JAR,
copy-ready description and change notes, license, upload settings, SHA-256
checksums, and the optional Minecraft 1.21.1 universal datapack.

Test that exact JAR through the installed-server and connected-client runners
in [NeoForge development](neoforge-development.md). Keep the matching JAR hashes
and reports. Choose **Release** for stable versions. For an alpha version, use
`prepareNeoForgeAlpha` and the Alpha file type instead. Packaging does not replace
the installed gameplay checks. The loader and dependency ranges are in `gradle.properties` and
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

## File dependencies

Set Guard Villagers, Fzzy Config, and Kotlin for Forge as required. Set Timeless and Classics Zero, MrCrayfish’s Gun Mod (Unofficial), Vic’s Point Blank, and Point Blank: Jelly as optional. These relations apply to the multi-mod builds. Earlier files keep their original requirements.

## Final review

- For stable releases, complete every applicable row in `docs/release-checklist.md`
  against the exact candidate JAR. Record validation limits explicitly.
- Confirm the Minecraft and loader tags agree with the packaged metadata.
- Confirm the release type matches the intended release channel.
- Confirm the required and optional relations match this file.
- Confirm the project license matches `LICENSE.txt`.
- Confirm the uploaded JAR matches the recorded SHA-256 checksum.
- Confirm the public description and release note match the uploaded version.
- Add or update the project icon and representative screenshots when needed.
