# Battery: idle sampling

## Decision
Keep a slow safety poll and add `Sensor.TYPE_SIGNIFICANT_MOTION` as a wake-up. Logic lives in `DutyCycle` (tested) and `TrackingService`.

| State | Location | Interval |
|---|---|---|
| Idle, motion sensor present | network provider | 5 min |
| Idle, no motion sensor | network provider | 60 s (as before) |
| Watch (8 min after a motion trigger) | network provider | 15 s |
| Riding | GPS | 10 s |

- The trigger sensor is a one-shot, wake-up, hardware-offloaded sensor on most phones; re-armed after each trigger. Its cost is negligible next to location fixes.
- Why not "no polling when idle": the sensor is built for walking/driving and may not fire on a smooth train departure while the phone lies still. The 5-minute poll bounds that miss: a fast fix pair still starts a ride, and the matcher tolerates a start up to 10 minutes late. `TripDetector` ignores speed between fixes more than 10 minutes apart, so a long silence cannot invent a ride.
- If the GPS provider is off, the plan falls back to network (and vice versa). With everything off we register GPS, which costs nothing and reports `onProviderEnabled`.

## Expected drain (estimates, not measured)
Network fixes use the radio already awake for the device; Android coalesces them. Rough order of magnitude from published figures for passive network location: 60 s polling is roughly 1-2 % of battery per day, 5 min polling about a fifth of that. Each motion trigger adds a 8 min window at 15 s (about 32 fixes); a commuter triggers it a few dozen times a day, so the watch windows can cost as much as the old polling. Hence the window is short and only network is used. GPS runs only while riding. **Measure before release** with `adb shell dumpsys batterystats --reset`, a day of normal use, then `dumpsys batterystats` for `app.delayd` (location and sensor lines); tune `WATCH_MS` and the idle interval in `DutyCycle`.

## Rejected
- Activity Recognition API: Google Play Services, not allowed (F-Droid).
- Geofencing: also Play Services.
