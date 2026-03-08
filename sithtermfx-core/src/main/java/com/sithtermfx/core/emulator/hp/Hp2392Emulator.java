package com.sithtermfx.core.emulator.hp;

import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalDataStream;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * HP 2392A terminal emulator.
 * <p>
 * The 2392 is a character-cell data-entry terminal that supports both block mode
 * and character mode.  Key features modelled here:
 * <ul>
 *   <li>8 display enhancements (half-bright, underline, blink, inverse and combinations)</li>
 *   <li>8 user-definable softkeys (f1-f8)</li>
 *   <li>Memory-locked screen region (protected fields in block mode)</li>
 *   <li>Block mode transmit and field-level editing</li>
 *   <li>132-column support</li>
 *   <li>HP absolute / relative cursor addressing</li>
 * </ul>
 *
 * @author Daniel Mengel
 */
public class Hp2392Emulator extends AbstractHpEmulator {

    private static final Logger logger = LoggerFactory.getLogger(Hp2392Emulator.class);

    private static final String MODEL = "2392";

    private int myMemoryLockRow = -1;

    private boolean myProtectMode = false;

    private boolean myFormatMode = false;

    private boolean myInsertMode = false;

    public Hp2392Emulator(@NotNull TerminalDataStream dataStream, @NotNull Terminal terminal) {
        super(dataStream, terminal);
    }

    @Override
    @NotNull
    protected String getHpModel() {
        return MODEL;
    }

    // ---------------------------------------------------------------
    //  Character processing – extends the base class to intercept
    //  2392-specific escape sub-commands before falling through.
    // ---------------------------------------------------------------

    @Override
    protected void processHpEscape(Terminal terminal) throws IOException {
        char second = peekNextChar();
        switch (second) {
            case '1':
                consumeChar();
                setBlockMode(true, terminal);
                return;
            case '2':
                consumeChar();
                setBlockMode(false, terminal);
                return;
            case '`':
                consumeChar();
                processMemoryLock(terminal);
                return;
            case 'b':
                consumeChar();
                processMemoryUnlock(terminal);
                return;
            case 'c':
                consumeChar();
                processProtectModeOn(terminal);
                return;
            case 'd':
                consumeChar();
                processProtectModeOff(terminal);
                return;
            default:
                break;
        }
        super.processHpEscape(terminal);
    }

    // ---------------------------------------------------------------
    //  Block mode support
    // ---------------------------------------------------------------

    private void setBlockMode(boolean enabled, Terminal terminal) {
        myBlockMode = enabled;
        if (logger.isDebugEnabled()) {
            logger.debug("[HP-{}] Block mode {}", MODEL, enabled ? "ON" : "OFF");
        }
    }

    /**
     * Returns whether the emulator is currently operating in block mode.
     */
    public boolean isBlockMode() {
        return myBlockMode;
    }

    // ---------------------------------------------------------------
    //  Memory lock (scroll region)
    //  ESC ` – lock memory above current cursor row
    // ---------------------------------------------------------------

    private void processMemoryLock(Terminal terminal) {
        myMemoryLockRow = terminal.getCursorY();
        terminal.setScrollingRegion(myMemoryLockRow + 1, terminal.getTerminalHeight());
        if (logger.isDebugEnabled()) {
            logger.debug("[HP-{}] Memory locked at row {}", MODEL, myMemoryLockRow);
        }
    }

    private void processMemoryUnlock(Terminal terminal) {
        myMemoryLockRow = -1;
        terminal.resetScrollRegions();
        if (logger.isDebugEnabled()) {
            logger.debug("[HP-{}] Memory unlocked", MODEL);
        }
    }

    /**
     * Returns the row at which memory is locked, or {@code -1} if unlocked.
     */
    public int getMemoryLockRow() {
        return myMemoryLockRow;
    }

    // ---------------------------------------------------------------
    //  Protect mode  (field-level protection for block mode forms)
    //  ESC c – protect mode on;  ESC d – protect mode off
    // ---------------------------------------------------------------

    private void processProtectModeOn(Terminal terminal) {
        myProtectMode = true;
        logger.debug("[HP-{}] Protect mode ON", MODEL);
    }

    private void processProtectModeOff(Terminal terminal) {
        myProtectMode = false;
        logger.debug("[HP-{}] Protect mode OFF", MODEL);
    }

    public boolean isProtectMode() {
        return myProtectMode;
    }

    // ---------------------------------------------------------------
    //  Format mode  (forms handling in block mode)
    // ---------------------------------------------------------------

    public boolean isFormatMode() {
        return myFormatMode;
    }

    public void setFormatMode(boolean formatMode) {
        myFormatMode = formatMode;
    }

    // ---------------------------------------------------------------
    //  Insert mode (local editing)
    // ---------------------------------------------------------------

    public boolean isInsertMode() {
        return myInsertMode;
    }

    public void setInsertMode(boolean insertMode) {
        myInsertMode = insertMode;
    }

    // ---------------------------------------------------------------
    //  Utility – peek / consume from data stream
    // ---------------------------------------------------------------

    private char peekNextChar() throws IOException {
        char ch = myDataStream.getChar();
        myDataStream.pushChar(ch);
        return ch;
    }

    private void consumeChar() throws IOException {
        myDataStream.getChar();
    }
}
