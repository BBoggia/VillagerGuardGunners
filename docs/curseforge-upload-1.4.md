# CurseForge Upload Checklist — Villager Guard Gunners 1.4

## Project

* Name: `Villager Guard Gunners`
* Summary: `Give Guard Villagers native TaCZ firearms, tactical squads, ammunition logistics, and naturally generated armed Watchtowers.`
* Class: `Mods`
* Suggested main category: `Mobs`
* Suggested additional categories: `Armor, Tools, and Weapons`, `Server Utility`, `Technology`, `World Gen`
* License: `Custom License` using the packaged `LICENSE.txt`
* Environment: required on both client and server

Paste `docs/curseforge-description.md` into the project description field.

## File

* Display name: `Villager Guard Gunners 1.4`
* Release type: `Release` after the retained manual release matrix passes
* Game version: `Minecraft 1.20.1`
* Mod loader: `Forge`
* Java: `17`
* Changelog: paste `docs/releases/1.4.md`

Upload only `villagerguardgunners-1.4.jar` as the mod file. The documents and
checksum are upload guidance, not Minecraft mods.

* Candidate size: `939,189 bytes`
* SHA-256: `2ebf9920e7916bc9d7e81e85a55c522020e7149eaabf74d2354bfe6c838e59ac`

## Required Relations

Mark each project as a required dependency on the CurseForge file:

* Guard Villagers
* Timeless and Classics Zero
* Fzzy Config
* Kotlin for Forge

The dependencies are not bundled in the JAR and must remain separate downloads.

## Final Review

* Complete every applicable row in `docs/release-checklist.md` and
  `docs/village-watchtowers-release-matrix.md` against the exact candidate JAR.
* Confirm the CurseForge file is marked **Release** for Minecraft 1.20.1 and Forge.
* Confirm all four related projects are marked as required dependencies.
* Confirm the uploaded JAR matches the supplied SHA-256 checksum.
* Back up an existing world before the first test and confirm that Watchtowers
  appear only in newly generated chunks.
* Confirm the description calls Watchtowers standalone structures and does not
  imply that they are inserted into vanilla village roads or building pools.
