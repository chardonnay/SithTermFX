package com.sithtermfx.core.emulator.tn3270;

import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalColor;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.emulator.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Block-mode 3270 terminal emulator.
 * <p>
 * Unlike character-mode emulators that extend {@code DataStreamIteratingEmulator},
 * this emulator operates in block mode: it reads complete 3270 records from a
 * {@link Tn3270TtyConnector}, processes them via {@link DataStream3270}, and then
 * renders the resulting screen buffer to the {@link Terminal}.
 * <p>
 * Keyboard input is collected locally until an AID key is pressed, at which point
 * a read response is built from modified fields and sent to the host.
 *
 * @author Daniel Mengel
 */
public class Tn3270Emulator implements Emulator {

    private static final Logger LOG = LoggerFactory.getLogger(Tn3270Emulator.class);

    private static final TextStyle STYLE_NORMAL = TextStyle.EMPTY;
    private static final TextStyle STYLE_INTENSIFIED = new TextStyle(
            TerminalColor.index(15), null);
    private static final TextStyle STYLE_HIDDEN = new TextStyle(
            TerminalColor.index(0), TerminalColor.index(0));
    private static final TextStyle STYLE_PROTECTED = new TextStyle(
            TerminalColor.index(6), null);

    private Tn3270TtyConnector myConnector;
    private final Terminal myTerminal;
    private final ScreenBuffer3270 myScreen;

    private int myCursorAddress = 0;
    private boolean myEof = false;
    private boolean myKeyboardLocked = true;

    /**
     * @param connector the TN3270 connector to read records from
     * @param terminal  the terminal to render screen content to
     * @param rows      number of screen rows (typically 24 or 27)
     * @param cols      number of screen columns (typically 80 or 132)
     */
    public Tn3270Emulator(Tn3270TtyConnector connector, Terminal terminal, int rows, int cols) {
        myConnector = connector;
        myTerminal = terminal;
        myScreen = new ScreenBuffer3270(rows, cols);
    }

    /**
     * Convenience constructor for the standard Model 2 (24x80) screen.
     */
    public Tn3270Emulator(Tn3270TtyConnector connector, Terminal terminal) {
        this(connector, terminal, 24, 80);
    }

    /**
     * Constructor for the {@link com.sithtermfx.core.emulator.EmulatorFactory}.
     * The connector must be set later via the widget before starting the emulator.
     */
    public Tn3270Emulator(com.sithtermfx.core.TerminalDataStream dataStream, Terminal terminal) {
        myConnector = null;
        myTerminal = terminal;
        myScreen = new ScreenBuffer3270(terminal.getTerminalHeight(), terminal.getTerminalWidth());
    }

    public void setConnector(Tn3270TtyConnector connector) {
        myConnector = connector;
    }

    @Override
    public boolean hasNext() {
        return !myEof && myConnector != null && myConnector.isConnected();
    }

    @Override
    public void next() throws IOException {
        try {
            byte[] record = myConnector.readRecord();
            if (record == null) {
                myEof = true;
                myTerminal.disconnected();
                return;
            }

            DataStream3270.ProcessResult result = DataStream3270.processRecord(record, myScreen);
            myScreen.recalculateFieldLengths();

            if (result.getCursorAddress() >= 0) {
                myCursorAddress = result.getCursorAddress();
            }

            if (result.isSoundAlarm()) {
                myTerminal.beep();
            }

            if (result.isKeyboardRestore()) {
                myKeyboardLocked = false;
            }

            renderScreen();
            positionCursor();
        } catch (IOException e) {
            LOG.error("Error reading 3270 record", e);
            myEof = true;
            myTerminal.disconnected();
        }
    }

    @Override
    public void resetEof() {
        myEof = false;
    }

