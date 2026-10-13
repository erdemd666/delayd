# Simulation results

Model: duty-cycled fixes (DutyCycle plan), GPS noise 5-30 m (x2 within 400 m of a station, independent per fix: pessimistic),
network noise 25-120 m with 3 % jumps of 0.3-1.5 km (half report a poor accuracy and are dropped like the service does),
tunnel gaps, line-speed physics with trapezoid acceleration. Real: the S7 board (fixture), station coordinates. Synthetic:
RE1, ICE, RE7 boards (real coordinates, physically consistent times). Real trains: see the live section.
Errors are detected minus true time. GPS min is the battery proxy (ideal = the ride itself).
"Before" = main at 49c2bc4 with the same scenarios.

| Scenario | Before | After | Start err min (b / a) | End err min (b / a) | Trips stored (before -> after) | GPS min, ideal in brackets (b / a) |
|---|---|---|---|---|---|---|
| 130 min delay, 70 min blockage in the open | FAIL | pass | +0.6 / +0.6 | -0.3 / -0.3 | none -> ICE 1 to Hamburg Hbf (130 min) | 152 (152) / 90 (152) |
| 75 min delay, waiting on the platform | pass | pass | +0.1 / +0.1 | -0.1 / -0.1 | RE1 to Berlin Hbf (75 min) -> RE1 to Berlin Hbf (75 min) | 47 (38) / 47 (38) |
| Brisk walk 7 km/h, 6 km | not run | pass | - / - | - / - | not run -> none | - / 9 (0) |
| Cancelled train + replacement 30 min later | FAIL | pass | +0.3 / +0.3 | +2.0 / +2.0 | RE1 to Berlin Hbf (0 min) -> RE1 to Berlin Hbf (30 min) | 107 (38) / 43 (38) |
| Cancelled train + replacement 60 min later | FAIL | pass | +0.1 / +0.1 | +0.7 / -0.1 | RE1 to Berlin Hbf (0 min) -> RE1 to Berlin Hbf (60 min) | 107 (38) / 41 (38) |
| Car, Hbf to Potsdam Hbf at 108 km/h | FAIL | pass | - / - | - / - | none -> none | 38 (0) / 22 (0) (2 of 16 start times matched a train) |
| Car, home to work along the rails | pass | pass | - / - | - / - | none -> none | 41 (0) / 15 (0) |
| City bus Hbf to Ostbahnhof, ~19 km/h | FAIL | pass | - / - | - / - | S7 to S Ostbahnhof (Berlin) (0 min) -> none | 54 (0) / 18 (0) (5 of 16 start times matched a train) |
| Cyclist 25 km/h, 10 km | FAIL | pass | - / - | - / - | none -> none | 20 (0) / 8 (0) |
| Deutschlandticket month: 40 rides, 3 over 60 min | pass | pass | - / - | - / - | 40 trips; 3 claimable -> 40 trips; 3 claimable | 1784 (0) / 596 (0) (monthly pool 450 ct) |
| Held 12 min, 1.5 km before the destination | FAIL | pass | +0.1 / +0.1 | -13.1 / -0.0 | none -> RE1 to Berlin Hbf (12 min) | 88 (50) / 50 (50) |
| ICE Hbf-Hamburg, tunnel gap at Hbf (synthetic board) | pass | pass | -5.7 / -5.7 | -0.3 / -0.3 | ICE 1 to Hamburg Hbf (0 min) -> ICE 1 to Hamburg Hbf (0 min) | 109 (82) / 83 (82) |
| Night at home, 8 h, motion sensor | pass | pass | - / - | - / - | none -> none | 0 (0) / 0 (0) |
| Night at home, 8 h, no motion sensor (60 s poll) | FAIL | pass | - / - | - / - | none -> none | 41 (0) / 1 (0) |
| RE1 commute, 4 stops (synthetic board) | pass | pass | +0.1 / +0.1 | -0.1 / -0.1 | RE1 to Berlin Hbf (0 min) -> RE1 to Berlin Hbf (0 min) | 88 (38) / 41 (38) |
| S-Bahn hop Hbf-Ostbahnhof (real S7) | FAIL | pass | +0.1 / +0.1 | +9.0 / +0.0 | S7 to S Ostbahnhof (Berlin) (0 min) -> S7 to S Ostbahnhof (Berlin) (0 min) | 49 (10) / 18 (10) |
| Signal stop 14 min mid-route | pass | pass | +0.1 / +0.1 | -0.2 / -0.1 | RE1 to Berlin Hbf (14 min) -> RE1 to Berlin Hbf (14 min) | 67 (52) / 53 (52) |
| Signal stop 6 min mid-route | pass | pass | +0.1 / +0.1 | -0.1 / -0.1 | RE1 to Berlin Hbf (6 min) -> RE1 to Berlin Hbf (6 min) | 63 (44) / 49 (44) |
| Transfer, 12 min change | pass | pass | +0.1 / +0.1 | -0.1 / -0.2 | RE1 + RE7 to Berlin Hbf (8 min) -> RE1 + RE7 to Berlin Hbf (8 min) | 101 (51) / 40 (51) |
| Transfer, 2 min change (second train held 1.5 min mid-route) | not run (one ride, no train, dropped) | pass | +0.1 / +0.1 | -0.1 / -0.1 | none -> RE1 + RE7 to Berlin Hbf (1 min) | - / 38 (34) |
| Transfer, 5 min change | pass | pass | +0.1 / +0.1 | -0.1 / +0.0 | RE1 + RE7 to Berlin Hbf (8 min) -> RE1 + RE7 to Berlin Hbf (8 min) | 53 (44) / 49 (44) |
| Transfer, 9 min change | pass | pass | +0.1 / +0.1 | +2.1 / -0.2 | RE1 + RE7 to Berlin Hbf (8 min) -> RE1 + RE7 to Berlin Hbf (8 min) | 80 (48) / 40 (48) |

