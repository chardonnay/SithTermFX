package com.sithtermfx.ui;

import javafx.scene.input.DataFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The parts of {@link JavaFxClipboardHandler} that do not need the clipboard itself.
 */
public class JavaFxClipboardHandlerTest {

    @Test
    public void theJavaFxFormatsAreOfferedAsMimeTypes() {
        assertEquals(List.of("text/plain"), JavaFxClipboardHandler.mimeTypesOf(DataFormat.PLAIN_TEXT));
        assertEquals(List.of("text/html"), JavaFxClipboardHandler.mimeTypesOf(DataFormat.HTML));
        assertEquals(List.of("image/png"), JavaFxClipboardHandler.mimeTypesOf(DataFormat.IMAGE));
        assertEquals(List.of(), JavaFxClipboardHandler.mimeTypesOf(DataFormat.FILES));
    }

    @Test
    public void aMimeTypeIsComparedWithoutParametersOrCase() {
        assertEquals("text/plain", JavaFxClipboardHandler.baseType("Text/Plain; charset=utf-8"));
        assertEquals("image/png", JavaFxClipboardHandler.baseType("image/png"));
    }

    @Test
    public void theNameAProgramGivesItselfIsShownWithoutControlAndBidiChars() {
        assertEquals("evilgnp.exe", JavaFxClipboardHandler.displayName("evil‮gnp.exe\u001B[2J".replace("[2J", "")));
        assertEquals(80, JavaFxClipboardHandler.displayName("x".repeat(500)).length());
    }
}
