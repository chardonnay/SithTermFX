package com.sithtermfx.ui;

import com.sithtermfx.core.model.hyperlinks.LinkInfo;
import com.sithtermfx.core.model.hyperlinks.LinkInfoProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Default {@link LinkInfoProvider} for OSC 8 hyperlinks: resolves the explicit URI to a
 * {@link LinkInfo} that opens it via {@link java.awt.Desktop}, subject to scheme validation.
 *
 * @author Daniel Mengel
 */
public class DefaultOsc8LinkInfoProvider implements LinkInfoProvider {

    @Nullable
    @Override
    public LinkInfo createLinkInfo(@NotNull String uri) {
        return DesktopLinkInfoFactory.forUri(uri);
    }
}
