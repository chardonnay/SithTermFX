package com.sithtermfx.core.emulator.petscii;

/**
 * Maps PETSCII byte values to Unicode code points for the Commodore 64/128.
 * <p>
 * PETSCII has two character set modes:
 * <ul>
 *   <li><b>Uppercase/Graphics</b> (default on C64 boot): uppercase letters A-Z at 0x41-0x5A,
 *       block graphics at 0x60-0x7F</li>
 *   <li><b>Lowercase/Uppercase</b> (toggled via CHR$(14)): lowercase a-z at 0x41-0x5A,
 *       uppercase A-Z at 0x61-0x7A</li>
 * </ul>
 * Graphics characters are mapped to the closest Unicode equivalents from the
 * Block Elements (U+2580-U+259F), Box Drawing (U+2500-U+257F), and
 * Geometric Shapes blocks.
 *
 * @author Daniel Mengel
 */
public final class PetsciiCharacterSet {

    private static final char[] UPPERCASE_GRAPHICS = new char[256];
    private static final char[] LOWERCASE_UPPERCASE = new char[256];

    static {
        initShared(UPPERCASE_GRAPHICS);
        initShared(LOWERCASE_UPPERCASE);
        initShiftedGraphics(UPPERCASE_GRAPHICS);
        initShiftedGraphics(LOWERCASE_UPPERCASE);
        initUppercaseGraphicsMode();
        initLowercaseUppercaseMode();
    }

    private PetsciiCharacterSet() {
    }

    /**
     * Maps a PETSCII byte value to its Unicode equivalent.
     *
     * @param petsciiCode          the PETSCII code (0x00-0xFF)
     * @param uppercaseGraphicsMode {@code true} for uppercase/graphics mode,
     *                              {@code false} for lowercase/uppercase mode
     * @return the Unicode character, or {@code '\0'} if the code is a non-printable control character
     */
    public static char mapToUnicode(int petsciiCode, boolean uppercaseGraphicsMode) {
        int index = petsciiCode & 0xFF;
        return uppercaseGraphicsMode ? UPPERCASE_GRAPHICS[index] : LOWERCASE_UPPERCASE[index];
    }

    /**
     * Characters shared between both modes: ASCII-compatible range 0x20-0x3F
     * and common symbols at 0x40, 0x5B-0x5F, 0xFF.
     */
    private static void initShared(char[] table) {
        // 0x20-0x3F: ASCII punctuation, digits (identical to ASCII)
        for (int i = 0x20; i <= 0x3F; i++) {
            table[i] = (char) i;
        }

        table[0x40] = '@';
        table[0x5B] = '[';
        table[0x5C] = '\u00A3'; // £ POUND SIGN
        table[0x5D] = ']';
        table[0x5E] = '\u2191'; // ↑ UPWARDS ARROW
        table[0x5F] = '\u2190'; // ← LEFTWARDS ARROW
        table[0xFF] = '\u03C0'; // π GREEK SMALL LETTER PI
    }

