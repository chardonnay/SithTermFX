package com.sithtermfx.core.model;

import com.sithtermfx.core.StyledTextConsumer;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.model.TerminalLine.TextEntry;
import com.sithtermfx.core.model.hyperlinks.TextProcessing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ObjIntConsumer;

/**
 * Holds styled characters lines
 * <p>
 * A history buffer can also be bounded by memory ({@link #setMaxBytes}) and have its lines packed by
 * the scrollback compression ({@link #setPackingAllowed}, {@link PackedLineBlock}). Both only apply to
 * buffers a {@link TerminalTextBuffer} configured as history; every other buffer keeps no accounts.
 */
public class LinesBuffer {

    private static final Logger logger = LoggerFactory.getLogger(LinesBuffer.class);

    public static final int DEFAULT_MAX_LINES_COUNT = 5000;

    /** The most lines one {@link PackedLineBlock} holds. */
    static final int PACK_BLOCK_LINES = 128;

    /** Over its memory limit a buffer packs runs of at least this many lines before it drops any. */
    static final int PRESSURE_MIN_RUN = PACK_BLOCK_LINES / 2;

    /** The newest lines a buffer over its memory limit leaves unpacked. */
    static final int PRESSURE_KEEP_LINES = PACK_BLOCK_LINES;

    // negative number means no limit
    private int myBufferMaxLinesCount = DEFAULT_MAX_LINES_COUNT;

    private ArrayList<TerminalLine> myLines = new ArrayList<>();

    /** Whether this buffer counts the bytes its lines hold ({@link TerminalLine#myAccountedBytes}). */
    private boolean myTrackBytes;

    /** The memory limit in bytes, or 0 or less for none. */
    private long myMaxBytes = -1;

    /** Whether this buffer may pack its lines, in a compression pass and over its memory limit. */
    private boolean myPackingAllowed;

    /** The sum of {@link TerminalLine#myAccountedBytes} over all lines, while {@link #myTrackBytes}. */
    private long myBytes;

    /** Every packable line is at or after this index; a compression pass starts here. */
    private int myPackScanStart;

    /** {@link PackedLineBlock#unpackCount()} when {@link #myPackScanStart} was last set. */
    private long myPackScanUnpacks;

    @Nullable
    private final TextProcessing myTextProcessing;

    public LinesBuffer(@Nullable TextProcessing textProcessing) {
        myTextProcessing = textProcessing;
    }

    public LinesBuffer(int bufferMaxLinesCount, @Nullable TextProcessing textProcessing) {
        myBufferMaxLinesCount = bufferMaxLinesCount;
        myTextProcessing = textProcessing;
    }

    public String getLines() {
        final StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (TerminalLine line : myLines) {
            if (!first) {
                sb.append("\n");
            }
            sb.append(line.getText());
            first = false;
        }
        return sb.toString();
    }

    public @NotNull List<String> getLineTexts() {
        return getLineTexts(0, getLineCount());
    }

    @SuppressWarnings("SameParameterValue")
    @NotNull
    List<String> getLineTexts(int from, int to) {
        List<String> lines = new ArrayList<>();
        for (int i = from; i < Math.min(to, getLineCount()); i++) {
            lines.add(myLines.get(i).getText());
        }
        return lines;
    }

    public void addNewLine(@NotNull TextStyle style, @NotNull CharBuffer characters) {
        addNewLine(new TerminalLine.TextEntry(style, characters));
    }

    private void addNewLine(@NotNull TerminalLine.TextEntry entry) {
        addLine(new TerminalLine(entry));
    }

    private void addLine(@NotNull TerminalLine line) {
        if (myBufferMaxLinesCount > 0 && myLines.size() >= myBufferMaxLinesCount) {
            removeTopLines(1);
        }
        myLines.add(line);
        accountAdded(line);
        enforceMemoryLimit();
    }

    public int getLineCount() {
        return myLines.size();
    }

    public void removeTopLines(int count) {
        accountRemoved(0, Math.min(count, myLines.size()));
        if (count >= myLines.size()) { // remove all lines
            myLines = new ArrayList<>();
        } else {
            myLines = new ArrayList<>(myLines.subList(count, myLines.size()));
        }
        myPackScanStart = Math.max(0, myPackScanStart - count);
    }

    public String getLineText(int row) {
        TerminalLine line = getLine(row);
        return line.getText();
    }

