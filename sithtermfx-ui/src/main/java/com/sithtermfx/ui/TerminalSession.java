package com.sithtermfx.ui;

import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TtyConnector;
import com.sithtermfx.core.model.TerminalTextBuffer;

/**
 * @author Daniel Mengel
 */
public interface TerminalSession {

    void start();

    TerminalTextBuffer getTerminalTextBuffer();

    Terminal getTerminal();

    TtyConnector getTtyConnector();

    void close();
}
