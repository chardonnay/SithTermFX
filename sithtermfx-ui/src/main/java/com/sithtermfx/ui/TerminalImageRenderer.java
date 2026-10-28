package com.sithtermfx.ui;

import com.sithtermfx.core.graphics.ImagePlacement;
import com.sithtermfx.core.graphics.KittyImage;
import com.sithtermfx.core.model.TerminalTextBuffer;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws the images programs place with the kitty graphics protocol onto the terminal canvas, in three layers
 * by z-index: under the cell backgrounds, between the backgrounds and the text, and over the text.
 * <p>
 * Each image is turned into a JavaFX image the first time it is drawn, and that copy is kept only while the
 * image shows: one that scrolls out of view is copied again when it comes back.
 */
final class TerminalImageRenderer {

    /**
     * A z-index below this draws an image under the cells with a background other than the default, too.
     */
    static final int UNDER_BACKGROUND_Z_INDEX = Integer.MIN_VALUE / 2;

    enum Layer {
        UNDER_BACKGROUND,
        UNDER_TEXT,
        OVER_TEXT;

        static @NotNull Layer of(int zIndex) {
            if (zIndex < UNDER_BACKGROUND_Z_INDEX) {
                return UNDER_BACKGROUND;
            }
            return zIndex < 0 ? UNDER_TEXT : OVER_TEXT;
        }
    }

    private static final class VisiblePlacement {

        final ImagePlacement placement;

        final int row;

        VisiblePlacement(@NotNull ImagePlacement placement, int row) {
            this.placement = placement;
            this.row = row;
        }
    }

    /** The placements of the frame being drawn, lowest z-index first. */
    private final List<VisiblePlacement> myPlacements = new ArrayList<>();

    private final Map<KittyImage, Image> myFxImages = new HashMap<>();

    private final Set<KittyImage> myDrawnImages = new HashSet<>();

    /**
     * Collects the placements that show in the rows of a frame. Call it with the buffer locked.
     */
    void collect(@NotNull TerminalTextBuffer buffer, int scrollOrigin, int rowCount) {
        myPlacements.clear();
        buffer.processImagePlacements(scrollOrigin, rowCount, (placement, row) -> {
            if (!placement.getImage().isDeleted()) {
                myPlacements.add(new VisiblePlacement(placement, row));
            }
        });
        // a stable sort: of the same z-index, what was placed later is drawn on top
        myPlacements.sort(Comparator.comparingInt(visible -> visible.placement.getZIndex()));
    }

    boolean hasPlacements(@NotNull Layer layer) {
        for (VisiblePlacement visible : myPlacements) {
            if (Layer.of(visible.placement.getZIndex()) == layer) {
                return true;
            }
        }
        return false;
    }

    void draw(@NotNull GraphicsContext graphics, @NotNull Layer layer, double cellWidth, double cellHeight,
              double insetX) {
        for (VisiblePlacement visible : myPlacements) {
            ImagePlacement placement = visible.placement;
            if (Layer.of(placement.getZIndex()) != layer) {
                continue;
            }
            Image image = getFxImage(placement.getImage());
            if (image == null) {
                continue;
            }
            graphics.drawImage(image,
                    placement.getSourceX(), placement.getSourceY(),
                    placement.getSourceWidth(), placement.getSourceHeight(),
                    insetX + (placement.getColumn() + placement.getLeft()) * cellWidth,
                    (visible.row + placement.getTop()) * cellHeight,
                    placement.getWidth() * cellWidth, placement.getHeight() * cellHeight);
        }
    }

    /**
     * Ends a frame: drops the JavaFX copies of the images it did not draw.
     */
    void endFrame() {
        myFxImages.keySet().retainAll(myDrawnImages);
        myDrawnImages.clear();
        myPlacements.clear();
    }

    private Image getFxImage(@NotNull KittyImage image) {
        int[] pixels = image.getPixels();
        if (pixels == null) {
            myFxImages.remove(image);
            return null;
        }
        myDrawnImages.add(image);
        return myFxImages.computeIfAbsent(image, key -> {
            WritableImage fxImage = new WritableImage(key.getWidth(), key.getHeight());
            fxImage.getPixelWriter().setPixels(0, 0, key.getWidth(), key.getHeight(),
                    PixelFormat.getIntArgbInstance(), pixels, 0, key.getWidth());
            return fxImage;
        });
    }
}
