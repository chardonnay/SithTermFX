package com.sithtermfx.core.emulator.petscii;

import com.sithtermfx.core.DataStreamIteratingEmulator;
import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalColor;
import com.sithtermfx.core.TerminalDataStream;
import com.sithtermfx.core.TextStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Commodore PETSCII terminal emulator for C64 and C128.
 * <p>
 * PETSCII is fundamentally different from ANSI/VT terminals: it uses single-byte
 * inline control codes rather than multi-byte ESC sequences. Every control function
 * (cursor movement, color changes, character set switching, screen clearing) is
 * encoded as a single byte in the 0x00-0x1F and 0x80-0x9F ranges.
 * <p>
 * The emulator maintains three key state variables:
 * <ul>
 *   <li>Character set mode (uppercase/graphics vs. lowercase/uppercase)</li>
 *   <li>Reverse video mode (RVS ON/OFF)</li>
 *   <li>Current foreground color (one of 16 C64 colors)</li>
 * </ul>
 * Default configuration is C64: 40 columns, 25 rows, light blue text on blue background.
 * C128 mode uses 80 columns with additional color codes at 0x85-0x8A.
 *
 * @author Daniel Mengel
 */
public class PetsciiEmulator extends DataStreamIteratingEmulator {

    private static final Logger logger = LoggerFactory.getLogger(PetsciiEmulator.class);

    private static final int C64_COLUMNS = 40;
    private static final int C128_COLUMNS = 80;

    // C64/C128 color palette indices (matches VIC-II / VDC hardware order)
    private static final int COLOR_BLACK = 0;
    private static final int COLOR_WHITE = 1;
    private static final int COLOR_RED = 2;
    private static final int COLOR_CYAN = 3;
    private static final int COLOR_PURPLE = 4;
    private static final int COLOR_GREEN = 5;
    private static final int COLOR_BLUE = 6;
    private static final int COLOR_YELLOW = 7;
    private static final int COLOR_ORANGE = 8;
    private static final int COLOR_BROWN = 9;
    private static final int COLOR_LIGHT_RED = 10;
    private static final int COLOR_DARK_GREY = 11;
    private static final int COLOR_MEDIUM_GREY = 12;
    private static final int COLOR_LIGHT_GREEN = 13;
    private static final int COLOR_LIGHT_BLUE = 14;
    private static final int COLOR_LIGHT_GREY = 15;

    private boolean myUppercaseGraphicsMode = true;
    private boolean myReverseMode = false;
    private int myCurrentColor = COLOR_LIGHT_BLUE;
    private final int myColumns;

    /**
     * Creates a PETSCII emulator in C64 mode (40 columns).
     */
    public PetsciiEmulator(TerminalDataStream dataStream, Terminal terminal) {
        this(dataStream, terminal, C64_COLUMNS);
    }

    /**
     * Creates a PETSCII emulator with a specific column width.
     *
     * @param columns 40 for C64 mode, 80 for C128 mode
     */
    public PetsciiEmulator(TerminalDataStream dataStream, Terminal terminal, int columns) {
        super(dataStream, terminal);
        myColumns = columns;
    }

    /**
     * Creates a PETSCII emulator in C128 80-column mode.
     */
    public static PetsciiEmulator createC128(TerminalDataStream dataStream, Terminal terminal) {
        return new PetsciiEmulator(dataStream, terminal, C128_COLUMNS);
    }

