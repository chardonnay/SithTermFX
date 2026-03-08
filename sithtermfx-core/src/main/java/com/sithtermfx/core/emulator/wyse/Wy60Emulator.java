package com.sithtermfx.core.emulator.wyse;

import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalDataStream;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.util.Ascii;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Emulates the Wyse WY-60 terminal — a superset of the WY-50 adding
 * personality mode switching, an extended attribute set, and additional
 * ESC sequences for programmable function keys and display pages.
 * <p>
 * Personality modes allow the WY-60 to emulate other terminal types
 * (WY-50, TVI-910/925, ADDS-VP/A2). In SithTermFX only native WY-60
 * personality is fully supported; other personality requests are logged
 * and ignored.
 * <p>
 * Enhanced attributes over the WY-50 include bold (in addition to dim),
 * and support for write-protected fields with distinct visual rendering.
 *
 * @author Daniel Mengel
 */
public class Wy60Emulator extends AbstractWyseEmulator {

    private static final Logger logger = LoggerFactory.getLogger(Wy60Emulator.class);

    private static final int WYSE_LEVEL = 60;

    /** Active personality: 0 = native WY-60, 1 = WY-50, 2 = TVI-925, etc. */
    private int myPersonality = 0;

    /** Number of display pages (WY-60 supports 2 or 4 pages in 80-col mode). */
    private int myDisplayPage = 0;

    public Wy60Emulator(@NotNull TerminalDataStream dataStream, @NotNull Terminal terminal) {
        super(dataStream, terminal);
        if (logger.isDebugEnabled()) {
            logger.debug("WY-60 emulator initialised");
        }
    }

    @Override
    public int getWyseLevel() {
        return WYSE_LEVEL;
    }

    @Override
    protected void processWyseEscape(char cmd, Terminal terminal) throws IOException {
        switch (cmd) {
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
            case 'e':
                terminal.setModeEnabled(com.sithtermfx.core.TerminalMode.InsertMode, true);
                return;
            case 'r':
                terminal.setModeEnabled(com.sithtermfx.core.TerminalMode.InsertMode, false);
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
     * Processes {@code ESC c <personality>}: personality mode switch.
     * <ul>
     *   <li>'0' = Wyse native (WY-60)</li>
     *   <li>'1' = WY-50 compatible</li>
     *   <li>'2' = TVI-925 compatible</li>
     *   <li>'3' = ADDS VP/A2 compatible</li>
     * </ul>
     */
    private void processPersonalitySwitch(Terminal terminal) throws IOException {
        char p = myDataStream.getChar();
        int newPersonality = p - '0';
        if (newPersonality < 0 || newPersonality > 3) {
            if (logger.isDebugEnabled()) {
                logger.debug("WY-60: invalid personality '{}', ignoring", p);
            }
            return;
        }
        myPersonality = newPersonality;
        if (logger.isDebugEnabled()) {
            String[] names = {"WY-60 native", "WY-50", "TVI-925", "ADDS VP/A2"};
            logger.debug("WY-60: personality switched to {} ({})", myPersonality,
                    names[myPersonality]);
        }
    }

    /**
     * Processes {@code ESC d <attrib>}: enhanced WY-60 attribute selection.
     * This extends the WY-50 attribute byte with an additional bold bit:
     * <ul>
     *   <li>Bit 0 (0x01): blank / invisible</li>
     *   <li>Bit 1 (0x02): blink</li>
     *   <li>Bit 2 (0x04): reverse video</li>
     *   <li>Bit 3 (0x08): underline</li>
     *   <li>Bit 4 (0x10): dim</li>
     *   <li>Bit 5 (0x20): bold</li>
     * </ul>
     */
    private void processEnhancedAttribute(Terminal terminal) throws IOException {
        char attrib = myDataStream.getChar();
        int bits = attrib - '0';
        if (logger.isTraceEnabled()) {
            logger.trace("WY-60: enhanced attribute 0x{} (bits=0x{})",
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
     * Processes {@code ESC w <page>}: switch display page.
     */
    private void processDisplayPage(Terminal terminal) throws IOException {
        char page = myDataStream.getChar();
        int pageNum = page - '0';
        if (pageNum < 0 || pageNum > 3) {
            logger.warn("WY-60: invalid display page '{}' (0x{}), ignoring",
                    page, Integer.toHexString(page));
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("WY-60: switch to display page {}", pageNum);
        }
        myDisplayPage = pageNum;
    }

    /**
     * Responds to {@code ESC Z} — identify terminal. Sends back the WY-60
     * device attribute response.
     */
    private void sendIdentification(Terminal terminal) {
        if (logger.isDebugEnabled()) {
            logger.debug("WY-60: device identification requested");
        }
        terminal.deviceAttributes("60\r".getBytes());
    }

    /**
     * Processes {@code ESC z <line>}: display message on the status line.
     * Reads until CR.
     */
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
            logger.trace("WY-60: status line (mode={}): {}", sub, sb);
        }
        terminal.setWindowTitle(sb.toString());
    }

    /**
     * Processes {@code ESC A <key> <label> CR}: program a function key label
     * or redefinition. Reads the key identifier and label string until CR.
     */
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
            logger.debug("WY-60: function key definition keyId='{}' content='{}'", keyId, content);
        }
    }

    public int getPersonality() {
        return myPersonality;
    }

    public int getDisplayPage() {
        return myDisplayPage;
    }
}
