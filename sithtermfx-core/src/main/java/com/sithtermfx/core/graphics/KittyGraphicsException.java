package com.sithtermfx.core.graphics;

import org.jetbrains.annotations.NotNull;

/**
 * A graphics command that failed. The message is the error part of the response, an error code and a text
 * separated by a colon, as in {@code ENOENT:No image with id 7}.
 */
final class KittyGraphicsException extends Exception {

    KittyGraphicsException(@NotNull String code, @NotNull String text) {
        super(code + ":" + text);
    }
}
