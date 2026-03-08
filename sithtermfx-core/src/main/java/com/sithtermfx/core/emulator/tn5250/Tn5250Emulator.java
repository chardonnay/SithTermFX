package com.sithtermfx.core.emulator.tn5250;

import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalColor;
import com.sithtermfx.core.TerminalDataStream;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.emulator.Emulator;
import com.sithtermfx.core.emulator.tn3270.EbcdicCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 5250 block-mode terminal emulator.
 * <p>
 * Unlike stream-mode emulators that extend {@code DataStreamIteratingEmulator}, this emulator
 * reads complete 5250 records from a {@link Tn5250TtyConnector}, parses them via
 * {@link DataStream5250}, and renders the resulting screen buffer to the {@link Terminal}.
 *
 * @author Daniel Mengel
 */
public class Tn5250Emulator implements Emulator {

    private static final Logger logger = LoggerFactory.getLogger(Tn5250Emulator.class);

    private static final TextStyle NORMAL_STYLE = TextStyle.EMPTY;
    private static final TextStyle FIELD_STYLE = new TextStyle(
            TerminalColor.index(2), null);
    private static final TextStyle PROTECTED_STYLE = new TextStyle(
            TerminalColor.index(6), null);
    private static final TextStyle STATUS_STYLE = new TextStyle(
            TerminalColor.index(3), null);
    private static final TextStyle ERROR_STYLE = new TextStyle(
            TerminalColor.index(1), null);

    private final TerminalDataStream myDataStream;
    private final Terminal myTerminal;

    private final ScreenBuffer5250 myScreen;
    private Tn5250TtyConnector myConnector;
    private boolean myEof;

    private int pendingAid;
    private boolean queryPending;

    public Tn5250Emulator(TerminalDataStream dataStream, Terminal terminal) {
        myDataStream = dataStream;
        myTerminal = terminal;
        myScreen = new ScreenBuffer5250(
                terminal.getTerminalHeight(),
                terminal.getTerminalWidth()
        );
        myEof = false;
        pendingAid = -1;
    }

    /**
     * Attaches the TN5250 connector used to send/receive records.
     */
    public void setConnector(Tn5250TtyConnector connector) {
        this.myConnector = connector;
    }

    public ScreenBuffer5250 getScreen() {
        return myScreen;
    }

    @Override
    public boolean hasNext() {
        return !myEof;
    }

    @Override
    public void next() throws IOException {
        try {
            if (myConnector == null) {
                char c = myDataStream.getChar();
                processInbandChar(c);
                return;
            }

            byte[] record = myConnector.readRecord();
            if (record == null) {
                myEof = true;
                return;
            }

            DataStream5250.processRecord(record, myScreen);
            myScreen.syncFieldsToScreen();
            renderScreen();

            if (queryPending) {
                sendQueryReply();
                queryPending = false;
            }

        } catch (TerminalDataStream.EOF e) {
            myEof = true;
        }
    }

    @Override
    public void resetEof() {
        myEof = false;
    }

    private int getFieldOffset(Field5250 field, int row, int col) {
        int cursorAddr = (row - 1) * myScreen.getCols() + (col - 1);
        int offset = cursorAddr - field.getBufferAddress(myScreen.getCols());
        if (offset >= 0 && offset < field.getLength()) {
            return offset;
        }
        return -1;
    }

    /**
     * Processes a single character received through the TerminalDataStream
     * fallback path (used when no connector is attached).
     */
    private void processInbandChar(char c) throws IOException {
        int row = myScreen.getCursorRow();
        int col = myScreen.getCursorCol();

        Field5250 field = myScreen.getFieldAt(row, col);
        if (field != null && field.isInputField()) {
            int offset = getFieldOffset(field, row, col);
            if (offset >= 0) {
                field.setCharAt(offset, c);
                field.setModified(true);
            }
        }
        myScreen.setCharAt(row, col, c);
        advanceCursorInField();
    }

    /**
     * Renders the full screen buffer to the terminal display.
     */
    public void renderScreen() {
        myTerminal.eraseInDisplay(2);

        for (int r = 1; r <= myScreen.getRows(); r++) {
            myTerminal.cursorPosition(1, r);

            if (r == myScreen.getErrorLine() && myScreen.isKeyboardLocked()) {
                myTerminal.characterAttributes(ERROR_STYLE);
                myTerminal.writeCharacters(myScreen.getRowText(r));
                continue;
            }

            int col = 1;
            while (col <= myScreen.getCols()) {
                Field5250 field = myScreen.getFieldAt(r, col);
                if (field != null && field.getRow() == r && field.getCol() == col) {
                    TextStyle style = field.isProtected() ? PROTECTED_STYLE : FIELD_STYLE;
                    myTerminal.characterAttributes(style);

                    int fieldEnd = Math.min(col + field.getLength() - 1, myScreen.getCols());
                    StringBuilder sb = new StringBuilder(fieldEnd - col + 1);
                    for (int c = col; c <= fieldEnd; c++) {
                        sb.append(myScreen.getCharAt(r, c));
                    }
                    myTerminal.writeCharacters(sb.toString());
                    col = fieldEnd + 1;
                } else {
                    myTerminal.characterAttributes(NORMAL_STYLE);
                    myTerminal.writeCharacters(String.valueOf(myScreen.getCharAt(r, col)));
                    col++;
                }
            }
        }

        renderStatusLine();

        myTerminal.cursorPosition(myScreen.getCursorCol(), myScreen.getCursorRow());
        myTerminal.characterAttributes(NORMAL_STYLE);
    }

