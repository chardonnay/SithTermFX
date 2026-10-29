# Changelog

All notable changes to SithTermFX are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- OSC 5522, kitty's clipboard protocol (https://sw.kovidgoyal.net/kitty/clipboard/): programs read and write the clipboard in any MIME type, with aliases, chunked writes and the `id` echo; with private mode 5522 a paste reaches the program as a paste event with a one-time password. Off by default: `SithTermFxWidget.setClipboardHandler(...)` (or `SithTerminal.setClipboardHandler(...)`) takes a `com.sithtermfx.core.clipboard.TerminalClipboardHandler`, which decides what is allowed and does the access asynchronously. Programs detect support with DECRQM `CSI ? 5522 $ p`.
- `com.sithtermfx.ui.JavaFxClipboardHandler`: the system clipboard through JavaFX, writes allowed or not, reads only after a `ReadPermission` (`askUser(...)` shows a dialog, optionally remembering a program by name and password; `DENY_READS`), text, HTML, RTF, URI lists and `image/png`.
- Limits for untrusted writes: strict base64, at most 64 MiB (`ClipboardProtocol.MAX_WRITE_BYTES`) and 32 MIME types per write, one read at a time (`EBUSY`).
- `TerminalPanel.sendClipboardPasteEvent()`, called by `handlePaste()`, for subclasses that paste their own way.

### Fixed

- A CSI with the intermediate `$` (DECRQM and the other DEC `$` sequences) no longer prints the `$` and runs as the sequence without it: `CSI ... $ r` (DECCARA) used to set the scrolling region like DECSTBM. Of these sequences only DECRQM for mode 5522 is answered; the others are ignored.

---

## [1.2.6] - 2026-10-28

### Added