Notes
- A 6-12 min change of trains passed before as well, because the standstill plus braking and dwell already exceeded 10 min; the 4 min split matters for changes under ~8 min.
- 130 min case: GPS is below "ideal" because the 70 min stop is correctly not GPS time.
- Car/bus look-alikes: matches that remain happen when the vehicle keeps the train's timeline within about 3 min over the whole route; they are a known limit.
- Sim start error of -5.7 min (ICE): last fix before the underground gap; the matcher tolerates 10 min.

## Production fixes
1. TripDetector: a fast-looking fix needs the next fix to agree (a network jump out and back is not a ride): night/cyclist GPS 41/20 min -> 1/8.
2. TripDetector: after a standstill, "fast" counts only 150 m away from it (GPS spikes at stations extended rides by up to 9 min).
3. TripDetector: over a data gap (>60 s) the speed bar is 8 m/s, not 12 (average includes standing; a tunnel start was lost).
4. TripDetector: END_AFTER 10 -> 4 min so short changes split into legs; pieces of a split ride count together for the 3 km minimum (a 1.5 km last piece was dropped, trip lost); pieces under 1 km are noise.
5. Journeys/RideQueue: pieces up to 3 h apart are tried as one ride, connections still need 30 min; unmatched rides wait 3 h as orphans (a 70 min blockage in the open lost the 130 min delay).
6. Transitous: cancelled-train lookback 30 -> 75 min (hourly replacement showed 0 min delay); response about 0.85 MB at Berlin Hbf instead of 0.45.
7. Ride.via (3 checkpoints) + Transitous.onTheTrain: a bus or car is placed against the train's timeline and rejected when 2 of 3 checkpoints are off (14 -> 5 of 16 bus false matches, 6 -> 2 car).

## Live replay (real API, JVM, 3 trains)
Trains that ran within the last 2 h, fixes synthesized from their real stop coordinates and live times, through detector, Journeys and the live lookup.

| Station | Train | Ride | Planned arrival | Replay end err | Result |
|---|---|---|---|---|---|
| Berlin Hbf | RE20 | 17:07Z to S Königs Wusterhausen 17:49Z | 17:42Z | -10 s | rides=1; RE20 to S Königs Wusterhausen Bhf, delay 7 min, realtime |
| Hamburg Hbf | ICE 617 | 16:38Z to Hamburg-Harburg 16:50Z | 15:55Z | -10 s | rides=1; ICE 617 to Hamburg-Harburg, delay 57 min, realtime |
| S Potsdam Hauptbahnhof | RB20 (18478) | 2026-10-13T16:47:00Z to S Hennigsdorf Bhf 2026-10-13T17:36:00Z | 2026-10-13T17:28:00Z | -10 s | rides=2 gaps=[597] s; RB20 (18478) to S Hennigsdorf Bhf, delay 8 min, realtime=true |

Emulator: see the section at the end (filled when the emulator is free).

## Known limits
- Fixes are simulated, never from a real phone; noise correlation, Doze and OEM battery killers are untested.
- Ride start may be the first station where the train reaches 43 km/h (short city legs); the destination, which decides compensation, is right.
- Vehicles that mirror a train's timeline (bus beside the S-Bahn) can still match; realtime data is required for the check.
- A change shorter than about 3 min is one ride; it matches no single train, so `Journeys` splits it at its longest standstill and matches two legs (Workstream H). The standstill must be longer than any other stop on the way (a signal stop longer than the change would be chosen instead).

## Emulator replay (2026-10-13)

`adb shell am broadcast -a app.delayd.debug.REPLAY_REAL -p com.erdem6.delayd --es station "Berlin Hbf"` on the merged main debug build. The debug receiver is now exported, but in debug builds only, so adb can reach it.

| Real train | Detector output | Result after the 30 min connection hold |
|---|---|---|
| RE20 Berlin Hbf 17:07Z → S Königs Wusterhausen, planned 17:42Z, actual 17:49Z | 3 pieces (Hbf→Südkreuz, →Blankenfelde area, →KW), queued with notBefore = end + 30 min | joined into one trip **RE20 S+U Berlin Hauptbahnhof → S Königs Wusterhausen Bhf, +7 min, realtime**, queue empty |

Note: pending rides only drained while detection ran; in this run, starting detection from the tile action triggered the lookup. Since Workstream H the app drains them on foreground and a JobScheduler job does when the network is up.
