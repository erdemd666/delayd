# Workstream C: release plan

Each row: deliverable, file, how it is verified.

| # | Deliverable | File(s) | Verification |
|---|---|---|---|
| 1 | Release signing from `keystore.properties` or `DELAYD_*` env vars; debug-key fallback only without a key, hard failure on tag CI | `app/build.gradle.kts`, `.gitignore`, `docs/RELEASE.md` | `assembleRelease` with no key (fallback + warning); `GITHUB_REF_TYPE=tag ./gradlew assembleRelease` must fail; `apksigner verify --print-certs` |
| 2 | R8 + resource shrinking, minimal `proguard-rules.pro` | `app/build.gradle.kts`, `app/proguard-rules.pro` | `assembleRelease`/`bundleRelease` succeed; inspect R8 `usage`/`missing_rules.txt`; no rules added without evidence |
| 3 | Version scheme: `VERSION_NAME` + monotonic `VERSION_CODE` in `gradle.properties`; tag `vX.Y.Z` must equal the name | `gradle.properties`, `app/build.gradle.kts`, `release.yml` | Built APK `aapt`/manifest shows values |
| 4 | F-Droid friendly: `dependenciesInfo` off, no non-free deps, deterministic AGP defaults | `app/build.gradle.kts`, `docs/release/fdroid.md` | `./gradlew :app:dependencies` has no Play Services / proprietary libs |
| 5 | CI: `ci.yml` (tests, lint, assembleDebug, JDK 17, Gradle cache) and `release.yml` (tag `v*`, signed AAB+APK, GitHub Release via `gh`) | `.github/workflows/` | Action versions looked up via the GitHub releases API; YAML parsed locally |
| 6 | Lint run, fixes in owned files only | `app/build.gradle.kts` (`lint {}`) | `./gradlew lintDebug`; remaining issues listed in the report |
| 7 | LICENSE (MIT) | `LICENSE` | read |
| 8 | Privacy policy EN+DE, GitHub-Pages-ready | `docs/privacy-policy.md` | cross-checked against the code: only `api.transitous.org`, ride-start coordinate + time |
| 9 | Play Console answers (Data safety with cited definitions, FGS declaration + video script, permissions, content rating) | `docs/release/play-console.md` | cites Google help pages |
| 10 | Store listing en-US + de-DE | `fastlane/metadata/android/` | char counts checked with a script |
| 11 | F-Droid checklist + draft metadata YAML | `docs/release/fdroid.md` | read |
| 12 | README, CHANGELOG, CONTRIBUTING, RELEASE | root, `docs/RELEASE.md` | links resolve; commands match the Gradle setup |

Final gate: `./gradlew testDebugUnitTest assembleDebug assembleRelease bundleRelease`.
