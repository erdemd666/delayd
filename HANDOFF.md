# Handoff: where we stopped (2026-10-13)

## Done and on `main`
- v0.1 MVP, then workstreams A (core), B (UI/UX), C (release) and D (manual add), all merged.
- `main` at the D merge passes testDebugUnitTest (67 tests) and assembleDebug.
- Before D: assembleRelease and lintDebug passed (0 errors).
- Plan and contracts: [docs/PLAN.md](docs/PLAN.md).

## In progress: Workstream E (QA & polish), stopped midway
- Branch `ws/qa`, worktree `../delayd-worktrees/qa`. **Not merged into main, not verified.**
- One commit there: `a2b13af`. It fixes:
  - "delete all" leaving queued rides behind;
  - a stale duplicate banner;
  - fare overflow;
  - the BootReceiver action check;
  - plurals and lint cleanups.
- E stopped during the emulator e2e, at the onboarding "deny" path. Still not done:
  - `docs/qa/review.md` (review findings);
  - the rest of the e2e run, recorded in `docs/qa/e2e.md`;
  - the release (R8) smoke test on the emulator;
  - the accessibility pass;
  - docs close-out (CHANGELOG, fastlane, privacy policy now including manual-add search text, plan-status).
- The full task list for E is in the coordinator's prompt. It mirrors "Phase 2: E" in PLAN.md.

## Next session
1. Run `./gradlew testDebugUnitTest assembleDebug lintDebug` on `ws/qa` to check commit `a2b13af`.
2. Start E again on `ws/qa`, with the remaining items above.
3. Merge `ws/qa` → main, then run the final `clean testDebugUnitTest assembleDebug assembleRelease bundleRelease lintDebug`.
4. Owner to-dos (see docs/RELEASE.md and docs/release/play-console.md):
   - create the keystore and the 4 GitHub secrets;
   - create the GitHub repo `erdem61/delayd`;
   - put a real privacy email and postal address in docs/privacy-policy.md;
   - open the Play Console account.

## Environment notes
- JAVA_HOME = `C:\Program Files\Android\Android Studio\jbr`.
- `local.properties` must be exactly `sdk.dir=C\:/Users/erdem/AppData/Local/Android/Sdk` with an LF line ending, or lint fails. Run `clean` after changing it.
- The session's built-in worktree isolation fails, because the home folder is also a git repo. Create worktrees by hand under `../delayd-worktrees/`.
- The emulator `Medium_Phone_API_36.1` also has the owner's other apps on it (`com.erdem6.rehab`). Agents must only touch `app.delayd`.
- The folder `../delayd-worktrees/release` is left over: its worktree is deregistered but Windows refused to delete it. Delete it by hand.
