# Code review (Workstream E)

Scope: every Kotlin source in `app/src` (main, debug, test), manifests, resources. Ranked by severity. "Fixed" entries name the commit.

## Fixed

| # | Sev | Where | Failure scenario | Fix |
|---|---|---|---|---|
| 1 | High | `ui/Settings.kt:42` (a2b13af) | "Delete all data" deleted trips one by one but left `pending.json`; queued rides (with coordinates) survived and could re-create trips after the user deleted everything. | `TripStore.clear()` + `RideQueue.clear()`; test `clear empties the queue and the file`. |
| 2 | High | `Manifest` application (4bceb82) | `allowBackup` defaulted to true, so `pending.json` (ride start/end coordinates) and trips went to Google/device-transfer backups, contradicting "location never leaves the device". | `android:allowBackup="false"`. Export is the supported way to move data. |
| 3 | Medium | `RideQueue.drain` (a2b13af) | An unexpected `RuntimeException` (not JSON/DateTime) on one ride escaped `drain`, so the rides behind it were never looked up and the service logged the same failure forever. | Catch `RuntimeException` per journey, back off like bad data; test added. |
| 4 | Medium | `ui/Format.kt:34` (a2b13af) | `parseFareCt` used `Int` conversion: "42949672.96" wrapped to 0 and "42949722.96" to 50.00 (accepted, wrong claim). | Convert via `Long`, then range check; tests added. |
| 5 | Medium | `ui/AddRide.kt:247` (a2b13af) | After the duplicate warning, picking a different stop kept `existingId`, so the "Add" button stayed disabled and the stale banner showed. | Reset `existingId` when a stop is picked. |
| 6 | Medium | `ui/Home.kt` DTicketProgress (4bceb82) | The monthly-claim notification says "claim for September is ready", but Home only offered the current month; the previous month's pool was reachable only through a single trip. Also `remember{}` pinned the month across midnight on the 1st. | `claimMonth()` offers last month while its open pool is payable, else this month; test added. |
| 7 | Low | `BootReceiver.kt:10` (a2b13af) | Receiver acted on any intent action. | Check for BOOT_COMPLETED / MY_PACKAGE_REPLACED. |
| 8 | Low | `ui/TripDetail.kt` (4bceb82) | A claimed/paid Deutschlandticket trip showed "October: 0 open cases, 0,00 so far". | Month line only for OPEN trips. |
| 9 | Low | `core_strings` / plurals (a2b13af) | "1 cases" in month texts. | Plurals en + de. |
| 10 | Medium | `Notifier.open`, `MainActivity` (workstream I) | A notification tap used `CLEAR_TOP` without `SINGLE_TOP`: the running activity was recreated and in-app navigation reset. | `launchMode="singleTop"` plus `FLAG_ACTIVITY_SINGLE_TOP` instead of `CLEAR_TOP`; `onNewIntent` pushes Trip (or Trip > Claim) onto the current back stack. The tile's `ACTION_START_DETECTION` is handled in `onNewIntent` too. Checked on the emulator for the delay tap, "Claim now" and the tile; the monthly-claim notification carries no trip, so it just brings the app forward. |
| 11 | Low | `Compensation.dTicketMonthCt`, `ui/Format.totals` (workstream I) | The 25 % Deutschlandticket cap was applied per status group, so open + claimed + paid could exceed 15,75 EUR a month. | The cap is shared per month (paid first, then claimed, then open); `dTicketTakenCt` feeds Home, month claim, trip detail, claim text and the monthly notification. JVM tests in `CompensationTest`, `MonthlyClaimTest`, `FormatTest`. |
| 12 | Low | `ui/Components.kt Screen`, `ui/Claim.kt Fact` (workstream I) | At 200 % font scale the fixed-height app bar clipped long titles and "Planmäßige Ankunft" broke mid-word in the claim facts. | Own wrapping app bar; facts stack label above value above 1.3x. Also: fare field error state, announcement of "Kopiert", language names tagged with their language for TalkBack. |

## Not defects / checked and fine

- Races: `TripStore` and `RideQueue` mutate under `@Synchronized`, the service has one worker thread, `TripDetector.start` is volatile and is the only field the worker reads. `Pipeline.queue` is synchronized. The debug simulator may drain concurrently with the service; `TripStore.add` dedups by id so there is at most one notification.
- Android 14-16 FGS: `startForeground(..., LOCATION)` inside try/catch, falls back to the resume notification; started only from a visible activity or a notification tap (both exempt). Verified on the emulator (API 36): resume notification tap starts the service.
- PendingIntents all `FLAG_IMMUTABLE`, per-trip request codes.
- Time zones: everything is `Instant`; UI and month grouping use `Europe/Berlin`; typed manual times use `nearestInstant` (midnight safe).
- Money: integer cents, floor division like the DB rules; German claim text formats with `Locale.GERMANY`.

## Found, not fixed (accepted)

- A ride in progress is lost if the user switches detection off mid-ride (detector state is in memory). Rare, documented limit.
- Delay notifications arrive about 30 minutes after arrival (`SETTLE`, to allow a connecting ride). Design decision from workstream A.
- Export is a text share of the JSON (binder size limit is about 1 MB, roughly 4000 trips).
- Notifications are not re-sent if `POST_NOTIFICATIONS` is denied when a trip is found; the trip still appears in the app.

## Lint

0 errors, 10 warnings, all consciously accepted:

| Warning | Decision |
|---|---|
| `GradleDependency` compose-bom 2025.06.00 to 2026.09.00, activity-compose 1.10.1 to 1.13.0 | **Not upgraded.** The pinned versions are what the whole e2e run, the R8 smoke test and the screenshots were done with. A BOM jump is a 15-month leap of Compose/Material3 that changes layout and deprecates APIs (`LocalClipboardManager` etc.) and may force AGP/compileSdk changes, with no user-facing gain for 1.0. Revisit right after release in a dedicated change with a full e2e. |
| `NewerVersionAvailable` org.json (test only) | Test-only stub for JVM tests. Left. |
| `AndroidGradlePluginVersion` Gradle 8.14 to 8.14.6 | Patch release of the wrapper. Left; reproducible-build and CI settings were validated on 8.14. |
| `UseKtx` x5 (`SharedPreferences.edit`) | Would need the `core-ktx` dependency for a style change. Left (plan: no dependency without a reason). |
| `DataExtractionRules` on `android:allowBackup="false"` | Intentional (finding 2): no cloud backup and no device transfer of trips or queued coordinates. A `dataExtractionRules` XML would be more code for the same effect. |

## Accessibility (found and fixed)

| Sev | Where | Problem | Fix (81b0539) |
|---|---|---|---|
| Medium | `ui/Onboarding.kt` PermissionRow | Two buttons both announced just "Allow". | Spoken label "Allow: Precise location" / "Allow: Notifications". |
| Medium | `ui/Home.kt` FAB | The "Add a ride" label did not show up in the accessibility tree dump (empty clickable). | Explicit content description. |

Touch targets (uiautomator bounds) are at least 126 px = 48 dp on Home, Onboarding, Settings, trip detail, claim and manual add; 200 % font scale was checked on Onboarding, Home, Settings and trip detail (no clipping, buttons wrap).
