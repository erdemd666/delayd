# docs/PLAN.md status (Workstream E)

Legend: done / deferred (reason) / not done.

## Workstream A: core engine

| # | Item | Status |
|---|---|---|
| 1 | Reliable lookups: persisted queue, backoff | Done (`RideQueue`, tests; clear on delete-all added in QA) |
| 2 | Survive reboot / process death, decision doc | Done. "Tap to resume" notification; verified with a real `adb reboot` and a notification tap on API 36 |
| 3 | Battery: motion sensor, GPS provider off; measure and document drain | Done except the measurement: **deferred**, needs a day of real use on a device (`docs/decisions/battery.md` says how) |
| 4 | Detection quality: merge split rides, transfers, cancelled trains, ignore < 3 km / no match, mark phone-measured | Done (`Journeys`, `Transitous.match`, tests) |
| 5 | Notifications: delay with deep link, monthly claim, separate channels | Done. Delay notification and its deep link verified on the emulator; the monthly reminder is JVM-tested (`MonthlyClaimTest`), not waited for on a device |
| 6 | Robustness: errors, timeouts, rate limits, malformed JSON, TripStore concurrency | Done |
| 7 | Debug ride simulator | Done, used for the whole e2e run |
| 8 | Tests for new logic | Done (unit tests; see e2e.md for the final count run) |

## Workstream B: UI/UX

| # | Item | Status |
|---|---|---|
| 1 | Design system, dark/light, no dynamic color, adaptive + monochrome + notification icon | Done |
| 2 | Onboarding incl. denied / blocked states | Done, grant and deny paths run on the emulator |
| 3 | Home: status, money summary, trips by month, empty state, Deutschlandticket progress | Done |
| 4 | Trip detail: timeline, source, compensation, status actions, delete | Done |
| 5 | Claim flow incl. month bundle | Done, single and monthly run to "claimed" |
| 6 | Settings: ticket type, toggle, health, export, delete all, privacy, licenses, attribution, version | Done. Export opens the share sheet (content not inspected); delete-all verified in the files |
| 7 | Quality bar: TalkBack labels, 48 dp, contrast, 200 % font, insets, portrait + landscape, 360 dp to tablets, strings + plurals, formats | Done for labels (two fixes), 48 dp (dump), 200 % font, rotation, strings, formats. **Deferred**: WCAG contrast was not measured with a tool; 360 dp and tablet widths were not re-run on emulators in QA (previews and workstream B screenshots only); no real TalkBack run |
| 8 | Navigation + deep link | Done (string back stack) |
| 9 | Previews, screenshots light + dark, en + de | Mostly done. Missing: light-German manual-add screens, dark claim-single; the store set uses light screenshots that exist |

## Workstream C: release engineering

| # | Item | Status |
|---|---|---|
| 1 | Signing from keystore.properties / env, R8 verified, version scheme, reproducible-friendly | Done; release smoke test passed (see e2e.md). **Deferred**: the byte-for-byte reproducibility test (needs two machines) |
| 2 | CI (tests, lint, debug build; signed tag release; cache) | Done in `.github/workflows`; not run in QA (no remote) |
| 3 | Lint config | Done: 0 errors, accepted warnings in review.md |
| 4 | LICENSE, privacy policy en+de, Play answers, fastlane metadata + screenshots, F-Droid checklist | Done. **Owner TODOs remain** in the privacy policy (name, address, contact) |
| 5 | README, CHANGELOG, CONTRIBUTING, RELEASE.md | Done |

## Phase 2

| Item | Status |
|---|---|
| D: manual add | Done (search, trains, destination, typed arrival, duplicate, offline; verified on emulator and in the R8 build) |
| E: code review and fixes | Done (docs/qa/review.md) |
| E: simulator end-to-end | Done (docs/qa/e2e.md) |
| E: accessibility pass | Done, see the e2e.md section |
| E: final screenshots | Not redone: workstream B/D screenshots are current except for the two small UI label changes (no visual change) |
| E: release build smoke test | Done |
| E: every plan item done or deferred in CHANGELOG | Done: "Known limits" lists the deferred ones |

## Not done on purpose (roadmap, not in the plan)
Ticket QR scan, other countries, per-trip "separate ticket" flag for connections.
