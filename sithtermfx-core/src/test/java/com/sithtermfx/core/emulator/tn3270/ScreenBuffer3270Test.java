package com.sithtermfx.core.emulator.tn3270;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ScreenBuffer3270}.
 *
 * @author Daniel Mengel
 */
class ScreenBuffer3270Test {

    private static final int ROWS = 24;
    private static final int COLS = 80;

    private ScreenBuffer3270 createBuffer() {
        return new ScreenBuffer3270(ROWS, COLS);
    }

    @Test
    void constructorInitializesCorrectDimensions() {
        ScreenBuffer3270 buf = createBuffer();
        assertEquals(ROWS, buf.getRows());
        assertEquals(COLS, buf.getCols());
        assertEquals(ROWS * COLS, buf.getSize());
    }

    @Test
    void clearScreenSetsAllPositionsToSpace() {
        ScreenBuffer3270 buf = createBuffer();
        buf.setCharAt(0, 'X');
        buf.setCharAt(100, 'Y');
        buf.clearScreen();

        for (int i = 0; i < buf.getSize(); i++) {
            assertEquals(' ', buf.getCharAt(i), "Position " + i + " should be space after clear");
        }
    }

    @Test
    void clearScreenRemovesAllFields() {
        ScreenBuffer3270 buf = createBuffer();
        buf.addField(0, new FieldAttribute3270(0x00));
        buf.addField(10, new FieldAttribute3270(0x20));

        buf.clearScreen();
        assertTrue(buf.getFields().isEmpty());
    }

    @Test
    void setCharAtAndGetCharAt() {
        ScreenBuffer3270 buf = createBuffer();
        buf.setCharAt(0, 'A');
        buf.setCharAt(79, 'Z');
        buf.setCharAt(1919, '!');

        assertEquals('A', buf.getCharAt(0));
        assertEquals('Z', buf.getCharAt(79));
        assertEquals('!', buf.getCharAt(1919));
    }

    @Test
    void getCharAtOutOfBoundsReturnsSpace() {
        ScreenBuffer3270 buf = createBuffer();
        assertEquals(' ', buf.getCharAt(-1));
        assertEquals(' ', buf.getCharAt(ROWS * COLS));
    }

    @Test
    void setCharAtOutOfBoundsIsIgnored() {
        ScreenBuffer3270 buf = createBuffer();
        buf.setCharAt(-1, 'X');
        buf.setCharAt(ROWS * COLS, 'X');
    }

    @Test
    void addFieldCreatesFieldCorrectly() {
        ScreenBuffer3270 buf = createBuffer();
        FieldAttribute3270 attr = new FieldAttribute3270(0x00);
        Field3270 field = buf.addField(0, attr);

        assertNotNull(field);
        assertEquals(0, field.getStartPosition());
        assertFalse(field.isProtected());
        assertEquals(1, buf.getFields().size());
    }

    @Test
    void addMultipleFieldsCalculatesLengths() {
        ScreenBuffer3270 buf = createBuffer();
        buf.addField(0, new FieldAttribute3270(0x00));
        buf.addField(40, new FieldAttribute3270(0x20));

        List<Field3270> fields = buf.getFields();
        assertEquals(2, fields.size());

        Field3270 first = fields.get(0);
        Field3270 second = fields.get(1);
        assertEquals(0, first.getStartPosition());
        assertEquals(40, second.getStartPosition());
        assertEquals(39, first.getLength());
    }

    @Test
    void getFieldAtReturnsCorrectField() {
        ScreenBuffer3270 buf = createBuffer();
        buf.addField(0, new FieldAttribute3270(0x00));
        buf.addField(40, new FieldAttribute3270(0x20));

        Field3270 fieldAtPos5 = buf.getFieldAt(5);
        assertNotNull(fieldAtPos5);
        assertEquals(0, fieldAtPos5.getStartPosition());

        Field3270 fieldAtPos50 = buf.getFieldAt(50);
        assertNotNull(fieldAtPos50);
        assertEquals(40, fieldAtPos50.getStartPosition());
    }

