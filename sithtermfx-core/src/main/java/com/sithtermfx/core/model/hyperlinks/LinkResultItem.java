package com.sithtermfx.core.model.hyperlinks;

import org.jetbrains.annotations.NotNull;

/**
 * @author Daniel Mengel
 */
public class LinkResultItem {

    private int myStartOffset;

    private int myEndOffset;

    private LinkInfo myLinkInfo;

    public LinkResultItem(int startOffset, int endOffset, @NotNull LinkInfo linkInfo) {
        myStartOffset = startOffset;
        myEndOffset = endOffset;
        myLinkInfo = linkInfo;
    }

    public int getStartOffset() {
        return myStartOffset;
    }

    public int getEndOffset() {
        return myEndOffset;
    }

    public LinkInfo getLinkInfo() {
        return myLinkInfo;
    }
}
