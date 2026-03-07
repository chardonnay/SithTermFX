package com.sithtermfx.ui;

/**
 * @author Daniel Mengel
 */
public abstract class TerminalActionProviderBase implements TerminalActionProvider {

    @Override
    public TerminalActionProvider getNextProvider() {
        return null;
    }

    @Override
    public void setNextProvider(TerminalActionProvider provider) {

    }
}
