# Delayd: path to v1.0 (release-ready)

Every agent working on this repo reads this file first. It holds the product decisions, the contracts between workstreams and who owns which files.

## Product decisions (fixed, don't re-litigate)

- **What:** an Android app that detects train rides on its own, matches them to the real train with live data, and tells the user when DB passenger rights pay out. It also prepares the claim. Germany first.
- **Distribution:** free and open source (MIT), Google Play + F-Droid. **No Google Play Services** and no proprietary SDKs, because that keeps F-Droid possible. Platform APIs + AndroidX only.
- **Data:** Transitous API (open-source, non-commercial use, User-Agent required, credit `transitous.org/sources` + OpenStreetMap). No backend of our own. No analytics, no tracking, no ads.
- **Privacy:** location history never leaves the device. The only network calls are station-area timetable queries to Transitous.
- **Languages:** English (default) + German. All user-facing text lives in string resources.
- **Platform:** Kotlin, Jetpack Compose, Material 3, minSdk 26, target/compile 36, single `app` module.
- **Size discipline:** smallest code that fully works. Every new dependency must justify itself in one line in the PR/commit. No architecture frameworks (no DI framework, no Room unless JSON storage proves insufficient).
- **Brand:** "Delayd" is our own brand. **Never use DB's logo, DB red or any wording that implies we are Deutsche Bahn.** We mention "Deutsche Bahn" only factually ("claim with DB").

## Domain rules (source: bahn.de Fahrgastrechte, Oct 2026)

- Single ticket: 25 % of the fare at a 60+ min delay at the destination, 50 % at 120+ min.
- Deutschlandticket (63 €/month in 2026): 1.50 € per 60+ min case, collected per month, max 25 % of the ticket price.
- Nothing is paid below 4 €.
- Claims go through bahn.de / DB Navigator (needs login) or the paper "Fahrgastrechte-Formular".
- Delays from extraordinary circumstances may be excluded (since June 2023).

## Current state (v0.1, commit e95d8ba)

| File | Role |
|---|---|
| `TripDetector.kt` | location fixes → `Ride(start, end)`, a speed state machine |
| `Transitous.kt` | `Trip`/`ClaimStatus` model; departures at ride start → matched `Trip` |
| `TripStore.kt` | JSON file store, `trips: StateFlow<List<Trip>>`, `add`, `update(id){}`, `delete(id)` |
| `TrackingService.kt` | foreground location service, duty cycling, lookup thread, delay notification |
| `Compensation.kt` | DB rules in cents |
| `MainActivity.kt` | single-screen Compose UI (to be replaced) |

## Contracts between workstreams

These are the only APIs the UI may use from the core. Core may **add** to them but must not rename or remove:

```kotlin
// Transitous.kt
enum class ClaimStatus { OPEN, CLAIMED, PAID, DISMISSED }
data class Trip(train, from, to, scheduledArrival: Instant, arrival: Instant, realtime: Boolean,
                fareCt: Int? = null, status: ClaimStatus = OPEN) { val id: String; val delayMin: Long }
// TripStore.kt
TripStore.init(context); TripStore.trips: StateFlow<List<Trip>>
TripStore.update(id) { it.copy(...) }; TripStore.delete(id)
// TrackingService.kt
TrackingService.running: StateFlow<Boolean>   // detection on
TrackingService.riding: StateFlow<Boolean>    // a ride is in progress right now
TrackingService.EXTRA_TRIP_ID                 // String extra on the MainActivity intent from a delay notification
// start/stop: context.startForegroundService(Intent(context, TrackingService::class.java)) / stopService(...)
// Compensation.kt: singleTicketCt, dTicketMonthCt, payable, format, MIN_PAYOUT_CT, D_TICKET_CASE_CT, D_TICKET_PRICE_CT
```

New fields on `Trip` must have defaults and be stored/loaded backward-compatibly in `TripStore`.

## File ownership (parallel phase)

