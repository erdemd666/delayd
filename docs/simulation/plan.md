# Workstream G: real-world simulation plan

Goal: prove detection and matching hold up under phone-like input, fix what breaks.

## Approach
1. **Generator** (`src/test/.../sim/Sim.kt`): a ground-truth path built from a timetable (smooth accel/decel, dwells, signal holds,
   walks), plus a `PhoneSim` that mimics `TrackingService` + `DutyCycle` (idle poll, motion-triggered watch, GPS while riding),
   GPS/network noise, network jumps, tunnel gaps. Fixes go through the real `TripDetector`, a real `RideQueue`
   (so `Journeys` chain/resolve run), and `Transitous.match` on boards built from real fixtures or timetable-derived data.
2. **Scenarios** (`ScenarioTest.kt`): the 13 cases of the brief. Each reports start/end error, matched train, delay, GPS minutes
   to `app/build/sim/results.md` (copied to `docs/simulation/results.md`).
3. **Tune**: measure first (before column), fix root causes in detector/journeys/matching, re-run (after column).
4. **Live replay**: debug-only `REPLAY_REAL` broadcast in `DebugTools` + a JVM task `replayLive` (excluded from `test`).
   A handful of Transitous requests, User-Agent `Delayd/1.0.0 (github.com/erdem61/delayd)`, no loops.
5. **GPX**: 2-3 tracks exported from the generator into `docs/simulation/gpx/`, README on how to replay them.

## Boards
- S-Bahn hop and S7 to Potsdam: the real fixture `departures-berlin-hbf.json` (stop coordinates and live times).
- RE1, ICE, transfer, cancel: timetables built from real station coordinates (remembered to ~100 m) with plausible times,
  plus decoy trains (next train, other line). Marked "synthetic board" in the results.

## Out of scope
UI (owned by Workstream F), network in the default test run.
