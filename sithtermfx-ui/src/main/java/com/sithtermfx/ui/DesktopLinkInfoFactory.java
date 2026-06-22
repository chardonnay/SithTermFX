package com.sithtermfx.ui;

import com.sithtermfx.core.model.hyperlinks.LinkInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.awt.EventQueue;
import java.io.File;
import java.net.InetAddress;
import java.net.URI;

/**
 * Builds {@link LinkInfo} objects that open scheme-validated URIs via {@link java.awt.Desktop}.
 * Shared by the autolink filter ({@link DefaultHyperlinkFilter}) and the OSC 8 hyperlink provider
 * ({@link DefaultOsc8LinkInfoProvider}) so validation and the open action live in one place.
 *
 * @author Daniel Mengel
 */
final class DesktopLinkInfoFactory {

    private static final Logger logger = LoggerFactory.getLogger(DesktopLinkInfoFactory.class);

    private DesktopLinkInfoFactory() {
    }

    /**
     * @return a {@link LinkInfo} that opens the URI when navigated, or {@code null} if the scheme is
     *         not allowed (or a {@code file:} URI points at a non-local host).
     */
    @Nullable
    static LinkInfo forUri(@NotNull String rawUri) {
        URI uri = SafeUriOpen.validate(rawUri);
        if (uri == null) {
            return null;
        }
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            File file = toLocalFile(uri);
            if (file == null) {
                return null;
            }
            return new LinkInfo(() -> openFile(file, rawUri));
        }
        return new LinkInfo(() -> browse(uri, rawUri));
    }

    /**
     * Resolves a {@code file:} URI to a local {@link File}, accepting only an empty/local authority
     * (null, empty, {@code localhost}, or this machine's hostname). Remote hosts are rejected.
     */
    @Nullable
    private static File toLocalFile(@NotNull URI uri) {
        String authority = uri.getAuthority();
        if (!isLocalAuthority(authority)) {
            return null;
        }
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            return null;
        }
        // Strip the (local) authority by using the decoded path directly; new File(URI) would reject
        // a non-empty authority component.
        return new File(path);
    }

    private static boolean isLocalAuthority(@Nullable String authority) {
        if (authority == null || authority.isEmpty() || "localhost".equalsIgnoreCase(authority)) {
            return true;
        }
        String local = LocalHost.NAME;
        return local != null && local.equalsIgnoreCase(authority);
    }

    private static void openFile(@NotNull File file, @NotNull String rawUri) {
        Desktop d = desktop();
        if (d == null) {
            return;
        }
        EventQueue.invokeLater(() -> {
            try {
                d.open(file);
            } catch (Exception ex) {
                logger.error("Error opening file link: {}", rawUri, ex);
            }
        });
    }

    private static void browse(@NotNull URI uri, @NotNull String rawUri) {
        Desktop d = desktop();
        if (d == null) {
            return;
        }
        EventQueue.invokeLater(() -> {
            try {
                d.browse(uri);
            } catch (Exception ex) {
                logger.error("Error opening url: {}", rawUri, ex);
            }
        });
    }

    @Nullable
    private static Desktop desktop() {
        try {
            return Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Lazily-resolved local hostname, used to accept {@code file://<this-host>/path} links. */
    private static final class LocalHost {
        @Nullable
        static final String NAME = resolve();

        @Nullable
        private static String resolve() {
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                return null;
            }
        }
    }
}
