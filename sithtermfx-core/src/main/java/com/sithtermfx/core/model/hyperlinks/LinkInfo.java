package com.sithtermfx.core.model.hyperlinks;

import org.jetbrains.annotations.NotNull;

public class LinkInfo {

    private final Runnable myNavigateCallback;

    public LinkInfo(@NotNull Runnable navigateCallback) {
        myNavigateCallback = navigateCallback;
    }

    public void navigate() {
        myNavigateCallback.run();
    }
}