    public void insertLines(int y, int count, int lastLine, @NotNull TextEntry filler) {
        LinesBuffer tail = new LinesBuffer(myTextProcessing);
        if (lastLine < getLineCount() - 1) {
            moveBottomLinesTo(getLineCount() - lastLine - 1, tail);
        }
        LinesBuffer head = new LinesBuffer(myTextProcessing);
        if (y > 0) {
            moveTopLinesTo(y, head);
        }
        for (int i = 0; i < count; i++) {
            head.addNewLine(filler);
        }
        head.moveBottomLinesTo(head.getLineCount(), this);
        removeBottomLines(count);
        tail.moveTopLinesTo(tail.getLineCount(), this);
    }

    public LinesBuffer deleteLines(int y, int count, int lastLine, @NotNull TextEntry filler) {
        LinesBuffer tail = new LinesBuffer(myTextProcessing);
        if (lastLine < getLineCount() - 1) {
            moveBottomLinesTo(getLineCount() - lastLine - 1, tail);
        }
        LinesBuffer head = new LinesBuffer(myTextProcessing);
        if (y > 0) {
            moveTopLinesTo(y, head);
        }
        int toRemove = Math.min(count, getLineCount());
        LinesBuffer removed = new LinesBuffer(myTextProcessing);
        moveTopLinesTo(toRemove, removed);
        head.moveBottomLinesTo(head.getLineCount(), this);
        for (int i = 0; i < toRemove; i++) {
            addNewLine(filler);
        }
        tail.moveTopLinesTo(tail.getLineCount(), this);
        return removed;
    }

    public void writeString(int x, int y, CharBuffer str, @NotNull TextStyle style) {
        TerminalLine line = getLine(y);
        line.writeString(x, str, style);
        if (myTextProcessing != null) {
            myTextProcessing.processHyperlinks(this, line);
        }
    }

    public void clearLines(int startRow, int endRow, @NotNull TextEntry filler) {
        for (int i = startRow; i <= endRow; i++) {
            getLine(i).clear(filler);
        }
    }

    // used for reset, style not needed here (reset as well)
    public void clearAll() {
        myLines.clear();
        myBytes = 0;
        myPackScanStart = 0;
    }

    public void deleteCharacters(int x, int y, int count, @NotNull TextStyle style) {
        TerminalLine line = getLine(y);
        line.deleteCharacters(x, count, style);
    }

    public void insertBlankCharacters(final int x, final int y, final int count, final int maxLen, @NotNull TextStyle style) {
        TerminalLine line = getLine(y);
        line.insertBlankCharacters(x, count, maxLen, style);
    }

    public void clearArea(int leftX, int topY, int rightX, int bottomY, @NotNull TextStyle style) {
        for (int y = topY; y < bottomY; y++) {
            TerminalLine line = getLine(y);
            line.clearArea(leftX, rightX, style);
        }
    }

    public void processLines(final int yStart, final int yCount, @NotNull final StyledTextConsumer consumer) {
        processLines(yStart, yCount, consumer, -getLineCount());
    }

    public void processLines(final int firstLine, final int count, @NotNull final StyledTextConsumer consumer,
                             final int startRow) {
        if (firstLine < 0) {
            throw new IllegalArgumentException("firstLine=" + firstLine + ", should be >0");
        }
        for (int y = firstLine; y < Math.min(firstLine + count, myLines.size()); y++) {
            myLines.get(y).process(y, consumer, startRow);
        }
    }

    int moveTopLinesTo(int count, final @NotNull LinesBuffer buffer) {
        if (count < 0) {
            throw new AssertionError("Moving negative line count: " + count);
        }
        count = Math.min(count, getLineCount());
        buffer.addLines(myLines.subList(0, count));
        removeTopLines(count);
        return count;
    }

    public void addLines(@NotNull List<TerminalLine> lines) {
        if (myBufferMaxLinesCount > 0) {
            // adding more lines than max size
            if (lines.size() >= myBufferMaxLinesCount) {
                int index = lines.size() - myBufferMaxLinesCount;
                myLines = new ArrayList<>(lines.subList(index, lines.size()));
                myPackScanStart = 0;
                recountBytes();
                enforceMemoryLimit();
                return;
            }
            int count = myLines.size() + lines.size();
            if (count >= myBufferMaxLinesCount) {
                removeTopLines(count - myBufferMaxLinesCount);
            }
        }
        int start = myLines.size();
        myLines.addAll(lines);
        accountAdded(start, myLines.size());
        enforceMemoryLimit();
    }

