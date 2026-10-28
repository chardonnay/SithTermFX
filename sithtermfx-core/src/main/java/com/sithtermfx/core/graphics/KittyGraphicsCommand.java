package com.sithtermfx.core.graphics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * One graphics command: the control data, comma separated {@code key=value} pairs with one-char keys, and
 * the base64 payload after the {@code ;}. Keys this terminal does not know are ignored; a value of the wrong
 * kind fails the command when it is read.
 */
final class KittyGraphicsCommand {

    private static final long MAX_UNSIGNED = 0xFFFF_FFFFL;

    private final Map<Character, String> myKeys;

    private final String myPayload;

    private final @Nullable String myMalformedPair;

    private KittyGraphicsCommand(@NotNull Map<Character, String> keys, @NotNull String payload,
                                 @Nullable String malformedPair) {
        myKeys = keys;
        myPayload = payload;
        myMalformedPair = malformedPair;
    }

    /**
     * @param body what follows {@code ESC _ G}, up to the terminator
     */
    static @NotNull KittyGraphicsCommand parse(@NotNull String body) {
        int semicolon = body.indexOf(';');
        String controlData = semicolon >= 0 ? body.substring(0, semicolon) : body;
        String payload = semicolon >= 0 ? body.substring(semicolon + 1) : "";
        Map<Character, String> keys = new HashMap<>();
        String malformedPair = null;
        for (String pair : controlData.split(",")) {
            if (pair.isEmpty()) {
                continue;
            }
            if (pair.length() < 2 || pair.charAt(1) != '=') {
                malformedPair = pair;
                continue;
            }
            keys.put(pair.charAt(0), pair.substring(2));
        }
        return new KittyGraphicsCommand(keys, payload, malformedPair);
    }

    /**
     * @return a pair of the control data that is not {@code key=value}, or null
     */
    @Nullable String getMalformedPair() {
        return myMalformedPair;
    }

    boolean has(char key) {
        return myKeys.containsKey(key);
    }

    @NotNull String getPayload() {
        return myPayload;
    }

    char getChar(char key, char defaultValue) throws KittyGraphicsException {
        String value = myKeys.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value.length() != 1) {
            throw invalid(key, value);
        }
        return value.charAt(0);
    }

    /**
     * @return the value of a key that holds an unsigned 32-bit integer, such as an id, or 0 when it is not given
     */
    long getUnsigned(char key) throws KittyGraphicsException {
        String value = myKeys.get(key);
        if (value == null) {
            return 0;
        }
        long result = parseLong(key, value);
        if (result < 0 || result > MAX_UNSIGNED) {
            throw invalid(key, value);
        }
        return result;
    }

    /**
     * @return {@link #getUnsigned(char)}, or 0 when the value is not one; for the ids a response echoes
     */
    long getUnsignedOrZero(char key) {
        try {
            return getUnsigned(key);
        } catch (KittyGraphicsException e) {
            return 0;
        }
    }

    /**
     * @return the value of a key that holds a count or a size, at most {@link Integer#MAX_VALUE}
     */
    int getCount(char key) throws KittyGraphicsException {
        return (int) Math.min(getUnsigned(key), Integer.MAX_VALUE);
    }

    /**
     * @return the value of a key that holds a signed 32-bit integer, such as the z-index
     */
    int getSigned(char key, int defaultValue) throws KittyGraphicsException {
        String value = myKeys.get(key);
        if (value == null) {
            return defaultValue;
        }
        long result = parseLong(key, value);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw invalid(key, value);
        }
        return (int) result;
    }

    /**
     * @return how much the response is suppressed: 0 not at all, 1 an OK, 2 an OK and an error
     */
    int getQuiet() {
        try {
            return Math.min(getCount('q'), 2);
        } catch (KittyGraphicsException e) {
            return 0;
        }
    }

    private static long parseLong(char key, @NotNull String value) throws KittyGraphicsException {
        // at most 11 chars: a sign and the ten digits of 4294967295
        if (value.isEmpty() || value.length() > 11) {
            throw invalid(key, value);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw invalid(key, value);
        }
    }

    private static @NotNull KittyGraphicsException invalid(char key, @NotNull String value) {
        String shown = value.length() > 16 ? value.substring(0, 16) + "..." : value;
        return new KittyGraphicsException("EINVAL", "Invalid value for key " + key + ": " + shown);
    }
}
