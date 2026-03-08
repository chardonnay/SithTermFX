package com.sithtermfx.core.emulator.petscii;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PetsciiCharacterSet}.
 *
 * @author Daniel Mengel
 */
class PetsciiCharacterSetTest {

    @Test
    void uppercaseGraphicsModeAToZ() {
        for (int i = 0; i < 26; i++) {
            char expected = (char) ('A' + i);
            char actual = PetsciiCharacterSet.mapToUnicode(0x41 + i, true);
            assertEquals(expected, actual,
                    "Uppercase/Graphics mode: 0x" + Integer.toHexString(0x41 + i) + " should map to '" + expected + "'");
        }
    }

    @Test
    void lowercaseModeLowercaseAToZ() {
        for (int i = 0; i < 26; i++) {
            char expected = (char) ('a' + i);
            char actual = PetsciiCharacterSet.mapToUnicode(0x41 + i, false);
            assertEquals(expected, actual,
                    "Lowercase/Uppercase mode: 0x" + Integer.toHexString(0x41 + i) + " should map to '" + expected + "'");
        }
    }

    @Test
    void lowercaseModeUppercaseAToZAt0x61() {
        for (int i = 0; i < 26; i++) {
            char expected = (char) ('A' + i);
            char actual = PetsciiCharacterSet.mapToUnicode(0x61 + i, false);
            assertEquals(expected, actual,
                    "Lowercase/Uppercase mode: 0x" + Integer.toHexString(0x61 + i) + " should map to '" + expected + "'");
        }
    }

    @Test
    void graphicCharactersMapToUnicodeBlockElements() {
        char graphic60 = PetsciiCharacterSet.mapToUnicode(0x60, true);
        assertEquals('\u2500', graphic60, "0x60 in uppercase mode should be box drawing horizontal");

        char graphic61 = PetsciiCharacterSet.mapToUnicode(0x61, true);
        assertEquals('\u2660', graphic61, "0x61 in uppercase mode should be black spade suit");
    }

    @Test
    void bothModesProduceDifferentOutputForSameCode() {
        char uppercaseMode = PetsciiCharacterSet.mapToUnicode(0x41, true);
        char lowercaseMode = PetsciiCharacterSet.mapToUnicode(0x41, false);
        assertNotEquals(uppercaseMode, lowercaseMode,
                "0x41 should produce different characters in each mode");
        assertEquals('A', uppercaseMode);
        assertEquals('a', lowercaseMode);
    }

    @Test
    void graphicsRange0x60DiffersBetweenModes() {
        char uppercaseMode = PetsciiCharacterSet.mapToUnicode(0x61, true);
        char lowercaseMode = PetsciiCharacterSet.mapToUnicode(0x61, false);
        assertNotEquals(uppercaseMode, lowercaseMode,
                "0x61 should produce different characters in each mode");
        assertEquals('\u2660', uppercaseMode);
        assertEquals('A', lowercaseMode);
    }

    @Test
    void spaceMapsToSpaceInBothModes() {
        assertEquals(' ', PetsciiCharacterSet.mapToUnicode(0x20, true));
        assertEquals(' ', PetsciiCharacterSet.mapToUnicode(0x20, false));
    }

    @Test
    void digitsAreIdenticalInBothModes() {
        for (int i = 0x30; i <= 0x39; i++) {
            char upper = PetsciiCharacterSet.mapToUnicode(i, true);
            char lower = PetsciiCharacterSet.mapToUnicode(i, false);
            assertEquals(upper, lower,
                    "Digit at 0x" + Integer.toHexString(i) + " should be identical in both modes");
            assertEquals((char) i, upper);
        }
    }

    @Test
    void shiftedGraphicsIdenticalInBothModes() {
        for (int i = 0xA0; i <= 0xBF; i++) {
            char upper = PetsciiCharacterSet.mapToUnicode(i, true);
            char lower = PetsciiCharacterSet.mapToUnicode(i, false);
            assertEquals(upper, lower,
                    "Shifted graphic at 0x" + Integer.toHexString(i) + " should be identical in both modes");
        }
    }

    @Test
    void atSignMapsCorrectly() {
        assertEquals('@', PetsciiCharacterSet.mapToUnicode(0x40, true));
        assertEquals('@', PetsciiCharacterSet.mapToUnicode(0x40, false));
    }

    @Test
    void poundSignAt0x5C() {
        assertEquals('\u00A3', PetsciiCharacterSet.mapToUnicode(0x5C, true));
        assertEquals('\u00A3', PetsciiCharacterSet.mapToUnicode(0x5C, false));
    }

    @Test
    void piCharacterAt0x7E() {
        assertEquals('\u03C0', PetsciiCharacterSet.mapToUnicode(0x7E, true));
        assertEquals('\u03C0', PetsciiCharacterSet.mapToUnicode(0x7E, false));
    }

    @Test
    void fullBlockAt0xBC() {
        assertEquals('\u2588', PetsciiCharacterSet.mapToUnicode(0xBC, true));
        assertEquals('\u2588', PetsciiCharacterSet.mapToUnicode(0xBC, false));
    }

    @Test
    void codeValueMaskedTo8Bits() {
        assertEquals(PetsciiCharacterSet.mapToUnicode(0x41, true),
                PetsciiCharacterSet.mapToUnicode(0x141, true));
    }
}
