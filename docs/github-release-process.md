# GitHub release process

Villager Guard Gunners uses one version identity across Gradle, the packaged
mod metadata, the changelog, CurseForge handoff files, and Git tags.

## Version and tag convention

- The canonical version is `mod_version` in `gradle.properties`.
- Release tags use an annotated `v<version>` tag, such as `v1.2`.
- The JAR is named `villagerguardgunners-<version>.jar`.
- The newest released section in `CHANGELOG.md` is
  `## <version> - YYYY-MM-DD`.
- CurseForge files are named `docs/curseforge-release-notes-<version>.md` and
  `docs/curseforge-upload-<version>.md`.
- Published tags are immutable. Never delete, recreate, or force-update one to
  point at different source.

Patch releases may use `1.2.1`; the matching tag is `v1.2.1`. Prereleases may
use a suffix such as `1.3-beta1`; the matching tag is `v1.3-beta1`.

## Prepare a release

1. Start from the intended release branch with no unrelated working-tree
   changes.
2. Set `mod_version` and update `CHANGELOG.md`, the CurseForge description,
   versioned release notes, upload checklist, and release checklist.
3. Run the JDK 17 automated gates:

   ```text
   ./gradlew clean check
   ./gradlew runGameTestServer
   ```

4. Complete the applicable manual, multiplayer, modpack, and performance rows
   in `docs/release-checklist.md` against the exact candidate JAR.
5. Run the release evidence and packaging tasks documented by that checklist.
6. Record the SHA-256 of `build/libs/villagerguardgunners-<version>.jar`.
7. Commit the exact reviewed release tree with a message such as
   `chore: prepare 1.2 release`.
8. Create an annotated tag on that commit:

   ```text
   git tag -a v1.2 -m "Villager Guard Gunners 1.2"
   ```

## Publish

1. Push the release commit and then its tag without force-updating either:

   ```text
   git push origin main
   git push origin v1.2
   ```

2. Wait for the GitHub Actions tag build to pass. Tag builds reject a tag whose
   name does not exactly match `mod_version` and retain the verified JAR plus a
   SHA-256 file as workflow artifacts.
3. Create a GitHub release from the immutable tag and paste the matching
   `docs/curseforge-release-notes-<version>.md` section as its release notes.
4. Attach the reobfuscated JAR from `build/libs`. Do not substitute a sources,
   development, or GameTest JAR.
5. Download the GitHub asset and verify its SHA-256 against the locally
   validated artifact.
6. Upload the same verified JAR to CurseForge using the matching versioned
   upload checklist.

GitHub Actions verifies source and packaged-artifact contracts. It does not
replace the manual client, dedicated-server, multiplayer-observer, third-party
gun-pack, or performance checks recorded in the release checklist.

## Historical release anchors

- `v1.0.0` identifies the native TaCZ rewrite release.
- `v1.1` identifies the per-guard orders and natural-loadout release.
- `v1.2` identifies the sidearm, squad-command, and Village Armory release.