    private void renderStatusLine() {
        // Status indicators are not rendered as a separate line in 5250;
        // but we can show a message-waiting indicator if active.
        if (myScreen.isMessageWaiting()) {
            myTerminal.cursorPosition(myScreen.getCols() - 1, myScreen.getRows());
            myTerminal.characterAttributes(STATUS_STYLE);
            myTerminal.writeCharacters("MW");
        }
    }

    // AID key handling

    /**
     * Processes an AID (Attention IDentifier) key press.
     * Constructs a response record from modified fields and sends it to the host.
     *
     * @param aid the AID code from {@link Command5250}
     */
    public void processAidKey(int aid) {
        if (myScreen.isKeyboardLocked() && aid != Command5250.AID_ATTN && aid != Command5250.AID_SYSREQ) {
            myTerminal.beep();
            return;
        }

        pendingAid = aid;

        if (aid == Command5250.AID_ATTN) {
            sendAttention();
            return;
        }
        if (aid == Command5250.AID_SYSREQ) {
            sendSystemRequest();
            return;
        }

        myScreen.setKeyboardLocked(true);
        myScreen.setInputInhibited(true);

        byte[] response = DataStream5250.buildReadResponse(
                myScreen, aid, myScreen.getCursorRow(), myScreen.getCursorCol());

        try {
            if (myConnector != null) {
                myConnector.writeRecord(response);
            }
        } catch (IOException e) {
            logger.error("Error sending AID response", e);
            myEof = true;
        }
    }

    private void sendAttention() {
        try {
            if (myConnector != null) {
                byte[] attn = new byte[]{
                        0x00, 0x05,
                        0x12,
                        (byte) 0xA0,
                        0x00
                };
                myConnector.writeRecord(attn);
            }
        } catch (IOException e) {
            logger.error("Error sending ATTN", e);
        }
    }

    private void sendSystemRequest() {
        try {
            if (myConnector != null) {
                byte[] sysReq = new byte[]{
                        0x00, 0x05,
                        0x12,
                        (byte) 0x80,
                        0x00
                };
                myConnector.writeRecord(sysReq);
            }
        } catch (IOException e) {
            logger.error("Error sending SysReq", e);
        }
    }

    private void sendQueryReply() {
        try {
            if (myConnector != null) {
                byte[] reply = DataStream5250.buildQueryReply(myScreen.getRows(), myScreen.getCols());
                myConnector.writeRecord(reply);
            }
        } catch (IOException e) {
            logger.error("Error sending query reply", e);
        }
    }

    // Field-level keyboard handling

    /**
     * Handles a displayable character typed by the user.
     */
    public void handleCharacterInput(char c) {
        if (myScreen.isKeyboardLocked()) {
            myTerminal.beep();
            return;
        }

        int row = myScreen.getCursorRow();
        int col = myScreen.getCursorCol();
        Field5250 field = myScreen.getFieldAt(row, col);

        if (field == null || field.isProtected()) {
            myTerminal.beep();
            return;
        }

        if (field.isNumericOnly() && !Character.isDigit(c) && c != '-' && c != '.' && c != ',') {
            myTerminal.beep();
            return;
        }
        if (field.isAlphaOnly() && Character.isDigit(c)) {
            myTerminal.beep();
            return;
        }

        int offset = getFieldOffset(field, row, col);
        if (offset < 0) {
            myTerminal.beep();
            return;
        }
        if (myScreen.isInsertMode()) {
            shiftFieldRight(field, offset);
        }

        field.setCharAt(offset, c);
        myScreen.setCharAt(row, col, c);
        advanceCursorInField();
        renderScreen();
    }

    /**
     * Handles the Tab key — moves to the next input field.
     */
    public void handleTab() {
        Field5250 next = myScreen.getNextInputField(myScreen.getCursorRow(), myScreen.getCursorCol());
        if (next != null) {
            myScreen.setCursorPosition(next.getRow(), next.getCol());
            renderScreen();
        }
    }

    /**
     * Handles Shift+Tab — moves to the previous input field.
     */
    public void handleBackTab() {
        Field5250 prev = myScreen.getPreviousInputField(myScreen.getCursorRow(), myScreen.getCursorCol());
        if (prev != null) {
            myScreen.setCursorPosition(prev.getRow(), prev.getCol());
            renderScreen();
        }
    }

    /**
     * Handles the Home key — moves to the first input field.
     */
    public void handleHome() {
        Field5250 first = myScreen.getFirstInputField();
        if (first != null) {
            myScreen.setCursorPosition(first.getRow(), first.getCol());
            renderScreen();
        }
    }