    public @NotNull TerminalLine getLine(int row) {
        if (row < 0) {
            logger.error("Negative line number: " + row);
            return TerminalLine.createEmpty();
        }
        for (int i = getLineCount(); i <= row; i++) {
            addLine(TerminalLine.createEmpty());
        }
        return myLines.get(row);
    }

    public void moveBottomLinesTo(int count, @NotNull LinesBuffer buffer) {
        count = Math.min(count, getLineCount());
        buffer.addLinesFirst(myLines.subList(getLineCount() - count, getLineCount()));
        removeBottomLines(count);
    }

    private void addLinesFirst(@NotNull List<TerminalLine> lines) {
        List<TerminalLine> list = new ArrayList<>(lines);
        list.addAll(myLines);
        myLines = new ArrayList<>(list);
        accountAdded(0, lines.size());
        myPackScanStart = 0;
        enforceMemoryLimit();
    }

    private void removeBottomLines(int count) {
        accountRemoved(getLineCount() - count, getLineCount());
        myLines = new ArrayList<>(myLines.subList(0, getLineCount() - count));
        myPackScanStart = Math.min(myPackScanStart, myLines.size());
    }

    public int removeBottomEmptyLines(int bottomMostLineInd, int maxCount) {
        int removedCount = 0;
        int ind = bottomMostLineInd;
        // the rows an image covers are part of it, though they hold no text
        int lastImageRow = lastImageRow(myLines);
        while (removedCount < maxCount && ind >= 0 && ind > lastImageRow
                && (ind >= myLines.size() || myLines.get(ind).isNulOrEmpty())) {
            if (ind < myLines.size()) {
                accountRemoved(ind, ind + 1);
                myLines.remove(ind);
                myPackScanStart = Math.min(myPackScanStart, ind);
            }
            ind--;
            removedCount++;
        }
        return removedCount;
    }

    int findLineIndex(@NotNull TerminalLine line) {
        return myLines.indexOf(line);
    }

    /**
     * Passes every line to the action with its index, without adding lines as {@link #getLine(int)} does.
     */
    void forEachLine(@NotNull ObjIntConsumer<TerminalLine> action) {
        for (int i = 0; i < myLines.size(); i++) {
            action.accept(myLines.get(i), i);
        }
    }

    /**
     * @return the index of the last of the lines that an image anchored at one of them covers, or -1
     */
    static int lastImageRow(@NotNull List<TerminalLine> lines) {
        int last = -1;
        for (int i = 0; i < lines.size(); i++) {
            TerminalLine line = lines.get(i);
            if (line.hasImagePlacements()) {
                last = Math.max(last, i + line.getImageRows() - 1);
            }
        }
        return last;
    }

    public void clearTypeAheadPredictions() {
        for (TerminalLine line : myLines) {
            line.setTypeAheadLine(null);
        }
    }

    // ---- memory limit and scrollback compression (history buffers only) ----

    /**
     * Bounds this buffer by memory: once its lines hold more than {@code maxBytes} (estimated, see
     * {@link #getEstimatedBytes()}), it first packs old lines if packing is allowed and then drops
     * the oldest lines until it fits. 0 or less removes the bound; the line count limit applies
     * either way. Turns on byte accounting for this buffer.
     */
    public void setMaxBytes(long maxBytes) {
        myMaxBytes = maxBytes;
        if (!myTrackBytes) {
            myTrackBytes = true;
            recountBytes();
        }
        enforceMemoryLimit();
    }

    public long getMaxBytes() {
        return myMaxBytes;
    }

    /**
     * Whether this buffer may pack lines, in {@link #packLines} and when it is over its memory limit.
     * Turning it off unpacks nothing: packed lines unpack as they are read.
     */
    public void setPackingAllowed(boolean allowed) {
        myPackingAllowed = allowed;
        if (allowed && !myTrackBytes) {
            myTrackBytes = true;
            recountBytes();
        }
        if (allowed) {
            enforceMemoryLimit();
        }
    }

    public boolean isPackingAllowed() {
        return myPackingAllowed;
    }

    /**
     * The heap the lines of this buffer hold, estimated: line objects, entries and text arrays, or
     * their share of a packed block. Lines read since they were packed are still counted as packed
     * until a compression pass packs them again. 0 for a buffer that keeps no accounts.
     */
    public long getEstimatedBytes() {
        return myTrackBytes ? myBytes : 0;
    }

