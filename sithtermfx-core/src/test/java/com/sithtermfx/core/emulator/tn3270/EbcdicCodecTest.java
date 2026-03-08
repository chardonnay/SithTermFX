package com.sithtermfx.core.emulator.tn3270;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EbcdicCodec}.
 *
 * @author Daniel Mengel
 */
class EbcdicCodecTest {

    @Test
    void ebcdicToUnicodeUppercaseLetters() {
        assertEquals('A', EbcdicCodec.ebcdicToUnicode(0xC1));
        assertEquals('B', EbcdicCodec.ebcdicToUnicode(0xC2));
        assertEquals('C', EbcdicCodec.ebcdicToUnicode(0xC3));
        assertEquals('I', EbcdicCodec.ebcdicToUnicode(0xC9));
        assertEquals('J', EbcdicCodec.ebcdicToUnicode(0xD1));
        assertEquals('R', EbcdicCodec.ebcdicToUnicode(0xD9));
        assertEquals('S', EbcdicCodec.ebcdicToUnicode(0xE2));
        assertEquals('Z', EbcdicCodec.ebcdicToUnicode(0xE9));
    }

    @Test
    void ebcdicToUnicodeLowercaseLetters() {
        assertEquals('a', EbcdicCodec.ebcdicToUnicode(0x81));
        assertEquals('i', EbcdicCodec.ebcdicToUnicode(0x89));
        assertEquals('j', EbcdicCodec.ebcdicToUnicode(0x91));
        assertEquals('r', EbcdicCodec.ebcdicToUnicode(0x99));
        assertEquals('s', EbcdicCodec.ebcdicToUnicode(0xA2));
        assertEquals('z', EbcdicCodec.ebcdicToUnicode(0xA9));
    }

    @Test
    void ebcdicToUnicodeDigits() {
        assertEquals('0', EbcdicCodec.ebcdicToUnicode(0xF0));
        assertEquals('1', EbcdicCodec.ebcdicToUnicode(0xF1));
        assertEquals('5', EbcdicCodec.ebcdicToUnicode(0xF5));
        assertEquals('9', EbcdicCodec.ebcdicToUnicode(0xF9));
    }

    @Test
    void ebcdicToUnicodeSpecialChars() {
        assertEquals(' ', EbcdicCodec.ebcdicToUnicode(0x40));
        assertEquals('.', EbcdicCodec.ebcdicToUnicode(0x4B));
        assertEquals(',', EbcdicCodec.ebcdicToUnicode(0x6B));
    }

    @Test
    void unicodeToEbcdicRoundTripUppercase() {
        for (char c = 'A'; c <= 'Z'; c++) {
            int ebcdic = EbcdicCodec.unicodeToEbcdic(c);
            assertEquals(c, EbcdicCodec.ebcdicToUnicode(ebcdic),
                    "Round-trip failed for uppercase '" + c + "'");
        }
    }

    @Test
    void unicodeToEbcdicRoundTripLowercase() {
        for (char c = 'a'; c <= 'z'; c++) {
            int ebcdic = EbcdicCodec.unicodeToEbcdic(c);
            assertEquals(c, EbcdicCodec.ebcdicToUnicode(ebcdic),
                    "Round-trip failed for lowercase '" + c + "'");
        }
    }

    @Test
    void unicodeToEbcdicRoundTripDigits() {
        for (char c = '0'; c <= '9'; c++) {
            int ebcdic = EbcdicCodec.unicodeToEbcdic(c);
            assertEquals(c, EbcdicCodec.ebcdicToUnicode(ebcdic),
                    "Round-trip failed for digit '" + c + "'");
        }
    }

    @Test
    void ebcdicBytesToStringHello() {
        byte[] hello = {(byte) 0xC8, (byte) 0xC5, (byte) 0xD3, (byte) 0xD3, (byte) 0xD6};
        assertEquals("HELLO", EbcdicCodec.ebcdicBytesToString(hello, 0, hello.length));
    }

    @Test
    void stringToEbcdicHello() {
        byte[] result = EbcdicCodec.stringToEbcdic("HELLO");
        assertEquals(5, result.length);
        assertEquals((byte) 0xC8, result[0]);
        assertEquals((byte) 0xC5, result[1]);
        assertEquals((byte) 0xD3, result[2]);
        assertEquals((byte) 0xD3, result[3]);
        assertEquals((byte) 0xD6, result[4]);
    }

    @Test
    void roundTripStringConversion() {
        String[] testStrings = {"HELLO", "WORLD", "Test 123", "abc XYZ"};
        for (String str : testStrings) {
            byte[] ebcdic = EbcdicCodec.stringToEbcdic(str);
            String result = EbcdicCodec.ebcdicBytesToString(ebcdic, 0, ebcdic.length);
            assertEquals(str, result, "Round-trip failed for \"" + str + "\"");
        }
    }

    @Test
    void ebcdicBytesToStringWithOffset() {
        byte[] data = {(byte) 0xC8, (byte) 0xC5, (byte) 0xD3, (byte) 0xD3, (byte) 0xD6};
        assertEquals("LLO", EbcdicCodec.ebcdicBytesToString(data, 2, 3));
        assertEquals("HE", EbcdicCodec.ebcdicBytesToString(data, 0, 2));
    }

    @Test
    void unmappedUnicodeReturnsEbcdicQuestionMark() {
        assertEquals(0x3F, EbcdicCodec.unicodeToEbcdic('\u4E00'));
    }
}
