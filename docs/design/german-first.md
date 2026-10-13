# German-first, daily-use shortcuts, branding (Workstream F)

## 1. Languages
- `values/` is German (default), `values-en/` is English. `core_strings.xml` is merged into `strings.xml` in each, so one file per language and a diff shows gaps. `app/resources.properties` sets `unqualifiedResLocale=de-DE` so AGP and Play treat the default as German. A device in any other language gets German, which is right for a Germany-only product.
- Hardcoded UI text: only `DebugSlot.kt` had some (resourced). Claim text stays German in code (`Claim.kt`) and never goes through resources.
- Formatting follows one `appLocale()` (device/app locale if de or en, else de-DE), so a French phone sees German text and German number formats, not a mix. Money `4,50 €` / `€4.50`, dates `13.10.2026` with a weekday (`Di., 13.10.2026`) in lists and details, 24 h times, Europe/Berlin everywhere.
- German copy pass: du-form, official terms (Fahrgastrechte, Entschädigung, Fahrgastrechte-Formular, Deutschlandticket, Verspätung am Zielbahnhof), fewer anglicisms (Live-Daten -> Echtzeitdaten, Funktionscheck -> Funktionsprüfung).
- Language choice (Settings): System / Deutsch / English.
  - API 33+: platform `LocaleManager.applicationLocales`; the system-settings per-app language works too through `res/xml/locales_config.xml` + `android:localeConfig`. "System" = empty locale list. No prefs: the platform stores it.
  - API 26-32: the row is hidden. A correct fallback needs a base-context wrapper for every context (activity, service, notifications, tile) plus a stored choice; that is more code than a Germany-first app needs, and the app already follows the phone's language (German or English). Documented in README.

## 2. Daily use (implemented)
| Win | Why | Notes |
|---|---|---|
| Quick Settings tile "Fahrterkennung" | Toggle detection without opening the app | Off = `stopService`. On: Android docs list no TileService exemption for starting a location FGS from the background (only visible activity, notification, widget...). So the tile opens `MainActivity` with `ACTION_START_DETECTION`; the activity, while visible, starts the service and finishes at once (brief flash, always allowed on 14+). Without location permission the app just stays open so the user can grant it. |
| "Kopieren & DB öffnen" | Copy and open in one tap, toast confirms | The copy-only button stays for DB Navigator / paper form. The separate "open" button is removed. |
| Fare memory per route | Same commute, same ticket price | One prefs string, newest first, max 20 routes (`FareMemory`, JVM-tested). Prefill only, with a hint line saying where the number came from; editable. Stored when a claim is marked sent. |
| Notification actions "Jetzt beantragen" / "Nicht relevant" | Skips open app -> find trip -> tap claim; dismisses noise | Claim = existing `EXTRA_TRIP_ID` plus `EXTRA_CLAIM`; stack becomes Home > Trip > Claim (or month claim for Deutschlandticket). Dismiss = non-exported broadcast receiver, marks DISMISSED and cancels the notification. Tap behaviour unchanged. |

Evaluated, not done: asking the ticket type in onboarding (extra step, more copy; default stays Deutschlandticket); `SINGLE_TOP` for notification taps (changes navigation, known open item); auto "did you send it?" prompt after returning from DB (more state, little gain).

## 3. Branding
`brand_line` "Ein Produkt von erdem6.com" / "A product of erdem6.com": `labelMedium`, onSurfaceVariant, underlined, one 48 dp tap target to https://erdem6.com. Bottom of Settings and bottom of onboarding page 1. Also fastlane full descriptions and README footer.

## 4. Dependencies
None.

## 5. Verification (emulator API 36, debug build)
DE/EN/System switching (in-app radio and `cmd locale`), tile (add + click turns the location service on through the activity, second click stops it), delay notification actions ("Jetzt beantragen" opens Home > Trip > month claim and clears the notification; "Nicht relevant" sets "Verworfen"), branding line on onboarding page 1 and Settings, German dates with weekday and `1,50 €`. Fare memory and 200 % font scale on the new rows are covered by JVM tests only / not re-checked. Refreshed screenshots: home (de), claim month (de/en), settings (de/en), onboarding 1 (de/en); the others are unchanged in layout but may show older wording.
