package com.sithtermfx.ui;

import java.util.List;

/**
 * @author Daniel Mengel
 */
public interface TerminalActionProvider {

    List<TerminalAction> getActions();

    TerminalActionProvider getNextProvider();

    void setNextProvider(TerminalActionProvider provider);
}
