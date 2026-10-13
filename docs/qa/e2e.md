# Emulator end-to-end (Workstream E)

AVD `Medium_Phone_API_36.1` (Android 16, 1080x2400, device clock UTC, app shows Europe/Berlin). Debug build for the flows, release build for the smoke test. Only `app.delayd` was touched. UI was driven with `adb shell input` plus `uiautomator dump`, with the app confirmed as the resumed activity before taps. Taps on system dialogs were limited to the permission prompt of Delayd and dismissing the share sheet.

| # | Step | Result |
|---|---|---|
| 1 | Fresh install (`pm clear`), onboarding, permissions **denied** twice | PASS. Second denial shows "Android will not ask again" with "Open app settings"; Get started leads to Home with detection Off and "Location access is blocked" |
| 2 | Fresh install, onboarding **grant** (location "while using", notifications "allow"), Get started | PASS. Foreground service running, type location (0x8), tracking notification present |
| 3 | Settings > Simulate ride | PASS. Trip "ICE 999 (simulated)" Berlin Hbf to Potsdam Hbf, +75 min appears on Home; money and Deutschlandticket progress update |
| 4 | Delay notification fires | PASS. "ICE 999 (simulated) was 75 min late" in channel `delays` |
| 5 | Tap the notification | PASS. Opens that trip's detail (deep link). Note: tapping the notification group header instead (not the notification) opens Home; that is the system's group summary |
| 6 | Single-ticket claim: fare "20,00" gives "You are owed 5.00", German claim text shown, "I sent this claim", status Claimed, "Mark as paid", Paid | PASS. Home Recovered 5.00 |
| 7 | Deutschlandticket monthly claim (4 open cases, 6.00) | PASS. The paid trip is not included, text lists the 4 cases, "I sent this monthly claim" moves them to Claimed, Home Claimed 6.00, progress resets |
| 8 | Manual add: station search "Potsdam", pick S Potsdam Hauptbahnhof, time 16:34, trains list, pick S7, destination S Wannsee, Add | PASS. Trip added, marked "Reported by you" (no live data left) |
| 9 | Manual add duplicate warning | PASS. "This ride is already in your history" with "Open trip" |
| 10 | Manual add offline (`svc wifi disable`, `svc data disable`) | PASS. "You're offline. Connect to the internet and try again."; after re-enabling, "Try again" lists stations |
| 11 | Export data | PASS (opens the Android share sheet; content not inspected) |
| 12 | Delete all data | PASS. `trips.json` and `pending.json` both `[]`, Home empty state |
| 13 | Rotation during the claim flow | PASS. Landscape and back: still on the claim screen, fare "20,00" and the generated text kept |
| 14 | Boot | PASS (real). `adb reboot` with detection on: after boot the "Detection paused / Tap to resume" notification is posted. Tapping it (tested earlier after a framework restart that killed the service) starts the foreground service from the notification, as designed for Android 14+. Not testable: a third-party `adb shell am broadcast ... BOOT_COMPLETED` is rejected (receiver not exported, protected broadcast), and `MY_PACKAGE_REPLACED` was not exercised separately (same receiver code path) |
| 15 | Detection toggle on Home | PASS. Switch starts the service |

Environment notes: the emulator quick-boot snapshot and low free memory (3 GB, load average up to 49 during builds) killed the app process and the service once; the sticky restart showed the resume notification, which doubled as a test of that path. Sticky restart is not a defect of the app.

## Release smoke test (assembleRelease, R8 on, debug-signed)

- APK `app-release.apk`: **1,582,219 bytes (1.51 MiB)**; AAB 3,461,050 bytes.
- Debug uninstalled, release installed, permissions granted via `pm grant`.
- PASS: onboarding, Compose UI, foreground service start, manual add with live Transitous responses (station search and departures parsed through org.json after R8), the stored trip reloaded from `trips.json` after `force-stop` and a fresh launch. No `FATAL EXCEPTION`, `ClassNotFound` or `NoSuchMethod` in logcat. No R8 rules were needed.
- The debug build was reinstalled afterwards, then the emulator was shut down with `adb emu kill`.

## Accessibility pass (debug build)

- uiautomator content descriptions: found and fixed the duplicate "Allow" buttons and the unlabeled "Add a ride" FAB; the date and time buttons, the delay progress bar, the step indicator and switches have labels; merged rows read as one item.
- 200 % font scale (`settings put system font_scale 2.0`): Onboarding, Home, Settings, trip detail OK. Claim and manual add use the same Panel/FlowRow components; not re-inspected visually.
- Touch targets all at least 48 dp.
- Not done: a run with TalkBack itself and a contrast measurement tool.

## Final verification

Command: `./gradlew clean testDebugUnitTest assembleDebug assembleRelease bundleRelease lintDebug` (after `./gradlew --stop`, a running daemon blocked `clean` on Windows).

```
BUILD SUCCESSFUL in 1m 39s
109 actionable tasks: 107 executed, 2 up-to-date
unit tests: 70 run, 0 failures
lintDebug: 0 errors, 10 warnings (accepted, see docs/qa/review.md)
app-release.apk 1,582,219 bytes, app-release.aab 3,461,050 bytes
```
