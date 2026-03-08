package com.sithtermfx.core.emulator.tn3270;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 3270 screen buffer with field-oriented storage.
 * <p>
 * The buffer is organized as a linear array of {@code rows × cols} character
 * positions. Each position may hold a displayable character and optionally a
 * field attribute that marks the start of a field.
 * <p>
 * Standard geometries are 24×80 (Model 2) and 27×132 (Model 5).
 *
 * @author Daniel Mengel
 */
public class ScreenBuffer3270 {

    private final int myRows;
    private final int myCols;
    private final int mySize;

    private final char[] myBuffer;
    private final FieldAttribute3270[] myAttributes;
    private final List<Field3270> myFields;

    public ScreenBuffer3270(int rows, int cols) {
        myRows = rows;
        myCols = cols;
        mySize = rows * cols;
        myBuffer = new char[mySize];
        myAttributes = new FieldAttribute3270[mySize];
        myFields = new ArrayList<>();
        Arrays.fill(myBuffer, ' ');
    }

    public int getRows() {
        return myRows;
    }

    public int getCols() {
        return myCols;
    }

    public int getSize() {
        return mySize;
    }

    public char getCharAt(int pos) {
        if (pos < 0 || pos >= mySize) return ' ';
        return myBuffer[pos];
    }

    public void setCharAt(int pos, char c) {
        if (pos >= 0 && pos < mySize) {
            myBuffer[pos] = c;
        }
    }

    /**
     * Returns the field attribute at the given position, or {@code null}
     * if this position is not a field start.
     */
    public FieldAttribute3270 getAttributeAt(int pos) {
        if (pos < 0 || pos >= mySize) return null;
        return myAttributes[pos];
    }

    /**
     * Returns the field that contains the given buffer position, or {@code null}
     * if the position is not inside any field.
     */
    public Field3270 getFieldAt(int pos) {
        if (pos < 0 || pos >= mySize) return null;

        for (Field3270 field : myFields) {
            int start = field.getStartPosition();
            int firstData = (start + 1) % mySize;
            int end = (start + field.getLength()) % mySize;

            if (firstData <= end) {
                if (pos >= firstData && pos <= end) return field;
            } else {
                if (pos >= firstData || pos <= end) return field;
            }
        }
        return null;
    }

    /**
     * Returns the field whose attribute is at the given position,
     * or {@code null} if none.
     */
    public Field3270 getFieldStartingAt(int pos) {
        for (Field3270 field : myFields) {
            if (field.getStartPosition() == pos) return field;
        }
        return null;
    }

    /**
     * Adds a field at the given position with the specified attribute.
     * The field length is computed as the distance to the next field start.
     */
    public Field3270 addField(int pos, FieldAttribute3270 attr) {
        pos = pos % mySize;
        myAttributes[pos] = attr;
        myBuffer[pos] = ' ';

        Field3270 field = new Field3270(pos, 0, attr);
        myFields.add(field);
        recalculateFieldLengths();
        return field;
    }

    /**
     * Recalculates all field lengths based on the distance between consecutive
     * field start positions. Must be called after adding or removing fields.
     */
    public void recalculateFieldLengths() {
        if (myFields.isEmpty()) return;

        myFields.sort((a, b) -> Integer.compare(a.getStartPosition(), b.getStartPosition()));

        int fieldCount = myFields.size();
        for (int i = 0; i < fieldCount; i++) {
            Field3270 current = myFields.get(i);
            Field3270 next = myFields.get((i + 1) % fieldCount);

            int start = current.getStartPosition();
            int nextStart = next.getStartPosition();
            int length;
            if (i == fieldCount - 1 && fieldCount > 1) {
                length = (nextStart - start - 1 + mySize) % mySize;
            } else if (fieldCount == 1) {
                length = mySize - 1;
            } else {
                length = (nextStart - start - 1 + mySize) % mySize;
            }
            current.setLength(length);
        }
    }

    /**
     * Clears the entire screen: fills buffer with spaces, removes all fields.
     */
    public void clearScreen() {
        Arrays.fill(myBuffer, ' ');
        Arrays.fill(myAttributes, null);
        myFields.clear();
    }

    /**
     * Converts row/column coordinates to a linear buffer address.
     */
    public int getBufferAddress(int row, int col) {
        return (row * myCols) + col;
    }

    /**
     * Converts a buffer address to the row number (0-based).
     */
    public int getRow(int address) {
        return address / myCols;
    }

    /**
     * Converts a buffer address to the column number (0-based).
     */
    public int getCol(int address) {
        return address % myCols;
    }

    /**
     * Finds the next unprotected field starting from the given position,
     * wrapping around the buffer if necessary.
     *
     * @return the first data position of the next unprotected field, or -1
     */
    public int nextUnprotectedField(int from) {
        if (myFields.isEmpty()) return -1;

        int startSearch = from % mySize;
        int bestPos = -1;
        int bestDist = Integer.MAX_VALUE;

        for (Field3270 field : myFields) {
            if (!field.isProtected()) {
                int fStart = (field.getStartPosition() + 1) % mySize;
                int dist = (fStart - startSearch + mySize) % mySize;
                if (dist == 0) dist = mySize;
                if (dist < bestDist) {
                    bestDist = dist;
                    bestPos = fStart;
                }
            }
        }
        return bestPos;
    }

    /**
     * Finds the previous unprotected field before the given position,
     * wrapping around the buffer if necessary.
     *
     * @return the first data position of the previous unprotected field, or -1
     */
    public int previousUnprotectedField(int from) {
        if (myFields.isEmpty()) return -1;

        int startSearch = from % mySize;
        int bestPos = -1;
        int bestDist = Integer.MAX_VALUE;

        for (Field3270 field : myFields) {
            if (!field.isProtected()) {
                int fStart = (field.getStartPosition() + 1) % mySize;
                int dist = (startSearch - fStart + mySize) % mySize;
                if (dist == 0) dist = mySize;
                if (dist < bestDist) {
                    bestDist = dist;
                    bestPos = fStart;
                }
            }
        }
        return bestPos;
    }

    /**
     * Returns all fields whose MDT (Modified Data Tag) is set.
     */
    public List<Field3270> getAllModifiedFields() {
        List<Field3270> modified = new ArrayList<>();
        for (Field3270 field : myFields) {
            if (field.isMdt()) {
                modified.add(field);
            }
        }
        return Collections.unmodifiableList(modified);
    }

    /**
     * @return an unmodifiable view of all fields
     */
    public List<Field3270> getFields() {
        return Collections.unmodifiableList(myFields);
    }

    /**
     * @return the raw character buffer (for direct access by the emulator)
     */
    public char[] getRawBuffer() {
        return myBuffer;
    }

    /**
     * @return the raw attribute array (for direct access by the emulator)
     */
    public FieldAttribute3270[] getRawAttributes() {
        return myAttributes;
    }

    /**
     * Returns the field attribute that governs the given position.
     * This scans backwards from pos to find the nearest field start.
     */
    public FieldAttribute3270 getEffectiveAttribute(int pos) {
        for (int i = 0; i < mySize; i++) {
            int check = (pos - i + mySize) % mySize;
            if (myAttributes[check] != null) {
                return myAttributes[check];
            }
        }
        return null;
    }

    /**
     * @return the first unprotected field, or -1 if none
     */
    public int getFirstUnprotectedField() {
        return nextUnprotectedField(0);
    }
}
