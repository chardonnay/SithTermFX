package com.sithtermfx.ui;

import com.sithtermfx.core.HyperlinkStyle;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.model.hyperlinks.LinkInfo;
import com.sithtermfx.ui.settings.DefaultSettingsProvider;
import com.sithtermfx.ui.settings.ModifierKeys;
import com.sithtermfx.ui.settings.SettingsProvider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code SettingsProvider.isFollowLinkGesture} decides which clicks follow a link and when a hovered link lights up. */
public class TerminalPanelFollowLinkTest {

    private static final ModifierKeys SHORTCUT = ModifierKeys.of(false, false, false, true, true);

    private final HyperlinkStyle myLink = new HyperlinkStyle(new TextStyle(), new LinkInfo(() -> {
    }));

    /** An embedder that follows links on Cmd/Ctrl+click only. */
    private final SettingsProvider myShortcutOnly = new DefaultSettingsProvider() {
        @Override
        public boolean isFollowLinkGesture(@NotNull ModifierKeys modifiers) {
            return modifiers.isShortcutDown() && !modifiers.isAltDown();
        }
    };

    @Test
    public void byDefaultEveryPrimaryClickFollowsALink() {
        SettingsProvider settings = new DefaultSettingsProvider();

        assertTrue(TerminalPanel.isFollowLinkClick(settings, click(MouseButton.PRIMARY, false)));
        assertTrue(TerminalPanel.isFollowLinkClick(settings, click(MouseButton.PRIMARY, true)));
        assertFalse(TerminalPanel.isFollowLinkClick(settings, click(MouseButton.SECONDARY, false)));
        assertTrue(TerminalPanel.isLinkHoverActive(settings, myLink, ModifierKeys.NONE));
    }

    @Test
    public void theNeverModesNeverLightUpALink() {
        SettingsProvider settings = new DefaultSettingsProvider();
        HyperlinkStyle never = new HyperlinkStyle(null, null, new LinkInfo(() -> {
        }), HyperlinkStyle.HighlightMode.NEVER_WITH_CUSTOM_COLOR, null, null);

        assertFalse(TerminalPanel.isLinkHoverActive(settings, never, ModifierKeys.NONE));
    }

    @Test
    public void anEmbedderCanRequireAModifierClick() {
        assertFalse(TerminalPanel.isFollowLinkClick(myShortcutOnly, click(MouseButton.PRIMARY, false)));
        assertTrue(TerminalPanel.isFollowLinkClick(myShortcutOnly, click(MouseButton.PRIMARY, true)));
        assertFalse(TerminalPanel.isFollowLinkClick(myShortcutOnly, click(MouseButton.SECONDARY, true)));

        assertFalse(TerminalPanel.isLinkHoverActive(myShortcutOnly, myLink, ModifierKeys.NONE));
        assertTrue(TerminalPanel.isLinkHoverActive(myShortcutOnly, myLink, SHORTCUT));
        assertFalse(TerminalPanel.isLinkHoverActive(myShortcutOnly, myLink,
                ModifierKeys.of(false, true, true, false, true)));
    }

    @Test
    public void modifierKeysAreReadFromMouseAndKeyEvents() {
        ModifierKeys fromMouse = ModifierKeys.of(click(MouseButton.PRIMARY, true));
        assertTrue(fromMouse.isShortcutDown());
        assertFalse(fromMouse.isAltDown());

        KeyEvent shiftPressed = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SHIFT,
                true, false, false, false);
        assertEquals(ModifierKeys.of(true, false, false, false, false), ModifierKeys.of(shiftPressed));
    }

    /** A primary or secondary click, with the platform's shortcut key down or not. */
    private static MouseEvent click(MouseButton button, boolean shortcutDown) {
        boolean mac = com.sithtermfx.core.util.Platform.isMacOS();
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, button, 1,
                false, shortcutDown && !mac, false, shortcutDown && mac,
                false, false, false, false, false, true, null);
    }
}
