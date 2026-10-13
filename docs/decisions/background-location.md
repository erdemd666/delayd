# Restarting location detection after reboot or update

## Decision
`BootReceiver` (BOOT_COMPLETED, MY_PACKAGE_REPLACED) does **not** start `TrackingService`. If the user had detection on, it posts a "Detection paused, tap to resume" notification (channel `resume`). The notification's `PendingIntent.getForegroundService` starts the service; the tap is the user interaction Android requires. `ACCESS_BACKGROUND_LOCATION` is not requested.

## Why (developer.android.com, checked Oct 2026)
- Background FGS start restrictions (`/develop/background-work/services/fgs/restrictions-bg-start`): BOOT_COMPLETED / MY_PACKAGE_REPLACED receivers are exempt from the general "no foreground service from background" rule.
- Same page, while-in-use permissions: for apps targeting Android 14+, starting a foreground service of type `location` (also camera, microphone) from the background throws `SecurityException` unless the app has a visible activity or the start falls into a listed exemption. Listed: system component, interaction with an app widget **or a notification**, PendingIntent from another visible app, device-owner DPC, voice-interaction app, privileged `START_ACTIVITIES_FROM_BACKGROUND`. A boot receiver is not on that list.
- `ACCESS_BACKGROUND_LOCATION` would allow location access from such a service, but only in the same exempt situations; it would add a Play review burden and a scarier permission prompt for nothing the notification doesn't already solve.
- Android 15 (`/about/versions/15/behavior-changes-15`) blocks BOOT_COMPLETED receivers from launching dataSync, camera, mediaPlayback, phoneCall, mediaProjection and microphone services. `location` is not listed, but the Android 14 while-in-use rule above still applies, so the result is the same.

## Related behaviour
- Process killed by the system (not reboot): `START_STICKY` restarts the service. If Android refuses the foreground start (`startForeground` throws), `TrackingService.onCreate` posts the same resume notification instead of crashing.
- The "detection wanted" flag (`SharedPreferences "core"`, key `detectionOn`) is set when the service reaches the foreground and cleared only in `onDestroy`, which a kill, reboot or update never calls. A deliberate `stopService` clears it.
- Pending rides are persisted (`pending.json`) so nothing is lost across these restarts.
- The notification needs `POST_NOTIFICATIONS` (Android 13+). If the user denied it, resume happens when they open the app.

## Not verified on a device
Reboot/update flows were reasoned from the docs, not run (no emulator in this phase). Test: enable detection, `adb reboot`, expect the notification; tap, expect the foreground notification and `running == true`.
