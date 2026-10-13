# Releasing Delayd

Flow: keystore (once) → secrets (once) → bump version → tag → CI builds → Play internal testing → F-Droid.

## 1. Create the upload keystore (once)

Use JDK 17's `keytool` (Android Studio ships one in `jbr/bin`). Keep the file and passwords in a password manager and make a backup. Never commit them (`*.jks`, `*.keystore` and `keystore.properties` are gitignored).

```bash
keytool -genkeypair -v -keystore delayd-release.jks -alias delayd \
  -keyalg RSA -keysize 4096 -validity 10000
```

With Play App Signing (default for new apps), this is only the **upload key**: if it is lost, Google can reset it. For F-Droid/GitHub APKs it is the actual signing key, so a lost key means users must reinstall. Back it up.

Local signing: create `keystore.properties` in the repo root:

```properties
storeFile=/absolute/path/to/delayd-release.jks
storePassword=...
keyAlias=delayd
keyPassword=...
```

Or set `DELAYD_KEYSTORE_FILE`, `DELAYD_KEYSTORE_PASSWORD`, `DELAYD_KEY_ALIAS`, `DELAYD_KEY_PASSWORD`. The properties file wins over env vars.

**Fallback and the safety net.** With no key configured, `assembleRelease`/`bundleRelease` sign with the **debug** key and print a warning. That is only for local smoke tests of the R8 build. The fallback is disabled (the build fails with "No release keystore configured") when `GITHUB_REF_TYPE=tag` or `DELAYD_REQUIRE_RELEASE_KEY=true`, so a tag build in CI can never publish a debug-signed artifact. Never upload a debug-signed APK or AAB.

## 2. GitHub secrets (once)

Repo > Settings > Secrets and variables > Actions:

| Secret | Value |
|---|---|
| `DELAYD_KEYSTORE_BASE64` | `base64 -w0 delayd-release.jks` (Linux/Git Bash; macOS: `base64 -i delayd-release.jks`) |
| `DELAYD_KEYSTORE_PASSWORD` | store password |
| `DELAYD_KEY_ALIAS` | `delayd` |
| `DELAYD_KEY_PASSWORD` | key password |

`GITHUB_TOKEN` is provided automatically; the workflow has `contents: write` to create the release.

## 3. Bump the version

Edit `gradle.properties`:

```properties
VERSION_NAME=1.0.1   # semantic version, shown to users
VERSION_CODE=2       # plain integer, +1 for every release that goes to a store, never reused
```

Then:

1. Add `fastlane/metadata/android/en-US/changelogs/<VERSION_CODE>.txt` and the `de-DE` twin (max 500 characters; Play and F-Droid show it).
2. Move the `[Unreleased]` entries in `CHANGELOG.md` under the new version with the date.
3. Update `docs/release/fdroid.md` draft if you maintain it.
4. Check locally: `./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease` (with the keystore configured).
5. Commit: `git commit -am "Release 1.0.1"`.

## 4. Tag and let CI release

```bash
git tag v1.0.1          # must equal v + VERSION_NAME; CI checks this
git push origin main v1.0.1
```

`.github/workflows/release.yml` runs the unit tests, builds the signed APK and AAB, and creates a GitHub Release with `delayd-v1.0.1.apk`, `delayd-v1.0.1.aab` and the R8 `mapping.txt` (needed to read crash stack traces). Verify the signature locally:

```bash
apksigner verify --print-certs delayd-v1.0.1.apk
```

## 5. Google Play (internal testing first)

One-time setup: Play Console developer account (one-time fee), create the app, fill App content using `docs/release/play-console.md` (Data safety, foreground service declaration with the demo video, permissions, content rating, target audience), host the privacy policy (enable GitHub Pages on `/docs` of the repo), and add the store listing from `fastlane/metadata/android` plus screenshots from `docs/screenshots`.

Each release:

1. Testing > **Internal testing** > Create new release > upload `delayd-vX.Y.Z.aab` (first time: accept Play App Signing).
2. Upload `mapping.txt` under the release's deobfuscation file if prompted.
3. Paste release notes from the changelog files, add testers (email list), roll out. Testers install through the opt-in link within minutes.
4. After a few days without issues: promote to Closed/Open testing, and for personal developer accounts created after Nov 2023 complete the required closed test with 12+ testers for 14 days before production access (check the current Play rule).
5. Production: promote the same release, staged rollout (e.g. 20 %) first.

## 6. F-Droid

First time: follow `docs/release/fdroid.md` (checklist, screenshots, metadata YAML) and open a merge request to `fdroiddata`. After inclusion, F-Droid picks up new `v*` tags automatically (`UpdateCheckMode: Tags`), builds from source and publishes within days. No upload is needed per release; make sure the tag exists and the changelog file for the `versionCode` is in the tagged commit.

## Checklist per release

- [ ] `VERSION_NAME` and `VERSION_CODE` bumped, tag equals `v<VERSION_NAME>`
- [ ] Changelog files (en, de) named after the new `VERSION_CODE`, `CHANGELOG.md` updated
- [ ] CI green on the commit, release workflow produced APK, AAB and mapping
- [ ] Privacy policy and Data safety still match what the app sends (`docs/privacy-policy.md`, `docs/release/play-console.md`)
- [ ] Play internal test installed on a real device, detection toggle works
