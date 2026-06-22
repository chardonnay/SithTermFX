package com.sithtermfx.core.model.hyperlinks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds a {@link LinkInfo} for an explicit hyperlink URI supplied by the program through an
 * OSC 8 escape sequence ({@code ESC ] 8 ; params ; URI ST}).
 *
 * <p>Unlike {@link HyperlinkFilter}, which scans visible text for link-shaped patterns, the URI
 * here is given directly by the application, so no pattern matching is involved. Implementations
 * live in the UI layer so that scheme validation and the open action (e.g. {@code Desktop.browse}
 * / {@code Desktop.open}) stay out of {@code sithtermfx-core}.
 *
 * @author Daniel Mengel
 */
public interface LinkInfoProvider {

    /**
     * @param uri the explicit hyperlink target supplied via OSC 8
     * @return a {@link LinkInfo} if the URI is allowed and openable, or {@code null} to reject it
     *         (a rejected link renders as plain text).
     */
    @Nullable
    LinkInfo createLinkInfo(@NotNull String uri);
}
