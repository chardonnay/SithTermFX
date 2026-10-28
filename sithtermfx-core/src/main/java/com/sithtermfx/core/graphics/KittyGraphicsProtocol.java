package com.sithtermfx.core.graphics;

import com.sithtermfx.core.util.Ascii;
import com.sithtermfx.core.util.PixelSize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The <a href="https://sw.kovidgoyal.net/kitty/graphics-protocol/">kitty graphics protocol</a>: programs send
 * images with {@code ESC _ G <control data> ; <base64 payload> ESC \} and place them at the cursor.
 * <p>
 * Supported are transmitting, displaying and querying ({@code a=t}, {@code a=T}, {@code a=q}), placing a
 * stored image again ({@code a=p}) and deleting ({@code a=d}, every {@code d} target). Images are sent
 * directly in the escape code ({@code t=d}), in one piece or in chunks ({@code m=1}), as 24-bit RGB, 32-bit
 * RGBA or PNG ({@code f=24}, {@code 32}, {@code 100}), optionally zlib-compressed ({@code o=z}), and are
 * addressed by image id ({@code i}), image number ({@code I}) and placement id ({@code p}). A placement takes a
 * source rectangle ({@code x}, {@code y}, {@code w}, {@code h}), an offset within its first cell
 * ({@code X}, {@code Y}), a size in cells ({@code c}, {@code r}), a z-index ({@code z}) and the cursor policy
 * ({@code C}). Responses follow the protocol, including the quiet modes ({@code q}).
 * <p>
 * Answered with an error, so a program can fall back: reading an image from a file, a temporary file or
 * shared memory ({@code t=f}, {@code t}, {@code s}), animation ({@code a=f}, {@code a=a}, {@code a=c}),
 * Unicode placeholders ({@code U=1}) and relative placements ({@code P}, {@code Q}).
 * <p>
 * The decoded images are kept up to a storage quota and up to {@link #MAX_IMAGES} of them; when a new image
 * does not fit, the images no line shows any more are deleted first, then the oldest ones. A line holds at
 * most {@link #MAX_PLACEMENTS_PER_LINE} placements. An image too large for the heap is answered with
 * {@code ENOMEM} and dropped, not the session.
 * <p>
 * Not thread-safe: commands are handled on the emulator thread, and the {@link Host} guards what it shares
 * with the renderer.
 */
public final class KittyGraphicsProtocol {

    /**
     * The most bytes the decoded images of one terminal take by default, 4 per pixel.
     */
    public static final long DEFAULT_STORAGE_QUOTA = 128L * 1024 * 1024;

    /**
     * The largest width and height of an image in pixels.
     */
    public static final int MAX_IMAGE_DIMENSION = 10_000;

    /**
     * The most images one terminal stores, however small they are.
     */
    public static final int MAX_IMAGES = 4096;

    /**
     * The most image placements one line holds; a new one beyond it replaces the oldest.
     */
    public static final int MAX_PLACEMENTS_PER_LINE = 256;

    private static final int MAX_CELLS = MAX_IMAGE_DIMENSION;

    private static final String OK = "OK";

    private static final long MAX_UNSIGNED = 0xFFFF_FFFFL;

    /**
     * Ids the terminal assigns to images sent with only an image number start in the upper half of the id
     * range, away from the small ids programs choose themselves.
     */
    private static final long FIRST_ASSIGNED_ID = 1L << 31;

    /**
     * What the protocol needs from the terminal.
     */
    public interface Host {

        /**
         * @return the size of one character cell in pixels
         */
        @NotNull PixelSize getCellPixelSize();

        /**
         * @return the 0-based column of the cursor, on the screen
         */
        int getCursorColumn();

        /**
         * @return the 0-based row of the cursor
         */
        int getCursorRow();

        /**
         * Anchors the placement at the line of the cursor and, if asked to, moves the cursor right by its
         * columns and down by its rows less one, scrolling when that passes the bottom margin.
         */
        void placeAtCursor(@NotNull ImagePlacement placement, boolean moveCursor);

        /**
         * Removes the placements the filter accepts from the lines of the screen, and with {@code screenOnly}
         * false also from the history and from the main screen while the alternate screen is shown.
         *
         * @return the removed placements
         */
        @NotNull List<ImagePlacement> removePlacements(boolean screenOnly, @NotNull PlacementFilter filter);

        /**
         * @return the images that at least one line holds a placement of
         */
        @NotNull Set<KittyImage> getPlacedImages();

        void sendResponse(@NotNull String response);
    }

    @FunctionalInterface
    public interface PlacementFilter {

        /**
         * @param row the 0-based screen row of the line that holds the placement: negative in the history, and
         *            {@link Integer#MIN_VALUE} on the main screen while the alternate screen is shown
         */
        boolean accept(@NotNull ImagePlacement placement, int row);
    }

    private final Host myHost;

    private final long myStorageQuota;

    /** The stored images, oldest first. */
    private final List<KittyImage> myImages = new ArrayList<>();

    private final Map<Long, KittyImage> myImagesById = new HashMap<>();

    private long myStoredBytes;

    private long myNextSequence;

    private long myNextAssignedId = FIRST_ASSIGNED_ID;

    /** A chunked transmission ({@code m=1}) whose last chunk has not arrived yet. */
    private @Nullable Transmission myTransmission;

    public KittyGraphicsProtocol(@NotNull Host host) {
        this(host, DEFAULT_STORAGE_QUOTA);
    }

    /**
     * @param storageQuota the most bytes the decoded images take, 4 per pixel
     */
    public KittyGraphicsProtocol(@NotNull Host host, long storageQuota) {
        if (storageQuota <= 0) {
            throw new IllegalArgumentException("storage quota must be positive: " + storageQuota);
        }
        myHost = host;
        myStorageQuota = storageQuota;
    }

    /**
     * Handles one command.
     *
     * @param body what follows {@code ESC _ G}, up to the terminator
     */
    public void handle(@NotNull String body) {
        KittyGraphicsCommand command = KittyGraphicsCommand.parse(body);
        Transmission transmission = myTransmission;
        try {
            dispatch(command, transmission);
        } catch (OutOfMemoryError e) {
            // An image as large as the quota allows can be more than the heap holds: drop it, not the session.
            myTransmission = null;
            KittyGraphicsCommand answered = transmission != null && isChunkOf(command, transmission)
                    ? transmission.command : command;
            respond(answered, answered.getUnsignedOrZero('i'), answered.getQuiet(), "ENOMEM:Out of memory");
        }
    }

    private void dispatch(@NotNull KittyGraphicsCommand command, @Nullable Transmission transmission) {
        if (transmission != null) {
            if (isChunkOf(command, transmission)) {
                continueTransmission(transmission, command);
                return;
            }
            // Any other command ends an unfinished transmission; deleting during one is how a program aborts it.
            myTransmission = null;
        }
        try {
            if (command.getMalformedPair() != null) {
                throw new KittyGraphicsException("EINVAL", "Malformed control data: " + command.getMalformedPair());
            }
            if (command.getUnsigned('i') != 0 && command.getUnsigned('I') != 0) {
                throw new KittyGraphicsException("EINVAL", "Both an image id and an image number were given");
            }
            char action = command.getChar('a', 't');
            switch (action) {
                case 't':
                case 'T':
                case 'q':
                case 'f':
                    startTransmission(command, action);
                    break;
                case 'p':
                    placeStoredImage(command);
                    break;
                case 'd':
                    delete(command);
                    break;
                case 'a':
                case 'c':
                    throw new KittyGraphicsException("EINVAL", "Animation is not supported");
                default:
                    throw new KittyGraphicsException("EINVAL", "Unknown action: " + action);
            }
        } catch (KittyGraphicsException e) {
            respond(command, command.getUnsignedOrZero('i'), command.getQuiet(), e.getMessage());
        }
    }

    /**
     * Deletes every image, for a full reset of the terminal.
     */
    public void reset() {
        for (KittyImage image : myImages) {
            image.delete();
        }
        myImages.clear();
        myImagesById.clear();
        myStoredBytes = 0;
        myTransmission = null;
    }

    /**
     * @return the stored images, oldest first
     */
    public @NotNull List<KittyImage> getImages() {
        return Collections.unmodifiableList(new ArrayList<>(myImages));
    }

    long getStoredBytes() {
        return myStoredBytes;
    }

    private static boolean isChunkOf(@NotNull KittyGraphicsCommand command, @NotNull Transmission transmission) {
        // A later chunk carries only m and q, and the action of an animation frame. A command with anything
        // else is a new one, from a program that gave up on the transmission.
        for (char key : new char[]{'i', 'I', 'p', 'f', 't', 's', 'v', 'o', 'd'}) {
            if (command.has(key)) {
                return false;
            }
        }
        try {
            return !command.has('a') || command.getChar('a', 't') == transmission.action;
        } catch (KittyGraphicsException e) {
            return false;
        }
    }

    private void startTransmission(@NotNull KittyGraphicsCommand command, char action) throws KittyGraphicsException {
        Transmission transmission = new Transmission(command, action, myStorageQuota);
        try {
            char medium = command.getChar('t', 'd');
            if (medium != 'd') {
                throw new KittyGraphicsException("EINVAL", "Only direct transmission (t=d) is supported");
            }
            int format = command.has('f') ? command.getCount('f') : KittyImageDecoder.FORMAT_RGBA;
            if (format != KittyImageDecoder.FORMAT_RGB && format != KittyImageDecoder.FORMAT_RGBA
                    && format != KittyImageDecoder.FORMAT_PNG) {
                throw new KittyGraphicsException("EINVAL", "Unknown image format: " + format);
            }
            char compression = command.getChar('o', '\0');
            if (compression != '\0' && compression != 'z') {
                throw new KittyGraphicsException("EINVAL", "Unknown compression: " + compression);
            }
            if (format != KittyImageDecoder.FORMAT_PNG) {
                int width = command.getCount('s');
                int height = command.getCount('v');
                if (width == 0 || height == 0) {
                    throw new KittyGraphicsException("EINVAL", "The image width and height must be given");
                }
                if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                        || 4L * width * height > myStorageQuota) {
                    throw new KittyGraphicsException("EFBIG", "The image is too large: " + width + "x" + height);
                }
                if (compression == '\0') {
                    // raw pixels: what is more than the image needs is not kept
                    transmission.limit = (long) width * height * (format / 8);
                    transmission.dropBeyondLimit = true;
                }
            }
        } catch (KittyGraphicsException e) {
            if (!hasMoreChunks(command)) {
                throw e;
            }
            // swallow the chunks still to come and answer once, after the last one
            transmission.error = e;
        }
        appendPayload(transmission, command.getPayload());
        if (hasMoreChunks(command)) {
            myTransmission = transmission;
        } else {
            finishTransmission(transmission);
        }
    }

    private void continueTransmission(@NotNull Transmission transmission, @NotNull KittyGraphicsCommand command) {
        if (command.has('q')) {
            transmission.quiet = command.getQuiet();
        }
        appendPayload(transmission, command.getPayload());
        if (!hasMoreChunks(command)) {
            myTransmission = null;
            finishTransmission(transmission);
        }
    }

    private static boolean hasMoreChunks(@NotNull KittyGraphicsCommand command) {
        try {
            return command.getCount('m') == 1;
        } catch (KittyGraphicsException e) {
            return false;
        }
    }

    private void appendPayload(@NotNull Transmission transmission, @NotNull String payload) {
        if (transmission.error != null) {
            return;
        }
        // Chunks hold whole groups of four base64 chars, but keep any rest for the next chunk all the same.
        String chars = transmission.base64Rest.isEmpty() ? payload : transmission.base64Rest + payload;
        int whole = chars.length() - chars.length() % 4;
        transmission.base64Rest = chars.substring(whole);
        appendDecoded(transmission, chars.substring(0, whole));
    }

    private void appendDecoded(@NotNull Transmission transmission, @NotNull String base64) {
        if (base64.isEmpty() || transmission.error != null) {
            return;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            transmission.error = new KittyGraphicsException("EINVAL", "The payload is not base64");
            return;
        }
        if (!transmission.append(bytes)) {
            transmission.error = new KittyGraphicsException("EFBIG", "The image data is too large");
        }
    }

    private void finishTransmission(@NotNull Transmission transmission) {
        KittyGraphicsCommand command = transmission.command;
        try {
            // the last chunk may end without its padding
            String rest = transmission.base64Rest;
            transmission.base64Rest = "";
            appendDecoded(transmission, rest);
            if (transmission.error != null) {
                throw transmission.error;
            }
            int format = command.has('f') ? command.getCount('f') : KittyImageDecoder.FORMAT_RGBA;
            KittyImageDecoder.Decoded decoded = KittyImageDecoder.decode(format, command.has('o'),
                    command.getCount('s'), command.getCount('v'), transmission.data, transmission.size,
                    MAX_IMAGE_DIMENSION, myStorageQuota);
            switch (transmission.action) {
                case 'q':
                    respond(command, command.getUnsigned('i'), transmission.quiet, OK);
                    break;
                case 'f':
                    throw new KittyGraphicsException("EINVAL", "Animation frames are not supported");
                default:
                    KittyImage image = store(command, decoded);
                    if (transmission.action == 'T') {
                        place(command, image);
                    }
                    respond(command, image.getId(), transmission.quiet, OK);
                    break;
            }
        } catch (KittyGraphicsException e) {
            respond(command, command.getUnsignedOrZero('i'), transmission.quiet, e.getMessage());
        }
    }

    private @NotNull KittyImage store(@NotNull KittyGraphicsCommand command, @NotNull KittyImageDecoder.Decoded decoded)
            throws KittyGraphicsException {
        long id = command.getUnsigned('i');
        long number = command.getUnsigned('I');
        if (id != 0) {
            // an image sent with the id of another one replaces it, and its placements go with it
            KittyImage replaced = myImagesById.get(id);
            if (replaced != null) {
                deleteImage(replaced, true);
            }
        } else if (number != 0) {
            id = assignId();
        }
        long bytes = 4L * decoded.width * decoded.height;
        makeRoom(bytes);
        KittyImage image = new KittyImage(id, number, decoded.width, decoded.height, decoded.pixels, myNextSequence++);
        myImages.add(image);
        if (id != 0) {
            myImagesById.put(id, image);
        }
        myStoredBytes += bytes;
        return image;
    }

    private long assignId() {
        while (myImagesById.containsKey(myNextAssignedId)) {
            advanceAssignedId();
        }
        long id = myNextAssignedId;
        advanceAssignedId();
        return id;
    }

    private void advanceAssignedId() {
        myNextAssignedId = myNextAssignedId >= MAX_UNSIGNED ? FIRST_ASSIGNED_ID : myNextAssignedId + 1;
    }

    /**
     * Deletes images until one of {@code bytes} more fits into the quota and the image count: first those no
     * line shows, then the others, oldest first.
     */
    private void makeRoom(long bytes) {
        if (fits(bytes)) {
            return;
        }
        Set<KittyImage> placed = myHost.getPlacedImages();
        for (boolean evictPlaced : new boolean[]{false, true}) {
            for (KittyImage image : new ArrayList<>(myImages)) {
                if (fits(bytes)) {
                    return;
                }
                if (placed.contains(image) == evictPlaced) {
                    deleteImage(image, evictPlaced);
                }
            }
        }
    }

    private boolean fits(long bytes) {
        return myStoredBytes + bytes <= myStorageQuota && myImages.size() < MAX_IMAGES;
    }

    private void deleteImage(@NotNull KittyImage image, boolean removePlacements) {
        myImages.remove(image);
        if (myImagesById.get(image.getId()) == image) {
            myImagesById.remove(image.getId());
        }
        myStoredBytes -= image.getByteCount();
        image.delete();
        if (removePlacements) {
            myHost.removePlacements(false, (placement, row) -> placement.getImage() == image);
        }
    }

    private void placeStoredImage(@NotNull KittyGraphicsCommand command) throws KittyGraphicsException {
        long id = command.getUnsigned('i');
        long number = command.getUnsigned('I');
        KittyImage image = id != 0 ? myImagesById.get(id) : newestWithNumber(number);
        if (image == null) {
            if (id == 0 && number == 0) {
                throw new KittyGraphicsException("EINVAL", "No image id or number was given");
            }
            throw new KittyGraphicsException("ENOENT",
                    id != 0 ? "No image with id " + id : "No image with number " + number);
        }
        place(command, image);
        respond(command, image.getId(), command.getQuiet(), OK);
    }

    private @Nullable KittyImage newestWithNumber(long number) {
        if (number == 0) {
            return null;
        }
        for (int i = myImages.size() - 1; i >= 0; i--) {
            if (myImages.get(i).getNumber() == number) {
                return myImages.get(i);
            }
        }
        return null;
    }

    private void place(@NotNull KittyGraphicsCommand command, @NotNull KittyImage image) throws KittyGraphicsException {
        if (command.getCount('U') == 1) {
            throw new KittyGraphicsException("EINVAL", "Unicode placeholders are not supported");
        }
        if (command.getUnsigned('P') != 0 || command.getUnsigned('Q') != 0) {
            throw new KittyGraphicsException("EINVAL", "Relative placements are not supported");
        }
        ImagePlacement placement = layOut(command, image, myHost.getCursorColumn(), myHost.getCellPixelSize());
        long placementId = placement.getId();
        if (placementId != 0) {
            // a placement with the id of another one of the same image replaces it
            myHost.removePlacements(false, (other, row) -> other.getImage() == image && other.getId() == placementId);
        }
        myHost.placeAtCursor(placement, command.getCount('C') != 1);
    }

    /**
     * Works out the cells a placement covers and where in them the image is drawn: at its own size in pixels,
     * or scaled to the given columns or rows keeping its aspect ratio, or fit into the given columns and rows
     * and centred there.
     */
    static @NotNull ImagePlacement layOut(@NotNull KittyGraphicsCommand command, @NotNull KittyImage image,
                                          int column, @NotNull PixelSize cell) throws KittyGraphicsException {
        int cellWidth = Math.max(1, cell.getWidth());
        int cellHeight = Math.max(1, cell.getHeight());
        int sourceX = Math.min(command.getCount('x'), image.getWidth());
        int sourceY = Math.min(command.getCount('y'), image.getHeight());
        int sourceWidth = sourceSize(command.getCount('w'), image.getWidth() - sourceX);
        int sourceHeight = sourceSize(command.getCount('h'), image.getHeight() - sourceY);
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new KittyGraphicsException("EINVAL", "The source rectangle is outside the image");
        }
        double offsetX = Math.min(command.getCount('X'), cellWidth - 1);
        double offsetY = Math.min(command.getCount('Y'), cellHeight - 1);
        int columns = Math.min(command.getCount('c'), MAX_CELLS);
        int rows = Math.min(command.getCount('r'), MAX_CELLS);
        double width;
        double height;
        if (columns == 0 && rows == 0) {
            width = sourceWidth;
            height = sourceHeight;
        } else if (rows == 0) {
            width = Math.max(1, columns * cellWidth - offsetX);
            height = width * sourceHeight / sourceWidth;
        } else if (columns == 0) {
            height = Math.max(1, rows * cellHeight - offsetY);
            width = height * sourceWidth / sourceHeight;
        } else {
            double boxWidth = Math.max(1, columns * cellWidth - offsetX);
            double boxHeight = Math.max(1, rows * cellHeight - offsetY);
            double scale = Math.min(boxWidth / sourceWidth, boxHeight / sourceHeight);
            width = sourceWidth * scale;
            height = sourceHeight * scale;
            offsetX += (boxWidth - width) / 2;
            offsetY += (boxHeight - height) / 2;
        }
        if (columns == 0) {
            columns = cellsFor(offsetX + width, cellWidth);
        }
        if (rows == 0) {
            rows = cellsFor(offsetY + height, cellHeight);
        }
        long placementId = image.getId() != 0 ? command.getUnsigned('p') : 0;
        return new ImagePlacement(image, placementId, column, columns, rows,
                sourceX, sourceY, sourceWidth, sourceHeight,
                offsetX / cellWidth, offsetY / cellHeight, width / cellWidth, height / cellHeight,
                command.getSigned('z', 0));
    }

    private static int sourceSize(int requested, int available) {
        return requested == 0 ? available : Math.min(requested, available);
    }

    private static int cellsFor(double pixels, int cellSize) {
        // the epsilon keeps a size that is a whole number of cells from rounding up to one more
        return (int) Math.max(1, Math.min(MAX_CELLS, Math.ceil(pixels / cellSize - 1e-9)));
    }

    private void delete(@NotNull KittyGraphicsCommand command) throws KittyGraphicsException {
        char target = command.getChar('d', 'a');
        // images an uppercase target frees, once no line shows them any more
        Set<KittyImage> images = new HashSet<>();
        List<ImagePlacement> removed;
        switch (Character.toLowerCase(target)) {
            case 'a':
                removed = myHost.removePlacements(true, (placement, row) -> true);
                break;
            case 'i':
                removed = removePlacementsOf(myImagesById.get(command.getUnsigned('i')), command.getUnsigned('p'), images);
                break;
            case 'n':
                removed = removePlacementsOf(newestWithNumber(command.getUnsigned('I')), command.getUnsigned('p'), images);
                break;
            case 'c': {
                int column = myHost.getCursorColumn();
                int cursorRow = myHost.getCursorRow();
                removed = myHost.removePlacements(true, (placement, row) -> placement.intersects(column, cursorRow - row));
                break;
            }
            case 'p':
            case 'q': {
                int column = command.getCount('x') - 1;
                int cellRow = command.getCount('y') - 1;
                boolean anyZIndex = Character.toLowerCase(target) == 'p';
                int zIndex = command.getSigned('z', 0);
                removed = myHost.removePlacements(true, (placement, row) -> placement.intersects(column, cellRow - row)
                        && (anyZIndex || placement.getZIndex() == zIndex));
                break;
            }
            case 'x': {
                int column = command.getCount('x') - 1;
                removed = myHost.removePlacements(true, (placement, row) ->
                        column >= placement.getColumn() && column < placement.getColumn() + placement.getColumns());
                break;
            }
            case 'y': {
                int cellRow = command.getCount('y') - 1;
                removed = myHost.removePlacements(true, (placement, row) ->
                        cellRow >= row && cellRow < row + placement.getRows());
                break;
            }
            case 'z': {
                int zIndex = command.getSigned('z', 0);
                removed = myHost.removePlacements(true, (placement, row) -> placement.getZIndex() == zIndex);
                break;
            }
            case 'r': {
                long first = command.getUnsigned('x');
                long last = command.getUnsigned('y');
                for (KittyImage image : myImages) {
                    if (image.getId() != 0 && image.getId() >= first && image.getId() <= last) {
                        images.add(image);
                    }
                }
                Set<KittyImage> inRange = new HashSet<>(images);
                removed = myHost.removePlacements(false, (placement, row) -> inRange.contains(placement.getImage()));
                break;
            }
            case 'f':
                // there are no animation frames to delete
                return;
            default:
                throw new KittyGraphicsException("EINVAL", "Unknown delete target: " + target);
        }
        if (!Character.isUpperCase(target)) {
            return;
        }
        for (ImagePlacement placement : removed) {
            images.add(placement.getImage());
        }
        if (images.isEmpty()) {
            return;
        }
        images.removeAll(myHost.getPlacedImages());
        for (KittyImage image : images) {
            if (!image.isDeleted()) {
                deleteImage(image, false);
            }
        }
    }

    private @NotNull List<ImagePlacement> removePlacementsOf(@Nullable KittyImage image, long placementId,
                                                             @NotNull Set<KittyImage> images) {
        if (image == null) {
            return Collections.emptyList();
        }
        images.add(image);
        return myHost.removePlacements(false, (placement, row) ->
                placement.getImage() == image && (placementId == 0 || placement.getId() == placementId));
    }

    /**
     * Answers a command, unless it asked not to be ({@code q}) or named no image: a program learns the outcome
     * only of commands with an image id or number.
     */
    private void respond(@NotNull KittyGraphicsCommand command, long id, int quiet, @NotNull String message) {
        boolean ok = OK.equals(message);
        if (quiet >= (ok ? 1 : 2)) {
            return;
        }
        long number = command.getUnsignedOrZero('I');
        if (id == 0 && number == 0) {
            return;
        }
        StringBuilder response = new StringBuilder().append(Ascii.ESC_CHAR).append("_G");
        if (id != 0) {
            response.append("i=").append(id);
        }
        if (number != 0) {
            response.append(id != 0 ? "," : "").append("I=").append(number);
        }
        long placementId = command.getUnsignedOrZero('p');
        if (placementId != 0 && id != 0) {
            response.append(",p=").append(placementId);
        }
        response.append(';');
        // an error may quote the command; never echo a control char back into the program's input
        for (int i = 0; i < message.length(); i++) {
            char ch = message.charAt(i);
            response.append(ch < 0x20 || (ch >= 0x7F && ch < 0xA0) ? '?' : ch);
        }
        response.append(Ascii.ESC_CHAR).append('\\');
        myHost.sendResponse(response.toString());
    }

    private static final class Transmission {

        final @NotNull KittyGraphicsCommand command;

        final char action;

        /** The decoded payload, its first {@link #size} bytes. */
        byte @NotNull [] data = new byte[0];

        int size;

        /** The most bytes the payload may have. */
        long limit;

        /** Whether bytes past the limit are dropped rather than failing the transmission. */
        boolean dropBeyondLimit;

        /** The base64 chars after the last whole group of four. */
        @NotNull String base64Rest = "";

        int quiet;

        @Nullable KittyGraphicsException error;

        Transmission(@NotNull KittyGraphicsCommand command, char action, long limit) {
            this.command = command;
            this.action = action;
            this.limit = Math.min(limit, Integer.MAX_VALUE - 8);
            this.quiet = command.getQuiet();
        }

        /**
         * @return false when the payload would grow past its limit
         */
        boolean append(byte @NotNull [] bytes) {
            int count = bytes.length;
            if (size + (long) count > limit) {
                if (!dropBeyondLimit) {
                    return false;
                }
                count = (int) (limit - size);
            }
            if (size + count > data.length) {
                data = Arrays.copyOf(data, (int) Math.min(limit, Math.max(2L * data.length, (long) size + count)));
            }
            System.arraycopy(bytes, 0, data, size, count);
            size += count;
            return true;
        }
    }
}