    /**
     * Renders the entire screen buffer content to the terminal.
     * Each line is written with appropriate character attributes based on
     * the field attributes (protected, intensified, hidden).
     */
    public void renderScreen() {
        myTerminal.eraseInDisplay(2);

        int rows = myScreen.getRows();
        int cols = myScreen.getCols();

        for (int row = 0; row < rows; row++) {
            myTerminal.cursorPosition(1, row + 1);
            StringBuilder lineBuilder = new StringBuilder();
            TextStyle activeStyle = STYLE_NORMAL;
            TextStyle segmentStyle = null;

            for (int col = 0; col < cols; col++) {
                int pos = myScreen.getBufferAddress(row, col);
                FieldAttribute3270 attrAtPos = myScreen.getAttributeAt(pos);

                if (attrAtPos != null) {
                    if (lineBuilder.length() > 0 && segmentStyle != null) {
                        myTerminal.characterAttributes(segmentStyle);
                        myTerminal.writeCharacters(lineBuilder.toString());
                        lineBuilder.setLength(0);
                    }
                    activeStyle = attributeToStyle(attrAtPos);
                    segmentStyle = activeStyle;
                    lineBuilder.append(' ');
                    continue;
                }

                FieldAttribute3270 effAttr = myScreen.getEffectiveAttribute(pos);
                TextStyle cellStyle = effAttr != null ? attributeToStyle(effAttr) : STYLE_NORMAL;

                if (!cellStyle.equals(segmentStyle) && lineBuilder.length() > 0) {
                    if (segmentStyle != null) {
                        myTerminal.characterAttributes(segmentStyle);
                    }
                    myTerminal.writeCharacters(lineBuilder.toString());
                    lineBuilder.setLength(0);
                }

                segmentStyle = cellStyle;
                lineBuilder.append(myScreen.getCharAt(pos));
            }

            if (lineBuilder.length() > 0) {
                if (segmentStyle != null) {
                    myTerminal.characterAttributes(segmentStyle);
                }
                myTerminal.writeCharacters(lineBuilder.toString());
            }
        }

        myTerminal.characterAttributes(STYLE_NORMAL);
    }

    private TextStyle attributeToStyle(FieldAttribute3270 attr) {
        if (attr.isHidden()) return STYLE_HIDDEN;
        if (attr.isIntensified()) return STYLE_INTENSIFIED;
        if (attr.isProtected()) return STYLE_PROTECTED;
        return STYLE_NORMAL;
    }

    private void positionCursor() {
        int row = myScreen.getRow(myCursorAddress);
        int col = myScreen.getCol(myCursorAddress);
        myTerminal.cursorPosition(col + 1, row + 1);
    }

    /**
     * Processes an AID key press. Constructs a read response from the current
     * screen state and sends it to the host via the connector.
     *
     * @param aid the AID byte (see {@link Aid3270})
     */
    public void processAidKey(int aid) throws IOException {
        if (myKeyboardLocked && aid != Aid3270.ATTN && aid != Aid3270.SYSREQ) {
            myTerminal.beep();
            return;
        }

        myKeyboardLocked = true;

        if (aid == Aid3270.ATTN) {
            myConnector.write(new byte[]{
                    (byte) TelnetConstants.IAC, (byte) TelnetConstants.IP
            });
            return;
        }

        if (aid == Aid3270.CLEAR) {
            myScreen.clearScreen();
            myCursorAddress = 0;
            renderScreen();
            positionCursor();
        }

        syncFieldsFromBuffer();

        byte[] response = DataStream3270.buildReadModifiedResponse(aid, myCursorAddress, myScreen);
        myConnector.writeRecord(response);
    }

    /**
     * Synchronizes field content arrays with the screen buffer positions,
     * ensuring the MDT reflects actual changes.
     */
    private void syncFieldsFromBuffer() {
        for (Field3270 field : myScreen.getFields()) {
            if (field.isProtected()) continue;
            int start = field.getFirstDataPosition();
            for (int i = 0; i < field.getLength(); i++) {
                int bufPos = (start + i) % myScreen.getSize();
                field.setCharAt(i, myScreen.getCharAt(bufPos));
            }
        }
    }

