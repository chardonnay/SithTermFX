package com.sithtermfx.core.util;

import java.util.Objects;

/**
 * A size in pixels, of one character cell or of the whole text area.
 */
public final class PixelSize {

    private final int myWidth;

    private final int myHeight;

    public PixelSize(int width, int height) {
        if (width < 0) {
            throw new IllegalArgumentException("negative width: " + width);
        }
        if (height < 0) {
            throw new IllegalArgumentException("negative height: " + height);
        }
        myWidth = width;
        myHeight = height;
    }

    public int getWidth() {
        return myWidth;
    }

    public int getHeight() {
        return myHeight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PixelSize other = (PixelSize) o;
        return myWidth == other.myWidth && myHeight == other.myHeight;
    }

    @Override
    public int hashCode() {
        return Objects.hash(myWidth, myHeight);
    }

    @Override
    public String toString() {
        return "width=" + myWidth + ", height=" + myHeight;
    }
}
