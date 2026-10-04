package com.sithtermfx.ui;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The character half of a Meta/Cmd chord (e.g. the "d" of a Cmd+Shift+D menu accelerator) must never
 * be written to the pty, while plain typing and Windows AltGr input (reported as Ctrl+Alt) must.
 */
public class TerminalPanelShortcutChordTest {

    @Test
    public void suppressesTheCharacterOfAMetaChord() {
        assertTrue(TerminalPanel.isShortcutChordCharacter(keyTyped("d", false, false, false, true)));
        assertTrue(TerminalPanel.isShortcutChordCharacter(keyTyped("d", true, false, false, true)));
        assertTrue(TerminalPanel.isShortcutChordCharacter(keyTyped("`", false, false, false, true)));
    }

    @Test
    public void keepsPlainTypedCharacters() {
        assertFalse(TerminalPanel.isShortcutChordCharacter(keyTyped("d", false, false, false, false)));
        assertFalse(TerminalPanel.isShortcutChordCharacter(keyTyped("D", true, false, false, false)));
    }

    @Test
    public void keepsAltGrComposedCharacters() {
        assertFalse(TerminalPanel.isShortcutChordCharacter(keyTyped("@", false, true, true, false)));
        assertFalse(TerminalPanel.isShortcutChordCharacter(keyTyped("€", false, true, true, false)));
    }

    private static KeyEvent keyTyped(
            String character, boolean shiftDown, boolean controlDown, boolean altDown, boolean metaDown) {
        return new KeyEvent(
                KeyEvent.KEY_TYPED, character, character, KeyCode.UNDEFINED,
                shiftDown, controlDown, altDown, metaDown);
    }
}
