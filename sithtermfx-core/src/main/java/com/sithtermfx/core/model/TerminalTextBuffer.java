package com.sithtermfx.core.model;

import com.sithtermfx.core.compatibility.Point;
import com.sithtermfx.core.util.CellPosition;
import com.sithtermfx.core.util.TermSize;
import com.sithtermfx.core.StyledTextConsumer;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.graphics.ImagePlacement;
import com.sithtermfx.core.graphics.KittyGraphicsProtocol;
import com.sithtermfx.core.graphics.KittyImage;
import com.sithtermfx.core.model.TerminalLine.TextEntry;
import com.sithtermfx.core.model.hyperlinks.TextProcessing;
import com.sithtermfx.core.util.CharUtils;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ObjIntConsumer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Buffer for storing styled text data.
 * Stores only text that fit into one screen XxY, but has scrollBuffer to save history lines and screenBuffer to restore
 * screen after resize. ScrollBuffer stores all lines before the first line currently shown on the screen. TextBuffer
 * stores lines that are shown currently on the screen and they have there(in TextBuffer) their initial length (even if
 * it doesn't fit to screen width).
 * <p/>
 * The history can be bounded by memory as well as by line count ({@link #setHistoryMemoryLimit}),
 * and its lines can be compressed while the terminal is idle ({@link #setScrollbackCompressionEnabled}).
 */
public class TerminalTextBuffer {

    private static final Logger logger = LoggerFactory.getLogger(TerminalTextBuffer.class);

    private static final Boolean USE_CONPTY_COMPATIBLE_RESIZE = true;

    @NotNull
    private final StyleState myStyleState;

    private LinesBuffer myHistoryBuffer;

    private LinesBuffer myScreenBuffer;

    private int myWidth;

    private int myHeight;

    private final int myHistoryLinesCount;

    private final Lock myLock = new ReentrantLock();

    private LinesBuffer myHistoryBufferBackup;

    private LinesBuffer myScreenBufferBackup; // to store textBuffer after switching to alternate buffer

    private boolean myAlternateBuffer = false;

    private boolean myUsingAlternateBuffer = false;

    private final List<TerminalModelListener> myListeners = new CopyOnWriteArrayList<>();

    private final List<TerminalModelListener> myTypeAheadListeners = new CopyOnWriteArrayList<>();

    private final List<TerminalHistoryBufferListener> myHistoryBufferListeners = new CopyOnWriteArrayList<>();

    @Nullable
    private final TextProcessing myTextProcessing;

    /** The most rows an image placement ever covered: how far above a window an image may start that shows in it. */
    private int myMaxImageRows;

    /** The style {@link #myEmptyStyleWithCurrentColor} was made from. */
    private TextStyle myEmptyStyleSource;

    private TextStyle myEmptyStyleWithCurrentColor;

    /** The history's memory limit in bytes, 0 or less for none. */
    private long myHistoryMaxBytes = -1;

    private boolean myCompressionEnabled;

    private volatile boolean myCompressionPaused;

    /** When the buffer last changed, for the idle check of the background compression. */
    private volatile long myLastChangeNanos = System.nanoTime();

    /** Whether a compression pass may find something to pack: set by changes, cleared by a full pass. */
    private volatile boolean myCompressionPending;

    /** {@link PackedLineBlock#unpackCount()} at the end of the last full compression pass. */
    private long myCompressionPassUnpacks;

    /** The history rows the last render showed, {@code [from, to)}; empty while the view is at the bottom. */
    private int myViewHistoryFrom;

    private int myViewHistoryTo;

    public TerminalTextBuffer(final int width, final int height, @NotNull StyleState styleState) {
        this(width, height, styleState, null);
    }

    public TerminalTextBuffer(final int width, final int height, @NotNull StyleState styleState,
                              @Nullable TextProcessing textProcessing) {
        this(width, height, styleState, LinesBuffer.DEFAULT_MAX_LINES_COUNT, textProcessing);
    }

    public TerminalTextBuffer(final int width, final int height, @NotNull StyleState styleState,
                              final int historyLinesCount, @Nullable TextProcessing textProcessing) {
        myStyleState = styleState;
        myWidth = width;
        myHeight = height;
        myHistoryLinesCount = historyLinesCount;
        myTextProcessing = textProcessing;
        myScreenBuffer = createScreenBuffer();
        myHistoryBuffer = createHistoryBuffer();
    }

    @NotNull
    private LinesBuffer createScreenBuffer() {
        return new LinesBuffer(-1, myTextProcessing);
    }

    @NotNull
    private LinesBuffer createHistoryBuffer() {
        LinesBuffer buffer = new LinesBuffer(myHistoryLinesCount, myTextProcessing);
        buffer.setMaxBytes(myHistoryMaxBytes);
        buffer.setPackingAllowed(isPackingAllowed());
        return buffer;
    }

    @NotNull
    TerminalResizeResult resize(@NotNull TermSize newTermSize, @NotNull CellPosition oldCursor,
                                @Nullable TerminalSelection selection) {
        final int newWidth = newTermSize.getColumns();
        final int newHeight = newTermSize.getRows();
        int newCursorX = oldCursor.getX();
        int newCursorY = oldCursor.getY();
        final int oldCursorY = oldCursor.getY();
        if (myWidth != newWidth) {
            ChangeWidthOperation changeWidthOperation = new ChangeWidthOperation(this, newWidth, newHeight);
            Point cursorPoint = new Point(oldCursor.getX() - 1, oldCursor.getY() - 1);
            changeWidthOperation.addPointToTrack(cursorPoint, true);
            if (selection != null) {
                changeWidthOperation.addPointToTrack(selection.getStart(), false);
                changeWidthOperation.addPointToTrack(selection.getEnd(), false);
            }
            changeWidthOperation.run();
            myWidth = newWidth;
            myHeight = newHeight;
            Point newCursor = changeWidthOperation.getTrackedPoint(cursorPoint);
            newCursorX = newCursor.x + 1;
            newCursorY = newCursor.y + 1;
            if (selection != null) {
                selection.getStart().setLocation(changeWidthOperation.getTrackedPoint(selection.getStart()));
                selection.getEnd().setLocation(changeWidthOperation.getTrackedPoint(selection.getEnd()));
            }
        }
        final int oldHeight = myHeight;
        if (newHeight < oldHeight) {
            if (!myAlternateBuffer) {
                int lineDiffCount = oldHeight - newHeight;
                //we need to move lines from text buffer to the scroll buffer
                //but empty bottom lines up to the cursor can be collapsed
                int maxBottomLinesToRemove = Math.min(lineDiffCount, Math.max(0, oldHeight - oldCursorY));
                int emptyLinesDeleted = myScreenBuffer.removeBottomEmptyLines(oldHeight - 1, maxBottomLinesToRemove);
                int screenLinesToMove = lineDiffCount - emptyLinesDeleted;
                myScreenBuffer.moveTopLinesTo(screenLinesToMove, myHistoryBuffer);
                newCursorY = oldCursorY - screenLinesToMove;
                if (selection != null) {
                    selection.shiftY(-screenLinesToMove);
                }
            } else {
                newCursorY = oldCursorY;
            }
        } else if (newHeight > oldHeight) {
            if (USE_CONPTY_COMPATIBLE_RESIZE) {
                // do not move lines from scroll buffer to the screen buffer
                newCursorY = oldCursorY;
            } else {
                if (!myAlternateBuffer) {
                    //we need to move lines from scroll buffer to the text buffer
                    int historyLinesCount = Math.min(newHeight - oldHeight, myHistoryBuffer.getLineCount());
                    myHistoryBuffer.moveBottomLinesTo(historyLinesCount, myScreenBuffer);
                    newCursorY = oldCursorY + historyLinesCount;
                    if (selection != null) {
                        selection.shiftY(historyLinesCount);
                    }
                } else {
                    newCursorY = oldCursorY;
                }
            }
        }
        myWidth = newWidth;
        myHeight = newHeight;
        fireModelChangeEvent();
        return new TerminalResizeResult(new CellPosition(newCursorX, newCursorY));
    }

    public void addModelListener(TerminalModelListener listener) {
        myListeners.add(listener);
    }

    public void removeModelListener(TerminalModelListener listener) {
        myListeners.remove(listener);
    }

    public void addHistoryBufferListener(@NotNull TerminalHistoryBufferListener listener) {
        myHistoryBufferListeners.add(listener);
    }

    public void removeHistoryBufferListener(@NotNull TerminalHistoryBufferListener listener) {
        myHistoryBufferListeners.remove(listener);
    }

    public void addTypeAheadModelListener(TerminalModelListener listener) {
        myTypeAheadListeners.add(listener);
    }

    public void removeTypeAheadModelListener(TerminalModelListener listener) {
        myTypeAheadListeners.remove(listener);
    }

    void fireModelChangeEvent() {
        myLastChangeNanos = System.nanoTime();
        myCompressionPending = true;
        for (TerminalModelListener modelListener : myListeners) {
            modelListener.modelChanged();
        }
    }

    public void fireTypeAheadModelChangeEvent() {
        for (TerminalModelListener modelListener : myTypeAheadListeners) {
            modelListener.modelChanged();
        }
    }

    private TextStyle createEmptyStyleWithCurrentColor() {
        // Every scrolled line gets a filler in this style: share one object (with its option set,
        // ~56 bytes) while the current style stays the same, instead of one per line.
        TextStyle current = myStyleState.getCurrent();
        TextStyle empty = myEmptyStyleWithCurrentColor;
        if (current != myEmptyStyleSource || empty == null) {
            empty = current.createEmptyWithColors();
            myEmptyStyleSource = current;
            myEmptyStyleWithCurrentColor = empty;
        }
        return empty;
    }

    private TextEntry createFillerEntry() {
        return new TextEntry(createEmptyStyleWithCurrentColor(), new CharBuffer(CharUtils.NUL_CHAR, myWidth));
    }

    public void deleteCharacters(final int x, final int y, final int count) {
        if (y > myHeight - 1 || y < 0) {
            logger.error("attempt to delete in line " + y + "\n" +
                    "args were x:" + x + " count:" + count);
        } else if (count < 0) {
            logger.error("Attempt to delete negative chars number: count:" + count);
        } else if (count > 0) {
            myScreenBuffer.deleteCharacters(x, y, count, createEmptyStyleWithCurrentColor());

            fireModelChangeEvent();
        }
    }

    public void insertBlankCharacters(final int x, final int y, final int count) {
        if (y > myHeight - 1 || y < 0) {
            logger.error("attempt to insert blank chars in line " + y + "\n" +
                    "args were x:" + x + " count:" + count);
        } else if (count < 0) {
            logger.error("Attempt to insert negative blank chars number: count:" + count);
        } else if (count > 0) { //nothing to do
            myScreenBuffer.insertBlankCharacters(x, y, count, myWidth, createEmptyStyleWithCurrentColor());

            fireModelChangeEvent();
        }
    }

    public void writeString(final int x, final int y, @NotNull final CharBuffer str) {
        writeString(x, y, str, myStyleState.getCurrent());
    }

    public void addLine(@NotNull final TerminalLine line) {
        myScreenBuffer.addLines(List.of(line));

        fireModelChangeEvent();
    }

    private void writeString(int x, int y, @NotNull CharBuffer str, @NotNull TextStyle style) {
        myScreenBuffer.writeString(x, y - 1, str, style);

        fireModelChangeEvent();
    }

    public void scrollArea(final int scrollRegionTop, final int dy, int scrollRegionBottom) {
        if (dy == 0) {
            return;
        }
        if (dy > 0) {
            insertLines(scrollRegionTop - 1, dy, scrollRegionBottom);
        } else {
            LinesBuffer removed = deleteLines(scrollRegionTop - 1, -dy, scrollRegionBottom);
            if (scrollRegionTop == 1) {
                removed.moveTopLinesTo(removed.getLineCount(), myHistoryBuffer);
            }

            fireModelChangeEvent();
        }
    }

    /**
     * Returns terminal lines. Negative indexes are for history buffer. Non-negative for screen buffer.
     *
     * @param index index of line
     * @return history lines for {@code index < 0}, screen line for {@code index >= 0}
     */
    public @NotNull TerminalLine getLine(int index) {
        if (index >= 0) {
            if (index >= getHeight()) {
                logger.error("Attempt to get line out of bounds: " + index + " >= " + getHeight());
                return TerminalLine.createEmpty();
            }
            return myScreenBuffer.getLine(index);
        } else {
            if (index < -getHistoryLinesCount()) {
                logger.error("Attempt to get line out of bounds: " + index + " < " + -getHistoryLinesCount());
                return TerminalLine.createEmpty();
            }
            return myHistoryBuffer.getLine(getHistoryLinesCount() + index);
        }
    }

    public String getScreenLines() {
        myLock.lock();
        try {
            final StringBuilder sb = new StringBuilder();
            for (int row = 0; row < myHeight; row++) {
                StringBuilder line = new StringBuilder(myScreenBuffer.getLine(row).getText());
                for (int i = line.length(); i < myWidth; i++) {
                    line.append(' ');
                }
                if (line.length() > myWidth) {
                    line.setLength(myWidth);
                }
                sb.append(line);
                sb.append('\n');
            }
            return sb.toString();
        } finally {
            myLock.unlock();
        }
    }

    public void processScreenLines(final int yStart, final int yCount, @NotNull final StyledTextConsumer consumer) {
        myScreenBuffer.processLines(yStart, yCount, consumer);
    }

    public void lock() {
        myLock.lock();
    }

    public void unlock() {
        myLock.unlock();
    }

    public void modify(@NotNull Runnable runnable) {
        myLock.lock();
        try {
            runnable.run();
        } finally {
            myLock.unlock();
        }
    }

    public boolean tryLock() {
        return myLock.tryLock();
    }

    public int getWidth() {
        return myWidth;
    }

    public int getHeight() {
        return myHeight;
    }

    public int getHistoryLinesCount() {
        return myHistoryBuffer.getLineCount();
    }

    public int getScreenLinesCount() {
        return myScreenBuffer.getLineCount();
    }

    public char getBuffersCharAt(int x, int y) {
        return getLine(y).charAt(x);
    }

    public TextStyle getStyleAt(int x, int y) {
        return getLine(y).getStyleAt(x);
    }

    public @NotNull Pair<Character, TextStyle> getStyledCharAt(int x, int y) {
        TerminalLine line = getLine(y);
        return new Pair<>(line.charAt(x), line.getStyleAt(x));
    }

    public char getCharAt(int x, int y) {
        return getLine(y).charAt(x);
    }

    public boolean isUsingAlternateBuffer() {
        return myUsingAlternateBuffer;
    }

    public void useAlternateBuffer(boolean enabled) {
        myAlternateBuffer = enabled;
        if (enabled) {
            if (!myUsingAlternateBuffer) {
                myScreenBufferBackup = myScreenBuffer;
                myHistoryBufferBackup = myHistoryBuffer;
                myScreenBuffer = createScreenBuffer();
                myHistoryBuffer = createHistoryBuffer();
                myUsingAlternateBuffer = true;
            }
        } else {
            if (myUsingAlternateBuffer) {
                myScreenBuffer = myScreenBufferBackup;
                myHistoryBuffer = myHistoryBufferBackup;
                myScreenBufferBackup = createScreenBuffer();
                myHistoryBufferBackup = createHistoryBuffer();
                myUsingAlternateBuffer = false;
            }
        }
        fireModelChangeEvent();
    }

    public LinesBuffer getHistoryBuffer() {
        return myHistoryBuffer;
    }

    public @NotNull LinesBuffer getScreenBuffer() {
        return myScreenBuffer;
    }

    public void insertLines(int y, int count, int scrollRegionBottom) {
        myScreenBuffer.insertLines(y, count, scrollRegionBottom - 1, createFillerEntry());
        fireModelChangeEvent();
    }

    // returns deleted lines
    public LinesBuffer deleteLines(int y, int count, int scrollRegionBottom) {
        LinesBuffer linesBuffer = myScreenBuffer.deleteLines(y, count, scrollRegionBottom - 1, createFillerEntry());
        fireModelChangeEvent();
        return linesBuffer;
    }

    public void clearLines(int startRow, int endRow) {
        myScreenBuffer.clearLines(startRow, endRow, createFillerEntry());
        fireModelChangeEvent();
    }

    public void eraseCharacters(int leftX, int rightX, int y) {
        TextStyle style = createEmptyStyleWithCurrentColor();
        if (y >= 0) {
            myScreenBuffer.clearArea(leftX, y, rightX, y + 1, style);
            fireModelChangeEvent();
            if (myTextProcessing != null && y < getHeight()) {
                myTextProcessing.processHyperlinks(myScreenBuffer, getLine(y));
            }
        } else {
            logger.error("Attempt to erase characters in line: " + y);
        }
    }

    public void clearScreenAndHistoryBuffers() {
        myScreenBuffer.clearAll();
        myHistoryBuffer.clearAll();
        fireModelChangeEvent();
    }

    public void clearScreenBuffer() {
        myScreenBuffer.clearAll();
        fireModelChangeEvent();
    }

    /**
     * @deprecated use {@link #clearScreenAndHistoryBuffers()} instead
     */
    @Deprecated
    public void clearAll() {
        myScreenBuffer.clearAll();
        fireModelChangeEvent();
    }

    /**
     * @param scrollOrigin row where a scrolling window starts, should be in the range [-history_lines_count, 0]
     */
    public void processHistoryAndScreenLines(int scrollOrigin, int maximalLinesToProcess, StyledTextConsumer consumer) {
        // A render asks for a screenful; a search asks for everything (-1).
        boolean render = maximalLinesToProcess >= 0 && maximalLinesToProcess <= Math.max(myHeight, 1) * 2;
        if (maximalLinesToProcess < 0) {
            //Process all lines in this case
            maximalLinesToProcess = myHistoryBuffer.getLineCount() + myScreenBuffer.getLineCount();
        }
        int linesFromHistory = Math.min(-scrollOrigin, maximalLinesToProcess);
        int y = myHistoryBuffer.getLineCount() + scrollOrigin;
        if (y < 0) { // it seems that lower bound of scrolling can get out of sync with history buffer lines count
            y = 0; // to avoid exception we start with the first line in this case
        }
        if (render) {
            // Keep what the terminal shows away from the compression.
            int viewTo = y + Math.max(0, linesFromHistory);
            if (y != myViewHistoryFrom || viewTo != myViewHistoryTo) {
                myViewHistoryFrom = y;
                myViewHistoryTo = viewTo;
                myCompressionPending = true;
            }
        }
        myHistoryBuffer.processLines(y, linesFromHistory, consumer, y);
        if (linesFromHistory < maximalLinesToProcess) {
            // we can show lines from screen buffer
            myScreenBuffer.processLines(0, maximalLinesToProcess - linesFromHistory, consumer, -linesFromHistory);
        }
    }

    public void clearHistory() {
        modify(() -> {
            int lineCount = myHistoryBuffer.getLineCount();
            myHistoryBuffer.clearAll();
            if (lineCount > 0) {
                fireHistoryBufferLineCountChanged();
            }
        });
        fireModelChangeEvent();
    }

    void moveScreenLinesToHistory() {
        myLock.lock();
        try {
            myScreenBuffer.removeBottomEmptyLines(myScreenBuffer.getLineCount() - 1, myScreenBuffer.getLineCount());
            int movedToHistoryLineCount = myScreenBuffer.moveTopLinesTo(myScreenBuffer.getLineCount(), myHistoryBuffer);
            if (myHistoryBuffer.getLineCount() > 0) {
                myHistoryBuffer.getLine(myHistoryBuffer.getLineCount() - 1).setWrapped(false);
            }
            if (movedToHistoryLineCount > 0) {
                fireHistoryBufferLineCountChanged();
            }
        } finally {
            myLock.unlock();
        }
    }

    private void fireHistoryBufferLineCountChanged() {
        for (TerminalHistoryBufferListener historyBufferListener : myHistoryBufferListeners) {
            historyBufferListener.historyBufferLineCountChanged();
        }
    }

    @NotNull
    LinesBuffer getHistoryBufferOrBackup() {
        return myUsingAlternateBuffer ? myHistoryBufferBackup : myHistoryBuffer;
    }

    @NotNull
    LinesBuffer getScreenBufferOrBackup() {
        return myUsingAlternateBuffer ? myScreenBufferBackup : myScreenBuffer;
    }

    public int findScreenLineIndex(@NotNull TerminalLine line) {
        return myScreenBuffer.findLineIndex(line);
    }

    public void clearTypeAheadPredictions() {
        myScreenBuffer.clearTypeAheadPredictions();
        myHistoryBuffer.clearTypeAheadPredictions();
        fireModelChangeEvent();
    }

    /**
     * Passes the image placements that show in a window of rows to the consumer, with the row of the line that
     * holds each one, counted from the top of the window. That row is negative for a placement whose line is
     * above the window but which reaches into it. Call it with the buffer locked.
     *
     * @param scrollOrigin the row where the window starts, in the range [-history_lines_count, 0]
     */
    public void processImagePlacements(int scrollOrigin, int rowCount, @NotNull ObjIntConsumer<ImagePlacement> consumer) {
        int historyLineCount = myHistoryBuffer.getLineCount();
        int first = Math.max(scrollOrigin - Math.max(0, myMaxImageRows - 1), -historyLineCount);
        int last = Math.min(scrollOrigin + rowCount, myScreenBuffer.getLineCount()) - 1;
        for (int y = first; y <= last; y++) {
            TerminalLine line = y < 0 ? myHistoryBuffer.getLine(historyLineCount + y) : myScreenBuffer.getLine(y);
            if (!line.hasImagePlacements()) {
                continue;
            }
            int row = y - scrollOrigin;
            for (ImagePlacement placement : line.getImagePlacements()) {
                if (row + placement.getRows() > 0) {
                    consumer.accept(placement, row);
                }
            }
        }
    }

    void addImagePlacement(int y, @NotNull ImagePlacement placement) {
        getLine(y).addImagePlacement(placement);
        myMaxImageRows = Math.max(myMaxImageRows, placement.getRows());
        fireModelChangeEvent();
    }

    /**
     * Removes the image placements the filter accepts from the screen lines and, unless {@code screenOnly}, also
     * from the history and from the main screen behind the alternate one.
     *
     * @return the removed placements
     */
    @NotNull List<ImagePlacement> removeImagePlacements(boolean screenOnly,
                                                        @NotNull KittyGraphicsProtocol.PlacementFilter filter) {
        List<ImagePlacement> removed = new ArrayList<>();
        myScreenBuffer.forEachLine((line, y) -> removeImagePlacements(line, y, filter, removed));
        if (!screenOnly) {
            int historyLineCount = myHistoryBuffer.getLineCount();
            myHistoryBuffer.forEachLine((line, y) -> removeImagePlacements(line, y - historyLineCount, filter, removed));
            if (myUsingAlternateBuffer) {
                myScreenBufferBackup.forEachLine((line, y) -> removeImagePlacements(line, Integer.MIN_VALUE, filter, removed));
                myHistoryBufferBackup.forEachLine((line, y) -> removeImagePlacements(line, Integer.MIN_VALUE, filter, removed));
            }
        }
        if (!removed.isEmpty()) {
            fireModelChangeEvent();
        }
        return removed;
    }

    private static void removeImagePlacements(@NotNull TerminalLine line, int row,
                                              @NotNull KittyGraphicsProtocol.PlacementFilter filter,
                                              @NotNull List<ImagePlacement> removed) {
        if (line.hasImagePlacements()) {
            removed.addAll(line.removeImagePlacements(placement -> filter.accept(placement, row)));
        }
    }

    /**
     * @return the images that a line of the screen, the history or the main screen behind the alternate one
     * holds a placement of
     */
    @NotNull Set<KittyImage> getPlacedImages() {
        Set<KittyImage> images = new HashSet<>();
        ObjIntConsumer<TerminalLine> collect = (line, y) -> {
            if (line.hasImagePlacements()) {
                for (ImagePlacement placement : line.getImagePlacements()) {
                    images.add(placement.getImage());
                }
            }
        };
        myScreenBuffer.forEachLine(collect);
        myHistoryBuffer.forEachLine(collect);
        if (myUsingAlternateBuffer) {
            myScreenBufferBackup.forEachLine(collect);
            myHistoryBufferBackup.forEachLine(collect);
        }
        return images;
    }

    @Nullable
    TextProcessing getTextProcessing() {
        return myTextProcessing;
    }

    // ---- history memory limit and scrollback compression ----

    /**
     * Bounds the history by memory as well as by line count: once its lines hold more than
     * {@code maxBytes} (an estimate of their heap, see {@link #getHistoryMemoryUsage()}), the oldest
     * lines are dropped, after packing old lines first while compression is on. 0 or less removes
     * the bound. Applies to the alternate screen's history too, and at once to the current lines.
     */
    public void setHistoryMemoryLimit(long maxBytes) {
        modify(() -> {
            myHistoryMaxBytes = maxBytes;
            int before = myHistoryBuffer.getLineCount();
            myHistoryBuffer.setMaxBytes(maxBytes);
            if (myHistoryBufferBackup != null) {
                myHistoryBufferBackup.setMaxBytes(maxBytes);
            }
            if (myHistoryBuffer.getLineCount() != before) {
                fireHistoryBufferLineCountChanged();
            }
        });
        fireModelChangeEvent();
    }

    public long getHistoryMemoryLimit() {
        return myHistoryMaxBytes;
    }

    /**
     * The heap the history lines hold, estimated: line objects, text and style runs, or their share of
     * a compressed block. Lines read since they were compressed still count as compressed until the
     * next compression pass. Includes the main history while the alternate screen is active.
     */
    public long getHistoryMemoryUsage() {
        myLock.lock();
        try {
            long bytes = myHistoryBuffer.getEstimatedBytes();
            if (myUsingAlternateBuffer && myHistoryBufferBackup != null) {
                bytes += myHistoryBufferBackup.getEstimatedBytes();
            }
            return bytes;
        } finally {
            myLock.unlock();
        }
    }

    /** The number of history lines whose content is currently compressed. Walks every line. */
    public int getCompressedHistoryLinesCount() {
        myLock.lock();
        try {
            int count = myHistoryBuffer.getPackedLineCount();
            if (myUsingAlternateBuffer && myHistoryBufferBackup != null) {
                count += myHistoryBufferBackup.getPackedLineCount();
            }
            return count;
        } finally {
            myLock.unlock();
        }
    }

    /**
     * Turns the scrollback compression on or off. While on, history lines that are neither shown nor
     * among the newest screenful are packed into deflated blocks in the background, a slice at a
     * time, once the terminal has had no output for a moment; a history over its memory limit packs
     * old lines before it drops any. A packed line unpacks (with its whole block) when it is drawn or
     * its entries are read, keeping its identity and its styles; reading its text alone does not
     * unpack it. Turning compression off leaves packed lines packed until they are read.
     */
    public void setScrollbackCompressionEnabled(boolean enabled) {
        modify(() -> {
            myCompressionEnabled = enabled;
            applyPackingAllowed();
        });
        if (enabled) {
            myCompressionPending = true;
            ScrollbackCompressor.register(this);
        } else {
            ScrollbackCompressor.unregister(this);
        }
    }

    public boolean isScrollbackCompressionEnabled() {
        return myCompressionEnabled;
    }

    /**
     * Holds the compression back without turning it off, e.g. while find results are shown: they
     * are keyed by the identity of the text buffers the compression would replace.
     */
    public void setScrollbackCompressionPaused(boolean paused) {
        if (myCompressionPaused == paused) {
            return;
        }
        modify(() -> {
            myCompressionPaused = paused;
            applyPackingAllowed();
        });
        myCompressionPending = true;
    }

    public boolean isScrollbackCompressionPaused() {
        return myCompressionPaused;
    }

    /**
     * Compresses every history line the background compression would, now and without waiting
     * for the terminal to go idle.
     *
     * @return the number of lines compressed
     */
    public int compressHistoryNow() {
        myLock.lock();
        try {
            int packed = 0;
            int result;
            do {
                result = compressSlice(Integer.MAX_VALUE);
                packed += result < 0 ? -(result + 1) : result;
            } while (result < 0);
            return packed;
        } finally {
            myLock.unlock();
        }
    }

    /**
     * One slice of the background compression, from {@link ScrollbackCompressor}: does nothing
     * unless the buffer has had no change for {@code idleNanos} and its lock is free, then packs for
     * about {@code sliceNanos}.
     */
    void compressIdleHistory(long nowNanos, long idleNanos, long sliceNanos) {
        if (!isPackingAllowed()
                || (!myCompressionPending && myCompressionPassUnpacks == PackedLineBlock.unpackCount())
                || nowNanos - myLastChangeNanos < idleNanos
                || !myLock.tryLock()) {
            return;
        }
        try {
            long deadline = System.nanoTime() + sliceNanos;
            int result;
            do {
                result = compressSlice(4);
            } while (result < 0 && System.nanoTime() < deadline);
        } finally {
            myLock.unlock();
        }
    }

    /**
     * Packs up to {@code maxBlocks} blocks of the history, and of the main history while the
     * alternate screen is active, under the lock. Keeps the newest screenful and the rows last shown
     * (with a screenful around them) unpacked.
     *
     * @return the lines packed, or {@code -(packed + 1)} if work remains
     */
    private int compressSlice(int maxBlocks) {
        long unpacks = PackedLineBlock.unpackCount();
        int keep = Math.max(myHeight, 1);
        int protectFrom = -1;
        int protectTo = -1;
        if (myViewHistoryTo > myViewHistoryFrom) {
            protectFrom = myViewHistoryFrom - keep;
            protectTo = myViewHistoryTo + keep;
        }
        int packed = myHistoryBuffer.packLines(0, myHistoryBuffer.getLineCount() - keep, protectFrom, protectTo,
                1, maxBlocks, false);
        if (packed < 0) {
            return packed;
        }
        if (myUsingAlternateBuffer && myHistoryBufferBackup != null) {
            int result = myHistoryBufferBackup.packLines(0, myHistoryBufferBackup.getLineCount() - keep, -1, -1,
                    1, maxBlocks, false);
            if (result < 0) {
                return result - packed;
            }
            packed += result;
        }
        myCompressionPending = false;
        myCompressionPassUnpacks = unpacks;
        return packed;
    }

    private boolean isPackingAllowed() {
        return myCompressionEnabled && !myCompressionPaused;
    }

    private void applyPackingAllowed() {
        boolean allowed = isPackingAllowed();
        myHistoryBuffer.setPackingAllowed(allowed);
        if (myHistoryBufferBackup != null) {
            myHistoryBufferBackup.setPackingAllowed(allowed);
        }
    }
}