    /** The number of lines whose content is currently packed. Walks every line. */
    public int getPackedLineCount() {
        int count = 0;
        for (TerminalLine line : myLines) {
            if (line.isPacked()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Packs runs of packable lines in {@code [from, to)} outside {@code [protectFrom, protectTo)},
     * at most {@code maxBlocks} blocks of up to {@link #PACK_BLOCK_LINES} lines; a run shorter than
     * {@code minRun} stays unpacked. With {@code untilWithinLimit} the pass stops as soon as the
     * buffer fits its memory limit. Called under the text buffer lock.
     *
     * @return the number of lines packed, or {@code -(packed + 1)} if the pass stopped before {@code to}
     */
    int packLines(int from, int to, int protectFrom, int protectTo, int minRun, int maxBlocks,
                  boolean untilWithinLimit) {
        if (!myPackingAllowed) {
            return 0;
        }
        long unpacks = PackedLineBlock.unpackCount();
        if (unpacks != myPackScanUnpacks) {
            // Lines packed before were read and unpacked again, possibly ahead of the scan start.
            myPackScanStart = 0;
            myPackScanUnpacks = unpacks;
        }
        to = Math.min(to, myLines.size());
        boolean fromScanStart = from <= myPackScanStart;
        int packed = 0;
        int blocks = 0;
        int firstLeftover = -1;
        int runStart = -1;
        List<TerminalLine> run = new ArrayList<>(PACK_BLOCK_LINES);
        for (int i = Math.max(from, myPackScanStart); i < to; i++) {
            TerminalLine line = myLines.get(i);
            boolean packable = line.isPackable();
            boolean eligible = packable && (i < protectFrom || i >= protectTo);
            if (eligible) {
                if (run.isEmpty()) {
                    runStart = i;
                }
                run.add(line);
            } else if (packable && firstLeftover < 0) {
                firstLeftover = i;
            }
            if (!run.isEmpty() && (!eligible || run.size() == PACK_BLOCK_LINES || i == to - 1)) {
                if (run.size() >= minRun) {
                    packRun(run);
                    packed += run.size();
                    blocks++;
                } else if (firstLeftover < 0 || runStart < firstLeftover) {
                    firstLeftover = runStart;
                }
                run.clear();
                if (i + 1 < to && (blocks >= maxBlocks || (untilWithinLimit && myBytes <= myMaxBytes))) {
                    if (fromScanStart) {
                        myPackScanStart = firstLeftover >= 0 ? firstLeftover : i + 1;
                    }
                    return -(packed + 1);
                }
            }
        }
        if (fromScanStart) {
            myPackScanStart = firstLeftover >= 0 ? firstLeftover : Math.max(myPackScanStart, to);
        }
        return packed;
    }

    private void packRun(@NotNull List<TerminalLine> run) {
        long before = 0;
        for (TerminalLine line : run) {
            before += line.myAccountedBytes;
        }
        PackedLineBlock.pack(run);
        if (myTrackBytes) {
            long after = 0;
            for (TerminalLine line : run) {
                line.myAccountedBytes = line.estimateBytes();
                after += line.myAccountedBytes;
            }
            myBytes += after - before;
        }
    }

    private void enforceMemoryLimit() {
        if (!myTrackBytes || myMaxBytes <= 0 || myBytes <= myMaxBytes) {
            return;
        }
        if (myPackingAllowed) {
            packLines(0, myLines.size() - PRESSURE_KEEP_LINES, -1, -1, PRESSURE_MIN_RUN, Integer.MAX_VALUE, true);
            if (myBytes <= myMaxBytes) {
                return;
            }
        }
        long excess = myBytes - myMaxBytes;
        int count = 0;
        long freed = 0;
        while (count < myLines.size() && freed < excess) {
            freed += myLines.get(count).myAccountedBytes;
            count++;
        }
        removeTopLines(count);
    }

    private void accountAdded(@NotNull TerminalLine line) {
        if (myTrackBytes) {
            line.myAccountedBytes = line.estimateBytes();
            myBytes += line.myAccountedBytes;
        }
    }

    private void accountAdded(int from, int to) {
        if (myTrackBytes) {
            for (int i = from; i < to; i++) {
                accountAdded(myLines.get(i));
            }
        }
    }

    private void accountRemoved(int from, int to) {
        if (myTrackBytes) {
            for (int i = from; i < to; i++) {
                myBytes -= myLines.get(i).myAccountedBytes;
            }
        }
    }

    private void recountBytes() {
        myBytes = 0;
        accountAdded(0, myLines.size());
    }
}
