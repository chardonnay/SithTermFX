package com.techsenger.jeditermfx.app.example;

import com.techsenger.jeditermfx.app.DarkThemeSettingsProvider;
import com.techsenger.jeditermfx.app.pty.PtyProcessTtyConnector;
import com.techsenger.jeditermfx.core.TtyConnector;
import com.techsenger.jeditermfx.ui.JediTermFxWidget;
import com.techsenger.jeditermfx.ui.split.SplitConnectorFactory;
import com.techsenger.jeditermfx.ui.split.SplitRequest;
import com.techsenger.jeditermfx.ui.split.TerminalSplitPane;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.techsenger.jeditermfx.core.util.Platform;
import com.techsenger.jeditermfx.ui.DefaultHyperlinkFilter;
import com.techsenger.jeditermfx.ui.settings.MutableFontSizeProvider;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

/**
 * Demo application showing TerminalSplitPane with nested splits.
 * Right-click in the terminal to split horizontally or vertically.
 * Each split gets its own local shell session.
 */
public class SplitTerminalShellExample extends Application {

    @Override
    public void start(Stage stage) {
        DarkThemeSettingsProvider settingsProvider = new DarkThemeSettingsProvider();
        SplitConnectorFactory connectorFactory = request -> createTtyConnector();

        TerminalSplitPane splitPane = new TerminalSplitPane(settingsProvider, connectorFactory, widget -> {
            widget.addHyperlinkFilter(new DefaultHyperlinkFilter());
        });

        stage.setTitle("Split Terminal Shell Example");
        stage.setOnCloseRequest(e -> {
            splitPane.closeAll();
        });

        Scene scene = new Scene(splitPane, 800, 600);
        var splitPaneCss = SplitTerminalShellExample.class.getResource("split-pane.css");
        if (splitPaneCss != null) {
            scene.getStylesheets().add(splitPaneCss.toExternalForm());
        }
        // Font zoom via Scene accelerators (more reliable than KeyEvent on Mac)
        if (settingsProvider instanceof MutableFontSizeProvider) {
            MutableFontSizeProvider fontProvider = (MutableFontSizeProvider) settingsProvider;
            Runnable zoomIn = () -> fontProvider.increaseFontSize(2);
            Runnable zoomOut = () -> fontProvider.decreaseFontSize(2);
            Runnable zoomReset = fontProvider::resetFontSize;
            // SHORTCUT = Cmd on Mac, Ctrl on Win/Linux
            // Zoom In: Cmd/Ctrl + Plus (EQUALS, ADD, or with Shift)
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.SHORTCUT_DOWN), zoomIn);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.SHIFT_DOWN, KeyCombination.SHORTCUT_DOWN), zoomIn);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ADD, KeyCombination.SHORTCUT_DOWN), zoomIn);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.PLUS, KeyCombination.SHORTCUT_DOWN), zoomIn);
            // Zoom Out: Cmd/Ctrl + Minus
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.SHORTCUT_DOWN), zoomOut);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.SUBTRACT, KeyCombination.SHORTCUT_DOWN), zoomOut);
            // Reset: Cmd/Ctrl + 0
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN), zoomReset);
        }
        splitPane.prefWidthProperty().bind(scene.widthProperty());
        splitPane.prefHeightProperty().bind(scene.heightProperty());
        splitPane.setMinSize(0, 0);
        splitPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        stage.setScene(scene);
        stage.show();
    }

    private @Nullable TtyConnector createTtyConnector() {
        try {
            Map<String, String> envs = System.getenv();
            String[] command;
            if (Platform.isWindows()) {
                command = new String[]{"cmd.exe"};
            } else {
                command = new String[]{"/bin/bash", "--login"};
                envs = new HashMap<>(System.getenv());
                envs.put("TERM", "xterm-256color");
            }
            PtyProcess process = new PtyProcessBuilder().setCommand(command).setEnvironment(envs).start();
            return new PtyProcessTtyConnector(process, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
