# GitHub release process

Villager Guard Gunners uses one version identity across Gradle, the packaged
mod metadata, the changelog, CurseForge handoff files, and Git tags.

## Version and tag convention

- The canonical version is `mod_version` in `gradle.properties`.
- Release tags use an annotated `v<version>` tag, such as `v1.2`.
- The JAR is named `villagerguardgunners-<version>.jar`.
- The newest released section in `CHANGELOG.md` is
  `## <version> - YYYY-MM-DD`.
- Platform-neutral release notes are named `docs/releases/<version>.md` and are
  reused for GitHub and CurseForge.
- The reusable CurseForge process is `docs/curseforge-upload.md`.
- Published tags are immutable. Never delete, recreate, or force-update one to
  point at different source.

Patch releases may use `1.2.1`; the matching tag is `v1.2.1`. Prereleases may
use a suffix such as `1.3-beta1`; the matching tag is `v1.3-beta1`.

## Prepare a release

1. Start from the intended release branch with no unrelated working-tree
   changes.
2. Set `mod_version` and update `CHANGELOG.md`, the CurseForge description when
   its evergreen content changed, `docs/releases/<version>.md`, and the release
   checklist.
3. Review the README, changelog, CurseForge description, release notes, and
   `mod_description` against the shared writing standard. From the shared
   workspace, run:

   ```text
   ../Content&DocumentationStandards/scripts/check-writing.sh .
   ```

4. Run the JDK 17 automated gates:

   ```text
   ./gradlew clean check
   ./gradlew runGameTestServer
   ```

5. Complete the applicable manual, multiplayer, modpack, and performance rows
   in `docs/release-checklist.md` against the exact candidate JAR. Releases
   containing Village Defense Buildings must also complete
   `docs/village-defense-buildings-release-matrix.md` using its manual
   validation guide.
6. Run `./gradlew prepareReleaseCandidate` for a local review bundle of the JAR,
   optional building packs, documentation, and checksums. Then complete the
   release evidence and public packaging tasks documented by that checklist.
7. Record the SHA-256 of `build/libs/villagerguardgunners-<version>.jar`.
8. Commit the exact reviewed release tree with a message such as
   `chore(release): prepare 1.2`.
9. Create an annotated tag on that commit:

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
   `docs/releases/<version>.md` as its release notes.
4. Attach the reobfuscated JAR from `build/libs`. Do not substitute a sources,
   development, or GameTest JAR.
   Offer the selected optional building datapacks as separate downloads and
   include their installation guide. Preview and QA packs are not player packs.
5. Download the GitHub asset and verify its SHA-256 against the locally
   validated artifact.
6. Upload the same verified JAR to CurseForge using
   `docs/curseforge-upload.md` and the same platform-neutral release notes.

GitHub Actions verifies source and packaged-artifact contracts. It does not
replace the manual client, dedicated-server, multiplayer-observer, third-party
gun-pack, or performance checks recorded in the release checklist.

Tagged and manually dispatched builds retain the review bundle alongside the
mod JAR. A retained candidate bundle does not indicate public release approval.

## Historical release anchors

- `v1.0.0` identifies the native TaCZ rewrite release.
- `v1.1` identifies the per-guard orders and natural-loadout release.
- `v1.2` identifies the sidearm, squad-command, and Village Armory release.
