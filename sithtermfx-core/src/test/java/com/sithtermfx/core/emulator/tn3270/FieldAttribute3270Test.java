package com.sithtermfx.core.emulator.tn3270;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FieldAttribute3270}.
 *
 * @author Daniel Mengel
 */
class FieldAttribute3270Test {

    @Test
    void defaultAttributeIsUnprotectedAlphanumericNormal() {
        FieldAttribute3270 attr = new FieldAttribute3270(0x00);
        assertFalse(attr.isProtected());
        assertFalse(attr.isNumeric());
        assertTrue(attr.isNormalDisplay());
        assertFalse(attr.isHidden());
        assertFalse(attr.isIntensified());
        assertFalse(attr.isMdt());
    }

    @Test
    void isProtectedForProtectedAttribute() {
        FieldAttribute3270 attr = new FieldAttribute3270(0x20);
        assertTrue(attr.isProtected());
    }

    @Test
    void isProtectedFalseForUnprotectedAttribute() {
        FieldAttribute3270 attr = new FieldAttribute3270(0x00);
        assertFalse(attr.isProtected());
    }

    @Test
    void isNumericForNumericAttribute() {
        FieldAttribute3270 attr = new FieldAttribute3270(0x10);
        assertTrue(attr.isNumeric());
    }

    @Test
    void isNumericFalseForAlphanumericAttribute() {
        FieldAttribute3270 attr = new FieldAttribute3270(0x00);
        assertFalse(attr.isNumeric());
    }

    @Test
    void isHiddenForHiddenDisplay() {
        FieldAttribute3270 attr = new FieldAttribute3270(0x0C);
        assertTrue(attr.isHidden());
        assertFalse(attr.isIntensified());
        assertFalse(attr.isNormalDisplay());
    }

    @Test
    void isIntensifiedForIntensifiedDisplay() {
        FieldAttribute3270 attr = new FieldAttribute3270(0x08);
        assertTrue(attr.isIntensified());
        assertFalse(attr.isHidden());
        assertFalse(attr.isNormalDisplay());
    }

    @Test
    void normalDisplayForBothNormalValues() {
        FieldAttribute3270 normal1 = new FieldAttribute3270(0x00);
        assertTrue(normal1.isNormalDisplay());

        FieldAttribute3270 normal2 = new FieldAttribute3270(0x04);
        assertTrue(normal2.isNormalDisplay());
    }

    @Test
    void setMdtAndIsMdt() {
        FieldAttribute3270 attr = new FieldAttribute3270(0x00);
        assertFalse(attr.isMdt());

        attr.setMdt(true);
        assertTrue(attr.isMdt());

        attr.setMdt(false);
        assertFalse(attr.isMdt());
    }

    @Test
    void setMdtPreservesOtherBits() {
        FieldAttribute3270 attr = new FieldAttribute3270(0x28);
        assertTrue(attr.isProtected());
        assertTrue(attr.isIntensified());

        attr.setMdt(true);
        assertTrue(attr.isProtected());
        assertTrue(attr.isIntensified());
        assertTrue(attr.isMdt());
    }

    @Test
    void protectedNumericHiddenCombination() {
        FieldAttribute3270 attr = new FieldAttribute3270(0x3C);
        assertTrue(attr.isProtected());
        assertTrue(attr.isNumeric());
        assertTrue(attr.isHidden());
        assertFalse(attr.isMdt());
    }

    @Test
    void mdtBitSetViaConstructor() {
        FieldAttribute3270 attr = new FieldAttribute3270(0x01);
        assertTrue(attr.isMdt());
        assertFalse(attr.isProtected());
    }

    @Test
    void rawByteIsMaskedTo6Bits() {
        FieldAttribute3270 attr = new FieldAttribute3270(0xFF);
        assertEquals(0x3F, attr.getRawByte());
    }

    @Test
    void decodeExtractsLower6Bits() {
        FieldAttribute3270 attr = FieldAttribute3270.decode(0xFF);
        assertEquals(0x3F, attr.getRawByte());
    }

    @Test
    void toStringContainsKeyInfo() {
        FieldAttribute3270 attr = new FieldAttribute3270(0x20);
        String str = attr.toString();
        assertTrue(str.contains("P"), "Protected field should show 'P'");
    }
}
