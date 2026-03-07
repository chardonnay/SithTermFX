# Security and Error Audit (bugfixes/security-and-error-audit)

## Summary

- **URI/URL opening**: Fixed. Only whitelisted schemes (http, https, mailto, ftp, ftps, news) are opened via `Desktop.browse()`. Prevents opening `file://`, `javascript:`, `data:`, etc. from selected text or hyperlinks.
- **Process execution**: Uses `PtyProcessBuilder` / `ProcessBuilder` with fixed or app-controlled commands (e.g. shell, debug tools). No user-controlled command injection identified.
- **Deserialization**: No `readObject`/`ObjectInputStream` usage found.
- **Locks**: Lock/unlock pairs in TerminalTextBuffer and JediTerminal are used in try/finally; no obvious leak.
- **Clipboard**: Copy/paste uses system clipboard; content is not executed, only displayed/pasted as text.

## Change

- **SafeUriOpen**: New helper in `jeditermfx-ui` that validates URI scheme before opening. Used in `TerminalPanel.openSelectedTextAsURL()`, `TerminalPanel.isSelectedTextUrl()`, and `DefaultHyperlinkFilter` (hyperlink click). Rejected schemes are not opened; hyperlinks with disallowed schemes are not made clickable.

## Verification

1. Run the terminal (e.g. BasicTerminalShellExample or KorTTY).
2. Select text such as `file:///etc/passwd` (or `file:///C:/Windows/System32/drivers/etc/hosts` on Windows).
3. Use "Open as URL" (or equivalent). The URL must **not** open in the browser/default handler.
4. Select `https://example.com` and open as URL; it **should** open.
5. Optional: run with working directory = repo root so `.cursor` exists; try opening `file:///...` and check `.cursor/debug-d6fa37.log` for a `uri_reject` entry (scheme `file`).

Press Proceed/Mark as fixed when done.
