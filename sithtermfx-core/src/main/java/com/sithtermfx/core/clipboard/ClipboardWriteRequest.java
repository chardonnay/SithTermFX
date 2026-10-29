package com.sithtermfx.core.clipboard;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A program writes to the clipboard (OSC 5522 {@code type=write}): data for one or more MIME types, all of which
 * replace what is on the clipboard together.
 */
public final class ClipboardWriteRequest {

    private final ClipboardLocation myLocation;

    private final Map<String, byte[]> myData;

    private final Map<String, List<String>> myAliases;

    private final @Nullable String myName;

    private final @Nullable String myPassword;

    public ClipboardWriteRequest(@NotNull ClipboardLocation location, @NotNull Map<String, byte[]> data,
                                 @NotNull Map<String, List<String>> aliases,
                                 @Nullable String name, @Nullable String password) {
        myLocation = location;
        myData = Collections.unmodifiableMap(new LinkedHashMap<>(data));
        myAliases = Collections.unmodifiableMap(new LinkedHashMap<>(aliases));
        myName = name;
        myPassword = password;
    }

    public @NotNull ClipboardLocation getLocation() {
        return myLocation;
    }

    /**
     * @return the data by MIME type, in the order it was sent; the arrays must not be modified
     */
    public @NotNull Map<String, byte[]> getData() {
        return myData;
    }

    /**
     * @return for a MIME type of {@link #getData()}, the further types the clipboard offers with the same data
     * ({@code type=walias})
     */
    public @NotNull Map<String, List<String>> getAliases() {
        return myAliases;
    }

    /**
     * @return the name the program gives itself; untrusted text, or null
     */
    public @Nullable String getName() {
        return myName;
    }

    /**
     * @return the password the program sends, or null; see {@link ClipboardReadRequest#getPassword()}
     */
    public @Nullable String getPassword() {
        return myPassword;
    }
}