Nobody edits a file outside their area. If you need a change there, write it under "Requests to other workstreams" in your final report.

| Workstream | Owns |
|---|---|
| **A: Core engine** | `TripDetector.kt`, `Transitous.kt`, `TripStore.kt`, `TrackingService.kt`, `Compensation.kt`, any new non-UI file in `app.delayd` (e.g. `BootReceiver.kt`, `core/…`), their tests, `AndroidManifest.xml` (permissions, receivers, services) |
| **B: UI/UX** | `MainActivity.kt`, new package `app.delayd.ui` (all screens, theme, components), `res/` (values, values-de, drawable, mipmap, xml), the `<application>`/`<activity>` attributes in the manifest that concern the theme, icon and label only (state these in the report, A merges manifest) |
| **C: Release engineering** | root + app Gradle files **except the `dependencies {}` block**, `proguard-rules.pro`, `.github/`, `docs/` (except this file), `fastlane/`, `LICENSE`, `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md` |

Any workstream may add lines to `dependencies {}` in `app/build.gradle.kts` (the coordinator resolves conflicts).

## Workstream A: Core engine

1. **Reliable lookups:** persist pending rides (JSON next to trips). Retry with backoff when the network returns. No ride is lost if the process dies. WorkManager is acceptable here (AndroidX, F-Droid-safe), and so is a hand-rolled queue if smaller.
2. **Survive reboots and process death:** detection comes back after reboot and app updates if the user had it on. Research what Android 14/15/16 allow for a `location` foreground service started from `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` with only while-in-use location. If it needs `ACCESS_BACKGROUND_LOCATION`, decide on the smallest compliant path (e.g. a notification "Tap to resume detection") and document the reasoning in `docs/decisions/background-location.md`.
3. **Battery:** idle sampling that costs near nothing. Consider `Sensor.TYPE_SIGNIFICANT_MOTION` (platform, low-power) to wake from idle instead of polling every 60 s. Stop GPS when the GPS provider is off. Measure and document the expected drain in `docs/decisions/battery.md`.
4. **Detection quality:**
   - Merge rides split by a long stop at the same place.
   - Treat a transfer on one journey as one trip where the rules allow it (delay counts at the final destination).
   - Handle cancelled trains: a ride on a replacement train still counts against the original planned arrival when possible.
   - Ignore rides under ~3 km or without a matching train.
   - Mark when arrival time was measured by the phone.
5. **Notifications:** delay notification (≥ 60 min) with `EXTRA_TRIP_ID` deep link. A monthly "your Deutschlandticket claim is ready (≥ 4 €)" notification. Separate channels.
6. **Robustness:**
   - Handle Transitous errors, timeouts and rate limits (back off, never hammer).
   - Malformed JSON never crashes the app.
   - `TripStore` concurrency is safe between service, worker and UI.
7. **Debug ride simulator** (debug builds only): replay a recorded ride through the real pipeline, so QA and demos work without riding a train. Expose it as a debug-only entry point that UI can show (e.g. `DebugTools.simulateRide(context)` in `src/debug`).
8. **Tests** for every new branch of logic (JVM unit tests, fixtures from real API responses).

## Workstream B: UI/UX

Professional, calm, trustworthy design: a finance-app feel (money you get back), not a transit-app clone.

1. **Design system:** custom Material 3 color scheme (light + dark), not DB red. Typography scale, spacing tokens, shapes. Dynamic color off by default so the brand stays consistent. Adaptive launcher icon + monochrome themed icon + notification icon.
2. **Onboarding (first run):** value → how it works + privacy promise → permissions with rationale (precise location, notifications) → battery-optimization guidance (open system settings, explain why). Handle every denied / "don't ask again" state gracefully.
3. **Home:**
   - Detection status (on / off / riding now).
   - Money summary: owed now, claimed, recovered (sum of PAID).
   - Trips grouped by month, with clear delay badges.
   - Empty state.
   - Deutschlandticket monthly progress toward the 4 € minimum.
