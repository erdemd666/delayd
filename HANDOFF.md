# Handoff: v1.0.0 code-complete

## State
- Workstreams A (core), B (UI/UX), C (release), D (manual add) and E (QA) are all merged into `main`.
- Final check on `main` passed: `clean testDebugUnitTest assembleDebug assembleRelease bundleRelease lintDebug`.
  - 70 unit tests, 0 failures.
  - Lint: 0 errors and 10 accepted warnings, with reasons in [docs/qa/review.md](docs/qa/review.md).
  - Release APK 1.51 MiB, AAB 3.3 MiB.
- QA results:
  - [docs/qa/review.md](docs/qa/review.md): defects, ranked; the fixed and the known-open ones.
  - [docs/qa/e2e.md](docs/qa/e2e.md): every emulator flow passed, including the R8 release build.
  - [docs/qa/plan-status.md](docs/qa/plan-status.md): status per PLAN item.
- Nothing has been pushed. There is no remote yet.

## Owner to-dos before publishing
1. Fill in the TODOs in [docs/privacy-policy.md](docs/privacy-policy.md): name, postal address (a legal requirement in Germany) and contact email.
2. Create the GitHub repo `erdem61/delayd`, push, and enable Pages on `/docs` so the privacy policy has a URL.
3. Create the release keystore and back it up. Add the 4 GitHub secrets. Steps: [docs/RELEASE.md](docs/RELEASE.md).
4. Play Console:
   - App content answers from [docs/release/play-console.md](docs/release/play-console.md).
   - Data safety now includes the manual-add station search text.
   - Record the foreground-service demo video.
5. Real-device checks the emulator can't give:
   - one real train ride;
   - a day of battery measurement;
   - a TalkBack pass;
   - a contrast check.
6. Tag `v1.0.0`. CI builds the signed APK + AAB. Upload to the internal testing track, then submit to F-Droid ([docs/release/fdroid.md](docs/release/fdroid.md)).

## Known open items (from review.md)
- Tapping a notification resets the back stack.
- A ride in progress is lost if detection is switched off mid-ride.
- The Deutschlandticket cap is applied per status group (only matters beyond 10 cases a month).
- Export goes out as text (fine up to ~4000 trips).

## Environment notes
- JAVA_HOME = `C:\Program Files\Android\Android Studio\jbr`.
- `local.properties` must be exactly `sdk.dir=C\:/Users/erdem/AppData/Local/Android/Sdk` with an LF line ending, or lint fails. Run `clean` after changing it.
- The session's worktree isolation fails, because the home folder is also a git repo. Create worktrees by hand under `../delayd-worktrees/`.
- The emulator `Medium_Phone_API_36.1` also has the owner's other apps on it. Agents must only touch `app.delayd`.
