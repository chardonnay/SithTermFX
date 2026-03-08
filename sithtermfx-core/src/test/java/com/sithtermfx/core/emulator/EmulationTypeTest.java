package com.sithtermfx.core.emulator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EmulationType}.
 *
 * @author Daniel Mengel
 */
class EmulationTypeTest {

    @Test
    void allEnumValuesExist() {
        EmulationType[] expected = {
                EmulationType.VT100, EmulationType.VT102, EmulationType.VT220,
                EmulationType.VT320, EmulationType.VT420, EmulationType.VT520,
                EmulationType.TN3270, EmulationType.TN5250,
                EmulationType.SUN_CDE,
                EmulationType.WY50, EmulationType.WY60, EmulationType.WY160,
                EmulationType.TVI910, EmulationType.TVI920, EmulationType.TVI925,
                EmulationType.HP2392, EmulationType.HP700_92,
                EmulationType.SCOANSI, EmulationType.CTERM, EmulationType.PETSCII,
                EmulationType.XTERM
        };
        assertEquals(expected.length, EmulationType.values().length);
        for (EmulationType type : expected) {
            assertNotNull(type);
        }
    }

    @Test
    void getTermNameReturnsNonNullForAll() {
        for (EmulationType type : EmulationType.values()) {
            assertNotNull(type.getTermName(), type.name() + " termName should not be null");
            assertFalse(type.getTermName().isEmpty(), type.name() + " termName should not be empty");
        }
    }

    @Test
    void getDisplayNameReturnsNonNullForAll() {
        for (EmulationType type : EmulationType.values()) {
            assertNotNull(type.getDisplayName(), type.name() + " displayName should not be null");
            assertFalse(type.getDisplayName().isEmpty(), type.name() + " displayName should not be empty");
        }
    }

    @Test
    void getDefaultColumnsIsPositive() {
        for (EmulationType type : EmulationType.values()) {
            assertTrue(type.getDefaultColumns() > 0,
                    type.name() + " defaultColumns should be > 0, was " + type.getDefaultColumns());
        }
    }

    @Test
    void getDefaultRowsIsPositive() {
        for (EmulationType type : EmulationType.values()) {
            assertTrue(type.getDefaultRows() > 0,
                    type.name() + " defaultRows should be > 0, was " + type.getDefaultRows());
        }
    }

    @Test
    void isBlockModeTrueOnlyForTn3270AndTn5250() {
        assertTrue(EmulationType.TN3270.isBlockMode());
        assertTrue(EmulationType.TN5250.isBlockMode());

        for (EmulationType type : EmulationType.values()) {
            if (type != EmulationType.TN3270 && type != EmulationType.TN5250) {
                assertFalse(type.isBlockMode(),
                        type.name() + " should not be block mode");
            }
        }
    }

    @Test
    void isVtFamilyTrueForVtAndXterm() {
        assertTrue(EmulationType.VT100.isVtFamily());
        assertTrue(EmulationType.VT102.isVtFamily());
        assertTrue(EmulationType.VT220.isVtFamily());
        assertTrue(EmulationType.VT320.isVtFamily());
        assertTrue(EmulationType.VT420.isVtFamily());
        assertTrue(EmulationType.VT520.isVtFamily());
        assertTrue(EmulationType.XTERM.isVtFamily());
    }

    @Test
    void isVtFamilyFalseForNonVtTypes() {
        EmulationType[] nonVt = {
                EmulationType.TN3270, EmulationType.TN5250,
                EmulationType.SUN_CDE,
                EmulationType.WY50, EmulationType.WY60, EmulationType.WY160,
                EmulationType.TVI910, EmulationType.TVI920, EmulationType.TVI925,
                EmulationType.HP2392, EmulationType.HP700_92,
                EmulationType.SCOANSI, EmulationType.CTERM, EmulationType.PETSCII
        };
        for (EmulationType type : nonVt) {
            assertFalse(type.isVtFamily(), type.name() + " should not be VT family");
        }
    }

    @Test
    void getScreenModeIsNonNull() {
        for (EmulationType type : EmulationType.values()) {
            assertNotNull(type.getScreenMode(), type.name() + " screenMode should not be null");
        }
    }

    @Test
    void getColorSupportIsNonNull() {
        for (EmulationType type : EmulationType.values()) {
            assertNotNull(type.getColorSupport(), type.name() + " colorSupport should not be null");
        }
    }

    @Test
    void specificTermNames() {
        assertEquals("vt100", EmulationType.VT100.getTermName());
        assertEquals("xterm-256color", EmulationType.XTERM.getTermName());
        assertEquals("IBM-3279-2-E", EmulationType.TN3270.getTermName());
        assertEquals("petscii", EmulationType.PETSCII.getTermName());
    }

    @Test
    void petsciiHas40Columns() {
        assertEquals(40, EmulationType.PETSCII.getDefaultColumns());
        assertEquals(25, EmulationType.PETSCII.getDefaultRows());
    }

    @Test
    void scoAnsiHas25Rows() {
        assertEquals(80, EmulationType.SCOANSI.getDefaultColumns());
        assertEquals(25, EmulationType.SCOANSI.getDefaultRows());
    }
}
