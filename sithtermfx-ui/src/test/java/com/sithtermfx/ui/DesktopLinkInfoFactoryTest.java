package com.sithtermfx.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code forUri} only validates and builds a {@link com.sithtermfx.core.model.hyperlinks.LinkInfo};
 * it never opens anything (that happens on {@code navigate()}), so these assertions are safe and
 * headless.
 */
public class DesktopLinkInfoFactoryTest {

    @Test
    public void webLinkResolves() {
        assertNotNull(DesktopLinkInfoFactory.forUri("https://example.com"));
        assertNotNull(DesktopLinkInfoFactory.forUri("mailto:a@b.com"));
    }

    @Test
    public void dangerousSchemeRejected() {
        assertNull(DesktopLinkInfoFactory.forUri("javascript:alert(1)"));
        assertNull(DesktopLinkInfoFactory.forUri("data:text/html,x"));
    }

    @Test
    public void localFileResolves() {
        assertNotNull(DesktopLinkInfoFactory.forUri("file:///tmp/x"));
        assertNotNull(DesktopLinkInfoFactory.forUri("file://localhost/tmp/x"));
    }

    @Test
    public void remoteFileRejected() {
        // A file:// URI pointing at a non-local host must not resolve to a local file.
        assertNull(DesktopLinkInfoFactory.forUri("file://definitely-not-this-host.invalid/tmp/x"));
    }
}
