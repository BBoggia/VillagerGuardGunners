# CurseForge Upload Checklist — Villager Guard Gunners 1.3 Beta 1

## Project

* Name: `Villager Guard Gunners`
* Summary: `Give Guard Villagers native TaCZ firearms, automatic Armory resupply, reusable roles, sidearms, squads, and configurable natural loadouts.`
* Class: `Mods`
* Suggested main category: `Mobs`
* Suggested additional categories: `Armor, Tools, and Weapons`, `Server Utility`, `Technology`
* License: `Custom License` using the packaged `LICENSE.txt`
* Environment: required on both client and server

Paste `docs/curseforge-description.md` into the project description field.

## File

* Display name: `Villager Guard Gunners 1.3 Beta 1`
* Release type: `Beta`
* Game version: `Minecraft 1.20.1`
* Mod loader: `Forge`
* Java: `17`
* Changelog: paste `docs/releases/1.3-beta1.md`

Upload only `villagerguardgunners-1.3-beta1.jar` as the mod file. The documents
and checksum are upload guidance, not Minecraft mods.

## Required Relations

Mark each project as a required dependency on the CurseForge file:

* Guard Villagers
* Timeless and Classics Zero
* Fzzy Config
* Kotlin for Forge

The dependencies are not bundled in the JAR and must remain separate downloads.

## Final Review

* Confirm the CurseForge file is marked **Beta** for Minecraft 1.20.1 and Forge.
* Confirm all four related projects are marked as required dependencies.
* Confirm the uploaded JAR matches the supplied SHA-256 checksum.
* Back up a test world and complete the highest-priority manual Armory, role,
  client-rendering, dedicated-server, and multiplayer checks before promoting
  this build to the final 1.3 release.
* Do not advertise the unimplemented squad visualization or automatic target-
  coordination items as part of this beta.
