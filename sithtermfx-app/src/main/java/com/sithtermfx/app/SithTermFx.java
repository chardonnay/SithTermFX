package com.sithtermfx.app;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.sithtermfx.app.debug.TerminalDebugUtil;
import com.sithtermfx.app.debug.TerminalDebugView;
import com.sithtermfx.app.pty.LoggingTtyConnector;
import com.sithtermfx.app.pty.PtyProcessTtyConnector;
import com.sithtermfx.app.pty.TtyConnectorWaitFor;
import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TtyConnector;
import com.sithtermfx.core.compatibility.Point;
import com.sithtermfx.core.model.SelectionUtil;
import com.sithtermfx.core.model.TerminalSelection;
import com.sithtermfx.core.model.TerminalTextBuffer;
import com.sithtermfx.ui.DefaultHyperlinkFilter;
import com.sithtermfx.ui.SithTermFxWidget;
import com.sithtermfx.ui.TerminalPanel;
import com.sithtermfx.ui.TerminalWidget;
import com.sithtermfx.ui.settings.DefaultSettingsProvider;
import com.sithtermfx.ui.settings.SettingsProvider;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import kotlin.Pair;
import kotlin.collections.ArraysKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.Charsets;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SithTermFx extends Application {

    public static final Logger logger = LoggerFactory.getLogger(SithTermFx.class);

    public static void main(String[] args) {
        launch(args);
    }

    public static final class LoggingPtyProcessTtyConnector extends PtyProcessTtyConnector
            implements LoggingTtyConnector {

        private final int MAX_LOG_SIZE = 200;

        @NotNull
        private final LinkedList<char[]> myDataChunks = new LinkedList<>();

        @NotNull
        private final LinkedList<LoggingTtyConnector.TerminalState> myStates = new LinkedList<>();

        @Nullable
        private SithTermFxWidget myWidget;

        private int logStart;

        public LoggingPtyProcessTtyConnector(@NotNull PtyProcess process, @NotNull Charset charset, @NotNull List command) {
            super(process, charset, command);
            Intrinsics.checkNotNullParameter(process, "process");
            Intrinsics.checkNotNullParameter(charset, "charset");
            Intrinsics.checkNotNullParameter(command, "command");
        }

        @Override
        public int read(@NotNull char[] buf, int offset, int length) throws IOException {
            Intrinsics.checkNotNullParameter(buf, "buf");
            int len = super.read(buf, offset, length);
            if (len > 0) {
                char[] arr = ArraysKt.copyOfRange(buf, offset, len);
                this.myDataChunks.add(arr);
                Intrinsics.checkNotNull(this.myWidget);
                TerminalTextBuffer terminalTextBuffer = this.myWidget.getTerminalTextBuffer();
                String lines = terminalTextBuffer.getScreenLines();
                Intrinsics.checkNotNull(terminalTextBuffer);
                LoggingTtyConnector.TerminalState terminalState =
                        new LoggingTtyConnector.TerminalState(lines, TerminalDebugUtil.getStyleLines(terminalTextBuffer),
                                terminalTextBuffer.getHistoryBuffer().getLines());
                this.myStates.add(terminalState);
                if (this.myDataChunks.size() > this.MAX_LOG_SIZE) {
                    this.myDataChunks.removeFirst();
                    this.myStates.removeFirst();
                    this.logStart++;
                }
            }
            return len;
        }

        @NotNull
        @Override
        public List<char[]> getChunks() {
            return new ArrayList(this.myDataChunks);
        }

        @NotNull
        @Override
        public List<LoggingTtyConnector.TerminalState> getStates() {
            return new ArrayList(this.myStates);
        }

        @Override
        public int getLogStart() {
            return this.logStart;
        }

        @Override
        public void write(@NotNull String string) throws IOException {
            Intrinsics.checkNotNullParameter(string, "string");
            logger.debug("Writing in OutputStream : " + string);
            super.write(string);
        }

        @Override
        public void write(@NotNull byte[] bytes) throws IOException {
            Intrinsics.checkNotNullParameter(bytes, "bytes");
            logger.debug("Writing in OutputStream : " + Arrays.toString(bytes) + " " + new String(bytes, Charsets.UTF_8));
            super.write(bytes);
        }

        public final void setWidget(@NotNull SithTermFxWidget widget) {
            Intrinsics.checkNotNullParameter(widget, "widget");
            this.myWidget = widget;
        }
    }

    private static void onTermination(@NotNull SithTermFxWidget widget, @NotNull IntConsumer terminationCallback) {
        new TtyConnectorWaitFor(widget.getTtyConnector(),
                widget.getExecutorServiceManager().getUnboundedExecutorService(),
                terminationCallback);
    }

    private Stage myBufferStage;

    private SithTermFxWidget myWidget;

    private final MenuItem myShowBuffersAction = new MenuItem("Show buffers");

    private final MenuItem myDumpDimension = new MenuItem("Dump terminal dimension");

    private final MenuItem myDumpSelection = new MenuItem("Dump selection");

    private final MenuItem myDumpCursorPosition = new MenuItem("Dump cursor position");

    private final MenuItem myCursor0x0 = new MenuItem("1x1");

    private final MenuItem myCursor10x10 = new MenuItem("10x10");

    private final MenuItem myCursor80x24 = new MenuItem("80x24");

    @Override
    public void start(Stage stage) throws Exception {
        DefaultSettingsProvider settings = null;
        var args = getParameters().getRaw();
        for (var arg : args) {
            if (arg.equals("theme=dark")) {
                settings = new DarkThemeSettingsProvider();
            }
        }
        if (settings == null) {
            settings = new DefaultSettingsProvider();
        }
        myWidget = createTerminalWidget(settings);
        stage.setTitle("SithTermFX");
        stage.setOnCloseRequest(e -> {
            System.exit(0);
        });
        final MenuBar mb = getMenuBar();
        initMenuItems();
        VBox.setVgrow(myWidget.getPane(), Priority.ALWAYS);
        var root = new VBox(mb, myWidget.getPane());
        Scene scene = new Scene(root, 600, 400);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.setResizable(true);
        myWidget.getTerminal().addApplicationTitleListener(e -> Platform.runLater(() -> stage.setTitle(e)));
        openSession(myWidget);
        onTermination(myWidget, exitCode -> {
            myWidget.close();
            System.exit(exitCode); // unneeded, but speeds up the JVM termination
        });
        stage.show();
    }

    @NotNull
    public TtyConnector createTtyConnector() {
        try {
            var envs = configureEnvironmentVariables();
            String[] command;
            if (com.sithtermfx.core.util.Platform.isWindows()) {
                command = new String[]{"powershell.exe"};
            } else {
                String shell = (String) envs.get("SHELL");
                if (shell == null) {
                    shell = "/bin/bash";
                }
                if (com.sithtermfx.core.util.Platform.isMacOS()) {
                    command = new String[]{shell, "--login"};
                } else {
                    command = new String[]{shell};
                }
            }
            var workingDirectory = Path.of(".").toAbsolutePath().normalize().toString();
            logger.info("Starting {} in {}", String.join(" ", command), workingDirectory);
            var process = new PtyProcessBuilder()
                    .setDirectory(workingDirectory)
                    .setInitialColumns(120)
                    .setInitialRows(20)
                    .setCommand(command)
                    .setEnvironment(envs)
                    .setConsole(false)
                    .setUseWinConPty(true)
                    .start();
            return new LoggingPtyProcessTtyConnector(process, StandardCharsets.UTF_8, Arrays.asList(command));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void openSession(TerminalWidget terminal, TtyConnector ttyConnector) {
        SithTermFxWidget session = terminal.createTerminalSession(ttyConnector);
        if (ttyConnector instanceof SithTermFx.LoggingPtyProcessTtyConnector) {
            ((SithTermFx.LoggingPtyProcessTtyConnector) ttyConnector).setWidget(session);
        }
        session.start();
    }

    @NotNull
    protected SithTermFxWidget createTerminalWidget(@NotNull SettingsProvider settingsProvider) {
        Intrinsics.checkNotNullParameter(settingsProvider, "settingsProvider");
        SithTermFxWidget widget = new SithTermFxWidget(settingsProvider);
        widget.addHyperlinkFilter(new DefaultHyperlinkFilter());
        return widget;
    }

    protected void openSession(TerminalWidget terminal) {
        if (terminal.canOpenSession()) {
            openSession(terminal, createTtyConnector());
        }
    }

    private MenuBar getMenuBar() {
        final MenuBar mb = new MenuBar();
        final Menu dm = new Menu("Debug");
        Menu logLevel = new Menu("Set log level ...");
        Level[] levels = new Level[]{Level.ALL, Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR,
            Level.FATAL, Level.OFF};
        for (final Level l : levels) {
            var item = new MenuItem(l.name());
            item.setOnAction(e ->  Configurator.setRootLevel(l));
            logLevel.getItems().add(item);
        }
        Menu cursorPosition = new Menu("Set cursor position ...");
        cursorPosition.getItems().addAll(
                myCursor0x0,
                myCursor10x10,
                myCursor80x24);
        dm.getItems().addAll(
                logLevel,
                new SeparatorMenuItem(),
                myShowBuffersAction,
                new SeparatorMenuItem(),
                myDumpDimension,
                myDumpSelection,
                myDumpCursorPosition,
                cursorPosition
        );
        mb.getMenus().add(dm);
        return mb;
    }

    private void initMenuItems() {
        myShowBuffersAction.setOnAction(e -> {
            showBuffers();
        });
        myDumpDimension.setOnAction(e -> {
            Terminal terminal = myWidget.getTerminal();
            logger.info(terminal.getTerminalWidth() + "x" + terminal.getTerminalHeight());
        });
        myDumpSelection.setOnAction(e -> {
            SithTermFxWidget widget = myWidget;
            TerminalPanel terminalPanel = widget.getTerminalPanel();
            TerminalSelection selection = terminalPanel.getSelection();
            if (selection != null) {
                Pair<Point, Point> points = selection.pointsForRun(widget.getTerminal().getTerminalWidth());
                logger.info(selection + " : '" + SelectionUtil.getSelectedText(points.getFirst(), points.getSecond(),
                                terminalPanel.getTerminalTextBuffer()) + "'");
            } else {
                logger.info("No selection");
            }
        });
        myDumpCursorPosition.setOnAction(e -> {
            logger.info(myWidget.getTerminal().getCursorX() +
                    "x" + myWidget.getTerminal().getCursorY());
        });
        myCursor0x0.setOnAction(e -> {
            myWidget.getTerminal().cursorPosition(1, 1);
        });
        myCursor10x10.setOnAction(e -> {
            myWidget.getTerminal().cursorPosition(10, 10);
        });
        myCursor80x24.setOnAction(e -> {
            myWidget.getTerminal().cursorPosition(80, 24);
        });
    }

    private final Map<String, String> configureEnvironmentVariables() {
        HashMap envs = new HashMap<String, String>(System.getenv());
        if (com.sithtermfx.core.util.Platform.isMacOS()) {
            envs.put("LC_CTYPE", Charsets.UTF_8.name());
        }
        if (!com.sithtermfx.core.util.Platform.isWindows()) {
            envs.put("TERM", "xterm-256color");
        }
        return envs;
    }

//TODO?
//  private void sizeFrameForTerm(final JFrame frame) {
//    SwingUtilities.invokeLater(() -> {
//      Dimension d = myWidget.getPreferredSize();
//      d.width += frame.getWidth() - frame.getContentPane().getWidth();
//      d.height += frame.getHeight() - frame.getContentPane().getHeight();
//      frame.setSize(d);
//    });
//  }

    private void showBuffers() {
        if (myBufferStage != null) {
            myBufferStage.requestFocus();
            return;
        }
        myBufferStage = new Stage();
        myBufferStage.setTitle("Buffers");
        TerminalDebugView debugView = new TerminalDebugView(myWidget);
        var scene = new Scene(debugView.getPane(), 1600, 800);
        myBufferStage.setScene(scene);
        myBufferStage.centerOnScreen();
        myBufferStage.setOnCloseRequest(e -> {
            myBufferStage = null;
            debugView.stop();
            logger.info("Buffer stage closed");
        });
        myBufferStage.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                myBufferStage.close();
            }
        });
        myBufferStage.show();
    }
}
