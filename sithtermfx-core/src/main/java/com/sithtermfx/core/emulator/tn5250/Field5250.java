package com.sithtermfx.core.emulator.tn5250;

import java.util.Arrays;

/**
 * Represents a single field on a 5250 display screen.
 * <p>
 * A field is defined by its position (1-based row/column), its length, and a pair of
 * attribute words: the Field Format Word (FFW) and the Field Control Word (FCW).
 * The FFW encodes protection, data type, shift, and other characteristics. The FCW
 * controls validation, mandatory-entry/fill, and cursor progression.
 *
 * @author Daniel Mengel
 */
public class Field5250 {

    // FFW bit masks (first byte of the two-byte FFW)
    private static final int FFW_BYPASS       = 0x2000;
    private static final int FFW_DUP_ENABLE   = 0x1000;
    private static final int FFW_MOD_DATA_TAG = 0x0800;
    private static final int FFW_SHIFT_MASK   = 0x0700;
    private static final int FFW_AUTO_ENTER   = 0x0080;
    private static final int FFW_FER          = 0x0040;
    private static final int FFW_MONOCASE     = 0x0020;
    private static final int FFW_MANDATORY_ENTRY = 0x0008;
    private static final int FFW_MANDATORY_FILL  = 0x0004;

    // FFW shift values (bits 8-10)
    private static final int SHIFT_ALPHA_ONLY     = 0x0000;
    private static final int SHIFT_ALPHA_SHIFT    = 0x0100;
    private static final int SHIFT_NUMERIC_SHIFT  = 0x0200;
    private static final int SHIFT_NUMERIC_ONLY   = 0x0300;
    private static final int SHIFT_KATA           = 0x0400;
    private static final int SHIFT_DIGITS_ONLY    = 0x0500;
    private static final int SHIFT_IO_FEATURE     = 0x0600;
    private static final int SHIFT_SIGNED_NUMERIC = 0x0700;

    // FCW command codes (first byte selects the function)
    public static final int FCW_RESEQUENCE   = 0x80;
    public static final int FCW_MSR          = 0x81;
    public static final int FCW_CURSOR_PROG  = 0x88;
    public static final int FCW_ENTRY_CHECK  = 0xB0;
    public static final int FCW_HIGHLIGHT    = 0xE0;

    private int row;
    private int col;
    private int length;
    private int fieldFormat;
    private int fieldControl;
    private char[] content;
    private boolean modified;

    /**
     * Creates a new field at the given 1-based screen position.
     *
     * @param row         1-based row
     * @param col         1-based column
     * @param length      field length in characters
     * @param fieldFormat the FFW (Field Format Word)
     * @param fieldControl the FCW (Field Control Word)
     */
    public Field5250(int row, int col, int length, int fieldFormat, int fieldControl) {
        this.row = row;
        this.col = col;
        this.length = length;
        this.fieldFormat = fieldFormat;
        this.fieldControl = fieldControl;
        this.content = new char[length];
        Arrays.fill(this.content, ' ');
        this.modified = false;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getCol() {
        return col;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
        if (this.content.length != length) {
            char[] newContent = new char[length];
            Arrays.fill(newContent, ' ');
            System.arraycopy(this.content, 0, newContent, 0, Math.min(this.content.length, length));
            this.content = newContent;
        }
    }

    public int getFieldFormat() {
        return fieldFormat;
    }

    public void setFieldFormat(int fieldFormat) {
        this.fieldFormat = fieldFormat;
    }

    public int getFieldControl() {
        return fieldControl;
    }

    public void setFieldControl(int fieldControl) {
        this.fieldControl = fieldControl;
    }

    public char[] getContent() {
        return content;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public boolean isBypass() {
        return (fieldFormat & FFW_BYPASS) != 0;
    }

    public boolean isDupEnabled() {
        return (fieldFormat & FFW_DUP_ENABLE) != 0;
    }

    public boolean isMandatoryEntry() {
        return (fieldFormat & FFW_MANDATORY_ENTRY) != 0;
    }

    public boolean isMandatoryFill() {
        return (fieldFormat & FFW_MANDATORY_FILL) != 0;
    }

    public boolean isAutoEnter() {
        return (fieldFormat & FFW_AUTO_ENTER) != 0;
    }

    public boolean isFieldExitRequired() {
        return (fieldFormat & FFW_FER) != 0;
    }

    public boolean isMonocase() {
        return (fieldFormat & FFW_MONOCASE) != 0;
    }

    /**
     * Returns {@code true} if this field is a bypass (protected) field.
     */
    public boolean isProtected() {
        return isBypass();
    }

    /**
     * Returns {@code true} if this field only accepts numeric input.
     */
    public boolean isNumericOnly() {
        int shift = fieldFormat & FFW_SHIFT_MASK;
        return shift == SHIFT_NUMERIC_ONLY || shift == SHIFT_SIGNED_NUMERIC || shift == SHIFT_DIGITS_ONLY;
    }

    /**
     * Returns {@code true} if this field only accepts alphabetic input.
     */
    public boolean isAlphaOnly() {
        return (fieldFormat & FFW_SHIFT_MASK) == SHIFT_ALPHA_ONLY;
    }

    /**
     * Returns {@code true} if this field accepts numeric shift input (numeric preferred but alpha allowed).
     */
    public boolean isNumericShift() {
        return (fieldFormat & FFW_SHIFT_MASK) == SHIFT_NUMERIC_SHIFT;
    }

    /**
     * Returns {@code true} if this field accepts alpha shift input (alpha preferred but numeric allowed).
     */
    public boolean isAlphaShift() {
        return (fieldFormat & FFW_SHIFT_MASK) == SHIFT_ALPHA_SHIFT;
    }

    /**
     * Returns {@code true} if this is an input field (not bypass/protected).
     */
    public boolean isInputField() {
        return !isBypass();
    }

    /**
     * Returns the field text as a String.
     */
    public String getText() {
        return new String(content);
    }

    /**
     * Sets the field content from a String. Pads or truncates to the field length.
     */
    public void setText(String text) {
        Arrays.fill(content, ' ');
        if (text != null) {
            int len = Math.min(text.length(), length);
            text.getChars(0, len, content, 0);
        }
        modified = true;
    }

    /**
     * Clears the field content to spaces and resets the modified flag.
     */
    public void clear() {
        Arrays.fill(content, ' ');
        modified = false;
    }

    /**
     * Sets a character at the given offset within this field.
     *
     * @param offset zero-based offset within the field
     * @param c      the character to set
     */
    public void setCharAt(int offset, char c) {
        if (offset >= 0 && offset < length) {
            content[offset] = c;
            modified = true;
        }
    }

    /**
     * Returns the character at the given offset within this field.
     */
    public char getCharAt(int offset) {
        if (offset >= 0 && offset < length) {
            return content[offset];
        }
        return ' ';
    }

    /**
     * Returns the linear buffer address (0-based) corresponding to the start of this field,
     * given the screen width.
     */
    public int getBufferAddress(int screenCols) {
        return (row - 1) * screenCols + (col - 1);
    }

    /**
     * Returns {@code true} if the given 1-based row/col position falls within this field,
     * assuming the field does not wrap across rows.
     */
    public boolean containsPosition(int r, int c) {
        if (r != row) {
            return false;
        }
        return c >= col && c < col + length;
    }

    @Override
    public String toString() {
        return "Field5250[row=" + row + ", col=" + col + ", len=" + length
                + ", ffw=0x" + Integer.toHexString(fieldFormat)
                + ", fcw=0x" + Integer.toHexString(fieldControl)
                + ", bypass=" + isBypass()
                + ", modified=" + modified + "]";
    }
}
