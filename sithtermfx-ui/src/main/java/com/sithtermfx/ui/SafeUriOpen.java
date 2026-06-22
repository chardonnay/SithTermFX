package com.sithtermfx.ui;

import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Set;

/**
 * Validates a URI scheme against an allowlist before it is opened, to keep dangerous schemes
 * (javascript:, data:, vbscript:, ...) from ever reaching the OS open handlers.
 */
final class SafeUriOpen {

    /** Schemes safe to hand to {@code Desktop.browse()}. */
    private static final Set<String> BROWSE_SCHEMES = Set.of(
            "http", "https", "mailto", "ftp", "ftps", "news"
    );

    /**
     * Schemes allowed for explicit OSC 8 hyperlinks. Superset of {@link #BROWSE_SCHEMES} that also
     * permits {@code file:} (opened via {@code Desktop.open}), needed by producers such as
     * {@code ls --hyperlink}.
     */
    private static final Set<String> OSC8_SCHEMES = Set.of(
            "http", "https", "mailto", "ftp", "ftps", "news", "file"
    );

    private SafeUriOpen() {
    }

    /**
     * Returns true if the URI has a scheme safe for {@code Desktop.browse()}.
     * Rejects file:, javascript:, data:, vbscript:, and other dangerous schemes.
     */
    static boolean isAllowedScheme(@Nullable URI uri) {
        return hasScheme(uri, BROWSE_SCHEMES);
    }

    /**
     * Returns the URI if its scheme is safe for {@code Desktop.browse()}, otherwise null.
     * Use before calling {@code Desktop.getDesktop().browse(uri)}.
     */
    @Nullable
    static URI validateForBrowse(@Nullable String urlString) {
        return parseAllowed(urlString, BROWSE_SCHEMES);
    }

    /**
     * Returns the URI if its scheme is allowed for an explicit OSC 8 hyperlink (browse schemes
     * plus {@code file:}), otherwise null.
     */
    @Nullable
    static URI validate(@Nullable String urlString) {
        return parseAllowed(urlString, OSC8_SCHEMES);
    }

    @Nullable
    private static URI parseAllowed(@Nullable String urlString, Set<String> allowed) {
        if (urlString == null || urlString.isBlank()) return null;
        try {
            URI uri = new URI(urlString.trim());
            return hasScheme(uri, allowed) ? uri : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasScheme(@Nullable URI uri, Set<String> allowed) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        return scheme != null && allowed.contains(scheme.toLowerCase());
    }
}
