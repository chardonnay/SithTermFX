package com.sithtermfx.core.emulator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VtEmulationLevel}.
 *
 * @author Daniel Mengel
 */
class VtEmulationLevelTest {

    @Test
    void fromEmulationTypeMapsVtTypes() {
        assertEquals(VtEmulationLevel.VT100, VtEmulationLevel.fromEmulationType(EmulationType.VT100));
        assertEquals(VtEmulationLevel.VT102, VtEmulationLevel.fromEmulationType(EmulationType.VT102));
        assertEquals(VtEmulationLevel.VT220, VtEmulationLevel.fromEmulationType(EmulationType.VT220));
        assertEquals(VtEmulationLevel.VT320, VtEmulationLevel.fromEmulationType(EmulationType.VT320));
        assertEquals(VtEmulationLevel.VT420, VtEmulationLevel.fromEmulationType(EmulationType.VT420));
        assertEquals(VtEmulationLevel.VT520, VtEmulationLevel.fromEmulationType(EmulationType.VT520));
    }

    @Test
    void fromEmulationTypeDefaultsToVt100ForNonVtTypes() {
        assertEquals(VtEmulationLevel.VT100, VtEmulationLevel.fromEmulationType(EmulationType.TN3270));
        assertEquals(VtEmulationLevel.VT100, VtEmulationLevel.fromEmulationType(EmulationType.XTERM));
        assertEquals(VtEmulationLevel.VT100, VtEmulationLevel.fromEmulationType(EmulationType.PETSCII));
    }

    @Test
    void isAtLeastOrdering() {
        assertTrue(VtEmulationLevel.VT520.isAtLeast(VtEmulationLevel.VT100));
        assertTrue(VtEmulationLevel.VT520.isAtLeast(VtEmulationLevel.VT220));
        assertTrue(VtEmulationLevel.VT520.isAtLeast(VtEmulationLevel.VT520));
        assertTrue(VtEmulationLevel.VT420.isAtLeast(VtEmulationLevel.VT320));
        assertTrue(VtEmulationLevel.VT220.isAtLeast(VtEmulationLevel.VT100));
        assertTrue(VtEmulationLevel.VT100.isAtLeast(VtEmulationLevel.VT100));

        assertFalse(VtEmulationLevel.VT100.isAtLeast(VtEmulationLevel.VT220));
        assertFalse(VtEmulationLevel.VT102.isAtLeast(VtEmulationLevel.VT220));
        assertFalse(VtEmulationLevel.VT220.isAtLeast(VtEmulationLevel.VT320));
        assertFalse(VtEmulationLevel.VT320.isAtLeast(VtEmulationLevel.VT420));
    }

    @Test
    void supports8BitControlsFalseForVt100AndVt102() {
        assertFalse(VtEmulationLevel.VT100.supports8BitControls());
        assertFalse(VtEmulationLevel.VT102.supports8BitControls());
    }

    @Test
    void supports8BitControlsTrueForVt220AndAbove() {
        assertTrue(VtEmulationLevel.VT220.supports8BitControls());
        assertTrue(VtEmulationLevel.VT320.supports8BitControls());
        assertTrue(VtEmulationLevel.VT420.supports8BitControls());
        assertTrue(VtEmulationLevel.VT520.supports8BitControls());
    }

    @Test
    void supportsRectangularAreaOpsTrueOnlyForVt420AndVt520() {
        assertFalse(VtEmulationLevel.VT100.supportsRectangularAreaOps());
        assertFalse(VtEmulationLevel.VT102.supportsRectangularAreaOps());
        assertFalse(VtEmulationLevel.VT220.supportsRectangularAreaOps());
        assertFalse(VtEmulationLevel.VT320.supportsRectangularAreaOps());
        assertTrue(VtEmulationLevel.VT420.supportsRectangularAreaOps());
        assertTrue(VtEmulationLevel.VT520.supportsRectangularAreaOps());
    }

    @Test
    void supportsLeftRightMarginsTrueOnlyForVt420AndVt520() {
        assertFalse(VtEmulationLevel.VT100.supportsLeftRightMargins());
        assertFalse(VtEmulationLevel.VT102.supportsLeftRightMargins());
        assertFalse(VtEmulationLevel.VT220.supportsLeftRightMargins());
        assertFalse(VtEmulationLevel.VT320.supportsLeftRightMargins());
        assertTrue(VtEmulationLevel.VT420.supportsLeftRightMargins());
        assertTrue(VtEmulationLevel.VT520.supportsLeftRightMargins());
    }

    @Test
    void getFunctionKeyCountIs4ForVt100AndVt102() {
        assertEquals(4, VtEmulationLevel.VT100.getFunctionKeyCount());
        assertEquals(4, VtEmulationLevel.VT102.getFunctionKeyCount());
    }

    @Test
    void getFunctionKeyCountIs20ForVt220AndAbove() {
        assertEquals(20, VtEmulationLevel.VT220.getFunctionKeyCount());
        assertEquals(20, VtEmulationLevel.VT320.getFunctionKeyCount());
        assertEquals(20, VtEmulationLevel.VT420.getFunctionKeyCount());
        assertEquals(20, VtEmulationLevel.VT520.getFunctionKeyCount());
    }

    @Test
    void graphicSetCountIs2ForVt100And4ForVt220() {
        assertEquals(2, VtEmulationLevel.VT100.getGraphicSetCount());
        assertEquals(2, VtEmulationLevel.VT102.getGraphicSetCount());
        assertEquals(4, VtEmulationLevel.VT220.getGraphicSetCount());
        assertEquals(4, VtEmulationLevel.VT320.getGraphicSetCount());
        assertEquals(4, VtEmulationLevel.VT420.getGraphicSetCount());
        assertEquals(4, VtEmulationLevel.VT520.getGraphicSetCount());
    }

    @Test
    void supportsUserDefinedKeysFalseForVt100And102() {
        assertFalse(VtEmulationLevel.VT100.supportsUserDefinedKeys());
        assertFalse(VtEmulationLevel.VT102.supportsUserDefinedKeys());
        assertTrue(VtEmulationLevel.VT220.supportsUserDefinedKeys());
        assertTrue(VtEmulationLevel.VT320.supportsUserDefinedKeys());
        assertTrue(VtEmulationLevel.VT420.supportsUserDefinedKeys());
        assertTrue(VtEmulationLevel.VT520.supportsUserDefinedKeys());
    }
}
