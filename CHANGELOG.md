# Changelog

All notable changes to SithTermFX are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-03-09

### Added

- GitHub Actions release workflow (`.github/workflows/release-build.yml`) to build release artifacts from version tags or manual dispatch, upload JARs and app tarball, and optionally publish a GitHub release.

### Changed

- Maven project version set to `1.1.0` across all modules (root, sithtermfx-core, sithtermfx-ui, sithtermfx-app).
- Integration guide and release workflow examples updated to reference version `1.1.0` and tag format `v1.1.0`.

### Fixed

- Split terminal pane drag-and-drop now only handles internal terminal-pane reordering payloads; external drag events (e.g. files from the desktop) are no longer consumed, so they can be handled by other targets.

---

## [1.0.0.0] - 2026-03-08

### Added

- 21 terminal emulation types: XTerm, DEC VT100–VT520, IBM TN3270/TN5250, Wyse 50/60/160, TeleVideo 910/920/925, HP 2392/700-92, SCO ANSI, Sun CDE, DEC CTERM, Commodore PETSCII.
- Full TN3270E (RFC 2355) and TN5250E (RFC 4777) protocol support with EBCDIC, block-mode fields, and AID keys.
- `EmulationType` enum, `EmulatorFactory`, and `TerminalKeyEncoderFactory` for selecting and wiring emulations.
- Mermaid-generated documentation diagrams and `docs/generate-diagrams.sh` for regenerating PNGs.
- Expanded README, integration guide, and public API documentation with terminal emulation and integration examples.

### Changed

- `SithTermFxWidget` and `TerminalStarter` support explicit emulation type selection via `setEmulationType()`.
- Documentation uses English-only; legacy i18n message bundles removed.

### Fixed

- Various emulator and key-encoder fixes for the multi-emulation architecture.

[1.1.0]: https://github.com/chardonnay/SithTermFX/compare/v1.0.0...v1.1.0
[1.0.0.0]: https://github.com/chardonnay/SithTermFX/releases/tag/v1.0.0