    /**
     * Shifted PETSCII graphics at 0xA0-0xBF, identical in both character set modes.
     * These are the characters produced by Shift+key combinations.
     */
    private static void initShiftedGraphics(char[] table) {
        table[0xA0] = '\u00A0'; // NON-BREAKING SPACE (shifted space, visually solid in reverse)
        table[0xA1] = '\u258C'; // ▌ LEFT HALF BLOCK
        table[0xA2] = '\u2584'; // ▄ LOWER HALF BLOCK
        table[0xA3] = '\u2594'; // ▔ UPPER ONE EIGHTH BLOCK
        table[0xA4] = '\u2581'; // ▁ LOWER ONE EIGHTH BLOCK
        table[0xA5] = '\u258F'; // ▏ LEFT ONE EIGHTH BLOCK
        table[0xA6] = '\u2592'; // ▒ MEDIUM SHADE (checkerboard pattern)
        table[0xA7] = '\u2595'; // ▕ RIGHT ONE EIGHTH BLOCK
        table[0xA8] = '\u259E'; // ▞ QUADRANT UPPER RIGHT AND LOWER LEFT
        table[0xA9] = '\u25E4'; // ◤ BLACK UPPER LEFT TRIANGLE
        table[0xAA] = '\u259C'; // ▜ QUADRANT UPPER LEFT AND UPPER RIGHT AND LOWER RIGHT
        table[0xAB] = '\u2598'; // ▘ QUADRANT UPPER LEFT
        table[0xAC] = '\u2594'; // ▔ UPPER ONE EIGHTH BLOCK (shifted comma graphic)
        table[0xAD] = '\u2580'; // ▀ UPPER HALF BLOCK
        table[0xAE] = '\u2590'; // ▐ RIGHT HALF BLOCK
        table[0xAF] = '\u259A'; // ▚ QUADRANT UPPER LEFT AND LOWER RIGHT
        table[0xB0] = '\u2596'; // ▖ QUADRANT LOWER LEFT
        table[0xB1] = '\u259E'; // ▞ QUADRANT UPPER RIGHT AND LOWER LEFT
        table[0xB2] = '\u2597'; // ▗ QUADRANT LOWER RIGHT
        table[0xB3] = '\u2583'; // ▃ LOWER THREE EIGHTHS BLOCK
        table[0xB4] = '\u259D'; // ▝ QUADRANT UPPER RIGHT
        table[0xB5] = '\u2582'; // ▂ LOWER ONE QUARTER BLOCK
        table[0xB6] = '\u2596'; // ▖ QUADRANT LOWER LEFT
        table[0xB7] = '\u259E'; // ▞ QUADRANT UPPER RIGHT AND LOWER LEFT
        table[0xB8] = '\u259A'; // ▚ QUADRANT UPPER LEFT AND LOWER RIGHT
        table[0xB9] = '\u2599'; // ▙ QUADRANT UPPER LEFT AND LOWER LEFT AND LOWER RIGHT
        table[0xBA] = '\u259B'; // ▛ QUADRANT UPPER LEFT AND UPPER RIGHT AND LOWER LEFT
        table[0xBB] = '\u259C'; // ▜ QUADRANT UPPER LEFT AND UPPER RIGHT AND LOWER RIGHT
        table[0xBC] = '\u2588'; // █ FULL BLOCK
        table[0xBD] = '\u259F'; // ▟ QUADRANT UPPER RIGHT AND LOWER LEFT AND LOWER RIGHT
        table[0xBE] = '\u259B'; // ▛ QUADRANT UPPER LEFT AND UPPER RIGHT AND LOWER LEFT
        table[0xBF] = '\u2588'; // █ FULL BLOCK (shifted ?)
    }

    /**
     * Uppercase/Graphics mode: the default C64 character set.
     * Letters A-Z at 0x41-0x5A, PETSCII block graphics at 0x60-0x7F.
     * 0xC0-0xDF duplicates 0x60-0x7F, 0xE0-0xFE duplicates 0xA0-0xBE.
     */
    private static void initUppercaseGraphicsMode() {
        char[] t = UPPERCASE_GRAPHICS;

        // 0x41-0x5A: uppercase A-Z
        for (int i = 0; i < 26; i++) {
            t[0x41 + i] = (char) ('A' + i);
        }

        // 0x60-0x7F: PETSCII graphics (block elements, box drawing, symbols)
        t[0x60] = '\u2500'; // ─ BOX DRAWINGS LIGHT HORIZONTAL
        t[0x61] = '\u2660'; // ♠ BLACK SPADE SUIT
        t[0x62] = '\u2502'; // │ BOX DRAWINGS LIGHT VERTICAL
        t[0x63] = '\u2500'; // ─ HORIZONTAL BAR (alternate position)
        t[0x64] = '\u2597'; // ▗ QUADRANT LOWER RIGHT
        t[0x65] = '\u2590'; // ▐ RIGHT HALF BLOCK
        t[0x66] = '\u2581'; // ▁ LOWER ONE EIGHTH BLOCK
        t[0x67] = '\u2596'; // ▖ QUADRANT LOWER LEFT
        t[0x68] = '\u259D'; // ▝ QUADRANT UPPER RIGHT
        t[0x69] = '\u2518'; // ┘ BOX DRAWINGS LIGHT UP AND LEFT
        t[0x6A] = '\u250C'; // ┌ BOX DRAWINGS LIGHT DOWN AND RIGHT
        t[0x6B] = '\u2534'; // ┴ BOX DRAWINGS LIGHT UP AND HORIZONTAL
        t[0x6C] = '\u252C'; // ┬ BOX DRAWINGS LIGHT DOWN AND HORIZONTAL
        t[0x6D] = '\u2524'; // ┤ BOX DRAWINGS LIGHT VERTICAL AND LEFT
        t[0x6E] = '\u2514'; // └ BOX DRAWINGS LIGHT UP AND RIGHT
        t[0x6F] = '\u253C'; // ┼ BOX DRAWINGS LIGHT VERTICAL AND HORIZONTAL
        t[0x70] = '\u2510'; // ┐ BOX DRAWINGS LIGHT DOWN AND LEFT
        t[0x71] = '\u2582'; // ▂ LOWER ONE QUARTER BLOCK
        t[0x72] = '\u2584'; // ▄ LOWER HALF BLOCK
        t[0x73] = '\u258E'; // ▎ LEFT ONE QUARTER BLOCK
        t[0x74] = '\u258C'; // ▌ LEFT HALF BLOCK
        t[0x75] = '\u2583'; // ▃ LOWER THREE EIGHTHS BLOCK
        t[0x76] = '\u2585'; // ▅ LOWER FIVE EIGHTHS BLOCK
        t[0x77] = '\u2586'; // ▆ LOWER THREE QUARTERS BLOCK
        t[0x78] = '\u2587'; // ▇ LOWER SEVEN EIGHTHS BLOCK
        t[0x79] = '\u2588'; // █ FULL BLOCK
        t[0x7A] = '\u2598'; // ▘ QUADRANT UPPER LEFT
        t[0x7B] = '\u259E'; // ▞ QUADRANT UPPER RIGHT AND LOWER LEFT
        t[0x7C] = '\u2599'; // ▙ QUADRANT UPPER LEFT AND LOWER LEFT AND LOWER RIGHT
        t[0x7D] = '\u259B'; // ▛ QUADRANT UPPER LEFT AND UPPER RIGHT AND LOWER LEFT
        t[0x7E] = '\u03C0'; // π GREEK SMALL LETTER PI
        t[0x7F] = '\u25E5'; // ◥ BLACK UPPER RIGHT TRIANGLE

        // 0xC0-0xDF: duplicate of graphics range 0x60-0x7F
        for (int i = 0; i < 32; i++) {
            t[0xC0 + i] = t[0x60 + i];
        }

        // 0xE0-0xFE: duplicate of shifted graphics 0xA0-0xBE
        for (int i = 0; i < 31; i++) {
            t[0xE0 + i] = t[0xA0 + i];
        }
    }

