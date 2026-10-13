# Core engine plan (Workstream A)

Goal: smallest code that makes detection survive offline, reboots and odd data. No new runtime dependencies.

## Steps

1. **Model + parsing** (`Transitous.kt`, `TripStore.kt`)
   - `Trip` gets `replaced: String? = null` (cancelled train we substituted for) and `legs: Int = 1`; derived `measured = !realtime`. Stored with `optString/optInt`, old files still load.
   - `Departure.cancelled` kept (no longer dropped) so a replacement ride can use the cancelled train's planned arrival.
   - Query window widened by 30 min back (one request, still light). Errors: `LookupException(retryAfterSec)` for 429/5xx; parsing skips bad entries.
   - `TripStore`: synchronized `init`, tolerant load (skip bad rows, quarantine unreadable file), `add` returns Boolean.
2. **Pending rides + backoff** (`RideQueue.kt`): `pending.json`, per-item attempts and `notBefore`; exponential backoff 1 min..1 h honouring Retry-After; give up after 10 attempts or 48 h. Hand-rolled instead of WorkManager (no dependency; service is the only consumer).
3. **Journeys** (`Journeys.kt`): rides within 30 min and 1 km of each other form a chain. Try the whole chain as one ride first (signal stop that split a ride), else match every leg and join into one trip (delay at final destination). The queue holds a ride while the next connecting ride is still in progress.
4. **Notifications** (`Notifier.kt`, `res/values*/core_strings.xml`): four channels (tracking, delays, claims, resume). Delay notification carries `EXTRA_TRIP_ID`, unique request code. Monthly Deutschlandticket claim (previous month, open trips, >= 4 EUR), once per month.
5. **Reboot** (`BootReceiver.kt`, manifest): see `background-location.md`; resume notification, no background location permission.
6. **Battery** (`DutyCycle.kt`, `TrackingService.kt`): see `battery.md`; significant-motion wakes a short watch window, long safety poll otherwise, provider fallback.
7. **Service wiring** (`TrackingService.kt`): worker executor, network callback, persisted "detection on" flag, startForeground failure falls back to the resume notification.
8. **Debug simulator** (`src/debug`): `DebugTools.simulateRide` drives synthetic fixes through the real `TripDetector` -> `RideQueue` -> `TripStore` -> `Notifier`, with a canned departure instead of the network. Reached from main via `DebugHooks` (broadcast, only when the app is debuggable).

## Tests (JVM)
`TransitousTest` (cancelled/replacement, parse tolerance), `TripStoreTest` (round trip, old/corrupt files), `RideQueueTest` (backoff, retry-after, give up, hold, persistence), `JourneysTest`, `DutyCycleTest`, `CompensationTest` (monthly due), `TripDetectorTest` (stale gap reset).

## Risks
- Significant motion may not fire for a smooth train start: mitigated by the 5-minute safety poll.
- Joining legs assumes one ticket for connections under 30 min at the same place.
- Strings for notifications live in new `core_strings.xml` files (B owns `res/`, files are separate to avoid conflicts).
