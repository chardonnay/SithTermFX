package com.sithtermfx.ui;

import com.sithtermfx.core.model.StyleState;
import com.sithtermfx.core.model.TerminalTextBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hyperlink hit-testing must only look at cells that exist: the row just below the screen (reached
 * by hovering the bottom margin of the canvas) is outside the buffer.
 */
public class TerminalPanelHyperlinkBoundsTest {

    @Test
    public void lastScreenRowIsInsideAndTheRowBelowIsNot() {
        TerminalTextBuffer buffer = new TerminalTextBuffer(80, 24, new StyleState());

        assertTrue(TerminalPanel.isCellInsideTextBuffer(new Cell(23, 79), buffer));
        assertFalse(TerminalPanel.isCellInsideTextBuffer(new Cell(24, 0), buffer));
    }

    @Test
    public void columnsOutsideTheScreenAndMissingCellsAreRejected() {
        TerminalTextBuffer buffer = new TerminalTextBuffer(80, 24, new StyleState());

        assertTrue(TerminalPanel.isCellInsideTextBuffer(new Cell(0, 0), buffer));
        assertFalse(TerminalPanel.isCellInsideTextBuffer(new Cell(0, 80), buffer));
        assertFalse(TerminalPanel.isCellInsideTextBuffer(new Cell(0, -1), buffer));
        assertFalse(TerminalPanel.isCellInsideTextBuffer(new Cell(-1, 0), buffer));
        assertFalse(TerminalPanel.isCellInsideTextBuffer(null, buffer));
    }
}
