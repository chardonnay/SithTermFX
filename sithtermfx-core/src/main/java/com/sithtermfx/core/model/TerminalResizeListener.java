package com.sithtermfx.core.model;

import com.sithtermfx.core.util.TermSize;

public interface TerminalResizeListener {

    void onResize(TermSize oldTermSize, TermSize newTermSize);
}
