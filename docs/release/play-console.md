# Google Play Console answers

Copy-paste material for the Play Console forms. Facts below are taken from the code (`Transitous.kt`, `TrackingService.kt`, manifest). If A changes what is sent over the network or adds permissions, update this file first.

Sources:
- Data safety definitions: <https://support.google.com/googleplay/android-developer/answer/10787469>
- Foreground service declaration: <https://support.google.com/googleplay/android-developer/answer/13392821>

## 1. Data safety form

### Does "processed on device" count as "collected"?

Google defines it exactly: *"Collect" means transmitting data from your app off a user's device.* Data that is only used on the device is not collected, and the form says it only needs to cover data collected or shared. So:

| Data | In the app | Collected per Google's definition? |
|---|---|---|
| Location fixes used by the ride detector | Processed in memory/on the device, never sent | **No** |
| Trips (train, stations, times, status, fare) | Local JSON file | **No** |
| Ride-start coordinate + time in the Transitous request | Sent to `api.transitous.org` over HTTPS | **Yes** (it leaves the device) |
| Manual add: station search text, picked station ID, chosen date/time | Sent to `api.transitous.org` over HTTPS, only when the user taps "Add a ride" | **Yes** |

The Transitous request carries the GPS coordinate where the ride started (radius 500 m query), which is **precise location** (Google: precise = an area smaller than 3 km²). So the honest answer is: **precise location is collected**, for the single purpose of the timetable lookup. Do not answer "no data collected"; that would be wrong and a policy risk. The manual-add search text is declared as in-app search history.

Even if the coordinate were rounded before sending, a station-level query (500 m radius) is far finer than Google's 3 km² threshold, so declare precise.

### Answers

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | Yes |
| Is all of the user data collected by your app encrypted in transit? | Yes (HTTPS only) |
| Do you provide a way for users to request that their data be deleted? | See note below |
| Data type: Location > **Precise location** | Collected: **Yes**. Shared: **No** |
| Data type: Location > Approximate location | No (the app only handles the precise fix; do not tick both) |
| Data type: App activity > **In-app search history** (the station name typed in "Add a ride") | Collected: **Yes** (transmitted to Transitous, not stored by the developer). Shared: **No**. Required: No (optional feature). Purpose: App functionality |
| Is this data collection required or can users turn it off? | **Required** for the feature (without it there is no lookup); the user opts in by granting location and turning detection on |
| Purposes | **App functionality** only |
| Ephemeral processing | Does not apply (the data is transmitted, not only processed in memory) |
| All other data types (personal info, financial info, health, messages, photos, contacts, app activity, web browsing, app info and performance, device IDs) | Not collected |

Notes for the reviewer-facing reasoning:

- **Shared = No.** Google excludes "transferring data to a service provider that processes it on behalf of the developer" from sharing. Transitous answers the lookup the app asks for and the developer does not receive anything back beyond the timetable. This is the position of the developer; if you prefer the most conservative reading, tick Shared: Yes with purpose App functionality. Both are defensible; pick one deliberately and keep the privacy policy consistent with it.
- **Deletion.** The developer holds no user data (no backend). The in-app "delete all data" removes the local trips, and uninstalling removes everything. Choose the option that says users cannot request deletion because no data is stored by the developer, if the form offers it, and link the privacy policy. The Transitous server may keep ordinary request logs outside the developer's control (see privacy policy).
- IP address is visible to Transitous like for every request. It is not one of the form's data types.

### Privacy policy URL

`https://erdem6.com/delayd/privacy`: upload `docs/privacy-policy.html` there (it is the same text as `docs/privacy-policy.md`). The page must be public and not a PDF. Developer contact for the store listing: hi@erdem6.com, website https://erdem6.com.

## 2. Foreground service: type `location`

Play Console: *Policy and programs > App content > Foreground service permissions*. Required for apps targeting Android 14+, one declaration per type. Google asks for: a description of the feature, the user impact if the system delays or interrupts it, a video link, and one of the preset use cases.

**Manifest:** `<service android:name=".TrackingService" android:foregroundServiceType="location"/>` with `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_LOCATION`. Started by the user from the visible app (never from the background), with a permanent notification.

### Preset use case

The presets are: user-initiated location sharing, navigation, geofencing. Pick the closest one, **Navigation** (it explicitly includes tracking a ride). If the form offers "Other", use that and paste the text below.

