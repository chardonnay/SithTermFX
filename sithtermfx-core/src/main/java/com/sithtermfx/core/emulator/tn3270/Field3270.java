package com.sithtermfx.core.emulator.tn3270;

import java.util.Arrays;

/**
 * Represents a single field on the 3270 screen.
 * <p>
 * A field starts at a buffer address (the position of the field attribute byte)
 * and spans {@code length} positions. The content is stored as Unicode characters
 * decoded from EBCDIC.
 *
 * @author Daniel Mengel
 */
public class Field3270 {

    private final int myStartPosition;
    private int myLength;
    private final FieldAttribute3270 myAttribute;
    private char[] myContent;

    /**
     * Creates a new 3270 field.
     *
     * @param startPosition buffer address of the field attribute byte
     * @param length        number of data positions in the field (excluding the attribute)
     * @param attribute     the field attribute
     */
    public Field3270(int startPosition, int length, FieldAttribute3270 attribute) {
        myStartPosition = startPosition;
        myLength = length;
        myAttribute = attribute;
        myContent = new char[length];
        Arrays.fill(myContent, ' ');
    }

    public int getStartPosition() {
        return myStartPosition;
    }

    public int getLength() {
        return myLength;
    }

    public void setLength(int length) {
        if (length != myLength) {
            char[] newContent = new char[length];
            Arrays.fill(newContent, ' ');
            int copyLen = Math.min(myLength, length);
            System.arraycopy(myContent, 0, newContent, 0, copyLen);
            myContent = newContent;
            myLength = length;
        }
    }

    public FieldAttribute3270 getAttribute() {
        return myAttribute;
    }

    /**
     * @return the field content as a string (Unicode)
     */
    public String getText() {
        return new String(myContent);
    }

    /**
     * Sets the field content. If {@code text} is shorter than the field, the
     * remainder is filled with spaces. If longer, it is truncated.
     */
    public void setText(String text) {
        Arrays.fill(myContent, ' ');
        int copyLen = Math.min(text.length(), myLength);
        text.getChars(0, copyLen, myContent, 0);
        if (copyLen > 0) {
            myAttribute.setMdt(true);
        }
    }

    /**
     * @return the character at the given offset within this field
     */
    public char getCharAt(int offset) {
        if (offset < 0 || offset >= myLength) {
            return ' ';
        }
        return myContent[offset];
    }

    /**
     * Sets a character at the given offset within this field.
     */
    public void setCharAt(int offset, char c) {
        if (offset >= 0 && offset < myLength) {
            if (myContent[offset] != c) {
                myContent[offset] = c;
                if (myAttribute != null) {
                    myAttribute.setMdt(true);
                }
            }
        }
    }

    /**
     * Clears the field content to spaces and resets the MDT.
     */
    public void clear() {
        Arrays.fill(myContent, ' ');
        myAttribute.setMdt(false);
    }

    public boolean isProtected() {
        return myAttribute.isProtected();
    }

    public boolean isMdt() {
        return myAttribute.isMdt();
    }

    /**
     * @return the raw character array (for direct buffer manipulation)
     */
    public char[] getContentArray() {
        return myContent;
    }

    /**
     * @return the buffer address immediately after the field attribute position,
     *         i.e. the first data position
     */
    public int getFirstDataPosition() {
        return myStartPosition + 1;
    }

    @Override
    public String toString() {
        return "Field3270[pos=" + myStartPosition + ",len=" + myLength + "," + myAttribute + "]";
    }
}
