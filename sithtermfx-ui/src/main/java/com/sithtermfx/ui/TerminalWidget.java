package com.sithtermfx.ui;

import javafx.geometry.Dimension2D;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import com.sithtermfx.core.TerminalDisplay;
import com.sithtermfx.core.TtyConnector;

/**
 * @author Daniel Mengel
 */
public interface TerminalWidget {

    SithTermFxWidget createTerminalSession(TtyConnector ttyConnector);

    Pane getPane();

    Node getPreferredFocusableNode();

    boolean canOpenSession();

    Dimension2D getPreferredSize();

    TerminalDisplay getTerminalDisplay();

    void addListener(TerminalWidgetListener listener);

    void removeListener(TerminalWidgetListener listener);
}
