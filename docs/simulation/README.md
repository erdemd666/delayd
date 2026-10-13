# Simulation

Realistic rides without a train. Details and numbers: [results.md](results.md), plan: [plan.md](plan.md).

## JVM suite (offline, runs with every `testDebugUnitTest`)
`app/src/test/java/app/delayd/sim/`: `Sim.kt` (track generator and phone model), `Scenarios.kt` (stations, timetables, report),
`ScenarioTest.kt` (22 scenarios). Writes `app/build/sim/results.md`.

## Live replay against the real API (manual)
- JVM: `DELAYD_LIVE=1 DELAYD_STATIONS="Berlin Hbf;Hamburg Hbf" ./gradlew testDebugUnitTest --tests "*LiveReplayTest*"`
  (3 requests per station; skipped without `DELAYD_LIVE`). Output: `app/build/sim/live.md`.
- Emulator (debug build only):
  `adb shell am broadcast -a app.delayd.debug.REPLAY_REAL -p app.delayd --es station "Berlin Hbf"`, then `adb logcat -s DelaydReplay`.
  The trip appears in the app (and notifies from 60 min delay).

## GPX routes for the emulator
`gpx/`: `s-bahn-hbf-ostbahnhof.gpx` (10 min), `re1-brandenburg-berlin-signal-stop.gpx` (14 min stop in the open), `ice-berlin-hamburg.gpx`.
Emulator: Extended controls (...) > Location > Routes > import GPX > play (speed 1x, the detector reads the phone clock).
Detection (GPS mode, "riding", ride closing 4 min after the end) is visible. Matching needs a train that runs *now* on that
route, so for a stored trip use the live replay instead. Regenerate: `SIM_GPX_DIR=docs/simulation/gpx ./gradlew testDebugUnitTest --tests "*GpxExport*"`.
