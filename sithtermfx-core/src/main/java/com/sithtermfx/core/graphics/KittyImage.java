package com.sithtermfx.core.graphics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An image a program sent with the kitty graphics protocol, decoded to pixels. It is shown wherever an
 * {@link ImagePlacement} of it is anchored; one image can have several placements.
 */
public final class KittyImage {

    private final long myId;

    private final long myNumber;

    private final int myWidth;

    private final int myHeight;

    private volatile int @Nullable [] myPixels;

    private final long mySequence;

    KittyImage(long id, long number, int width, int height, int @NotNull [] pixels, long sequence) {
        myId = id;
        myNumber = number;
        myWidth = width;
        myHeight = height;
        myPixels = pixels;
        mySequence = sequence;
    }

    /**
     * @return the image id ({@code i}): the one the program chose, the one assigned to an image sent with
     * only an image number, or 0 for an image sent with neither
     */
    public long getId() {
        return myId;
    }

    /**
     * @return the image number ({@code I}) the image was sent with, or 0
     */
    public long getNumber() {
        return myNumber;
    }

    public int getWidth() {
        return myWidth;
    }

    public int getHeight() {
        return myHeight;
    }

    /**
     * @return the pixels row by row from the top left, as ARGB that is not premultiplied, or null once the image
     * is deleted. The array is the image's own and must not be modified.
     */
    public int @Nullable [] getPixels() {
        return myPixels;
    }

    /**
     * @return whether the image was deleted: by the program, by a newer image with its id, or to keep the
     * storage quota. A placement of a deleted image draws nothing.
     */
    public boolean isDeleted() {
        return myPixels == null;
    }

    long getByteCount() {
        return 4L * myWidth * myHeight;
    }

    long getSequence() {
        return mySequence;
    }

    void delete() {
        myPixels = null;
    }

    @Override
    public String toString() {
        return "KittyImage(id=" + myId + ", number=" + myNumber + ", " + myWidth + "x" + myHeight
                + (isDeleted() ? ", deleted" : "") + ")";
    }
}
