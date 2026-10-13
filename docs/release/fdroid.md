# F-Droid readiness

## Checklist

| Requirement | Status | Where |
|---|---|---|
| Open-source license (OSI) | Done, MIT | `LICENSE` |
| Public source repository, tagged releases | Done once the repo is public; tags `vX.Y.Z` | `docs/RELEASE.md` |
| No proprietary dependencies (Play Services, Firebase, Crashlytics, ads, analytics) | Done, only AndroidX/Compose/Kotlin, all Apache-2.0 | `app/build.gradle.kts` (`dependencies`) |
| No Google dependency metadata blob in the APK | Done | `dependenciesInfo { includeInApk = false }` |
| Builds from source with plain Gradle, no prebuilt binaries committed | Done (Gradle wrapper only) | `gradlew`, `gradle/wrapper` |
| Reproducible-friendly build | AGP builds are deterministic; no timestamps, no VCS info (`vcsInfo.include = false`), no random IDs in the build | verify below |
| Fastlane metadata in the repo | Done | `fastlane/metadata/android/{en-US,de-DE}` |
| Screenshots | Done: light phone screenshots in `fastlane/metadata/android/<locale>/images/phoneScreenshots/` (en-US 8, de-DE 6) | |
| Icon | Comes from the app; optional `images/icon.png` (512x512) | |
| Changelog per `versionCode` | Done: `changelogs/<versionCode>.txt`; add a new file every release | `docs/RELEASE.md` |
| Anti-Features | None expected. The app uses the public Transitous API, which is itself free software (MOTIS); no tracking, no ads, no non-free network service or non-free assets | |
| Licenses of data | Transitous sources and OpenStreetMap are credited in the app and README | |

Quick checks before submitting:

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -i -E "firebase|gms|crashlytics|play-services"   # must print nothing (guava listenablefuture is an empty Apache-2.0 stub)
unzip -l app/build/outputs/apk/release/app-release.apk | grep -i -E "dependencies.pb|version-control-info"  # must print nothing
```

Reproducibility test (optional but cheap): build the same tag twice on two machines or in two clean checkouts with the same JDK 17 and compare the unsigned APK contents (`diffoscope` or `apkdiff`). F-Droid then can publish the developer-signed APK if you enable it (see "Signing" below).

## Signing

Default: F-Droid builds from source and signs with its own key. Users get F-Droid's key, which differs from the Play key, so Play and F-Droid builds cannot update each other. That is the normal, simple path.

Optional: reproducible builds with the upstream signature. Keep the Play/GitHub key as the signing key, add `Binaries:` and `AllowedAPKSigningKeys:` to the metadata, and F-Droid ships your signed APK if its own build matches yours byte for byte. Only worth it once the reproducibility test above passes.

## Draft metadata

For `metadata/app.delayd.yml` in a merge request to <https://gitlab.com/fdroid/fdroiddata>. Replace `erdem61/delayd` if the repository path differs. Run `fdroid lint app.delayd` and `fdroid build -v -l app.delayd` locally (see the fdroiddata `CONTRIBUTING.md`) before opening the MR.

```yaml
Categories:
  - Navigation
License: MIT
AuthorName: erdem61
WebSite: https://github.com/erdem61/delayd
SourceCode: https://github.com/erdem61/delayd
IssueTracker: https://github.com/erdem61/delayd/issues
Changelog: https://github.com/erdem61/delayd/blob/HEAD/CHANGELOG.md

AutoName: Delayd

RepoType: git
Repo: https://github.com/erdem61/delayd.git

Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: v1.0.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9.]+$
CurrentVersion: 1.0.0
CurrentVersionCode: 1
```

Notes:

- `Categories`: Navigation fits best among F-Droid's fixed list (Navigation, Time, Money, ...); use `Money` if reviewers prefer it.
- Summary and description come from `fastlane/metadata/android/en-US/short_description.txt` and `full_description.txt`, so they are not repeated in the YAML.
- F-Droid reads `VERSION_NAME`/`VERSION_CODE` through Gradle; `UpdateCheckMode: Tags` bumps from the tag, and `versionCode` is read from the built manifest. If auto-update cannot parse `gradle.properties` indirection, fall back to `UpdateCheckMode: Tags` plus explicit `Builds` entries per release.
- Requesting inclusion starts with an RFP/MR; typical review takes days to weeks.
