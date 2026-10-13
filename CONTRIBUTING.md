# Contributing

Thanks for helping. Delayd stays small on purpose: the smallest code that fully works.

## Ground rules

- **No proprietary SDKs, no Google Play Services, no analytics or trackers.** This keeps F-Droid possible and the privacy promise true.
- Every new dependency must justify itself in one line in the commit or PR.
- User-facing text goes into `values/strings.xml` and `values-de/strings.xml`. Never mention or imitate Deutsche Bahn branding.
- Location data never leaves the device. The only network call is the Transitous lookup.

## Setup

JDK 17 and the Android SDK. Create `local.properties` with `sdk.dir=...` (it is gitignored).

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug   # must pass before you open a PR
```

## Pull requests

- Branch from `main`, keep PRs focused, add a JVM unit test for new logic (fixtures from real API responses where possible).
- Match the existing compact Kotlin style; comment only the *why*.
- Update `CHANGELOG.md` under `[Unreleased]`.
- Never commit keystores or `keystore.properties`.

Bugs and ideas: open an issue. Security or privacy problems: email the address in the [privacy policy](docs/privacy-policy.md) instead of opening a public issue.
