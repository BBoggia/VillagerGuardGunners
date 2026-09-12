# CurseForge upload process — Villager Guard Gunners

Use this checklist for every CurseForge release. Read the current version from
`gradle.properties`, use the verified JAR described by
`docs/release-checklist.md`, and use `docs/releases/<version>.md` as the file
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

- Display name: `Villager Guard Gunners <version>`
- Release type: choose Release, Beta, or Alpha to match the version and tested
  release status
- Game version: `Minecraft 1.20.1`
- Mod loader: `Forge`
- Java: `17`
- Changelog: `docs/releases/<version>.md`

Upload only the verified `villagerguardgunners-<version>.jar` as the mod file.
Do not substitute a development, sources, or GameTest JAR.

The local `prepareReleaseCandidate` task also stages optional player building
packs and [installation instructions](building-pack-installation.md). Offer
those ZIPs as clearly labeled optional datapack downloads, separate from the
mod JAR. Keep their required village providers visible. Preview and QA packs
are excluded from this collection. Candidate preparation does not complete
the manual publication checks below.

## Required relations

Mark each project as a required dependency on the CurseForge file:

- Guard Villagers
- Timeless and Classics Zero
- Fzzy Config
- Kotlin for Forge

The dependencies are separate downloads and are not bundled in the mod JAR.

## Final review

- Complete every applicable row in `docs/release-checklist.md` against the
  exact candidate JAR.
- Confirm the file is tagged for Minecraft 1.20.1 and Forge.
- Confirm the release type matches the intended release channel.
- Confirm all four required relations are present.
- Confirm the project license matches `LICENSE.txt`.
- Confirm the uploaded JAR matches the recorded SHA-256 checksum.
- Confirm the public description and release note match the uploaded version.
- Add or update the project icon and representative screenshots when needed.