    /**
     * Handles the Delete key — removes the character at the cursor and shifts field left.
     */
    public void handleDelete() {
        if (myScreen.isKeyboardLocked()) {
            return;
        }
        int row = myScreen.getCursorRow();
        int col = myScreen.getCursorCol();
        Field5250 field = myScreen.getFieldAt(row, col);

        if (field == null || field.isProtected()) {
            myTerminal.beep();
            return;
        }

        int offset = getFieldOffset(field, row, col);
        if (offset < 0) {
            return;
        }
        shiftFieldLeft(field, offset);
        renderScreen();
    }

    /**
     * Handles the Backspace key — moves cursor left and deletes.
     */
    public void handleBackspace() {
        if (myScreen.isKeyboardLocked()) {
            return;
        }
        int row = myScreen.getCursorRow();
        int col = myScreen.getCursorCol();
        Field5250 field = myScreen.getFieldAt(row, col);

        if (field == null || field.isProtected()) {
            myTerminal.beep();
            return;
        }

        int offset = getFieldOffset(field, row, col);
        if (offset <= 0) {
            myTerminal.beep();
            return;
        }

        retreatCursorInField();
        offset--;
        shiftFieldLeft(field, offset);
        renderScreen();
    }

    /**
     * Handles Field Exit — right-adjusts numeric fields and advances to next field.
     */
    public void handleFieldExit() {
        if (myScreen.isKeyboardLocked()) {
            return;
        }
        int row = myScreen.getCursorRow();
        int col = myScreen.getCursorCol();
        Field5250 field = myScreen.getFieldAt(row, col);

        if (field != null && field.isInputField()) {
            if (field.isNumericOnly() || field.isNumericShift()) {
                rightAdjustField(field);
            }
            int offset = getFieldOffset(field, row, col);
            if (offset < 0) offset = 0;
            for (int i = offset; i < field.getLength(); i++) {
                field.setCharAt(i, ' ');
            }
            field.setModified(true);
        }
        handleTab();
    }

    /**
     * Handles Field+ (positive sign for numeric fields).
     */
    public void handleFieldPlus() {
        handleFieldExit();
    }

    /**
     * Handles Dup key — fills the field with DUP characters and advances.
     */
    public void handleDup() {
        if (myScreen.isKeyboardLocked()) {
            return;
        }
        int row = myScreen.getCursorRow();
        int col = myScreen.getCursorCol();
        Field5250 field = myScreen.getFieldAt(row, col);

        if (field != null && field.isInputField() && field.isDupEnabled()) {
            char dupChar = EbcdicCodec.ebcdicToUnicode(0x1C);
            for (int i = 0; i < field.getLength(); i++) {
                field.setCharAt(i, dupChar);
            }
            field.setModified(true);
            handleTab();
        } else {
            myTerminal.beep();
        }
    }

    /**
     * Handles Erase Input — clears all input fields.
     */
    public void handleEraseInput() {
        if (myScreen.isKeyboardLocked()) {
            return;
        }
        for (Field5250 field : myScreen.getFields()) {
            if (field.isInputField()) {
                field.clear();
            }
        }
        myScreen.syncFieldsToScreen();

        Field5250 first = myScreen.getFirstInputField();
        if (first != null) {
            myScreen.setCursorPosition(first.getRow(), first.getCol());
        }
        renderScreen();
    }

    // Cursor movement helpers

    private void advanceCursorInField() {
        int col = myScreen.getCursorCol() + 1;
        int row = myScreen.getCursorRow();
        if (col > myScreen.getCols()) {
            col = 1;
            row++;
            if (row > myScreen.getRows()) {
                row = 1;
            }
        }
        myScreen.setCursorPosition(row, col);
    }

    private void retreatCursorInField() {
        int col = myScreen.getCursorCol() - 1;
        int row = myScreen.getCursorRow();
        if (col < 1) {
            col = myScreen.getCols();
            row--;
            if (row < 1) {
                row = myScreen.getRows();
            }
        }
        myScreen.setCursorPosition(row, col);
    }

    private void shiftFieldLeft(Field5250 field, int fromOffset) {
        char[] content = field.getContent();
        for (int i = fromOffset; i < field.getLength() - 1; i++) {
            content[i] = content[i + 1];
        }
        content[field.getLength() - 1] = ' ';
        field.setModified(true);
        myScreen.syncFieldsToScreen();
    }

    private void shiftFieldRight(Field5250 field, int fromOffset) {
        char[] content = field.getContent();
        for (int i = field.getLength() - 1; i > fromOffset; i--) {
            content[i] = content[i - 1];
        }
        content[fromOffset] = ' ';
        field.setModified(true);
    }

    private void rightAdjustField(Field5250 field) {
        String text = field.getText().stripTrailing();
        if (text.length() < field.getLength()) {
            StringBuilder sb = new StringBuilder(field.getLength());
            for (int i = 0; i < field.getLength() - text.length(); i++) {
                sb.append(' ');
            }
            sb.append(text);
            field.setText(sb.toString());
        }
    }
}