    @Override
    protected void processChar(char ch, Terminal terminal) throws IOException {
        int code = ch & 0xFF;

        switch (code) {
            // ── Carriage return ──
            case 0x0D -> {
                terminal.carriageReturn();
                terminal.newLine();
            }

            // ── Character set switching ──
            case 0x0E -> switchToLowercaseUppercase();
            case 0x8E -> switchToUppercaseGraphics();

            // ── Cursor movement ──
            case 0x11 -> terminal.cursorDown(1);
            case 0x91 -> terminal.cursorUp(1);
            case 0x1D -> terminal.cursorForward(1);
            case 0x9D -> terminal.cursorBackward(1);
            case 0x13 -> terminal.cursorPosition(1, 1); // HOME

            // ── Reverse video ──
            case 0x12 -> setReverseMode(true);
            case 0x92 -> setReverseMode(false);

            // ── Editing ──
            case 0x14 -> handleDelete(terminal);
            case 0x93 -> handleClearScreen(terminal);
            case 0x94 -> terminal.insertBlankCharacters(1);

            // ── Color codes (low range: 0x00-0x1F) ──
            case 0x05 -> setColor(COLOR_WHITE);
            case 0x1C -> setColor(COLOR_RED);
            case 0x1E -> setColor(COLOR_GREEN);
            case 0x1F -> setColor(COLOR_BLUE);

            // ── Color codes (high range: 0x80-0x9F) ──
            case 0x81 -> setColor(COLOR_ORANGE);
            case 0x90 -> setColor(COLOR_BLACK);
            case 0x95 -> setColor(COLOR_BROWN);
            case 0x96 -> setColor(COLOR_LIGHT_RED);
            case 0x97 -> setColor(COLOR_DARK_GREY);
            case 0x98 -> setColor(COLOR_MEDIUM_GREY);
            case 0x99 -> setColor(COLOR_LIGHT_GREEN);
            case 0x9A -> setColor(COLOR_LIGHT_BLUE);
            case 0x9B -> setColor(COLOR_LIGHT_GREY);
            case 0x9C -> setColor(COLOR_PURPLE);
            case 0x9E -> setColor(COLOR_YELLOW);
            case 0x9F -> setColor(COLOR_CYAN);

            // ── C128 extended color codes (0x85-0x8A) ──
            // In C128 80-column mode these provide alternate access to colors;
            // on C64 they are function key codes (handled only by the key encoder).
            case 0x85 -> setColor(COLOR_ORANGE);
            case 0x86 -> setColor(COLOR_BROWN);
            case 0x87 -> setColor(COLOR_LIGHT_RED);
            case 0x88 -> setColor(COLOR_DARK_GREY);
            case 0x89 -> setColor(COLOR_MEDIUM_GREY);
            case 0x8A -> setColor(COLOR_LIGHT_GREEN);

            default -> {
                if (isPrintable(code)) {
                    outputCharacter(code, terminal);
                } else if (code == 0x07) {
                    terminal.beep();
                } else if (logger.isDebugEnabled()) {
                    logger.debug("Unhandled PETSCII control code: 0x{}", String.format("%02X", code));
                }
            }
        }
    }

    private boolean isPrintable(int code) {
        return (code >= 0x20 && code <= 0x7E)
                || (code >= 0xA0 && code <= 0xFF);
    }

    private void outputCharacter(int petsciiCode, Terminal terminal) {
        char unicode = PetsciiCharacterSet.mapToUnicode(petsciiCode, myUppercaseGraphicsMode);
        if (unicode != 0) {
            applyCurrentStyle();
            terminal.writeCharacters(String.valueOf(unicode));
        }
    }

    private void setColor(int colorIndex) {
        myCurrentColor = colorIndex;
        applyCurrentStyle();
    }

    private void applyCurrentStyle() {
        TextStyle.Builder builder = new TextStyle.Builder()
                .setForeground(new TerminalColor(myCurrentColor));
        if (myReverseMode) {
            builder.setOption(TextStyle.Option.INVERSE, true);
        }
        myTerminal.characterAttributes(builder.build());
    }

    private void setReverseMode(boolean on) {
        myReverseMode = on;
        applyCurrentStyle();
    }

    private void switchToLowercaseUppercase() {
        myUppercaseGraphicsMode = false;
        if (logger.isDebugEnabled()) {
            logger.debug("PETSCII: switched to lowercase/uppercase character set");
        }
    }

    private void switchToUppercaseGraphics() {
        myUppercaseGraphicsMode = true;
        if (logger.isDebugEnabled()) {
            logger.debug("PETSCII: switched to uppercase/graphics character set");
        }
    }

    /**
     * PETSCII DEL (0x14): moves cursor left one position, erases the character
     * at that position, then shifts remaining characters on the line left.
     */
    private void handleDelete(Terminal terminal) {
        terminal.cursorBackward(1);
        terminal.deleteCharacters(1);
    }

    /**
     * PETSCII CLR (0x93): clears the entire screen and homes the cursor.
     * Also resets reverse video mode per C64 KERNAL behavior.
     */
    private void handleClearScreen(Terminal terminal) {
        terminal.clearScreen();
        terminal.cursorPosition(1, 1);
        myReverseMode = false;
        applyCurrentStyle();
    }

    public boolean isUppercaseGraphicsMode() {
        return myUppercaseGraphicsMode;
    }

    public boolean isReverseMode() {
        return myReverseMode;
    }

    public int getCurrentColor() {
        return myCurrentColor;
    }

    public int getColumns() {
        return myColumns;
    }
}
