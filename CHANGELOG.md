# Changelog

All notable changes to SithTermFX are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-06-22

### Added

- OSC 8 explicit hyperlink support: programs can mark arbitrary text as a clickable link via the `OSC 8` escape sequence (e.g. `ls --hyperlink`, `eza`, compiler diagnostics). Enabled by default on every `SithTermFxWidget` — no registration required.
- Local `file://` hyperlinks, restricted to the local host and opened via `Desktop.open` — covers the common `ls --hyperlink` / `eza --hyperlink` case.
- Public `com.sithtermfx.core.model.hyperlinks.LinkInfoProvider` API and `SithTermFxWidget.setLinkInfoProvider(...)` to customize how OSC 8 URIs are resolved (e.g. confirmation prompts, narrower scheme allowlists, alternate open actions).
- `DefaultOsc8LinkInfoProvider` (UI) as the default OSC 8 link resolver.

### Changed

- OSC 8 hyperlinks are now decoupled from automatic URL detection: the URI supplied by the program is honored directly instead of only when it matches the autolink regex. Previously OSC 8 links silently failed for `file://` and any non-web scheme.
- URI scheme validation and opening are unified in a shared `DesktopLinkInfoFactory`; `DefaultHyperlinkFilter` now delegates to it.
- `SafeUriOpen` gained a `validate()` entry that additionally allows `file:` for OSC 8, while browser-based opening (autolink detection and "open selection as URL") remains restricted to `http`, `https`, `mailto`, `ftp`, `ftps`, `news`.
- Maven project version set to `1.2.0` across all modules (root, sithtermfx-core, sithtermfx-ui, sithtermfx-app); README and integration-guide version references updated to `1.2.0`.
- Documented public release downloads (no authentication required) via GitHub Releases in the README.

### Fixed

- OSC 8 URIs containing `;` are no longer truncated; the URI is rejoined from all OSC arguments after the parameters field.

### Security

- OSC 8 hyperlinks are constrained to an allowlist of URI schemes; `file://` is limited to the local host (remote authorities rejected) and dangerous schemes (`javascript:`, `data:`, `vbscript:`, …) are never opened. Links open only on an explicit click.

---

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

[1.2.0]: https://github.com/chardonnay/SithTermFX/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/chardonnay/SithTermFX/compare/v1.0.0...v1.1.0
[1.0.0.0]: https://github.com/chardonnay/SithTermFX/releases/tag/v1.0.0
