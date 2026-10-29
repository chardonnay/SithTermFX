package com.sithtermfx.core.clipboard;

import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Gives programs in the terminal access to a clipboard with OSC 5522, kitty's clipboard protocol. The terminal
 * parses the escape codes and answers the program; the handler decides what is allowed and does the reading and
 * writing. Without a handler the protocol is off: its escape codes are ignored and the terminal reports it as
 * unsupported.
 * <p>
 * The methods are called on the emulator thread and must not block it: complete the stage later, for example
 * after asking the user or on the UI thread. Complete it exceptionally with a {@link ClipboardException} to send
 * that status; any other exception is sent as {@link ClipboardError#EIO}.
 */
public interface TerminalClipboardHandler {

    /**
     * @return the MIME types on the clipboard. Listing them needs no permission.
     */
    @NotNull CompletionStage<List<String>> getMimeTypes(@NotNull ClipboardLocation location);

    /**
     * Reads the clipboard for a program, which should be allowed only after the user agreed, unless
     * {@link ClipboardReadRequest#isGrantedByPaste()}.
     *
     * @return the data of those of the requested types the clipboard has, by requested type
     */
    @NotNull CompletionStage<Map<String, byte[]>> read(@NotNull ClipboardReadRequest request);

    /**
     * Replaces the clipboard with the data a program sent.
     */
    @NotNull CompletionStage<Void> write(@NotNull ClipboardWriteRequest request);
}
