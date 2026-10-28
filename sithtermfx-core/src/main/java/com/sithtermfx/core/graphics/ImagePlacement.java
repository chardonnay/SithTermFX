package com.sithtermfx.core.graphics;

import org.jetbrains.annotations.NotNull;

/**
 * Where an image is shown: a rectangle of cells whose top left cell is a column of the terminal line that
 * holds the placement. The placement belongs to that line, so it scrolls with the text, moves into the
 * history with it and is gone when the line is.
 * <p>
 * The image is drawn in a rectangle given in cells relative to the top left corner of that cell, so it keeps
 * its size in cells when the font size changes. A renderer multiplies {@link #getLeft()},
 * {@link #getTop()}, {@link #getWidth()} and {@link #getHeight()} by the current cell size and draws the
 * {@linkplain #getSourceX() source rectangle} of the image there; it may reach past the cells the placement
 * {@linkplain #getColumns() covers} by less than a cell.
 */
public final class ImagePlacement {

    private final KittyImage myImage;

    private final long myId;

    private final int myColumn;

    private final int myColumns;

    private final int myRows;

    private final int mySourceX;

    private final int mySourceY;

    private final int mySourceWidth;

    private final int mySourceHeight;

    private final double myLeft;

    private final double myTop;

    private final double myWidth;

    private final double myHeight;

    private final int myZIndex;

    ImagePlacement(@NotNull KittyImage image, long id, int column, int columns, int rows,
                   int sourceX, int sourceY, int sourceWidth, int sourceHeight,
                   double left, double top, double width, double height, int zIndex) {
        myImage = image;
        myId = id;
        myColumn = column;
        myColumns = columns;
        myRows = rows;
        mySourceX = sourceX;
        mySourceY = sourceY;
        mySourceWidth = sourceWidth;
        mySourceHeight = sourceHeight;
        myLeft = left;
        myTop = top;
        myWidth = width;
        myHeight = height;
        myZIndex = zIndex;
    }

    public @NotNull KittyImage getImage() {
        return myImage;
    }

    /**
     * @return the placement id ({@code p}), or 0
     */
    public long getId() {
        return myId;
    }

    /**
     * @return the 0-based column of the top left cell
     */
    public int getColumn() {
        return myColumn;
    }

    /**
     * @return how many columns the placement covers, at least 1
     */
    public int getColumns() {
        return myColumns;
    }

    /**
     * @return how many rows the placement covers, starting with the line that holds it; at least 1
     */
    public int getRows() {
        return myRows;
    }

    public int getSourceX() {
        return mySourceX;
    }

    public int getSourceY() {
        return mySourceY;
    }

    public int getSourceWidth() {
        return mySourceWidth;
    }

    public int getSourceHeight() {
        return mySourceHeight;
    }

    /**
     * @return the left edge of the drawn image in cells, from the left edge of the top left cell
     */
    public double getLeft() {
        return myLeft;
    }

    /**
     * @return the top edge of the drawn image in cells, from the top edge of the top left cell
     */
    public double getTop() {
        return myTop;
    }

    /**
     * @return the width of the drawn image in cells
     */
    public double getWidth() {
        return myWidth;
    }

    /**
     * @return the height of the drawn image in cells
     */
    public double getHeight() {
        return myHeight;
    }

    /**
     * @return the stacking order ({@code z}): 0 and above is drawn over the text, below 0 under it, and below
     * {@code Integer.MIN_VALUE / 2} also under cells with a background colour other than the default
     */
    public int getZIndex() {
        return myZIndex;
    }

    /**
     * @return whether the placement covers the cell at the given column of a row that is {@code rowOffset}
     * rows below the line that holds it
     */
    public boolean intersects(int column, int rowOffset) {
        return column >= myColumn && column < myColumn + myColumns && rowOffset >= 0 && rowOffset < myRows;
    }

    /**
     * @return this placement anchored at another column, for a line that was rewrapped
     */
    public @NotNull ImagePlacement withColumn(int column) {
        if (column == myColumn) {
            return this;
        }
        return new ImagePlacement(myImage, myId, column, myColumns, myRows, mySourceX, mySourceY,
                mySourceWidth, mySourceHeight, myLeft, myTop, myWidth, myHeight, myZIndex);
    }

    @Override
    public String toString() {
        return "ImagePlacement(" + myImage + ", id=" + myId + ", column=" + myColumn + ", " + myColumns + "x"
                + myRows + " cells, z=" + myZIndex + ")";
    }
}
