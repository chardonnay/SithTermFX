package com.sithtermfx.core.clipboard;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * A program asks to read the clipboard (OSC 5522 {@code type=read}).
 */
public final class ClipboardReadRequest {

    private final ClipboardLocation myLocation;

    private final List<String> myMimeTypes;

    private final @Nullable String myName;

    private final @Nullable String myPassword;

    private final boolean myGrantedByPaste;

    public ClipboardReadRequest(@NotNull ClipboardLocation location, @NotNull List<String> mimeTypes,
                                @Nullable String name, @Nullable String password, boolean grantedByPaste) {
        myLocation = location;
        myMimeTypes = List.copyOf(mimeTypes);
        myName = name;
        myPassword = password;
        myGrantedByPaste = grantedByPaste;
    }

    public @NotNull ClipboardLocation getLocation() {
        return myLocation;
    }

    /**
     * @return the MIME types asked for, in the order the program asked for them
     */
    public @NotNull List<String> getMimeTypes() {
        return myMimeTypes;
    }

    /**
     * @return the name the program gives itself ({@code name}), for a permission prompt; untrusted text, or null.
     * It only counts together with a password.
     */
    public @Nullable String getName() {
        return myName;
    }

    /**
     * @return the password the program sends ({@code pw}): when the user allows reading for it, the handler may
     * allow later requests with the same name and password without asking again. Null when not sent, or sent
     * without a name.
     */
    public @Nullable String getPassword() {
        return myPassword;
    }

    /**
     * @return whether the user already allowed this read by pasting: the program reads with the one-time password
     * of a paste event. The handler must not ask again.
     */
    public boolean isGrantedByPaste() {
        return myGrantedByPaste;
    }
}
