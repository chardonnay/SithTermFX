package com.sithtermfx.core.emulator.tn3270;

/**
 * EBCDIC ←→ Unicode conversion. Supports CCSID 037 (US/Canada).
 *
 * @author Daniel Mengel
 */
public final class EbcdicCodec {

    private EbcdicCodec() {
    }

    /**
     * CCSID 037 (EBCDIC US/Canada) to Unicode mapping.
     */
    private static final char[] EBCDIC_037_TO_UNICODE = new char[256];

    /**
     * Unicode to CCSID 037 reverse mapping (BMP only, first 256 entries).
     */
    private static final int[] UNICODE_TO_EBCDIC_037 = new int[256];

    static {
        // Initialize to replacement character
        for (int i = 0; i < 256; i++) {
            EBCDIC_037_TO_UNICODE[i] = '\uFFFD';
        }

        // Control characters 0x00-0x3F
        EBCDIC_037_TO_UNICODE[0x00] = 0x0000; // NULL
        EBCDIC_037_TO_UNICODE[0x01] = 0x0001; // SOH
        EBCDIC_037_TO_UNICODE[0x02] = 0x0002; // STX
        EBCDIC_037_TO_UNICODE[0x03] = 0x0003; // ETX
        EBCDIC_037_TO_UNICODE[0x05] = 0x0009; // HT
        EBCDIC_037_TO_UNICODE[0x07] = 0x007F; // DEL
        EBCDIC_037_TO_UNICODE[0x0B] = 0x000B; // VT
        EBCDIC_037_TO_UNICODE[0x0C] = 0x000C; // FF
        EBCDIC_037_TO_UNICODE[0x0D] = 0x000D; // CR
        EBCDIC_037_TO_UNICODE[0x0E] = 0x000E; // SO
        EBCDIC_037_TO_UNICODE[0x0F] = 0x000F; // SI
        EBCDIC_037_TO_UNICODE[0x10] = 0x0010; // DLE
        EBCDIC_037_TO_UNICODE[0x11] = 0x0011; // DC1
        EBCDIC_037_TO_UNICODE[0x12] = 0x0012; // DC2
        EBCDIC_037_TO_UNICODE[0x13] = 0x0013; // DC3
        EBCDIC_037_TO_UNICODE[0x15] = 0x0085; // NL
        EBCDIC_037_TO_UNICODE[0x16] = 0x0008; // BS
        EBCDIC_037_TO_UNICODE[0x19] = 0x0019; // EM
        EBCDIC_037_TO_UNICODE[0x1C] = 0x001C; // IFS
        EBCDIC_037_TO_UNICODE[0x1D] = 0x001D; // IGS
        EBCDIC_037_TO_UNICODE[0x1E] = 0x001E; // IRS
        EBCDIC_037_TO_UNICODE[0x1F] = 0x001F; // IUS
        EBCDIC_037_TO_UNICODE[0x25] = 0x000A; // LF
        EBCDIC_037_TO_UNICODE[0x26] = 0x0017; // ETB
        EBCDIC_037_TO_UNICODE[0x27] = 0x001B; // ESC
        EBCDIC_037_TO_UNICODE[0x2D] = 0x0005; // ENQ
        EBCDIC_037_TO_UNICODE[0x2E] = 0x0006; // ACK
        EBCDIC_037_TO_UNICODE[0x2F] = 0x0007; // BEL
        EBCDIC_037_TO_UNICODE[0x32] = 0x0016; // SYN
        EBCDIC_037_TO_UNICODE[0x37] = 0x0004; // EOT
        EBCDIC_037_TO_UNICODE[0x3C] = 0x0014; // DC4
        EBCDIC_037_TO_UNICODE[0x3D] = 0x0015; // NAK
        EBCDIC_037_TO_UNICODE[0x3F] = 0x001A; // SUB

        // Printable characters
        EBCDIC_037_TO_UNICODE[0x40] = ' ';
        EBCDIC_037_TO_UNICODE[0x41] = '\u00A0'; // NBSP
        EBCDIC_037_TO_UNICODE[0x4A] = '\u00A2'; // cent
        EBCDIC_037_TO_UNICODE[0x4B] = '.';
        EBCDIC_037_TO_UNICODE[0x4C] = '<';
        EBCDIC_037_TO_UNICODE[0x4D] = '(';
        EBCDIC_037_TO_UNICODE[0x4E] = '+';
        EBCDIC_037_TO_UNICODE[0x4F] = '|';
        EBCDIC_037_TO_UNICODE[0x50] = '&';
        EBCDIC_037_TO_UNICODE[0x5A] = '!';
        EBCDIC_037_TO_UNICODE[0x5B] = '$';
        EBCDIC_037_TO_UNICODE[0x5C] = '*';
        EBCDIC_037_TO_UNICODE[0x5D] = ')';
        EBCDIC_037_TO_UNICODE[0x5E] = ';';
        EBCDIC_037_TO_UNICODE[0x5F] = '\u00AC'; // NOT
        EBCDIC_037_TO_UNICODE[0x60] = '-';
        EBCDIC_037_TO_UNICODE[0x61] = '/';
        EBCDIC_037_TO_UNICODE[0x6A] = '\u00A6'; // broken bar
        EBCDIC_037_TO_UNICODE[0x6B] = ',';
        EBCDIC_037_TO_UNICODE[0x6C] = '%';
        EBCDIC_037_TO_UNICODE[0x6D] = '_';
        EBCDIC_037_TO_UNICODE[0x6E] = '>';
        EBCDIC_037_TO_UNICODE[0x6F] = '?';
        EBCDIC_037_TO_UNICODE[0x79] = '`';
        EBCDIC_037_TO_UNICODE[0x7A] = ':';
        EBCDIC_037_TO_UNICODE[0x7B] = '#';
        EBCDIC_037_TO_UNICODE[0x7C] = '@';
        EBCDIC_037_TO_UNICODE[0x7D] = '\'';
        EBCDIC_037_TO_UNICODE[0x7E] = '=';
        EBCDIC_037_TO_UNICODE[0x7F] = '"';

        // Lowercase letters a-i
        EBCDIC_037_TO_UNICODE[0x81] = 'a';
        EBCDIC_037_TO_UNICODE[0x82] = 'b';
        EBCDIC_037_TO_UNICODE[0x83] = 'c';
        EBCDIC_037_TO_UNICODE[0x84] = 'd';
        EBCDIC_037_TO_UNICODE[0x85] = 'e';
        EBCDIC_037_TO_UNICODE[0x86] = 'f';
        EBCDIC_037_TO_UNICODE[0x87] = 'g';
        EBCDIC_037_TO_UNICODE[0x88] = 'h';
        EBCDIC_037_TO_UNICODE[0x89] = 'i';

        // Lowercase letters j-r
        EBCDIC_037_TO_UNICODE[0x91] = 'j';
        EBCDIC_037_TO_UNICODE[0x92] = 'k';
        EBCDIC_037_TO_UNICODE[0x93] = 'l';
        EBCDIC_037_TO_UNICODE[0x94] = 'm';
        EBCDIC_037_TO_UNICODE[0x95] = 'n';
        EBCDIC_037_TO_UNICODE[0x96] = 'o';
        EBCDIC_037_TO_UNICODE[0x97] = 'p';
        EBCDIC_037_TO_UNICODE[0x98] = 'q';
        EBCDIC_037_TO_UNICODE[0x99] = 'r';

        // Lowercase letters s-z
        EBCDIC_037_TO_UNICODE[0xA2] = 's';
        EBCDIC_037_TO_UNICODE[0xA3] = 't';
        EBCDIC_037_TO_UNICODE[0xA4] = 'u';
        EBCDIC_037_TO_UNICODE[0xA5] = 'v';
        EBCDIC_037_TO_UNICODE[0xA6] = 'w';
        EBCDIC_037_TO_UNICODE[0xA7] = 'x';
        EBCDIC_037_TO_UNICODE[0xA8] = 'y';
        EBCDIC_037_TO_UNICODE[0xA9] = 'z';

        // Uppercase letters A-I
        EBCDIC_037_TO_UNICODE[0xC1] = 'A';
        EBCDIC_037_TO_UNICODE[0xC2] = 'B';
        EBCDIC_037_TO_UNICODE[0xC3] = 'C';
        EBCDIC_037_TO_UNICODE[0xC4] = 'D';
        EBCDIC_037_TO_UNICODE[0xC5] = 'E';
        EBCDIC_037_TO_UNICODE[0xC6] = 'F';
        EBCDIC_037_TO_UNICODE[0xC7] = 'G';
        EBCDIC_037_TO_UNICODE[0xC8] = 'H';
        EBCDIC_037_TO_UNICODE[0xC9] = 'I';

        // Uppercase letters J-R
        EBCDIC_037_TO_UNICODE[0xD1] = 'J';
        EBCDIC_037_TO_UNICODE[0xD2] = 'K';
        EBCDIC_037_TO_UNICODE[0xD3] = 'L';
        EBCDIC_037_TO_UNICODE[0xD4] = 'M';
        EBCDIC_037_TO_UNICODE[0xD5] = 'N';
        EBCDIC_037_TO_UNICODE[0xD6] = 'O';
        EBCDIC_037_TO_UNICODE[0xD7] = 'P';
        EBCDIC_037_TO_UNICODE[0xD8] = 'Q';
        EBCDIC_037_TO_UNICODE[0xD9] = 'R';

        // Uppercase letters S-Z
        EBCDIC_037_TO_UNICODE[0xE2] = 'S';
        EBCDIC_037_TO_UNICODE[0xE3] = 'T';
        EBCDIC_037_TO_UNICODE[0xE4] = 'U';
        EBCDIC_037_TO_UNICODE[0xE5] = 'V';
        EBCDIC_037_TO_UNICODE[0xE6] = 'W';
        EBCDIC_037_TO_UNICODE[0xE7] = 'X';
        EBCDIC_037_TO_UNICODE[0xE8] = 'Y';
        EBCDIC_037_TO_UNICODE[0xE9] = 'Z';

        // Digits 0-9
        EBCDIC_037_TO_UNICODE[0xF0] = '0';
        EBCDIC_037_TO_UNICODE[0xF1] = '1';
        EBCDIC_037_TO_UNICODE[0xF2] = '2';
        EBCDIC_037_TO_UNICODE[0xF3] = '3';
        EBCDIC_037_TO_UNICODE[0xF4] = '4';
        EBCDIC_037_TO_UNICODE[0xF5] = '5';
        EBCDIC_037_TO_UNICODE[0xF6] = '6';
        EBCDIC_037_TO_UNICODE[0xF7] = '7';
        EBCDIC_037_TO_UNICODE[0xF8] = '8';
        EBCDIC_037_TO_UNICODE[0xF9] = '9';

        // Build reverse table
        for (int i = 0; i < 256; i++) {
            UNICODE_TO_EBCDIC_037[i] = 0x3F; // default to EBCDIC '?'
        }
        for (int i = 0; i < 256; i++) {
            char u = EBCDIC_037_TO_UNICODE[i];
            if (u != '\uFFFD' && u < 256) {
                UNICODE_TO_EBCDIC_037[u] = i;
            }
        }
    }

    public static char ebcdicToUnicode(int ebcdic) {
        return EBCDIC_037_TO_UNICODE[ebcdic & 0xFF];
    }

    public static int unicodeToEbcdic(char unicode) {
        if (unicode < 256) {
            return UNICODE_TO_EBCDIC_037[unicode];
        }
        return 0x3F; // EBCDIC question mark for unmapped
    }

    public static String ebcdicBytesToString(byte[] data, int offset, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = offset; i < offset + length && i < data.length; i++) {
            sb.append(ebcdicToUnicode(data[i] & 0xFF));
        }
        return sb.toString();
    }

    public static byte[] stringToEbcdic(String str) {
        byte[] result = new byte[str.length()];
        for (int i = 0; i < str.length(); i++) {
            result[i] = (byte) unicodeToEbcdic(str.charAt(i));
        }
        return result;
    }
}
