# Villager Guard Gunners Instructions

Follow the workspace writing standard at
`../Content&DocumentationStandards/WRITING_STANDARD.md` when it is available.
If this repository is checked out separately, apply the rules summarized here.

## Public names

- Product: **Villager Guard Gunners**
- Dependencies: **Guard Villagers** and **Timeless and Classics Zero (TaCZ)**.
- Refer to entities as **guards** or **Guard Villagers**. Avoid the redundant
  phrase “Guard Villagers guards.”
- Named features: **Guard Orders**, **Orders Ledger**, **Squad Command Ledger**,
  **Village Armory** or **Village Armories**, **Village Watchtower** or
  **Village Watchtowers**, and **Village Defense Buildings**.

## Audience rules

- CurseForge copy explains how players equip, command, supply, and encounter
  armed guards. Omit invisible implementation details and comparisons with old
  internal approaches unless they change compatibility or player action.
- Prefer `can`, `ammo`, `gun`, and `valid` in ordinary player copy. Use `may`,
  `ammunition`, `firearm`, or `suitable` only when the more formal word adds
  necessary precision.
- README content may include operator and pack-author information, but detailed
  data formats, lifecycle contracts, validation matrices, and performance
  evidence belong in `docs/`.
- Changelog bullets record player-, operator-, pack-author-, or API-visible
  changes. Phase completion, fixture counts, audit identities, and test report
  paths belong in development documentation.
- `server-authoritative`, `atomic transaction`, and `deterministic` are approved
  terms. Use other engineering terms only when the target audience needs the
  exact concept.

## Local development notes

- Store agent journals, phased implementation plans, numbered phase reports,
  and temporary evidence under `.local/agent-development-notes/`.
- That directory is intentionally ignored and must never be required by a
  build, test, README link, release checklist, or published document.
- Durable behavior and integration contracts belong in normally named files
  under `docs/` without phase or step numbering.
- Before adding a Markdown file containing `phase-<number>` or
  `step-<number>` in its name, move it to the local-note directory instead.

## Release consistency

- `gradle.properties` is the source for the version and metadata description.
- `CHANGELOG.md` is the release-history source.
- `docs/curseforge-description.md` is evergreen mod-page copy and should not be
  tied to one release number.
- `docs/releases/<version>.md` contains release-specific copy and is reused on
  every distribution platform.
- `docs/curseforge-upload.md` contains the reusable CurseForge upload process;
  do not create a new upload checklist for every version.
- Verify public dependency and compatibility statements against build metadata
  and `META-INF/mods.toml`.

## Commit examples

```text
feat(worldgen): add village defense buildings
fix(armory): preserve ammunition during guard resupply
docs(curseforge): simplify squad and armory overview
chore(release): prepare 1.5 beta 1
```
