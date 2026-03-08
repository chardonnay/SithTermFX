package com.sithtermfx.core.emulator.tn3270;

/**
 * Represents a 3270 field attribute byte as defined in the IBM 3270 Data Stream
 * Programmer's Reference (GA23-0059).
 * <p>
 * The attribute byte occupies a screen position and encodes:
 * <ul>
 *   <li>Bit 5 – Protected (1) / Unprotected (0)</li>
 *   <li>Bit 4 – Numeric-only (1) / Alphanumeric (0)</li>
 *   <li>Bits 3-2 – Display: 00=normal, 01=normal, 10=intensified, 11=hidden</li>
 *   <li>Bit 0 – MDT (Modified Data Tag)</li>
 * </ul>
 * Bit numbering follows the 3270 convention (bit 0 = LSB).
 *
 * @author Daniel Mengel
 */
public class FieldAttribute3270 {

    private static final int MASK_PROTECTED = 0x20;
    private static final int MASK_NUMERIC = 0x10;
    private static final int MASK_DISPLAY = 0x0C;
    private static final int MASK_MDT = 0x01;

    private static final int DISPLAY_NORMAL_1 = 0x00;
    private static final int DISPLAY_NORMAL_2 = 0x04;
    private static final int DISPLAY_INTENSIFIED = 0x08;
    private static final int DISPLAY_HIDDEN = 0x0C;

    private int myRawByte;

    /**
     * Creates a field attribute from the raw attribute byte as received in
     * the 3270 data stream.
     *
     * @param rawByte the attribute byte (only lower 6 bits are significant)
     */
    public FieldAttribute3270(int rawByte) {
        myRawByte = rawByte & 0x3F;
    }

    /**
     * Decodes a raw attribute byte from the 3270 data stream.
     * The 3270 attribute byte uses bits 2–7 of the byte but is stored
     * in a 6-bit field. This method extracts the relevant bits.
     *
     * @param raw the full byte as received in the data stream
     * @return a new {@link FieldAttribute3270}
     */
    public static FieldAttribute3270 decode(int raw) {
        int attr = raw & 0x3F;
        return new FieldAttribute3270(attr);
    }

    public boolean isProtected() {
        return (myRawByte & MASK_PROTECTED) != 0;
    }

    public boolean isNumeric() {
        return (myRawByte & MASK_NUMERIC) != 0;
    }

    public boolean isHidden() {
        return (myRawByte & MASK_DISPLAY) == DISPLAY_HIDDEN;
    }

    public boolean isIntensified() {
        return (myRawByte & MASK_DISPLAY) == DISPLAY_INTENSIFIED;
    }

    public boolean isNormalDisplay() {
        int display = myRawByte & MASK_DISPLAY;
        return display == DISPLAY_NORMAL_1 || display == DISPLAY_NORMAL_2;
    }

    public boolean isMdt() {
        return (myRawByte & MASK_MDT) != 0;
    }

    public void setMdt(boolean mdt) {
        if (mdt) {
            myRawByte |= MASK_MDT;
        } else {
            myRawByte &= ~MASK_MDT;
        }
    }

    /**
     * @return the raw 6-bit attribute value
     */
    public int getRawByte() {
        return myRawByte;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FieldAttr[");
        sb.append(isProtected() ? "P" : "U");
        sb.append(isNumeric() ? "N" : "A");
        if (isHidden()) sb.append(",HID");
        else if (isIntensified()) sb.append(",INT");
        if (isMdt()) sb.append(",MDT");
        sb.append(']');
        return sb.toString();
    }
}
