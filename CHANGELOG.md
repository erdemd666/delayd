# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- German is now the default language (English stays available). In-app language choice in Settings (Android 13+), also in Android's per-app language settings. German dates with weekdays, `4,50 €`.
- Quick Settings tile to turn ride detection on and off.
- "Kopieren & DB öffnen" copies the claim text and opens the DB page in one tap.
- The ticket price of the last claim on a route is pre-filled next time.
- Delay notifications have "Claim now" and "Not relevant" actions.
- "A product of erdem6.com" line in Settings and onboarding.

### Changed
- German wording reviewed (Fahrgastrechte, Entschädigung, Verspätung am Zielbahnhof).

## [1.0.0] - Unreleased

First public release.

### Added
- Automatic train ride detection: a speed state machine over location fixes, with a battery-aware duty cycle (motion-sensor wake-up, slow network fixes while idle, GPS only while riding).
- Matching of rides to the real train via the Transitous API, with live delay data and a phone-measured fallback. Rides split by a long stop are merged, connections count as one journey (delay at the final destination), and a ride on a replacement train counts against the cancelled train's plan.
- A persistent ride queue with backoff: no ride is lost when the network or the process goes away.
- Resume after reboot or update: a "tap to resume" notification (Android 14+ does not allow starting a location service unseen).
- DB passenger-rights rules: single tickets (25 % / 50 %), Deutschlandticket (1.50 EUR per case, monthly cap), 4 EUR minimum.
- Notifications on separate channels: a delay notification that opens the trip, a monthly Deutschlandticket claim reminder, the resume notice.
- App: onboarding with permission rationale and denied / "don't ask again" handling, Home (detection status, money summary, Deutschlandticket progress, trips by month), trip detail with timeline, data source and rule explanation, claim flow (German claim text, copy, open the DB page, mark claimed / paid), Settings (ticket type, detection, health check, export, delete all, licenses, data sources).
- No ride is lost when detection is switched off mid-ride; pending rides are looked up with detection off (when the app opens and when the network is back); a change of trains under 3 min is matched as a connection.
- Add a ride by hand: station search, train and destination pick, optional typed arrival time, duplicate detection, offline state.
- English and German, light and dark theme, TalkBack labels, 48 dp touch targets, usable at 200 % font scale, edge-to-edge, landscape and tablets.
- Debug builds: a "Simulate ride" button that replays a recorded ride through the real pipeline.
- Release engineering: R8 minification, release signing from `keystore.properties` or environment variables, GitHub Actions CI and tag releases, F-Droid-friendly build settings.
- MIT license, bilingual privacy policy, Play Store and F-Droid metadata and screenshots (English and German).

### Fixed during QA
- "Delete all data" now also clears rides waiting for a lookup.
- Cloud backup is off, so queued rides (coordinates) are never copied off the phone.
- Ticket prices above 42 million EUR no longer wrap around to a small valid amount.
- The "already in your history" banner no longer sticks after picking another stop.
- A bug in one queued ride can no longer block the rides behind it.
- Home offers last month's Deutschlandticket claim while it is still payable.
- The boot receiver ignores other broadcasts; "1 cases" plural fixed in both languages.
- Tapping a notification while the app is open keeps your place (the trip opens on top of the current screen); the Deutschlandticket monthly cap now counts open, claimed and paid cases together; long titles and claim facts no longer clip at 200 % font scale; screenshots refreshed.

### Known limits
- Germany only; claims are prepared, not filed (DB's form needs a login).
- Battery drain is estimated from published figures, not measured (`docs/decisions/battery.md`).
- Ticket QR scan, other countries and per-trip "separate ticket" for connections are not built; see the roadmap in the README.
- The privacy policy still needs the owner's legal name and postal address before publishing (contact is hi@erdem6.com).

[Unreleased]: https://github.com/erdemd666/delayd/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/erdemd666/delayd/releases/tag/v1.0.0
