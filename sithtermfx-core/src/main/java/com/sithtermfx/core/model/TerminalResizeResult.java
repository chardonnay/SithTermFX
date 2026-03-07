package com.sithtermfx.core.model;

import com.sithtermfx.core.util.CellPosition;

public class TerminalResizeResult {

    private final CellPosition newCursor;

    TerminalResizeResult(CellPosition newCursor) {
        this.newCursor = newCursor;
    }

    public CellPosition getNewCursor() {
        return newCursor;
    }
}