### Declaration text (paste)

> Delayd lets the user switch on train-ride detection. While it is on, a foreground service with the type "location" reads the device location to recognise that the user is on a train: it samples network location every 5 minutes while idle (every 60 seconds on phones without a motion sensor), every 15 seconds for a few minutes after the motion sensor fires, and GPS every 10 seconds only while the device moves at train speed. When a ride ends, the app identifies the train through a public timetable service and tells the user if the delay entitles them to compensation. The service is started only by an explicit user action in the visible app and shows a persistent notification. Location data is processed on the device and is not stored as history or sent to any server; only the ride-start coordinate is used for the timetable query (plus, when the user adds a ride by hand, the station text they type).

### User impact if the system delays or interrupts it (paste)

> If the service is delayed, the start or end of a ride can be detected late or missed, so the arrival time and therefore the delay and compensation may be wrong or the ride may be missed entirely. Interruptions never harm the user; they only mean the user has to claim manually or the app resumes detection on the next start.

### Demo video script (about 60 seconds, upload to YouTube as unlisted, paste the link)

Use a debug build with the ride simulator, or a real ride, on Android 14+ with the Play build installed.

1. (0:00) Fresh install. Open the app and show the onboarding explaining why location is needed.
2. (0:10) Tap the permission button, show the system location prompt, grant "While using the app" with precise location.
3. (0:20) Turn on ride detection on the home screen. Show the persistent notification "Watching for train rides" with the location icon in the status bar.
4. (0:30) Pull down the notification shade to show the notification, then return to the app and show the "Riding now" state (simulator or real ride).
5. (0:40) End the ride. Show the delay notification and open the trip detail with scheduled versus actual arrival.
6. (0:50) Open Settings, turn detection off, show that the notification disappears.

Keep the video on-device with no cuts between steps 3 and 4. Say in the narration (or add captions) that the service starts only after the user taps the toggle.

## 3. Permissions justification

Android permissions in the manifest and why each is needed. Paste into the Play "Permissions" questions or into review replies.

| Permission | Justification |
|---|---|
| `ACCESS_FINE_LOCATION` | Detect that the user is on a train and where the ride starts and ends (stations are only a few hundred metres apart; coarse location cannot tell a stop from a transfer, and the arrival time at the destination is what decides the compensation). Requested at runtime after an in-app explanation; only while the app is in use. |
| `ACCESS_COARSE_LOCATION` | Required by Android alongside fine location so the user can choose approximate location in the system prompt. The app then falls back to the network provider. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` | Detection must keep running while the screen is off during a ride; see the declaration above. |
| `POST_NOTIFICATIONS` | Show the required foreground-service notification and the notification that a delay may qualify for compensation. Requested at runtime; the app works without it. |
| `INTERNET` | One HTTPS request per ride, plus the requests of the optional manual add (station search, departures), all to `api.transitous.org`. |
| `RECEIVE_BOOT_COMPLETED` | After a reboot or app update, if the user had detection on, show a "tap to resume" notification. Starts no location service in the background; see `docs/decisions/background-location.md`. Verified on an API 36 emulator. |
| `ACCESS_BACKGROUND_LOCATION` (**not requested**) | Delayd does not use it: the service is started by the user in the foreground. If A concludes a boot restart needs it, expect a much stricter review (core-functionality justification plus video). Prefer the "tap to resume" notification instead. |

## 4. Content rating and audience

- **Category:** Utilities or Travel and Local. No user-generated content, no social features.
- **IARC questionnaire:** violence none, sexuality none, language none, controlled substances none, gambling none, user interaction none, shares location with other users **no** (location is not shared with users), digital purchases none, ads none. Expected result: **Everyone / PEGI 3 / USK 0**.
- **Target audience:** **18 and over** (a consumer-rights and ticket-fare tool, not meant for children). This avoids the Families policy requirements.
- **Ads:** No. **App access:** all functionality available without login; no test account needed. **Government app:** No. **Financial features:** none (the app computes compensation estimates and does not process money; select "My app doesn't provide any financial features").
- **News app / COVID / Health:** No.
- Store listing must not imply an affiliation with Deutsche Bahn (Play impersonation policy); the texts in `fastlane/metadata` follow that.
