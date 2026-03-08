package com.sithtermfx.core.emulator.tn5250;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Models the 5250 display screen buffer.
 * <p>
 * The screen consists of character cells arranged in rows and columns (typically 24x80 or 27x132),
 * overlaid with an ordered list of field definitions. The buffer supports direct character access,
 * field-level navigation, and the command/status indicator area.
 *
 * @author Daniel Mengel
 */
public class ScreenBuffer5250 {

    public static final int DEFAULT_ROWS = 24;
    public static final int DEFAULT_COLS = 80;

    private final int rows;
    private final int cols;
    private final char[][] screenData;
    private final List<Field5250> fields;

    private int cursorRow;
    private int cursorCol;

    private int commandIndicator;
    private boolean messageWaiting;
    private boolean keyboardLocked;
    private boolean inputInhibited;
    private boolean insertMode;
    private int errorLine;

    public ScreenBuffer5250(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.screenData = new char[rows][cols];
        this.fields = new ArrayList<>();
        this.cursorRow = 1;
        this.cursorCol = 1;
        this.errorLine = rows;
        clearScreen();
    }

    public ScreenBuffer5250() {
        this(DEFAULT_ROWS, DEFAULT_COLS);
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public int getCursorRow() {
        return cursorRow;
    }

    public void setCursorRow(int cursorRow) {
        this.cursorRow = Math.max(1, Math.min(cursorRow, rows));
    }

    public int getCursorCol() {
        return cursorCol;
    }

    public void setCursorCol(int cursorCol) {
        this.cursorCol = Math.max(1, Math.min(cursorCol, cols));
    }

    public void setCursorPosition(int row, int col) {
        setCursorRow(row);
        setCursorCol(col);
    }

    /**
     * Returns the character at the given 1-based row and column.
     */
    public char getCharAt(int row, int col) {
        if (row < 1 || row > rows || col < 1 || col > cols) {
            return ' ';
        }
        return screenData[row - 1][col - 1];
    }

    /**
     * Sets the character at the given 1-based row and column.
     */
    public void setCharAt(int row, int col, char c) {
        if (row >= 1 && row <= rows && col >= 1 && col <= cols) {
            screenData[row - 1][col - 1] = c;
        }
    }

    /**
     * Returns the field that contains the given 1-based position, or {@code null} if no field
     * covers that position.
     */
    public Field5250 getFieldAt(int row, int col) {
        int linearPos = (row - 1) * cols + (col - 1);
        for (Field5250 field : fields) {
            int fieldStart = field.getBufferAddress(cols);
            int fieldEnd = fieldStart + field.getLength() - 1;
            if (linearPos >= fieldStart && linearPos <= fieldEnd) {
                return field;
            }
        }
        return null;
    }

    /**
     * Adds a field to the screen buffer.
     */
    public void addField(Field5250 field) {
        fields.add(field);
    }

    /**
     * Returns an unmodifiable view of all fields.
     */
    public List<Field5250> getFields() {
        return Collections.unmodifiableList(fields);
    }

    /**
     * Clears all character data to spaces.
     */
    public void clearScreen() {
        for (char[] row : screenData) {
            Arrays.fill(row, ' ');
        }
    }

    /**
     * Clears both the screen data and all field definitions.
     */
    public void clearFormatTable() {
        clearScreen();
        fields.clear();
        cursorRow = 1;
        cursorCol = 1;
        commandIndicator = 0;
        messageWaiting = false;
        keyboardLocked = false;
        inputInhibited = false;
        insertMode = false;
    }

    /**
     * Returns the next input (non-bypass) field after the given 1-based position,
     * wrapping around if needed. Returns {@code null} if there are no input fields.
     */
    public Field5250 getNextInputField(int row, int col) {
        if (fields.isEmpty()) {
            return null;
        }
        int linearPos = (row - 1) * cols + (col - 1);
        Field5250 firstInput = null;
        Field5250 nextAfter = null;

        for (Field5250 field : fields) {
            if (field.isBypass()) {
                continue;
            }
            if (firstInput == null) {
                firstInput = field;
            }
            int fieldStart = field.getBufferAddress(cols);
            if (fieldStart > linearPos && nextAfter == null) {
                nextAfter = field;
            }
        }
        return nextAfter != null ? nextAfter : firstInput;
    }

    /**
     * Returns the previous input (non-bypass) field before the given 1-based position,
     * wrapping around if needed. Returns {@code null} if there are no input fields.
     */
    public Field5250 getPreviousInputField(int row, int col) {
        if (fields.isEmpty()) {
            return null;
        }
        int linearPos = (row - 1) * cols + (col - 1);
        Field5250 lastInput = null;
        Field5250 prevBefore = null;

        for (Field5250 field : fields) {
            if (field.isBypass()) {
                continue;
            }
            lastInput = field;
            int fieldStart = field.getBufferAddress(cols);
            if (fieldStart < linearPos) {
                prevBefore = field;
            }
        }
        return prevBefore != null ? prevBefore : lastInput;
    }

    /**
     * Returns the first input (non-bypass) field, or {@code null} if none exist.
     */
    public Field5250 getFirstInputField() {
        for (Field5250 field : fields) {
            if (!field.isBypass()) {
                return field;
            }
        }
        return null;
    }

    /**
     * Returns all fields that have been modified since the last transmission.
     */
    public List<Field5250> getModifiedFields() {
        List<Field5250> result = new ArrayList<>();
        for (Field5250 field : fields) {
            if (field.isModified() && field.isInputField()) {
                result.add(field);
            }
        }
        return result;
    }

    /**
     * Resets the modified flag on all fields.
     */
    public void resetModifiedFlags() {
        for (Field5250 field : fields) {
            field.setModified(false);
        }
    }

    /**
     * Returns the content of a single screen row as a String (1-based row number).
     */
    public String getRowText(int row) {
        if (row < 1 || row > rows) {
            return "";
        }
        return new String(screenData[row - 1]);
    }

    /**
     * Writes field content into the screen character buffer for all defined fields.
     */
    public void syncFieldsToScreen() {
        for (Field5250 field : fields) {
            int r = field.getRow();
            int c = field.getCol();
            char[] content = field.getContent();
            for (int i = 0; i < field.getLength(); i++) {
                int col0 = c + i;
                int row0 = r;
                while (col0 > cols) {
                    col0 -= cols;
                    row0++;
                }
                setCharAt(row0, col0, content[i]);
            }
        }
    }

    /**
     * Scrolls the screen up by the given number of lines, filling new lines with spaces.
     */
    public void scrollUp(int lines) {
        if (lines <= 0) {
            return;
        }
        int effective = Math.min(lines, rows);
        for (int r = 0; r < rows - effective; r++) {
            System.arraycopy(screenData[r + effective], 0, screenData[r], 0, cols);
        }
        for (int r = rows - effective; r < rows; r++) {
            Arrays.fill(screenData[r], ' ');
        }
        Iterator<Field5250> it = fields.iterator();
        while (it.hasNext()) {
            Field5250 field = it.next();
            int newRow = field.getRow() - effective;
            if (newRow < 1) {
                it.remove();
            } else {
                field.setRow(newRow);
            }
        }
    }

    /**
     * Scrolls the screen down by the given number of lines, filling new lines with spaces.
     */
    public void scrollDown(int lines) {
        if (lines <= 0) {
            return;
        }
        int effective = Math.min(lines, rows);
        for (int r = rows - 1; r >= effective; r--) {
            System.arraycopy(screenData[r - effective], 0, screenData[r], 0, cols);
        }
        for (int r = 0; r < effective; r++) {
            Arrays.fill(screenData[r], ' ');
        }
        Iterator<Field5250> it = fields.iterator();
        while (it.hasNext()) {
            Field5250 field = it.next();
            int newRow = field.getRow() + effective;
            if (newRow > rows) {
                it.remove();
            } else {
                field.setRow(newRow);
            }
        }
    }

    // Command/status indicator support

    public int getErrorLine() {
        return errorLine;
    }

    public void setErrorLine(int errorLine) {
        this.errorLine = errorLine;
    }

    public int getCommandIndicator() {
        return commandIndicator;
    }

    public void setCommandIndicator(int commandIndicator) {
        this.commandIndicator = commandIndicator;
    }

    public boolean isMessageWaiting() {
        return messageWaiting;
    }

    public void setMessageWaiting(boolean messageWaiting) {
        this.messageWaiting = messageWaiting;
    }

    public boolean isKeyboardLocked() {
        return keyboardLocked;
    }

    public void setKeyboardLocked(boolean keyboardLocked) {
        this.keyboardLocked = keyboardLocked;
    }

    public boolean isInputInhibited() {
        return inputInhibited;
    }

    public void setInputInhibited(boolean inputInhibited) {
        this.inputInhibited = inputInhibited;
    }

    public boolean isInsertMode() {
        return insertMode;
    }

    public void setInsertMode(boolean insertMode) {
        this.insertMode = insertMode;
    }
}
