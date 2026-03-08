package com.sithtermfx.core.emulator.wyse;

import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalColor;
import com.sithtermfx.core.TerminalDataStream;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.util.Ascii;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Emulates the Wyse WY-160 terminal — a color-capable member of the Wyse
 * family that extends the WY-60 feature set with 16-color foreground and
 * background support.
 * <p>
 * Color is selected through the {@code ESC e <fg> <bg>} sequence, where
 * {@code <fg>} and {@code <bg>} are single-character color indices biased
 * by 0x30 ('0'). The mapping follows standard CGA ordering:
 * <pre>
 *   0 = black    4 = blue
 *   1 = red      5 = magenta
 *   2 = green    6 = cyan
 *   3 = yellow   7 = white
 * </pre>
 * Values 8–15 represent the bright (high-intensity) variants.
 * <p>
 * All WY-60 sequences are supported; additionally the WY-160 responds to
 * device attribute queries with a level-160 identification.
 *
 * @author Daniel Mengel
 */
public class Wy160Emulator extends AbstractWyseEmulator {

    private static final Logger logger = LoggerFactory.getLogger(Wy160Emulator.class);

    private static final int WYSE_LEVEL = 160;

    /**
     * CGA-order to ANSI color index mapping.
     * Index 0-7 = normal, 8-15 = bright.
     */
    private static final int[] CGA_TO_ANSI = {
            0, 1, 2, 3, 4, 5, 6, 7,
            8, 9, 10, 11, 12, 13, 14, 15
    };

    private int myPersonality = 0;
    private int myDisplayPage = 0;

    public Wy160Emulator(@NotNull TerminalDataStream dataStream, @NotNull Terminal terminal) {
        super(dataStream, terminal);
        if (logger.isDebugEnabled()) {
            logger.debug("WY-160 emulator initialised");
        }
    }

    @Override
    public int getWyseLevel() {
        return WYSE_LEVEL;
    }

    @Override
    protected void processWyseEscape(char cmd, Terminal terminal) throws IOException {
        switch (cmd) {
            case 'e':
                processColorAttribute(terminal);
                return;
            case 'c':
                processPersonalitySwitch(terminal);
                return;
            case 'd':
                processEnhancedAttribute(terminal);
                return;
            case 'w':
                processDisplayPage(terminal);
                return;
            case 'Z':
                sendIdentification(terminal);
                return;
            case 'z':
                processStatusLine(terminal);
                return;
            case 'A':
                processFunctionKeyDefinition(terminal);
                return;
            case '{':
                terminal.setModeEnabled(com.sithtermfx.core.TerminalMode.AutoWrap, true);
                return;
            case '}':
                terminal.setModeEnabled(com.sithtermfx.core.TerminalMode.AutoWrap, false);
                return;
            default:
                break;
        }
        super.processWyseEscape(cmd, terminal);
    }

    /**
     * Processes {@code ESC e <fg> <bg>}: set foreground and background color.
     * Each color byte is an ASCII character biased by 0x30 ('0'), giving a
     * value 0–15 that maps to the CGA color palette.
     */
    private void processColorAttribute(Terminal terminal) throws IOException {
        char fgChar = myDataStream.getChar();
        char bgChar = myDataStream.getChar();
        int fg = fgChar - '0';
        int bg = bgChar - '0';
        if (fg < 0 || fg > 15) fg = 7;
        if (bg < 0 || bg > 15) bg = 0;
        if (logger.isTraceEnabled()) {
            logger.trace("WY-160: color fg={} bg={}", fg, bg);
        }
        TerminalColor fgColor = TerminalColor.index(CGA_TO_ANSI[fg]);
        TerminalColor bgColor = TerminalColor.index(CGA_TO_ANSI[bg]);
        TextStyle style = new TextStyle(fgColor, bgColor);
        terminal.characterAttributes(style);
    }

    /**
     * Processes {@code ESC d <attrib>}: enhanced attribute selection (same as
     * WY-60 — includes bold bit on top of standard Wyse attribute bits).
     */
    private void processEnhancedAttribute(Terminal terminal) throws IOException {
        char attrib = myDataStream.getChar();
        int bits = attrib - '0';
        if (logger.isTraceEnabled()) {
            logger.trace("WY-160: enhanced attribute 0x{} (bits=0x{})",
                    Integer.toHexString(attrib), Integer.toHexString(bits));
        }
        TextStyle.Builder builder = new TextStyle.Builder();
        builder.setOption(TextStyle.Option.HIDDEN, (bits & 0x01) != 0);
        builder.setOption(TextStyle.Option.SLOW_BLINK, (bits & 0x02) != 0);
        builder.setOption(TextStyle.Option.INVERSE, (bits & 0x04) != 0);
        builder.setOption(TextStyle.Option.UNDERLINED, (bits & 0x08) != 0);
        builder.setOption(TextStyle.Option.DIM, (bits & 0x10) != 0);
        builder.setOption(TextStyle.Option.BOLD, (bits & 0x20) != 0);
        terminal.characterAttributes(builder.build());
    }

    /**
     * Processes {@code ESC c <personality>}: personality mode switch.
     */
    private void processPersonalitySwitch(Terminal terminal) throws IOException {
        char p = myDataStream.getChar();
        int newPersonality = p - '0';
        if (newPersonality < 0 || newPersonality > 3) {
            if (logger.isDebugEnabled()) {
                logger.debug("WY-160: invalid personality '{}', ignoring", p);
            }
            return;
        }
        myPersonality = newPersonality;
        if (logger.isDebugEnabled()) {
            String[] names = {"WY-160 native", "WY-50", "TVI-925", "ADDS VP/A2"};
            logger.debug("WY-160: personality switched to {} ({})", myPersonality,
                    names[myPersonality]);
        }
    }

    /**
     * Processes {@code ESC w <page>}: switch display page.
     */
    private void processDisplayPage(Terminal terminal) throws IOException {
        char page = myDataStream.getChar();
        int pageNum = page - '0';
        if (logger.isDebugEnabled()) {
            logger.debug("WY-160: switch to display page {}", pageNum);
        }
        myDisplayPage = pageNum;
    }

    /**
     * Responds to {@code ESC Z} — identify terminal. Returns level 160.
     */
    private void sendIdentification(Terminal terminal) {
        if (logger.isDebugEnabled()) {
            logger.debug("WY-160: device identification requested");
        }
        terminal.deviceAttributes("160\r".getBytes());
    }

    private void processStatusLine(Terminal terminal) throws IOException {
        char sub = myDataStream.getChar();
        StringBuilder sb = new StringBuilder();
        try {
            while (true) {
                char c = myDataStream.getChar();
                if (c == Ascii.CR) break;
                sb.append(c);
            }
        } catch (TerminalDataStream.EOF ignored) {
        }
        if (logger.isTraceEnabled()) {
            logger.trace("WY-160: status line (mode={}): {}", sub, sb);
        }
        terminal.setWindowTitle(sb.toString());
    }

    private void processFunctionKeyDefinition(Terminal terminal) throws IOException {
        char keyId = myDataStream.getChar();
        StringBuilder content = new StringBuilder();
        try {
            while (true) {
                char c = myDataStream.getChar();
                if (c == Ascii.CR) break;
                content.append(c);
            }
        } catch (TerminalDataStream.EOF ignored) {
        }
        if (logger.isDebugEnabled()) {
            logger.debug("WY-160: function key definition keyId='{}' content='{}'", keyId, content);
        }
    }

    public int getPersonality() {
        return myPersonality;
    }

    public int getDisplayPage() {
        return myDisplayPage;
    }
}
