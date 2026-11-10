---
title: Delayd Privacy Policy / Datenschutzerklärung
---

# Delayd Privacy Policy

*Deutsche Fassung weiter unten.* Last updated: 2026-10-13

Delayd is a free Android app by erdem6.com. It is built so that its developer cannot see your data: there is no account, no backend and no tracking.

## Controller

erdem6.com, <https://erdem6.com>
TODO: full legal name and postal address of the owner (a legal requirement in Germany).
Contact for privacy questions: **hi@erdem6.com**

## What the app does with your data

| Data | What happens | Leaves the device? |
|---|---|---|
| Location (precise, from GPS and network) | While detection is on, the app reads your location to notice when you are on a train. It is processed on the device and no location history is stored. The start and end point of a finished ride wait in a private on-device queue (at most 48 hours) until the timetable lookup succeeds. | **No** |
| Detected trips (train, stations, scheduled and actual arrival, claim status, a ticket fare if you enter one) | Stored in a file in the app's private storage so you can see and claim them. | **No** (unless you export them yourself) |
| Timetable lookup | When a ride ends, the app asks the public Transitous service which train you took. When you add a ride by hand, it also searches stations and departures there. | **Yes, see below** |

### The only network requests

The app talks only to `https://api.transitous.org` ([Transitous](https://transitous.org), an open-source transit data service run by volunteers), over HTTPS, in two situations.

**1. After a detected ride** one request containing:

- the coordinate where the ride **started** (usually a station) with a search radius of 500 m, and
- the time of the ride start, plus fixed filter parameters (train types).

**2. Only when you use "Add a ride"** (manual add):

- the **station name text you type** (a search request, sent after a short pause in typing, at least 2 letters),
- the **station ID** you pick and the **date and time** you choose, to list departures, plus the same fixed filter parameters.

The end station you pick, the arrival time you type and your ticket are kept on the device and not sent.

Your end point (for detected rides), your route, your name, your ticket and your location history are **not** sent. As with every internet request, Transitous receives your IP address and the app's user agent (`Delayd/<version> (+https://erdem6.com; hi@erdem6.com)`). Delayd has no control over how Transitous handles its server logs; see <https://transitous.org>. Delayd talks to no other server.

### What we do not do

No accounts, no analytics, no crash reporting, no advertising, no advertising ID, no Google Play Services, no third-party SDKs. We do not sell or share data. The app never uploads your trips anywhere.

## Permissions

- **Precise location** and **foreground service (location)**: to detect rides. Detection runs only if you turn it on and shows a permanent notification while active.
- **Notifications**: to tell you about delays and ready claims.
- **Internet**: for the Transitous requests above.

## Your control, your rights

- **Export and delete** your trips inside the app (Settings > Your data; delete also clears rides still waiting for a lookup). Uninstalling the app removes all local data. Cloud backup of the app's data is switched off, so your trips are not copied to Google Drive or a device transfer.
- You can stop detection at any time or revoke the location permission in system settings.
- The developer holds no personal data about you, so there is nothing to access or erase on our side. For data held by Transitous, contact its operators.
- Under the GDPR you have the rights of access, rectification, erasure, restriction, data portability and objection, and the right to lodge a complaint with a supervisory authority (in Germany, the authority of your federal state).
- Legal basis: your consent (Art. 6(1)(a) GDPR, the location permission) and performing the service you asked for (Art. 6(1)(b)).

## Children

Delayd is not directed at children under 13 (16 where national law requires).

## Changes

Changes will be published here and in the app's changelog.

## Disclaimer

Delayd is an independent project and is not affiliated with Deutsche Bahn AG. Compensation amounts are estimates; the railway company decides on every claim.

---

# Delayd Datenschutzerklärung

Stand: 13.10.2026

Delayd ist eine kostenlose Android-App von erdem6.com. Sie ist so gebaut, dass der Entwickler Ihre Daten gar nicht sehen kann: Es gibt kein Konto, kein Backend und kein Tracking.

## Verantwortlicher

erdem6.com, <https://erdem6.com>
TODO: vollständiger Name und Anschrift des Betreibers (Impressumspflicht).
Datenschutzanfragen: **hi@erdem6.com**

## Welche Daten die App verarbeitet

| Daten | Was passiert | Verlässt das Gerät? |
|---|---|---|
| Standort (genau, per GPS und Netzwerk) | Bei eingeschalteter Erkennung liest die App Ihren Standort, um zu erkennen, dass Sie im Zug sitzen. Er wird nur auf dem Gerät verarbeitet, ein Standortverlauf wird nicht gespeichert. Start- und Endpunkt einer beendeten Fahrt warten in einer privaten Warteschlange auf dem Gerät (höchstens 48 Stunden), bis die Fahrplanabfrage gelingt. | **Nein** |
| Erkannte Fahrten (Zug, Bahnhöfe, planmäßige und tatsächliche Ankunft, Antragsstatus, ggf. ein von Ihnen eingegebener Fahrpreis) | Liegen in einer Datei im privaten App-Speicher, damit Sie sie ansehen und geltend machen können. | **Nein** (außer Sie exportieren sie selbst) |
| Fahrplanabfrage | Nach einer Fahrt fragt die App den öffentlichen Dienst Transitous, welchen Zug Sie genommen haben. Beim manuellen Hinzufügen einer Fahrt sucht sie dort außerdem Bahnhöfe und Abfahrten. | **Ja, siehe unten** |

### Die einzigen Netzwerkanfragen

Die App spricht ausschließlich mit `https://api.transitous.org` ([Transitous](https://transitous.org), ein von Freiwilligen betriebener offener Fahrplandienst), per HTTPS, in zwei Fällen.

**1. Nach einer erkannten Fahrt** eine Anfrage mit:

- der Koordinate, an der die Fahrt **begann** (meist ein Bahnhof), mit einem Suchradius von 500 m, und
- der Startzeit der Fahrt sowie festen Filtern (Zugarten).

**2. Nur bei "Fahrt hinzufügen"** (manuell):

- der von Ihnen **eingetippte Bahnhofsname** (Suchanfrage nach einer kurzen Tipppause, mindestens 2 Buchstaben),
- der **Bahnhofskennung**, die Sie auswählen, sowie **Datum und Uhrzeit**, die Sie wählen, um Abfahrten aufzulisten, plus dieselben festen Filter.

Der gewählte Zielbahnhof, die eingegebene Ankunftszeit und Ihr Ticket bleiben auf dem Gerät und werden nicht gesendet.

Ihr Zielort (bei erkannten Fahrten), Ihre Route, Ihr Name, Ihr Ticket und Ihr Standortverlauf werden **nicht** übertragen. Wie bei jeder Internetanfrage erhält Transitous Ihre IP-Adresse und den User-Agent der App (`Delayd/<Version> (+https://erdem6.com; hi@erdem6.com)`). Auf die Server-Protokolle von Transitous hat Delayd keinen Einfluss, siehe <https://transitous.org>. Delayd nutzt keinen anderen Server.

### Was wir nicht tun

Keine Konten, keine Analyse, keine Absturzberichte, keine Werbung, keine Werbe-ID, keine Google Play Services, keine SDKs von Dritten. Wir verkaufen oder teilen keine Daten. Die App lädt Ihre Fahrten nirgendwohin hoch.

## Berechtigungen

- **Genauer Standort** und **Vordergrunddienst (Standort)**: zur Fahrterkennung. Sie läuft nur, wenn Sie sie einschalten, und zeigt dabei dauerhaft eine Benachrichtigung.
- **Benachrichtigungen**: für Hinweise auf Verspätungen und bereite Anträge.
- **Internet**: für die oben beschriebenen Transitous-Anfragen.

## Ihre Kontrolle, Ihre Rechte

- **Export und Löschen** Ihrer Fahrten in der App (Einstellungen > Deine Daten; das Löschen entfernt auch Fahrten, die noch auf ihre Abfrage warten). Beim Deinstallieren werden alle lokalen Daten gelöscht. Die Cloud-Sicherung der App-Daten ist abgeschaltet, Ihre Fahrten werden also nicht zu Google Drive oder bei einem Gerätewechsel kopiert.
- Sie können die Erkennung jederzeit beenden oder die Standortberechtigung in den Systemeinstellungen entziehen.
- Der Entwickler speichert keine personenbezogenen Daten von Ihnen, bei uns gibt es also nichts auszukunften oder zu löschen. Für Daten bei Transitous wenden Sie sich an dessen Betreiber.
- Nach der DSGVO haben Sie Rechte auf Auskunft, Berichtigung, Löschung, Einschränkung, Datenübertragbarkeit und Widerspruch sowie das Recht auf Beschwerde bei einer Aufsichtsbehörde (in Deutschland die Behörde Ihres Bundeslandes).
- Rechtsgrundlage: Ihre Einwilligung (Art. 6 Abs. 1 lit. a DSGVO, Standortberechtigung) und die Erbringung des von Ihnen gewünschten Dienstes (Art. 6 Abs. 1 lit. b).

## Kinder

Delayd richtet sich nicht an Kinder unter 13 Jahren (16, wo nationales Recht es verlangt).

## Änderungen

Änderungen werden hier und im Changelog der App veröffentlicht.

## Hinweis

Delayd ist ein unabhängiges Projekt und steht in keiner Verbindung zur Deutschen Bahn AG. Entschädigungsbeträge sind Schätzungen; über jeden Antrag entscheidet das Eisenbahnunternehmen.
