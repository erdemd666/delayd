# Delayd

**Your train was late. Your money comes back without you doing anything.**

In Germany, a train arriving 60+ minutes late gets you 25 % of the fare back, and 120+ minutes gets you 50 %. With a Deutschlandticket you get 1.50 € per case. Most people never claim. Delayd notices your train rides by itself, checks them against live data and tells you when you're owed money.

## How it works

```
GPS / network fixes ──► TripDetector ──► Ride(start, end) ──► Transitous ──► Trip(train, delay) ──► Compensation
 (duty-cycled)          speed state       station to           departures at start,    stored on device,     DB rules,
                        machine           station              matched by arrival      notification          claim text
```

| Mobile computing topic | Where |
|---|---|
| Sensing and context: is the phone riding a train? | [`TripDetector.kt`](app/src/main/java/app/delayd/TripDetector.kt) |
| Energy: network fixes every 60 s when idle, GPS every 10 s only while moving | [`TrackingService.kt`](app/src/main/java/app/delayd/TrackingService.kt) |
| Matching a ride to a real train with live delay data | [`Transitous.kt`](app/src/main/java/app/delayd/Transitous.kt) |
| Phone as witness: when there's no live data, the measured arrival is used | `Transitous.match` |
| Privacy: location history stays on the device, the API only sees one station query | whole app |
| Domain rules: thresholds, 4 € minimum, Deutschlandticket cap | [`Compensation.kt`](app/src/main/java/app/delayd/Compensation.kt) |

No accounts, no backend, no Google Play Services. It uses Kotlin, Jetpack Compose and the platform `LocationManager`.

## Build & test

Requires Android Studio (or JDK 17+ and the Android SDK).

```bash
./gradlew testDebugUnitTest   # detector, matching on a real API response, compensation rules
./gradlew installDebug
```

**Demo without riding a train:** the Android emulator can play back a GPX route. Go to *Extended controls → Location → Routes*. The unit tests use a real Berlin Hbf departure board ([fixture](app/src/test/resources/departures-berlin-hbf.json)).

## Data

Train data comes from [Transitous](https://transitous.org) (MOTIS, open European transit data incl. DB realtime), [sources](https://transitous.org/sources/), © OpenStreetMap contributors. The Transitous API is for open-source, non-commercial use only. A commercial version would self-host MOTIS or use the DB API Marketplace, and only `Transitous.kt` changes.

## Known limits

- A signal stop longer than 10 min splits a ride in two.
- Underground stations without GPS can blur the arrival time.
- DB's claim form needs a login, so Delayd prepares the facts for you to paste instead of filing the claim.
- Rides found while offline are retried for 3 minutes and then dropped.
- Detection stops after a reboot until you open the app again.
- Since June 2023, delays caused by extraordinary circumstances (storms, strikes by third parties) may not be compensated.

## Roadmap

- [ ] Persist pending rides, restart detection after reboot
- [ ] Merge rides split by long signal stops, transfers on one ticket
- [ ] Ticket QR scan for the fare
- [ ] Other countries: AT (ÖBB), CH, NL, IT
