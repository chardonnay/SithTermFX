package com.sithtermfx.core.model.hyperlinks;

import org.jetbrains.annotations.Nullable;

/**
 * @author Daniel Mengel
 */
public interface HyperlinkFilter {

    @Nullable
    LinkResult apply(String line);
}
