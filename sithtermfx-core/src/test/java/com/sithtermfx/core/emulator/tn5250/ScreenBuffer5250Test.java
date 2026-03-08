package com.sithtermfx.core.emulator.tn5250;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ScreenBuffer5250}.
 *
 * @author Daniel Mengel
 */
class ScreenBuffer5250Test {

    private ScreenBuffer5250 createBuffer() {
        return new ScreenBuffer5250(24, 80);
    }

    @Test
    void constructorInitializesDimensions() {
        ScreenBuffer5250 buf = createBuffer();
        assertEquals(24, buf.getRows());
        assertEquals(80, buf.getCols());
    }

    @Test
    void defaultConstructorUses24x80() {
        ScreenBuffer5250 buf = new ScreenBuffer5250();
        assertEquals(ScreenBuffer5250.DEFAULT_ROWS, buf.getRows());
        assertEquals(ScreenBuffer5250.DEFAULT_COLS, buf.getCols());
    }

    @Test
    void clearScreenSetsAllToSpace() {
        ScreenBuffer5250 buf = createBuffer();
        buf.setCharAt(1, 1, 'X');
        buf.setCharAt(12, 40, 'Y');

        buf.clearScreen();

        for (int r = 1; r <= 24; r++) {
            for (int c = 1; c <= 80; c++) {
                assertEquals(' ', buf.getCharAt(r, c),
                        "Position (" + r + "," + c + ") should be space after clear");
            }
        }
    }

    @Test
    void setCharAtAndGetCharAt() {
        ScreenBuffer5250 buf = createBuffer();
        buf.setCharAt(1, 1, 'A');
        buf.setCharAt(24, 80, 'Z');
        buf.setCharAt(12, 40, '!');

        assertEquals('A', buf.getCharAt(1, 1));
        assertEquals('Z', buf.getCharAt(24, 80));
        assertEquals('!', buf.getCharAt(12, 40));
    }

    @Test
    void getCharAtOutOfBoundsReturnsSpace() {
        ScreenBuffer5250 buf = createBuffer();
        assertEquals(' ', buf.getCharAt(0, 0));
        assertEquals(' ', buf.getCharAt(25, 81));
        assertEquals(' ', buf.getCharAt(-1, 1));
    }

    @Test
    void setCharAtOutOfBoundsIsIgnored() {
        ScreenBuffer5250 buf = createBuffer();
        buf.setCharAt(0, 0, 'X');
        buf.setCharAt(25, 81, 'X');
    }

    @Test
    void addFieldAndGetFieldAt() {
        ScreenBuffer5250 buf = createBuffer();
        Field5250 field = new Field5250(1, 10, 20, 0x0000, 0x0000);
        buf.addField(field);

        assertEquals(1, buf.getFields().size());

        Field5250 found = buf.getFieldAt(1, 15);
        assertNotNull(found);
        assertEquals(1, found.getRow());
        assertEquals(10, found.getCol());
    }

    @Test
    void getFieldAtReturnsNullWhenNoFieldCoversPosition() {
        ScreenBuffer5250 buf = createBuffer();
        Field5250 field = new Field5250(1, 10, 20, 0x0000, 0x0000);
        buf.addField(field);

        assertNull(buf.getFieldAt(1, 5));
        assertNull(buf.getFieldAt(2, 10));
    }

    @Test
    void getNextInputFieldSkipsBypassFields() {
        ScreenBuffer5250 buf = createBuffer();
        buf.addField(new Field5250(1, 1, 10, 0x2000, 0x0000));   // bypass
        buf.addField(new Field5250(2, 1, 10, 0x0000, 0x0000));   // input
        buf.addField(new Field5250(3, 1, 10, 0x2000, 0x0000));   // bypass

        Field5250 next = buf.getNextInputField(1, 1);
        assertNotNull(next);
        assertEquals(2, next.getRow());
    }

    @Test
    void getNextInputFieldWrapsAround() {
        ScreenBuffer5250 buf = createBuffer();
        buf.addField(new Field5250(1, 1, 10, 0x0000, 0x0000));   // input
        buf.addField(new Field5250(5, 1, 10, 0x2000, 0x0000));   // bypass

        Field5250 next = buf.getNextInputField(3, 1);
        assertNotNull(next);
        assertEquals(1, next.getRow());
    }

    @Test
    void getNextInputFieldReturnsNullWhenNoInputFields() {
        ScreenBuffer5250 buf = createBuffer();
        buf.addField(new Field5250(1, 1, 10, 0x2000, 0x0000));   // bypass only

        assertNull(buf.getNextInputField(1, 1));
    }

