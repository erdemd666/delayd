# Delayd UI/UX plan (Workstream B)

Direction: a calm finance app for "money you get back". Quiet surfaces, one confident deep-teal accent, big tabular numbers, amber for "late", never red-on-everything. Not a transit app: no train photography, no route maps, no DB colours or logo. DB is only named factually ("claim with DB").

## 1. Information architecture and navigation

Single activity, one hand-rolled back stack of string routes (saved across rotation / process death). No navigation library: five destinations don't justify the dependency, and `BackHandler` gives correct back.

```
Onboarding (first run only, replaces itself with Home)
Home ── Settings
  │       └─ (dialogs: licenses, delete-all confirm)
  ├─ Trip detail ── Claim (single ticket)
  └─ Claim month (Deutschlandticket bundle)
```

- Start destination: `Onboarding` until `onboarded` pref is set, else `Home`.
- Notification deep link: `EXTRA_TRIP_ID` on the launch intent -> stack `[Home, Trip(id)]`. Unknown id -> Home. Back from the detail returns to Home.
- Claim "done" pops back to the trip detail (single) or Home (month).
- Home is the only root; Settings is reached from the top-bar action (labelled, 48 dp).

## 2. Screens and states

### Onboarding (4 pages, pager-less: step index in saved state, "Back" + "Next")
1. Value: "Get back what a late train owes you." One-line how much (25 % / 50 %, Deutschlandticket 1.50 € per case).
2. How it works + privacy promise: detect -> match the real train -> prepare claim. "Your location never leaves your phone", no account, no ads, no tracking.
3. Permissions with rationale: precise location (why: to see where a ride starts/ends), notifications (why: tell you about a delay). States per permission: not asked, granted (check), denied once (ask again), denied permanently (button becomes "Open app settings"), approximate-only location (explained; precise needed). "Continue" always available: the app works without, detection stays off and Home explains it.
4. Battery: why optimisation can stop detection, button opens the system battery-optimisation list, "Skip" allowed. Finish -> starts detection if location is granted.

### Home
- Top bar: wordmark, Settings action.
- Detection card: states `Off` (neutral, "Turn on" switch), `On` (primary container, "Watching for train rides"), `Riding now` (pulsing-free, tertiary accent "Ride in progress"), `Permission missing` (error container, action "Fix"). Whole row is one toggle target (48 dp+).
- Money summary: three figures, hero first: "Owed now" (large), "Claimed" and "Recovered" (secondary). Tabular numerals.
- Deutschlandticket progress (only if ticket type = Deutschlandticket): this month, cases, linear progress toward 4 €, text "€2.00 more until you can claim", when reached a primary "Claim this month" button.
- Trip list grouped by month (sticky-free headers), each row: route, train, date/time, delay badge (amber 60-119, deeper amber/red-brown 120+, neutral < 60), status chip (Claimed/Paid/Dismissed) and amount.
- Empty state: illustration-free, a ticket glyph, "No rides yet", explanation that depends on whether detection is on.
- Loading: the store is synchronous; there is no spinner state. Lists render the last known state immediately.
- Error: none surfaced (store is local); lookup errors are core's concern.
- Attribution footer: "Train data: Transitous - (c) OpenStreetMap contributors" link.
- Debug-only: overflow slot "Simulate ride" shown only if `FLAG_DEBUGGABLE`.

### Trip detail
- Header: delay badge + train, date.
- Route timeline: vertical line, two nodes: From (departure place) and To; at the destination shows `Scheduled 14:05` struck through (when late) and `Actual 15:20` bold with `+75 min`.
- Data source row: "Live data" or "Measured by your phone" with a one-line explanation.
- Compensation card: rule explained in a sentence ("75 min late = 25 % of the fare"), amount, or for the Deutschlandticket "counts €1.50 toward <month>". Below 60 min: "No compensation below 60 minutes". Below 4 €: explained.
- Status: chip + actions: `Claim` (primary, only if late and OPEN), `Mark as paid` (CLAIMED), `Not worth it / Dismiss` (OPEN), `Reopen` (DISMISSED/CLAIMED), `Delete trip` (confirm dialog).
- Not found state (trip deleted while open): empty message + back.

### Claim (single ticket)
Three stacked cards on one scrolling page (a stepper would add chrome for nothing):
1. Check the facts (train, date, route, scheduled vs actual arrival).
2. Ticket price field (decimal, prefilled from `fareCt`), amount owed live. Below minimum -> explained, claim still allowed to copy but "Mark as claimed" is disabled until payable.
3. "This is exactly what you will send": German claim text in a monospace-free surface; buttons `Copy text`, `Open DB claim page` (bahn.de), `I submitted it` (marks CLAIMED, stores `fareCt`).
Info note: DB needs a login; paper form alternative; extraordinary circumstances may be excluded.

### Claim month (Deutschlandticket)
Same, step 1 lists the month's open delayed trips (60+ min), step 2 shows total, cap (25 % of price), minimum. `I submitted it` marks all those trips CLAIMED. Disabled until payable.

### Settings
Ticket type (segmented: Single ticket / Deutschlandticket), Detection toggle, Health check (location precise, notifications, battery optimisation: each row with state and action), Data (export JSON via share sheet, delete all data with confirm), About (privacy policy, open-source licenses dialog, data attribution, version). Debug-only section at the bottom.

## 3. Design tokens

