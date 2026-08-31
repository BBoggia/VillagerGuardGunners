# CurseForge Upload Checklist — Villager Guard Gunners 1.2

## Project

* Name: `Villager Guard Gunners`
* Summary: `Give Guard Villagers native TaCZ firearms, automatic sidearms, tactical squads, and configurable natural loadouts.`
* Class: `Mods`
* Suggested main category: `Mobs`
* Suggested additional categories: `Armor, Tools, and Weapons`, `Server Utility`, `Technology`
* License: `Custom License` using the packaged `LICENSE.txt`
* Environment: required on both client and server

Paste `docs/curseforge-description.md` into the project description field.

## File

* Display name: `Villager Guard Gunners 1.2`
* Release type: `Release`
* Game version: `Minecraft 1.20.1`
* Mod loader: `Forge`
* Java: `17`
* Changelog: paste `docs/curseforge-release-notes-1.2.md`

Upload only `villagerguardgunners-1.2.jar` as the mod file. The other files in
this directory are upload notes and verification material, not Minecraft mods.

## Required Relations

Mark each project as a required dependency on the CurseForge file:

* Guard Villagers
* Timeless and Classics Zero
* Fzzy Config
* Kotlin for Forge

The dependencies are not bundled in the JAR and must remain separate downloads.

## Final Review

Run `./gradlew prepareCurseForgeRelease` only after completing
`docs/release-checklist.md`. The packaging task deliberately fails while any
manual table row is `Not run`/`Failed` or any checklist item remains unchecked;
do not mark an item passed without retaining its hands-on evidence.

* Confirm the CurseForge file is tagged for Minecraft 1.20.1 and Forge.
* Confirm all four related projects are marked as required.
* Confirm the project license matches `LICENSE.txt`.
* Confirm the uploaded JAR matches the packaged SHA-256 checksum.
* Add a square project icon and representative screenshots through CurseForge.
* Keep the exact dependency versions visible in the description and changelog.
* Confirm the description accurately says automatic Armory withdrawal is not
  part of version 1.2.
