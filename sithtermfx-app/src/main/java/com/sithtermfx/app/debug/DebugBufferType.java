package com.sithtermfx.app.debug;

import com.sithtermfx.app.pty.LoggingTtyConnector;
import com.sithtermfx.app.pty.LoggingTtyConnector.TerminalState;
import com.sithtermfx.ui.TerminalSession;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/**
 * @author Daniel Mengel
 */
public enum DebugBufferType {
    Screen() {
        public @NotNull String getValue(TerminalSession session, int stateIndex) {
            List<TerminalState> states = ((LoggingTtyConnector) session.getTtyConnector()).getStates();
            if (stateIndex == states.size()) {
                return session.getTerminalTextBuffer().getScreenLines();
            } else {
                return states.get(stateIndex).myScreenLines;
            }
        }
    },
    BackStyle() {
        public @NotNull String getValue(TerminalSession session, int stateIndex) {
            List<TerminalState> states = ((LoggingTtyConnector) session.getTtyConnector()).getStates();
            if (stateIndex == states.size()) {
                return TerminalDebugUtil.getStyleLines(session.getTerminalTextBuffer());
            } else {
                return states.get(stateIndex).myStyleLines;
            }
        }
    },
    History() {
        public @NotNull String getValue(TerminalSession session, int stateIndex) {
            List<TerminalState> states = ((LoggingTtyConnector) session.getTtyConnector()).getStates();
            if (stateIndex == states.size()) {
                return session.getTerminalTextBuffer().getHistoryBuffer().getLines();
            } else {
                return states.get(stateIndex).myHistoryLines;
            }
        }
    };

    public abstract @NotNull String getValue(TerminalSession session, int stateIndex);
}
