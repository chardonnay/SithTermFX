package com.techsenger.jeditermfx.ui.split;

import com.techsenger.jeditermfx.core.TtyConnector;
import com.techsenger.jeditermfx.ui.JediTermFxWidget;
import com.techsenger.jeditermfx.ui.settings.SettingsProvider;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Pane that supports splitting terminal widgets horizontally or vertically.
 * Supports nested splits (e.g. 2x2 grid). Each split has its own session (TtyConnector).
 * Extends StackPane so children automatically fill the available space when the window is resized.
 */
public class TerminalSplitPane extends StackPane {

    private static final Logger logger = LoggerFactory.getLogger(TerminalSplitPane.class);

    /** Control sequences used in KEY_PRESSED; skip these in KEY_TYPED to avoid duplicate broadcast. */
    private static final String BACK_SPACE_CONTROL_SEQUENCE = "\u007F";
    private static final String DELETE_CONTROL_SEQUENCE = "\u001B[3~";

    private final SettingsProvider settingsProvider;
    private final SplitConnectorFactory connectorFactory;
    private final Consumer<JediTermFxWidget> widgetConfigurator;

    private SplitCell rootCell;
    private JediTermFxWidget focusedWidget;
    private boolean broadcastMode = false;

    /** Executor for broadcasting key input to other connectors off the JavaFX thread. */
    private final ExecutorService broadcastExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TerminalSplitPane-broadcast");
        t.setDaemon(true);
        return t;
    });

    public TerminalSplitPane(@NotNull SettingsProvider settingsProvider,
                             @NotNull SplitConnectorFactory connectorFactory) {
        this(settingsProvider, connectorFactory, w -> {});
    }

    public TerminalSplitPane(@NotNull SettingsProvider settingsProvider,
                             @NotNull SplitConnectorFactory connectorFactory,
                             @NotNull Consumer<JediTermFxWidget> widgetConfigurator) {
        this.settingsProvider = settingsProvider;
        this.connectorFactory = connectorFactory;
        this.widgetConfigurator = widgetConfigurator;
        this.rootCell = createInitialCell();
        getChildren().add(rootCell.getNode());
        VBox.setVgrow(this, Priority.ALWAYS);
    }
    
    /**
     * Broadcasts input to all OTHER widgets (not the source widget).
     * Writes are offloaded to a background executor to avoid blocking the JavaFX thread.
     */
    private void broadcastToOthers(@NotNull JediTermFxWidget sourceWidget, @NotNull String data) {
        if (!broadcastMode) return;

        List<JediTermFxWidget> allWidgets = getAllWidgets();
        for (JediTermFxWidget widget : allWidgets) {
            if (widget != sourceWidget) {
                TtyConnector connector = widget.getTtyConnector();
                if (connector != null && connector.isConnected()) {
                    final TtyConnector c = connector;
                    broadcastExecutor.submit(() -> {
                        try {
                            c.write(data);
                        } catch (IOException e) {
                            logger.debug("Failed to broadcast to widget: {}", e.getMessage());
                        }
                    });
                }
            }
        }
    }
    
    private @Nullable String getControlSequence(KeyEvent event) {
        switch (event.getCode()) {
            case ENTER: return "\r";
            case BACK_SPACE: return BACK_SPACE_CONTROL_SEQUENCE;
            case TAB: return "\t";
            case ESCAPE: return "\u001B";
            case UP: return "\u001B[A";
            case DOWN: return "\u001B[B";
            case RIGHT: return "\u001B[C";
            case LEFT: return "\u001B[D";
            case HOME: return "\u001B[H";
            case END: return "\u001B[F";
            case PAGE_UP: return "\u001B[5~";
            case PAGE_DOWN: return "\u001B[6~";
            case INSERT: return "\u001B[2~";
            case DELETE: return DELETE_CONTROL_SEQUENCE;
            case F1: return "\u001BOP";
            case F2: return "\u001BOQ";
            case F3: return "\u001BOR";
            case F4: return "\u001BOS";
            case F5: return "\u001B[15~";
            case F6: return "\u001B[17~";
            case F7: return "\u001B[18~";
            case F8: return "\u001B[19~";
            case F9: return "\u001B[20~";
            case F10: return "\u001B[21~";
            case F11: return "\u001B[23~";
            case F12: return "\u001B[24~";
            default: return null;
        }
    }

    private @NotNull SplitCell createInitialCell() {
        JediTermFxWidget widget = createWidget(null);
        setupWidget(widget);
        return new SplitCell(widget);
    }

    private @NotNull JediTermFxWidget createWidget(@Nullable SplitRequest request) {
        JediTermFxWidget widget = new JediTermFxWidget(80, 24, settingsProvider);
        widgetConfigurator.accept(widget);
        TtyConnector connector = connectorFactory.createConnectorForSplit(request);
        if (connector != null) {
            widget.setTtyConnector(connector);
            widget.start();
        }
        return widget;
    }

    private void setupWidget(@NotNull JediTermFxWidget widget) {
        focusedWidget = widget;
        widget.getPane().setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                focusedWidget = widget;
            }
        });
        widget.getPreferredFocusableNode().focusedProperty().addListener((obs, oldV, newV) -> {
            if (Boolean.TRUE.equals(newV)) {
                focusedWidget = widget;
            }
        });
        widget.setContextMenuExtender(menu -> addSplitMenuItems(menu, widget));
        
        // Broadcast mode: register event filters on the WIDGET'S OUTER PANE
        // This is the outermost container (myInnerPanel), ensuring our filter runs 
        // BEFORE any inner filters in the capturing phase
        var widgetPane = widget.getPane();
        
        widgetPane.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (!broadcastMode) return;
            String character = event.getCharacter();
            if (character == null || character.isEmpty()) return;
            // Skip single-char control sequences that KEY_PRESSED also sends (getControlSequence), to avoid duplicate broadcast.
            // getCharacter() yields only single characters; DELETE uses multi-char "\u001B[3~" so it is not seen here.
            if (character.equals(BACK_SPACE_CONTROL_SEQUENCE) || character.equals("\r")
                    || character.equals("\t") || character.equals("\u001B")) {
                return;
            }
            broadcastToOthers(widget, character);
        });
        
        widgetPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!broadcastMode) return;
            String sequence = getControlSequence(event);
            if (sequence != null) {
                broadcastToOthers(widget, sequence);
            }
        });
    }

    private void addSplitMenuItems(@NotNull ContextMenu menu, @NotNull JediTermFxWidget widget) {
        focusedWidget = widget;

        // Font size - same pattern as split items; widget methods no-op when not supported
        MenuItem increaseFont = new MenuItem("Increase font size");
        increaseFont.setOnAction(e -> widget.increaseFontSize(2));
        MenuItem decreaseFont = new MenuItem("Decrease font size");
        decreaseFont.setOnAction(e -> widget.decreaseFontSize(2));
        MenuItem resetFont = new MenuItem("Reset font size");
        resetFont.setOnAction(e -> widget.resetFontSize());
        menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().addAll(increaseFont, decreaseFont, resetFont);
        menu.getItems().add(new SeparatorMenuItem());
        
        // Broadcast mode toggle
        CheckMenuItem broadcastToggle = new CheckMenuItem("Broadcast mode (type in all terminals)");
        broadcastToggle.setSelected(broadcastMode);
        broadcastToggle.setOnAction(e -> {
            setBroadcastMode(broadcastToggle.isSelected());
        });
        // Only enable when there are multiple terminals
        broadcastToggle.setDisable(rootCell.countWidgets() <= 1);
        menu.getItems().add(broadcastToggle);
        menu.getItems().add(new SeparatorMenuItem());

        MenuItem splitRightSame = new MenuItem("Split right (same server)");
        splitRightSame.setOnAction(e -> split(SplitRequest.SplitMode.SAME_SERVER_NEW_SHELL, Orientation.HORIZONTAL));

        MenuItem splitRightNew = new MenuItem("Split right (new connection)");
        splitRightNew.setOnAction(e -> split(SplitRequest.SplitMode.NEW_CONNECTION, Orientation.HORIZONTAL));

        MenuItem splitDownSame = new MenuItem("Split down (same server)");
        splitDownSame.setOnAction(e -> split(SplitRequest.SplitMode.SAME_SERVER_NEW_SHELL, Orientation.VERTICAL));

        MenuItem splitDownNew = new MenuItem("Split down (new connection)");
        splitDownNew.setOnAction(e -> split(SplitRequest.SplitMode.NEW_CONNECTION, Orientation.VERTICAL));

        MenuItem closeSplit = new MenuItem("Close split");
        closeSplit.setOnAction(e -> closeSplit(widget));
        closeSplit.setDisable(rootCell.countWidgets() <= 1);

        menu.getItems().addAll(
                splitRightSame, splitRightNew,
                new SeparatorMenuItem(),
                splitDownSame, splitDownNew,
                new SeparatorMenuItem(),
                closeSplit
        );
    }

    /**
     * Splits the focused area in the given direction.
     */
    public void split(@NotNull SplitRequest.SplitMode mode, @NotNull Orientation orientation) {
        JediTermFxWidget parent = getFocusedWidget();
        if (parent == null) {
            logger.warn("No focused widget to split");
            return;
        }
        splitWidget(parent, mode, orientation);
    }

    /**
     * Splits horizontally (new pane to the right).
     */
    public void splitHorizontally(@Nullable SplitRequest.SplitMode mode) {
        split(mode != null ? mode : SplitRequest.SplitMode.NEW_CONNECTION, Orientation.HORIZONTAL);
    }

    /**
     * Splits vertically (new pane below).
     */
    public void splitVertically(@Nullable SplitRequest.SplitMode mode) {
        split(mode != null ? mode : SplitRequest.SplitMode.NEW_CONNECTION, Orientation.VERTICAL);
    }

    private void splitWidget(@NotNull JediTermFxWidget widget, @NotNull SplitRequest.SplitMode mode,
                            @NotNull Orientation orientation) {
        SplitRequest request = new SplitRequest(mode, widget);
        JediTermFxWidget newWidget = createWidget(request);
        if (newWidget.getTtyConnector() == null) {
            newWidget.close();
            return;
        }
        setupWidget(newWidget);

        SplitCell newCell = new SplitCell(newWidget);
        SplitCell replacement = rootCell.replaceWidget(widget, newCell, orientation);
        if (replacement != null) {
            getChildren().clear();
            rootCell = replacement;
            getChildren().add(rootCell.getNode());
            VBox.setVgrow(rootCell.getNode(), Priority.ALWAYS);
        }
    }

    private void closeSplit(@NotNull JediTermFxWidget widget) {
        widget.close();
        if (widget.getTtyConnector() != null) {
            try {
                widget.getTtyConnector().close();
            } catch (Exception e) {
                logger.debug("Error closing connector: {}", e.getMessage());
            }
        }
        SplitCell replacement = rootCell.removeWidget(widget);
        if (replacement != rootCell) {
            getChildren().clear();
            rootCell = replacement;
            if (rootCell != null) {
                getChildren().add(rootCell.getNode());
                VBox.setVgrow(rootCell.getNode(), Priority.ALWAYS);
            }
            focusedWidget = rootCell != null ? findFirstWidget(rootCell) : null;
        }
    }

    /**
     * Returns the currently focused terminal widget.
     */
    public @Nullable JediTermFxWidget getFocusedWidget() {
        return focusedWidget;
    }

    /**
     * Returns all terminal widgets in this split pane.
     */
    public @NotNull List<JediTermFxWidget> getAllWidgets() {
        List<JediTermFxWidget> widgets = new ArrayList<>();
        collectWidgets(rootCell, widgets);
        return widgets;
    }
    
    private void collectWidgets(@Nullable SplitCell cell, @NotNull List<JediTermFxWidget> widgets) {
        if (cell == null) return;
        if (cell.widget != null) {
            widgets.add(cell.widget);
        }
        collectWidgets(cell.leftCell, widgets);
        collectWidgets(cell.rightCell, widgets);
    }
    
    /**
     * Returns the number of terminal widgets in this split pane.
     */
    public int getWidgetCount() {
        return rootCell != null ? rootCell.countWidgets() : 0;
    }
    
    /**
     * Checks if broadcast mode is enabled.
     */
    public boolean isBroadcastMode() {
        return broadcastMode;
    }
    
    /**
     * Enables or disables broadcast mode.
     * When enabled, keyboard input is sent to all terminal widgets simultaneously.
     */
    public void setBroadcastMode(boolean enabled) {
        this.broadcastMode = enabled;
        logger.info("Broadcast mode {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Toggles broadcast mode on/off.
     */
    public void toggleBroadcastMode() {
        setBroadcastMode(!broadcastMode);
    }

    /**
     * Closes all terminal sessions in this split pane.
     */
    public void closeAll() {
        if (rootCell == null) return;
        rootCell.closeAll();
    }

    /**
     * Releases resources (e.g. broadcast executor). Call when this pane is no longer used
     * so the executor can be shut down and the pane can be GC'd.
     */
    public void dispose() {
        broadcastExecutor.shutdown();
    }

    /**
     * Recursive cell: either a single widget or a SplitPane with two cells.
     */
    private class SplitCell {
        private final @Nullable JediTermFxWidget widget;
        private final @Nullable SplitPane splitPane;
        private final @Nullable SplitCell leftCell;
        private final @Nullable SplitCell rightCell;
        private final Region node;

        SplitCell(@NotNull JediTermFxWidget widget) {
            this.widget = widget;
            this.splitPane = null;
            this.leftCell = null;
            this.rightCell = null;
            StackPane wrapper = new StackPane(widget.getPane());
            wrapper.setMinSize(0, 0);
            wrapper.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            VBox.setVgrow(wrapper, Priority.ALWAYS);
            this.node = wrapper;
        }

        SplitCell(@NotNull SplitCell left, @NotNull SplitCell right, @NotNull Orientation orientation) {
            this.widget = null;
            this.leftCell = left;
            this.rightCell = right;
            Region leftNode = left.getNode();
            Region rightNode = right.getNode();
            leftNode.setMinWidth(0);
            leftNode.setMinHeight(0);
            leftNode.setMaxWidth(Double.MAX_VALUE);
            leftNode.setMaxHeight(Double.MAX_VALUE);
            rightNode.setMinWidth(0);
            rightNode.setMinHeight(0);
            rightNode.setMaxWidth(Double.MAX_VALUE);
            rightNode.setMaxHeight(Double.MAX_VALUE);
            this.splitPane = new SplitPane(leftNode, rightNode);
            splitPane.setOrientation(orientation);
            splitPane.setDividerPositions(0.5);
            splitPane.setMinSize(0, 0);
            splitPane.setMaxWidth(Double.MAX_VALUE);
            splitPane.setMaxHeight(Double.MAX_VALUE);
            this.node = splitPane;
            // Re-apply divider position after layout - prevents right pane from getting zero width
            Platform.runLater(() -> splitPane.setDividerPositions(0.5));
        }

        Region getNode() {
            return node;
        }

        @Nullable
        SplitCell replaceWidget(@NotNull JediTermFxWidget target, @NotNull SplitCell newCell,
                               @NotNull Orientation orientation) {
            if (widget == target) {
                return new SplitCell(this, newCell, orientation);
            }
            if (leftCell != null && rightCell != null) {
                SplitCell newLeft = leftCell.replaceWidget(target, newCell, orientation);
                if (newLeft != null) {
                    return new SplitCell(newLeft, rightCell, splitPane.getOrientation());
                }
                SplitCell newRight = rightCell.replaceWidget(target, newCell, orientation);
                if (newRight != null) {
                    return new SplitCell(leftCell, newRight, splitPane.getOrientation());
                }
            }
            return null;
        }

        @Nullable
        SplitCell removeWidget(@NotNull JediTermFxWidget target) {
            if (widget == target) {
                return null;
            }
            if (leftCell != null && rightCell != null) {
                SplitCell newLeft = leftCell.removeWidget(target);
                if (newLeft != leftCell) {
                    if (newLeft == null) {
                        return rightCell;
                    }
                    return new SplitCell(newLeft, rightCell, splitPane.getOrientation());
                }
                SplitCell newRight = rightCell.removeWidget(target);
                if (newRight != rightCell) {
                    if (newRight == null) {
                        return leftCell;
                    }
                    return new SplitCell(leftCell, newRight, splitPane.getOrientation());
                }
            }
            return this;
        }

        int countWidgets() {
            if (widget != null) return 1;
            return (leftCell != null ? leftCell.countWidgets() : 0)
                    + (rightCell != null ? rightCell.countWidgets() : 0);
        }

        void closeAll() {
            if (widget != null) {
                widget.close();
                if (widget.getTtyConnector() != null) {
                    try {
                        widget.getTtyConnector().close();
                    } catch (Exception e) {
                        logger.debug("Error closing: {}", e.getMessage());
                    }
                }
            }
            if (leftCell != null) leftCell.closeAll();
            if (rightCell != null) rightCell.closeAll();
        }
    }

    private @Nullable JediTermFxWidget findFirstWidget(@NotNull SplitCell cell) {
        if (cell.widget != null) {
            return cell.widget;
        }
        if (cell.leftCell != null) {
            JediTermFxWidget w = findFirstWidget(cell.leftCell);
            if (w != null) return w;
        }
        if (cell.rightCell != null) {
            return findFirstWidget(cell.rightCell);
        }
        return null;
    }
}
