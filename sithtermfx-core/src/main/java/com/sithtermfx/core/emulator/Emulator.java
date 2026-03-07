package com.sithtermfx.core.emulator;

import java.io.IOException;

/**
 * @author Daniel Mengel
 */
public interface Emulator {

    boolean hasNext();

    void next() throws IOException;

    void resetEof();
}