Dynamic colour is off. Contrast ratios are WCAG 2.x, computed for the exact hexes (script in review notes), all text pairs >= 4.5:1, UI boundaries >= 3:1.

| Role | Light | Dark |
|---|---|---|
| background | #F6F8F7 | #0D1311 |
| surface | #FFFFFF | #131B19 |
| surfaceVariant | #E3EAE7 | #2A3532 |
| onSurface | #17211E (16.5 on surface) | #E0E6E3 (13.8) |
| onSurfaceVariant | #4A5853 (7.0 on bg) | #B2BEB9 (9.8 on bg) |
| outline | #6F7B76 (4.1) | #85918C (5.7) |
| primary | #0B6B5D (6.4 on white) | #6FD3BE (9.8 on surface) |
| onPrimary | #FFFFFF (6.4) | #00382F (7.3) |
| primaryContainer / on | #CBEBE2 / #00201B (13.5) | #00513F / #BDEFE2 (7.4) |
| secondaryContainer / on | #D6E4F2 / #17293A (11.5) | #2F4254 / #D6E4F2 (8.0) |
| tertiary (late/amber) | #7A4E00 (7.2) | #F1BE6A (10.3) |
| tertiaryContainer / on | #FBE6BE / #3D2600 (11.6) | #5C3B00 / #FBE6BE (8.2) |
| error / container | #B3261E (6.5) / #F9DEDC, on #410E0B (12.8) | #F2B8B5 (10.3) / #8C1D18, on #F9DEDC (7.2) |
| success (paid) / container | #1B6B2F (6.6) / #CFEFD3, on #0A3A17 (10.4) | #8FD99B (10.5) / #1F4D2B, on #CFEFD3 (7.9) |

Success has no M3 slot, so it lives in a small `Extra` colour set provided via CompositionLocal.

Type scale (default font family, tightened weights): displaySmall 36/44 SemiBold for the hero amount; headlineSmall 24/32; titleLarge 22/28; titleMedium 16/24 Medium; bodyLarge 16/24; bodyMedium 14/20; labelLarge 14/20 Medium; labelMedium 12/16. Money uses tabular numerals (`tnum`). All text in `sp`, no fixed heights on text containers.

Spacing: 4, 8, 12, 16, 24, 32 (`Space.xs..xxl`). Screen gutters 16 dp (24 dp >= 600 dp width). Content max width 640 dp, centred.
Shapes: small 8, medium 14 (cards, buttons use full pill), large 20, extraLarge 28. Cards are tonal fills (no shadows), 1 dp outline only where a card sits on a same-colour surface.
Touch targets: >= 48 dp for every clickable (rows use `minimumInteractiveComponentSize`/`heightIn(min=48.dp)`).

## 4. Copy tone

Plain, specific, a little warm; no exclamation marks, no legal jargon, no "Oops". We say what happened and what the user can do. Numbers before adjectives. Never imply we are DB or that payout is guaranteed ("you may be owed", "DB decides").

- EN: "Get back what a late train owes you." / "You're owed €4.50" / "Nothing is paid below €4."
- DE: informal-neutral "du"? Finance feel calls for the polite "Sie" less than app norms; German consumer apps (N26, Trade Republic) use "du". We use **du**, short sentences: "Hol dir zurück, was dir ein verspäteter Zug schuldet." / "Dir stehen 4,50 € zu" / "Unter 4 € zahlt die Bahn nichts aus."
- Claim text stays German in both languages (it goes to DB) and uses formal "Sie"/neutral facts.
- Money: EN `€4.50`, DE `4,50 €` via `NumberFormat.getCurrencyInstance(locale)` with EUR. Dates: localized medium date, 24 h time, Europe/Berlin.

## 5. Accessibility checklist

- [ ] Every icon-only control has a `contentDescription`; decorative icons are `null`.
- [ ] Every clickable region >= 48 dp, with a merged semantic (one TalkBack stop per row/card: `semantics(mergeDescendants = true)`).
- [ ] Rows with toggles use `toggleable` + `Role.Switch` so the label is read with the state.
- [ ] Delay badges are not colour-only: always show text "+75 min".
- [ ] Status chips carry text, not just colour.
- [ ] Text contrast >= 4.5:1, boundaries >= 3:1 (table above), light and dark.
- [ ] 200 % font scale: no fixed heights, rows wrap, buttons stack in a `FlowRow`; checked on the emulator at font_scale 2.0.
- [ ] Edge-to-edge: Scaffold insets, lists pad bottom with navigation bar inset, landscape horizontal cutout safe.
- [ ] Layout 360 dp phone to tablet: content max width 640 dp centred.
- [ ] Headings marked (`heading()`) for month headers and section titles.
- [ ] Progress indicators expose a text equivalent ("2 of 4 euros").
- [ ] No time-limited UI, no animation required to understand state.
- [ ] Permission denied / permanently denied handled with an explicit next action, never a dead end.
- [ ] Back works everywhere (stack + `BackHandler`), dialogs dismissible.

## 6. Requests to other workstreams (also in the final report)

- A: put `EXTRA_TRIP_ID` into the delay notification intent; use `R.drawable.ic_stat_delayd` as small icon (`ic_train` stays valid and is now white-on-transparent).
- A: `Compensation.format` is not locale-aware; UI formats money itself and does not call it.
- Maintainer: wire the debug simulator at `DebugSlot` in `ui/DebugSlot.kt`.
