package com.sithtermfx.core.emulator.wyse;

import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalDataStream;
import com.sithtermfx.core.util.Ascii;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Emulates the Wyse WY-50 terminal — a widely deployed ASCII display terminal
 * supporting 80 or 132 columns, 24 or 25 display lines, and the Wyse native
 * escape sequence set.
 * <p>
 * Supported video attributes: normal, dim (half-intensity), blank (invisible),
 * reverse, underline, and blink. No color support.
 * <p>
 * The WY-50 uses {@code ESC G <attrib>} for attribute selection and
 * {@code ESC = <row+0x20> <col+0x20>} for direct cursor addressing.
 *
 * @author Daniel Mengel
 */
public class Wy50Emulator extends AbstractWyseEmulator {

    private static final Logger logger = LoggerFactory.getLogger(Wy50Emulator.class);

    private static final int WYSE_LEVEL = 50;

    private static final int DEFAULT_COLUMNS = 80;
    private static final int DEFAULT_LINES = 24;

    public Wy50Emulator(@NotNull TerminalDataStream dataStream, @NotNull Terminal terminal) {
        super(dataStream, terminal);
        if (logger.isDebugEnabled()) {
            logger.debug("WY-50 emulator initialised ({}x{})", DEFAULT_COLUMNS, DEFAULT_LINES);
        }
    }

    @Override
    public int getWyseLevel() {
        return WYSE_LEVEL;
    }

    @Override
    protected void processWyseEscape(char cmd, Terminal terminal) throws IOException {
        switch (cmd) {
            case 'z':
                processStatusLine(terminal);
                return;
            case 'A':
                processFunctionKeyLabel(terminal);
                return;
            case 'F':
                terminal.setCursorVisible(true);
                return;
            case 'e':
                processInsertMode(terminal, true);
                return;
            case 'r':
                processInsertMode(terminal, false);
                return;
            case 'I':
                terminal.horizontalTab();
                return;
            case '-':
                processCursorMovePrevField(terminal);
                return;
            case 0x1A: // Ctrl-Z — reset
                terminal.reset(true);
                return;
            default:
                break;
        }
        super.processWyseEscape(cmd, terminal);
    }

    /**
     * Processes {@code ESC z <line>}: display status line message.
     * Reads characters until CR to fill the 25th (status) line.
     */
    private void processStatusLine(Terminal terminal) throws IOException {
        char sub = myDataStream.getChar();
        StringBuilder sb = new StringBuilder();
        try {
            while (true) {
                char c = myDataStream.getChar();
                if (c == Ascii.CR) {
                    break;
                }
                sb.append(c);
            }
        } catch (TerminalDataStream.EOF eof) {
            // stream ended before CR — use what we have
        }
        if (logger.isTraceEnabled()) {
            logger.trace("WY-50: status line (mode={}): {}", sub, sb);
        }
        terminal.setWindowTitle(sb.toString());
    }

    /**
     * Processes {@code ESC A <label>}: program a function key label.
     * The format is {@code ESC A <key-id> <label> CR}.
     */
    private void processFunctionKeyLabel(Terminal terminal) throws IOException {
        char keyId = myDataStream.getChar();
        StringBuilder label = new StringBuilder();
        try {
            while (true) {
                char c = myDataStream.getChar();
                if (c == Ascii.CR) {
                    break;
                }
                label.append(c);
            }
        } catch (TerminalDataStream.EOF eof) {
            // stream ended
        }
        if (logger.isDebugEnabled()) {
            logger.debug("WY-50: function key label keyId='{}' label='{}'", keyId, label);
        }
    }

    private void processInsertMode(Terminal terminal, boolean enabled) {
        if (logger.isTraceEnabled()) {
            logger.trace("WY-50: insert mode {}", enabled ? "ON" : "OFF");
        }
        terminal.setModeEnabled(com.sithtermfx.core.TerminalMode.InsertMode, enabled);
    }

    @SuppressWarnings("unused")
    private void processCursorMovePrevField(Terminal terminal) throws IOException {
        int n = 0, r = 0, c = 0;
        try {
            char ch = myDataStream.getChar();
            n = ch - 0x20;
            ch = myDataStream.getChar();
            r = ch - 0x20;
            ch = myDataStream.getChar();
            c = ch - 0x20;
        } catch (TerminalDataStream.EOF e) {
            terminal.cursorBackward(1);
            return;
        }

        r = Math.max(1, Math.min(r + 1, terminal.getTerminalHeight()));
        c = Math.max(1, Math.min(c + 1, terminal.getTerminalWidth()));
        terminal.cursorPosition(c, r);
    }
}
