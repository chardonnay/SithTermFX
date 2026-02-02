package com.techsenger.jeditermfx.ui.split;

import com.techsenger.jeditermfx.core.TtyConnector;
import com.techsenger.jeditermfx.ui.JediTermFxWidget;
import com.techsenger.jeditermfx.ui.settings.SettingsProvider;
import javafx.geometry.Orientation;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Pane that supports splitting terminal widgets horizontally or vertically.
 * Supports nested splits (e.g. 2x2 grid). Each split has its own session (TtyConnector).
 */
public class TerminalSplitPane extends Pane {

    private static final Logger logger = LoggerFactory.getLogger(TerminalSplitPane.class);

    private final SettingsProvider settingsProvider;
    private final SplitConnectorFactory connectorFactory;
    private final Consumer<JediTermFxWidget> widgetConfigurator;

    private SplitCell rootCell;
    private JediTermFxWidget focusedWidget;

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
        widget.getPane().setOnContextMenuRequested(e -> {
            showContextMenu(widget, e.getScreenX(), e.getScreenY());
        });
    }

    private void showContextMenu(@NotNull JediTermFxWidget widget, double screenX, double screenY) {
        focusedWidget = widget;
        ContextMenu menu = new ContextMenu();

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
        menu.show(getScene().getWindow(), screenX, screenY);
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
     * Closes all terminal sessions in this split pane.
     */
    public void closeAll() {
        rootCell.closeAll();
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
            VBox.setVgrow(wrapper, Priority.ALWAYS);
            this.node = wrapper;
        }

        SplitCell(@NotNull SplitCell left, @NotNull SplitCell right, @NotNull Orientation orientation) {
            this.widget = null;
            this.leftCell = left;
            this.rightCell = right;
            this.splitPane = new SplitPane(left.getNode(), right.getNode());
            splitPane.setOrientation(orientation);
            splitPane.setDividerPositions(0.5);
            splitPane.setMaxWidth(Double.MAX_VALUE);
            splitPane.setMaxHeight(Double.MAX_VALUE);
            this.node = splitPane;
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
