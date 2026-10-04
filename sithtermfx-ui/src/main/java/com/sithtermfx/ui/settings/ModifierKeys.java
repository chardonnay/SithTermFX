package com.sithtermfx.ui.settings;

import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The modifier keys held during a pointer gesture, as {@link UserSettingsProvider#isFollowLinkGesture}
 * sees them. Taken from the mouse event of a click or a move, or from the key event when a modifier
 * is pressed or released while the pointer rests over a link.
 */
public final class ModifierKeys {

    public static final ModifierKeys NONE = new ModifierKeys(false, false, false, false, false);

    private final boolean myShiftDown;

    private final boolean myControlDown;

    private final boolean myAltDown;

    private final boolean myMetaDown;

    private final boolean myShortcutDown;

    private ModifierKeys(boolean shiftDown, boolean controlDown, boolean altDown, boolean metaDown,
                         boolean shortcutDown) {
        myShiftDown = shiftDown;
        myControlDown = controlDown;
        myAltDown = altDown;
        myMetaDown = metaDown;
        myShortcutDown = shortcutDown;
    }

    /**
     * @param shortcutDown whether the platform's shortcut key is down (Cmd on macOS, Ctrl elsewhere)
     */
    public static @NotNull ModifierKeys of(boolean shiftDown, boolean controlDown, boolean altDown,
                                           boolean metaDown, boolean shortcutDown) {
        return new ModifierKeys(shiftDown, controlDown, altDown, metaDown, shortcutDown);
    }

    public static @NotNull ModifierKeys of(@NotNull MouseEvent event) {
        return of(event.isShiftDown(), event.isControlDown(), event.isAltDown(), event.isMetaDown(),
                event.isShortcutDown());
    }

    public static @NotNull ModifierKeys of(@NotNull KeyEvent event) {
        return of(event.isShiftDown(), event.isControlDown(), event.isAltDown(), event.isMetaDown(),
                event.isShortcutDown());
    }

    public boolean isShiftDown() {
        return myShiftDown;
    }

    public boolean isControlDown() {
        return myControlDown;
    }

    public boolean isAltDown() {
        return myAltDown;
    }

    public boolean isMetaDown() {
        return myMetaDown;
    }

    /**
     * @return whether the platform's shortcut key is down: Cmd on macOS, Ctrl on Windows and Linux
     */
    public boolean isShortcutDown() {
        return myShortcutDown;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModifierKeys)) return false;
        ModifierKeys that = (ModifierKeys) o;
        return myShiftDown == that.myShiftDown && myControlDown == that.myControlDown
                && myAltDown == that.myAltDown && myMetaDown == that.myMetaDown
                && myShortcutDown == that.myShortcutDown;
    }

    @Override
    public int hashCode() {
        return Objects.hash(myShiftDown, myControlDown, myAltDown, myMetaDown, myShortcutDown);
    }

    @Override
    public String toString() {
        return "ModifierKeys{shift=" + myShiftDown + ", control=" + myControlDown + ", alt=" + myAltDown
                + ", meta=" + myMetaDown + ", shortcut=" + myShortcutDown + '}';
    }
}