    /**
     * Types a character at the current cursor position in the current
     * unprotected field.
     */
    public void typeCharacter(char c) {
        if (myKeyboardLocked) {
            myTerminal.beep();
            return;
        }

        FieldAttribute3270 attr = myScreen.getEffectiveAttribute(myCursorAddress);
        if (attr != null && attr.isProtected()) {
            myTerminal.beep();
            return;
        }

        myScreen.setCharAt(myCursorAddress, c);
        Field3270 field = myScreen.getFieldAt(myCursorAddress);
        if (field != null) {
            field.getAttribute().setMdt(true);
        }

        myCursorAddress = (myCursorAddress + 1) % myScreen.getSize();

        FieldAttribute3270 nextAttr = myScreen.getAttributeAt(myCursorAddress);
        if (nextAttr != null) {
            int next = myScreen.nextUnprotectedField(myCursorAddress);
            if (next >= 0) {
                myCursorAddress = next;
            }
        }

        renderScreen();
        positionCursor();
    }

    /**
     * Deletes the character at the current cursor position, shifting remaining
     * field content left.
     */
    public void deleteCharacter() {
        if (myKeyboardLocked) return;

        Field3270 field = myScreen.getFieldAt(myCursorAddress);
        if (field == null || field.isProtected()) {
            myTerminal.beep();
            return;
        }

        int fieldStart = field.getFirstDataPosition();
        int fieldEnd = (fieldStart + field.getLength() - 1) % myScreen.getSize();
        int offset = (myCursorAddress - fieldStart + myScreen.getSize()) % myScreen.getSize();

        for (int i = offset; i < field.getLength() - 1; i++) {
            int from = (fieldStart + i + 1) % myScreen.getSize();
            int to = (fieldStart + i) % myScreen.getSize();
            myScreen.setCharAt(to, myScreen.getCharAt(from));
        }
        myScreen.setCharAt(fieldEnd, ' ');
        field.getAttribute().setMdt(true);

        renderScreen();
        positionCursor();
    }

    /**
     * Erases the character to the left of the cursor (backspace).
     */
    public void eraseCharacter() {
        if (myKeyboardLocked) return;

        Field3270 field = myScreen.getFieldAt(myCursorAddress);
        if (field == null || field.isProtected()) {
            myTerminal.beep();
            return;
        }

        int fieldStart = field.getFirstDataPosition();
        if (myCursorAddress == fieldStart) return;

        myCursorAddress = (myCursorAddress - 1 + myScreen.getSize()) % myScreen.getSize();
        deleteCharacter();
    }

    /**
     * Moves the cursor to the next unprotected field.
     */
    public void tabForward() {
        int next = myScreen.nextUnprotectedField(myCursorAddress);
        if (next >= 0) {
            myCursorAddress = next;
            positionCursor();
        }
    }

    /**
     * Moves the cursor to the previous unprotected field.
     */
    public void tabBackward() {
        int prev = myScreen.previousUnprotectedField(myCursorAddress);
        if (prev >= 0) {
            myCursorAddress = prev;
            positionCursor();
        }
    }

    /**
     * Moves the cursor to the first unprotected field on the screen.
     */
    public void home() {
        int first = myScreen.getFirstUnprotectedField();
        if (first >= 0) {
            myCursorAddress = first;
            positionCursor();
        }
    }

    /**
     * Resets the keyboard lock and error state.
     */
    public void resetKeyboard() {
        myKeyboardLocked = false;
    }

    public ScreenBuffer3270 getScreen() {
        return myScreen;
    }

    public int getCursorAddress() {
        return myCursorAddress;
    }

    public void setCursorAddress(int address) {
        myCursorAddress = address % myScreen.getSize();
    }

    public boolean isKeyboardLocked() {
        return myKeyboardLocked;
    }
}
