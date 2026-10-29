package com.sithtermfx.core.clipboard;

/**
 * Which clipboard a program reads or writes with OSC 5522 ({@code loc}).
 */
public enum ClipboardLocation {
    /** The clipboard, the default. */
    CLIPBOARD,
    /** The primary selection of X11 and Wayland ({@code loc=primary}). */
    PRIMARY
}