    /**
     * Lowercase/Uppercase mode: toggled via PETSCII code 0x0E (CHR$(14)).
     * Lowercase a-z at 0x41-0x5A, uppercase A-Z at 0x61-0x7A.
     * 0xC0-0xDF duplicates the lowercase range, 0xE0-0xFE duplicates 0xA0-0xBE.
     */
    private static void initLowercaseUppercaseMode() {
        char[] t = LOWERCASE_UPPERCASE;

        // 0x41-0x5A: lowercase a-z
        for (int i = 0; i < 26; i++) {
            t[0x41 + i] = (char) ('a' + i);
        }

        // 0x60: horizontal bar (same graphics glyph as uppercase mode)
        t[0x60] = '\u2500'; // ─

        // 0x61-0x7A: uppercase A-Z
        for (int i = 0; i < 26; i++) {
            t[0x61 + i] = (char) ('A' + i);
        }

        // 0x7B-0x7F: remaining graphics in lowercase mode
        t[0x7B] = '\u253C'; // ┼ BOX DRAWINGS LIGHT VERTICAL AND HORIZONTAL
        t[0x7C] = '\u2592'; // ▒ MEDIUM SHADE (checkerboard)
        t[0x7D] = '\u2502'; // │ BOX DRAWINGS LIGHT VERTICAL
        t[0x7E] = '\u03C0'; // π PI
        t[0x7F] = '\u25E5'; // ◥ BLACK UPPER RIGHT TRIANGLE

        // 0xC0: horizontal bar duplicate
        t[0xC0] = '\u2500'; // ─

        // 0xC1-0xDA: duplicate of lowercase a-z (same as 0x41-0x5A)
        for (int i = 0; i < 26; i++) {
            t[0xC1 + i] = (char) ('a' + i);
        }

        // 0xDB-0xDF: duplicate of graphics at 0x7B-0x7F
        t[0xDB] = '\u253C'; // ┼
        t[0xDC] = '\u2592'; // ▒
        t[0xDD] = '\u2502'; // │
        t[0xDE] = '\u03C0'; // π
        t[0xDF] = '\u25E5'; // ◥

        // 0xE0-0xFE: duplicate of shifted graphics 0xA0-0xBE
        for (int i = 0; i < 31; i++) {
            t[0xE0 + i] = t[0xA0 + i];
        }
    }
}