    @Test
    void getFieldAtReturnsNullForOutOfBounds() {
        ScreenBuffer3270 buf = createBuffer();
        assertNull(buf.getFieldAt(-1));
        assertNull(buf.getFieldAt(ROWS * COLS));
    }

    @Test
    void nextUnprotectedFieldSkipsProtected() {
        ScreenBuffer3270 buf = createBuffer();
        buf.addField(0, new FieldAttribute3270(0x20));   // protected
        buf.addField(40, new FieldAttribute3270(0x00));   // unprotected
        buf.addField(80, new FieldAttribute3270(0x20));   // protected

        int next = buf.nextUnprotectedField(0);
        assertEquals(41, next);
    }

    @Test
    void nextUnprotectedFieldReturnsMinusOneWhenNoFields() {
        ScreenBuffer3270 buf = createBuffer();
        assertEquals(-1, buf.nextUnprotectedField(0));
    }

    @Test
    void nextUnprotectedFieldWrapsAround() {
        ScreenBuffer3270 buf = createBuffer();
        buf.addField(0, new FieldAttribute3270(0x00));    // unprotected at start
        buf.addField(100, new FieldAttribute3270(0x20));  // protected

        int next = buf.nextUnprotectedField(50);
        assertEquals(1, next);
    }

    @Test
    void getAllModifiedFieldsReturnsOnlyMdtFields() {
        ScreenBuffer3270 buf = createBuffer();
        FieldAttribute3270 attr1 = new FieldAttribute3270(0x00);
        FieldAttribute3270 attr2 = new FieldAttribute3270(0x01); // MDT set
        FieldAttribute3270 attr3 = new FieldAttribute3270(0x00);

        buf.addField(0, attr1);
        buf.addField(40, attr2);
        buf.addField(80, attr3);

        List<Field3270> modified = buf.getAllModifiedFields();
        assertEquals(1, modified.size());
        assertEquals(40, modified.get(0).getStartPosition());
    }

    @Test
    void getAllModifiedFieldsReturnsEmptyWhenNoneModified() {
        ScreenBuffer3270 buf = createBuffer();
        buf.addField(0, new FieldAttribute3270(0x00));
        buf.addField(40, new FieldAttribute3270(0x00));

        assertTrue(buf.getAllModifiedFields().isEmpty());
    }

    @Test
    void getBufferAddressCalculatesCorrectly() {
        ScreenBuffer3270 buf = createBuffer();
        assertEquals(0, buf.getBufferAddress(0, 0));
        assertEquals(80, buf.getBufferAddress(1, 0));
        assertEquals(81, buf.getBufferAddress(1, 1));
        assertEquals(1919, buf.getBufferAddress(23, 79));
    }

    @Test
    void getRowAndGetCol() {
        ScreenBuffer3270 buf = createBuffer();
        assertEquals(0, buf.getRow(0));
        assertEquals(0, buf.getCol(0));
        assertEquals(1, buf.getRow(80));
        assertEquals(0, buf.getCol(80));
        assertEquals(1, buf.getRow(81));
        assertEquals(1, buf.getCol(81));
    }

    @Test
    void getAttributeAtReturnsNullWhenNoFieldStart() {
        ScreenBuffer3270 buf = createBuffer();
        assertNull(buf.getAttributeAt(0));
    }

    @Test
    void getAttributeAtReturnsAttributeAtFieldStart() {
        ScreenBuffer3270 buf = createBuffer();
        FieldAttribute3270 attr = new FieldAttribute3270(0x20);
        buf.addField(10, attr);
        assertNotNull(buf.getAttributeAt(10));
        assertTrue(buf.getAttributeAt(10).isProtected());
    }

    @Test
    void getEffectiveAttributeFindsNearestFieldStart() {
        ScreenBuffer3270 buf = createBuffer();
        FieldAttribute3270 attr = new FieldAttribute3270(0x20);
        buf.addField(10, attr);

        FieldAttribute3270 effective = buf.getEffectiveAttribute(15);
        assertNotNull(effective);
        assertTrue(effective.isProtected());
    }
}