4. **Trip detail:**
   - Route timeline: scheduled vs actual arrival.
   - Data source (live data vs measured by phone).
   - Compensation breakdown with the rule explained.
   - Status actions: mark claimed / paid / dismiss, delete.
5. **Claim flow:** a guided flow that confirms the facts, takes the fare (single ticket) or bundles the month's cases (Deutschlandticket), copies a German claim text, opens the official DB page and marks the trip as claimed. The user always sees exactly what will be sent.
6. **Settings:** ticket type, detection toggle, battery/permissions health check, export data (JSON share), delete all data, privacy policy, open-source licenses, data attribution (Transitous + OSM), app version.
7. **Quality bar:**
   - TalkBack labels, 48 dp touch targets, WCAG AA contrast, works at 200 % font scale.
   - Edge-to-edge insets, portrait and landscape, small phones (360 dp) up to tablets.
   - All strings in `values/strings.xml` + `values-de/strings.xml`, with plurals.
   - Dates/times in Europe/Berlin, money as `4,50 €` in German and `€4.50` in English.
8. Navigation: the smallest solution that handles back correctly and the notification deep link (navigation-compose is acceptable).
9. Compose previews for the main screens. Screenshots of every screen (light + dark, en + de) saved to `docs/screenshots/` via the emulator (`Medium_Phone_API_36.1`). **B is the only workstream that uses the emulator in the parallel phase.**

## Workstream C: Release engineering

1. **Build:**
   - Release signing from `keystore.properties` or env vars (never committed; document key creation).
   - R8 on, verify `assembleRelease` + `bundleRelease` work and the minified app keeps org.json/Compose working.
   - `versionCode`/`versionName` scheme.
   - Reproducible-build friendly (F-Droid).
2. **CI (GitHub Actions):**
   - On push/PR: unit tests, lint, debug build.
   - On tag `v*`: signed AAB + APK attached to a GitHub Release (secrets documented).
   - Cache Gradle.
3. **Lint:** lint config, no new warnings in C's files. Report others' lint issues to the coordinator.
4. **Legal & store:**
   - `LICENSE` (MIT, © 2026 erdem61).
   - Privacy policy (EN + DE) in `docs/privacy-policy.md`, GitHub-Pages-ready.
   - Play Console answers: Data safety, foreground service `location` declaration text plus a demo video script, permissions justification, content rating notes.
   - Store listing in `fastlane/metadata/android/{en-US,de-DE}` (title ≤ 30, short ≤ 80, full description, changelog). The same structure F-Droid reads.
   - F-Droid readiness checklist.
5. **Docs:** a README fit for GitHub (badges, what/why, how it works, build, privacy, data credits, screenshot placeholders), `CHANGELOG.md`, `CONTRIBUTING.md`, `docs/RELEASE.md` (step-by-step: create keystore → tag → upload to Play internal track → F-Droid submission).

## Phase 2 (after the merge, by the coordinator)

- **D: Manual add:** search a station and a train when detection missed a ride (Transitous geocode + stoptimes). Full stack, one agent.
- **E: QA & polish:**
  - Code review across the merged app and fixing what it finds.
  - Run the debug simulator end-to-end on the emulator.
  - Accessibility pass, final screenshots, release build smoke test.
  - Verify every item here is done or explicitly deferred in `CHANGELOG.md`.

## Rules for every agent

- Read the code you touch first. Match the existing style (compact Kotlin, comments only for the *why*).
- Build environment: `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`. Create `local.properties` with `sdk.dir=C:/Users/erdem/AppData/Local/Android/Sdk` in your worktree (it is gitignored). Run `./gradlew testDebugUnitTest assembleDebug` before you finish. It must pass.
- Commit your work on your branch in small logical commits. Never push and never touch remotes.
- Final report:
  - what you did;
  - what you verified, and how;
  - what you deferred and why;
  - requests to other workstreams;
  - exact dependencies you added.
