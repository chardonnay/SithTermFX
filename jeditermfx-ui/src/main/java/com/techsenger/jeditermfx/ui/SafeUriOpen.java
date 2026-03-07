package com.techsenger.jeditermfx.ui;

import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Set;

/**
 * Validates URI scheme before opening in browser to prevent file:, javascript:, data: etc.
 */
final class SafeUriOpen {

    private static final Set<String> ALLOWED_SCHEMES = Set.of(
            "http", "https", "mailto", "ftp", "ftps", "news"
    );

    private SafeUriOpen() {
    }

    /**
     * Returns true if the URI has an allowed scheme for Desktop.browse().
     * Rejects file:, javascript:, data:, vbscript:, and other dangerous schemes.
     */
    static boolean isAllowedScheme(@Nullable URI uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        return scheme != null && ALLOWED_SCHEMES.contains(scheme.toLowerCase());
    }

    /**
     * Returns the URI if its scheme is allowed, otherwise null.
     * Use before calling Desktop.getDesktop().browse(uri).
     */
    @Nullable
    static URI validateForBrowse(@Nullable String urlString) {
        if (urlString == null || urlString.isBlank()) return null;
        try {
            URI uri = new URI(urlString.trim());
            if (isAllowedScheme(uri)) return uri;
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