- The kitty graphics protocol (https://sw.kovidgoyal.net/kitty/graphics-protocol/): programs such as `kitten icat`, yazi, ranger, timg, chafa and mpv `--vo=kitty` show images. Supported are transmitting, displaying, querying, placing a stored image and deleting (`a=t`, `T`, `q`, `p`, `d`, every `d` target); direct transmission in one piece or in chunks; RGB, RGBA and PNG (`f=24`, `32`, `100`), optionally zlib-compressed; image ids, image numbers and placement ids; the source rectangle, the offset in the first cell, the size in cells, the cursor policy and the z-index of a placement (images under the text, under non-default cell backgrounds, or over the text); responses with the quiet modes. Reading images from files or shared memory, animation, Unicode placeholders and relative placements are answered with `EINVAL`, so programs fall back. Programs detect the support with the usual query; `TERM` can stay `xterm-256color`.
- Images belong to the line they were placed on: they scroll with the text into the scrollback, the alternate screen has its own, they stay with their char when a resize rewraps the line, and erasing text leaves them alone. `ED 2` moves them into the scrollback with the text, `ED 3` and `RIS` delete them. The decoded images of a terminal take at most 128 MiB (`KittyGraphicsProtocol.DEFAULT_STORAGE_QUOTA`) and number at most 4096 (`MAX_IMAGES`); images that no line shows any more are evicted first. A line holds at most 256 placements (`MAX_PLACEMENTS_PER_LINE`), and an image too large for the heap is answered with `ENOMEM` instead of ending the session.
- `ESC _` (APC) is parsed: up to the same terminators as OSC and DCS, at most 1 MiB (`ApplicationProgramCommand.MAX_BODY_LENGTH`), a longer one is dropped whole. Before, the body of an APC was printed as text.
- `CSI 14 t`, `CSI 16 t` and `CSI 18 t` report the size of the text area and of a cell in pixels and the size in characters.
- `TtyConnector.resize(TermSize, PixelSize)` gets the size of the text area in pixels with every resize; its default calls `resize(TermSize)`. The demo application's `PtyProcessTtyConnector` passes it to the pty (`ws_xpixel`, `ws_ypixel`).
- `UserSettingsProvider.isKittyGraphicsEnabled()` (default true) turns the protocol off; the terminal then neither acts on nor answers its commands. `TerminalDisplay.isKittyGraphicsEnabled()` and `getCellPixelSize()` let a display of an embedder's own take part; both default to no images.
- `TerminalTextBuffer.processImagePlacements(...)`, `TerminalLine.getImagePlacements()` and the new package `com.sithtermfx.core.graphics` (`KittyGraphicsProtocol`, `KittyImage`, `ImagePlacement`) for embedders that render the buffer themselves; `SithTerminal.getKittyGraphics()` returns the stored images.
- `UserSettingsProvider.getBufferMaxMemoryBytes()` bounds the scrollback by memory as well as by lines (`getBufferMaxLinesCount()`), whichever limit is reached first: once the history lines hold more than the limit (an estimate of their heap), the oldest lines are dropped. The default, -1, keeps the old behaviour. `TerminalTextBuffer.setHistoryMemoryLimit(long)` changes the limit of a running terminal and applies it at once; `getHistoryMemoryUsage()` returns the estimate.
- Scrollback compression, off by default (`UserSettingsProvider.isScrollbackCompressionEnabled()`, `TerminalTextBuffer.setScrollbackCompressionEnabled(boolean)`): once a terminal has had no output for 1.5 s, a low-priority background thread packs its history lines into deflated blocks of up to 128 lines, a few milliseconds at a time while the buffer lock is free. The rows on screen with a screenful around them and the newest screenful of history stay unpacked, and nothing is packed while find results are shown. A history over its memory limit packs old lines before it drops any. Packed lines keep their identity, wrapped flag and styles (links and `TextStyle` subclasses keep their identity, equal plain styles are shared); drawing a line or reading its entries unpacks its block, `TerminalLine.getText()` decodes without unpacking. Measured with 100,000 history lines, the heap they hold shrinks from 59 MB to 7 MB for `ls -l` output and from 67 MB to 9 MB for log lines full of random ids. `compressHistoryNow()` and `getCompressedHistoryLinesCount()` are there for embedders and tests.

### Changed

- `TerminalPanel.myCharSize` is volatile, as the emulator thread reads the cell size.
- A line scrolled out of the screen no longer brings its own copy of the blank filler style: the filler style is shared while the current style stays the same, which saves about 56 bytes per history line.
- A `TerminalLine` creates its custom-highlighting list on the first highlighting instead of up front (about 50 bytes per line).

---

## [1.2.5] - 2026-10-19

### Security

- A CSI sequence no longer grows memory without bound and no longer yields wrong parameters, whatever the remote side sends (follow-up to the 1.2.3 push-back fix, issue #5). At most 256 parameters are kept and the rest are dropped up to the final char; each value is clamped to 65535, as in xterm, instead of overflowing `int` (an overflowed value used to be re-emitted with a `-` on push-back); the raw copy kept for debug logging is capped at 1024 chars.

---

## [1.2.4] - 2026-10-05

### Added

- `UserSettingsProvider.getBoldTextMode()` chooses how bold text (SGR 1) is drawn: `BoldTextMode.BOLD_FONT` (the default, unchanged), `BRIGHT_COLOR` (xterm's `boldColors`: bold text in one of the eight basic ANSI colours, SGR 30–37 / palette 0–7, takes the matching bright colour 8–15 of the palette, custom palettes included, with the normal font weight) or `BOLD_FONT_AND_BRIGHT_COLOR`. Only a basic ANSI foreground is brightened; the default foreground, the bright colours, the 256-colour cube (16–255) and truecolour stay as they are (`SGR 38;5;0`–`7` names the same eight palette entries and is brightened too, as in xterm). The text's own colour is brightened before inverse video, selection, search and link highlighting are applied, so inverse bold text gets a bright background and the selection, found-pattern and link colours from the settings are never brightened; of an OSC 8 or filter link, the text beneath it (`HyperlinkStyle.getUnderlyingStyle()`) is brightened and the link is kept.
- `SystemSettingsProvider.getSearchBarText()` returns the find bar's strings as a `com.sithtermfx.ui.settings.SearchBarText` (placeholder, "Ignore Case" check box and its tooltip, match counter, "No matches", the next/previous buttons and their tooltips). Every method has the English default, so an embedder overrides only what it translates; the library ships no translations.

### Changed

- The find bar's search field shows a placeholder, and its buttons and check box have tooltips.

### Fixed

- A search text that is not found shows "No matches" in the find bar instead of keeping the counter of the previous search.

---

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
