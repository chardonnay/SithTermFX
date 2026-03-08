package com.sithtermfx.core.emulator.tvi;

import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalDataStream;
import com.sithtermfx.core.TerminalMode;
import com.sithtermfx.core.util.Ascii;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * TeleVideo 920 terminal emulator.
 * <p>
 * The TVI-920 extends the 910 with host-programmable function keys, a
 * user-definable status line, bidirectional scrolling (ESC Ej / ESC Ek),
 * and enhanced line editing (insert/delete character within a line).
 * It retains the 24x80 display and monochrome attribute set.
 *
 * @author Daniel Mengel
 */
public class Tvi920Emulator extends AbstractTviEmulator {

    private static final Logger logger = LoggerFactory.getLogger(Tvi920Emulator.class);

    public Tvi920Emulator(TerminalDataStream dataStream, Terminal terminal) {
        super(dataStream, terminal);
    }

    @Override
    protected int getTviModel() {
        return 920;
    }

    @Override
    protected boolean processExtendedEscape(char ch, Terminal terminal) throws IOException {
        switch (ch) {
            case 'v':
                terminal.setModeEnabled(TerminalMode.AutoWrap, true);
                return true;
            case 'w':
                terminal.setModeEnabled(TerminalMode.AutoWrap, false);
                return true;
            case '~':
                processScrollRegion(terminal);
                return true;
            case 'p':
                processFunctionKeyDefinition();
                return true;
            case 'z':
                processResetToFactory(terminal);
                return true;
            case Ascii.ESC_CHAR:
                return false;
            default:
                return false;
        }
    }

    /**
     * ESC ~ top bottom — set scrolling region (rows biased by +32).
     */
    private void processScrollRegion(Terminal terminal) throws IOException {
        char topChar = myDataStream.getChar();
        char botChar = myDataStream.getChar();
        int top = (topChar - 0x20) + 1;
        int bottom = (botChar - 0x20) + 1;
        top = Math.max(1, Math.min(top, terminal.getTerminalHeight()));
        bottom = Math.max(top, Math.min(bottom, terminal.getTerminalHeight()));
        terminal.setScrollingRegion(top, bottom);
    }

    /**
     * ESC p label ESC \ — program a function key label. The host sends
     * the key number, the label/data, and terminates with ESC \.
     * We consume and discard the definition since we handle keys locally.
     */
    private void processFunctionKeyDefinition() throws IOException {
        char prev = 0;
        boolean foundTerminator = false;
        try {
            while (true) {
                char c = myDataStream.getChar();
                if (prev == Ascii.ESC_CHAR && c == '\\') {
                    foundTerminator = true;
                    break;
                }
                prev = c;
            }
        } catch (TerminalDataStream.EOF e) {
            logger.debug("TVI920: EOF reached while consuming function key definition");
        }
        if (foundTerminator) {
            logger.debug("TVI920: function key definition received (consumed)");
        }
    }

    private void processResetToFactory(Terminal terminal) {
        terminal.reset(true);
        logger.debug("TVI920: reset to factory defaults");
    }
}
