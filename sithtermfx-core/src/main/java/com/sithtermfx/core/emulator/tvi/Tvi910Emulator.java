package com.sithtermfx.core.emulator.tvi;

import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalDataStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * TeleVideo 910 terminal emulator.
 * <p>
 * The TVI-910 is the base model with a 24x80 display and simple visual
 * attributes (reverse, underline, blink, blank). It supports basic cursor
 * addressing ({@code ESC = row col}), line/screen erase, and insert/delete
 * line operations. No programmable function keys or protected fields beyond
 * the basic protect-on/off toggle.
 *
 * @author Daniel Mengel
 */
public class Tvi910Emulator extends AbstractTviEmulator {

    private static final Logger logger = LoggerFactory.getLogger(Tvi910Emulator.class);

    public Tvi910Emulator(TerminalDataStream dataStream, Terminal terminal) {
        super(dataStream, terminal);
    }

    @Override
    protected int getTviModel() {
        return 910;
    }

    @Override
    protected boolean processExtendedEscape(char ch, Terminal terminal) throws IOException {
        switch (ch) {
            case 'v':
                enableAutoWrap(terminal, true);
                return true;
            case 'w':
                enableAutoWrap(terminal, false);
                return true;
            default:
                return false;
        }
    }

    private void enableAutoWrap(Terminal terminal, boolean enabled) {
        terminal.setModeEnabled(
                com.sithtermfx.core.TerminalMode.AutoWrap, enabled);
        if (logger.isDebugEnabled()) {
            logger.debug("TVI910: auto-wrap {}", enabled ? "enabled" : "disabled");
        }
    }
}
