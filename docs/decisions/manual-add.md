# Manual add (Workstream D)

Goal: add a ride detection missed, by hand, producing the same `Trip` as an auto-detected one.

## Flow (one screen, three steps, route `add` on the existing string back stack)
1. **Station + when:** name search (`/api/v1/geocode?text=&type=STOP&mode=<rail>`, debounced 300 ms, min 2 chars), date picker (no future days), approximate time (no future instants).
2. **Train:** `/api/v6/stoptimes?stopId=&time=&n=20&fetchStops=true&mode=<rail>`, starting 10 min before the chosen time. Cancelled trains are hidden.
3. **Destination:** the picked train's `nextStops` that were already reached (scheduled arrival in the past). Then the actual arrival (see below) and "Add ride".

## Decisions
- **Code:** `Transitous.kt` gets `searchStations`, `departuresAt`, `parsePlaces` and a shared `get()` (also used by `findTrip`). `ManualAdd.kt` holds the pure `manualTrip(...)`; `ui/AddRide.kt` holds a `ViewModel` (survives rotation, owns coroutines on `Dispatchers.IO`) and the screens. No new dependency.
- **Trip model:** `Trip.userReported: Boolean = false` (stored with `optBoolean`, old files load). `measured = !realtime && !userReported`, so "measured by your phone" is never claimed for a hand-added trip.
- **Arrival:** live data exists -> use it, `realtime = true`. Otherwise (history gone, S-Bahn without realtime) the field is prefilled with the scheduled time and the user types the real one: `realtime = false, userReported = true`. If the user edits a live time it also becomes user-reported. The typed time-of-day is placed on the day nearest the scheduled arrival (handles midnight).
- **Same id as auto-detected:** `id = train@scheduledArrival`, so a ride that detection also found is a duplicate; `TripStore.add` returns false and we say so and offer to open the existing trip.
- **Future:** date picker blocks days after today, a time later than now is rejected, and only stops already reached are offered as destination.
- **States:** loading, no results, network error with retry, offline (UnknownHost/Connect) with retry. Selections survive rotation in the ViewModel.
- **Transitous policy:** one request per debounced search, one per "show trains", no polling or loops; same User-Agent.
- **Entry point:** extended FAB "Add a ride" on Home (`Screen` got an optional `fab` slot).

## Deferred
- Paging to later/earlier departures; a recent-stations list; searching by train number.