    @Test
    void getPreviousInputFieldNavigation() {
        ScreenBuffer5250 buf = createBuffer();
        buf.addField(new Field5250(1, 1, 10, 0x0000, 0x0000));   // input
        buf.addField(new Field5250(3, 1, 10, 0x0000, 0x0000));   // input
        buf.addField(new Field5250(5, 1, 10, 0x0000, 0x0000));   // input

        Field5250 prev = buf.getPreviousInputField(5, 1);
        assertNotNull(prev);
        assertEquals(3, prev.getRow());
    }

    @Test
    void getPreviousInputFieldWrapsToLastInput() {
        ScreenBuffer5250 buf = createBuffer();
        buf.addField(new Field5250(5, 1, 10, 0x0000, 0x0000));   // input
        buf.addField(new Field5250(10, 1, 10, 0x0000, 0x0000));  // input

        Field5250 prev = buf.getPreviousInputField(1, 1);
        assertNotNull(prev);
        assertEquals(10, prev.getRow());
    }

    @Test
    void getModifiedFieldsReturnsOnlyModifiedInputFields() {
        ScreenBuffer5250 buf = createBuffer();

        Field5250 input1 = new Field5250(1, 1, 10, 0x0000, 0x0000);
        Field5250 input2 = new Field5250(2, 1, 10, 0x0000, 0x0000);
        Field5250 bypass = new Field5250(3, 1, 10, 0x2000, 0x0000);

        buf.addField(input1);
        buf.addField(input2);
        buf.addField(bypass);

        input1.setModified(true);

        List<Field5250> modified = buf.getModifiedFields();
        assertEquals(1, modified.size());
        assertEquals(1, modified.get(0).getRow());
    }

    @Test
    void getModifiedFieldsReturnsEmptyWhenNoneModified() {
        ScreenBuffer5250 buf = createBuffer();
        buf.addField(new Field5250(1, 1, 10, 0x0000, 0x0000));
        buf.addField(new Field5250(2, 1, 10, 0x0000, 0x0000));

        assertTrue(buf.getModifiedFields().isEmpty());
    }

    @Test
    void clearFormatTableClearsFieldsAndScreen() {
        ScreenBuffer5250 buf = createBuffer();
        buf.setCharAt(1, 1, 'X');
        buf.addField(new Field5250(1, 1, 10, 0x0000, 0x0000));

        buf.clearFormatTable();

        assertTrue(buf.getFields().isEmpty());
        assertEquals(' ', buf.getCharAt(1, 1));
        assertEquals(1, buf.getCursorRow());
        assertEquals(1, buf.getCursorCol());
    }

    @Test
    void cursorPositionIsClamped() {
        ScreenBuffer5250 buf = createBuffer();
        buf.setCursorPosition(0, 0);
        assertEquals(1, buf.getCursorRow());
        assertEquals(1, buf.getCursorCol());

        buf.setCursorPosition(100, 200);
        assertEquals(24, buf.getCursorRow());
        assertEquals(80, buf.getCursorCol());
    }

    @Test
    void getFirstInputFieldSkipsBypass() {
        ScreenBuffer5250 buf = createBuffer();
        buf.addField(new Field5250(1, 1, 10, 0x2000, 0x0000));   // bypass
        buf.addField(new Field5250(2, 1, 10, 0x0000, 0x0000));   // input

        Field5250 first = buf.getFirstInputField();
        assertNotNull(first);
        assertEquals(2, first.getRow());
    }

    @Test
    void resetModifiedFlagsClearsAllModified() {
        ScreenBuffer5250 buf = createBuffer();
        Field5250 f1 = new Field5250(1, 1, 10, 0x0000, 0x0000);
        Field5250 f2 = new Field5250(2, 1, 10, 0x0000, 0x0000);
        f1.setModified(true);
        f2.setModified(true);
        buf.addField(f1);
        buf.addField(f2);

        buf.resetModifiedFlags();

        assertFalse(f1.isModified());
        assertFalse(f2.isModified());
    }

    @Test
    void getRowTextReturnsRowContent() {
        ScreenBuffer5250 buf = createBuffer();
        buf.setCharAt(1, 1, 'H');
        buf.setCharAt(1, 2, 'i');

        String row = buf.getRowText(1);
        assertEquals(80, row.length());
        assertEquals('H', row.charAt(0));
        assertEquals('i', row.charAt(1));
    }
}
