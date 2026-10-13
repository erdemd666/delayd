# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.0] - TBD

First public release (work in progress on the `v1.0` plan, see `docs/PLAN.md`).

### Added
- Automatic train ride detection with a battery-aware duty cycle (network fixes idle, GPS while moving).
- Matching of rides to the real train via the Transitous API, with live delay data and a phone-measured fallback.
- DB passenger-rights compensation rules: single tickets (25 % / 50 %), Deutschlandticket (1.50 € per case, monthly cap), 4 € minimum.
- Release engineering: R8 minification, release signing from `keystore.properties` or environment variables, GitHub Actions CI and tag releases, F-Droid-friendly build settings.
- MIT license, bilingual privacy policy, Play Store and F-Droid metadata (English and German).

### Known limits
- Germany only; claims are prepared, not filed.
- Items still open from `docs/PLAN.md` are listed here before the tag is cut.

[Unreleased]: https://github.com/erdem61/delayd/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/erdem61/delayd/releases/tag/v1.0.0
