# Changelog

All notable changes to SithTermFX are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.3] - 2026-10-04

### Added

- `UserSettingsProvider.isFollowLinkGesture(ModifierKeys)` decides which clicks follow a link and when a hovered link shows the hand cursor and its hover highlight. The default follows a link on every primary click, as before; an embedder can require a modifier click (e.g. `modifiers.isShortcutDown()` for Cmd+click on macOS and Ctrl+click elsewhere). A click that does not follow a link acts as on plain text, so double and triple clicks on a link select a word or a line. The hover state follows the modifier keys as they are pressed and released over a link. `com.sithtermfx.ui.settings.ModifierKeys` carries the modifier state from a `MouseEvent` or `KeyEvent`.
- `HyperlinkStyle.getUnderlyingStyle()` returns the style of the text beneath a link, for embedders that render the buffer themselves.
- `SystemCommandSequence.MAX_BODY_LENGTH` and `isTruncated()` (see Security).

### Fixed

- OSC 8 link text keeps its colours and attributes. A link that was not hovered was drawn in the default colours in every original-colour highlight mode (including the default `HOVER_WITH_BOTH_COLORS`), because `TerminalPanel.provideOriginalStyle()` fell back to the colourless `HyperlinkStyle` instead of the text style the link was started over; `ls --hyperlink --color` lost its colours and bold. The hover style of an OSC 8 link now also keeps the text's attributes, not only its colours.
- SGR inside an OSC 8 span is no longer ignored: `HyperlinkStyle.Builder` now carries the colours and options set on it, so `ESC]8;;uri ST ESC[1;34m name` is drawn bold blue, and inverse selection and search highlighting work on links. SGR 0 resets the attributes without ending the link (hyperlinks are independent of SGR); closing a link leaves the SGR state as it is, as in xterm.
- Links found by a `HyperlinkFilter` in an original-colour mode no longer keep stale, truncated targets when a line is processed again (output arriving in chunks, a URL typed at the prompt), and no longer collapse into the first run's colours and target: style runs are told apart by identity, not `equals()`, which treats every filter-made `HyperlinkStyle` as equal. An explicit OSC 8 link is never replaced by a filter link, in any highlight mode.
- An OSC 8 link opened while another is open replaces it instead of nesting inside it, so the new link keeps the text's style and one closing OSC 8 ends it.
- A link is followed only on the first click of a multi-click and not on the release that ends a drag: a double click no longer opens a link twice, and a selection dragged over a link no longer opens it.
- `NEVER_WITH_CUSTOM_COLOR` no longer throws for a filter link without a previous text style.
- Hyperlink hit-testing no longer reads the row one past the bottom of the screen when the pointer is over the bottom margin of the canvas.
- On macOS the character of a Cmd shortcut chord (the "d" of Cmd+Shift+D, previously only Cmd+backtick was handled) is no longer written to the pty after a menu accelerator consumed the key press. Ctrl chords and AltGr input (reported as Ctrl+Alt on Windows) are unaffected.
- OSC 104 (reset colour) no longer falls through into the OSC 1341 branch and is no longer delivered to `TerminalCustomCommandListener`s.
- `junit-jupiter-api` is a test dependency again; 1.2.2 published it as a compile dependency of `sithtermfx-core` and `sithtermfx-ui`.

### Security

- A CSI sequence with more than about a thousand characters the parser cannot place (intermediate bytes, control characters, a misplaced `?`), or with one such character beside a few hundred parameters, threw `ArrayIndexOutOfBoundsException` in `ControlSequence.pushBackReordered()` on the emulator thread and ended the session. Any remote output could carry one. The push-back is now bounded: the sequence is always pushed back terminated, and what does not fit is dropped, whole parameters at a time. A sequence that fits is handled exactly as before.
- OSC and DCS strings are capped at 64 Ki characters. A longer one is read to its terminator and dropped instead of growing a buffer without bound, and is not acted on, so a cut-off title or link target is never applied.

---

## [1.2.2] - 2026-09-09

### Fixed

- The scrolling margins no longer confine the cursor while origin mode (DECOM) is off. `DECSTBM` sets the region that scrolls, not the region the cursor may be addressed in; only DECOM makes line addressing relative. `SithTerminal.scrollY()` enforced the margins on every write regardless, and enforced them by scrolling, so text addressed above the top margin was pulled down into the region and text addressed below the bottom margin scrolled the region out from under the content already on screen. Both positions are reachable from any full-screen application that keeps a status line outside its scrolling area — tmux hit both, drawing its first pane row one line too low and stranding the cursor on the blank line above it.

### Changed

- Line advancing is unified in a single `lineFeed()` used by `newLine()`, `index()`, `nextLine()` and the autowrap in `wrapLines()`: on the bottom margin it scrolls the region, below it the cursor is outside the region and simply advances to the last line of the screen. This removes three copies of the same margin check and stops `index()`/`nextLine()` from moving the cursor past the last line. `scrollY()` is now a plain clamp onto an addressable line and never scrolls.

---

## [1.2.1] - 2026-07-06

### Added

- `TerminalColor.rgba(r, g, b, a)`, so a background colour can carry an alpha channel.

### Fixed

- `TerminalPanel.doRepaint()` clears the canvas before filling the window background, and skips the redundant per-cell and margin fills when that background is translucent, so a see-through terminal background is painted once instead of accumulating alpha frame over frame. Opaque rendering is unchanged.

---

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
