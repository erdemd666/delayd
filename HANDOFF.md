# Handoff: v1.0.0 code-complete

## State
- Workstreams A–E (core, UI/UX, release, manual add, QA), F (German-first, UX, erdem6.com branding), G (simulation), H (core open items) and I (UI open items, a11y, screenshots) are all merged into `main`.
- Final check on `main` passed: `clean testDebugUnitTest assembleDebug assembleRelease bundleRelease lintDebug`.
  - 107 unit tests (2 live-API tests skipped by default), 0 failures.
  - Lint: 0 errors and 10 accepted warnings, with reasons in [docs/qa/review.md](docs/qa/review.md).
  - Release APK 1.51 MiB, AAB 3.3 MiB.
- QA results:
  - [docs/qa/review.md](docs/qa/review.md): defects, ranked; the fixed and the known-open ones.
  - [docs/qa/e2e.md](docs/qa/e2e.md): every emulator flow passed, including the R8 release build.
  - [docs/qa/plan-status.md](docs/qa/plan-status.md): status per PLAN item.
- Nothing has been pushed. There is no remote yet.

## Owner to-dos before publishing
Order chosen by the owner: stores first, GitHub last (after the legal side is settled).
1. Put the legal name and postal address into the TODO lines of [docs/privacy-policy.md](docs/privacy-policy.md) (Impressumspflicht), then regenerate `docs/privacy-policy.html`. Contact is already `hi@erdem6.com`.
2. Upload `docs/privacy-policy.html` to `https://erdem6.com/delayd/privacy`. The app's Settings link points there.
3. Create the release keystore and back it up ([docs/RELEASE.md](docs/RELEASE.md)). Without GitHub yet, build locally with `keystore.properties`: `./gradlew bundleRelease`.
4. Play Console: app id `com.erdem6.delayd`, App content answers from [docs/release/play-console.md](docs/release/play-console.md) (Data safety includes the manual-add station search text), record the foreground-service demo video, upload the AAB to internal testing.
5. Real-device checks: one real train ride, a day of battery, TalkBack, contrast.
6. Transitous only allows open-source use: publish the GitHub repo by the time the app is public, then restore the "Source code" link in Settings and the source line in the store descriptions.
7. Apple App Store: there is no iOS app yet. It needs a separate build.

## Simulation
- Real-world scenario suite + live replay: [docs/simulation/results.md](docs/simulation/results.md); GPX demo routes in docs/simulation/gpx/.

## Known open items (from review.md)
- A ride in progress is lost only if the system kills the app without `onDestroy` (force stop, low memory).
- The drain job is inexact: in Doze a pending ride is looked up later, never lost.
- Export goes out as text (fine up to ~4000 trips).
- Not run on a device yet: the JobScheduler drain, the monthly-claim notification, a real TalkBack pass.

## Environment notes
- JAVA_HOME = `C:\Program Files\Android\Android Studio\jbr`.
- `local.properties` must be exactly `sdk.dir=C\:/Users/erdem/AppData/Local/Android/Sdk` with an LF line ending, or lint fails. Run `clean` after changing it.
- Parallel work uses git worktrees under `../delayd-worktrees/` (the home folder is also a git repo, so create them by hand).
- The emulator `Medium_Phone_API_36.1` also has the owner's other apps on it. Only touch `com.erdem6.delayd` there.
