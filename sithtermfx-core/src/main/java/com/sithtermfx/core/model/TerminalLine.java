package com.sithtermfx.core.model;

import com.sithtermfx.core.StyledTextConsumer;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.graphics.ImagePlacement;
import com.sithtermfx.core.graphics.KittyGraphicsProtocol;
import com.sithtermfx.core.util.CharUtils;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Daniel Mengel
 */
public final class TerminalLine {

    private static final Logger logger = LoggerFactory.getLogger(TerminalLine.class);

    /** Shallow size of a line object: header, five references, two ints and the wrapped flag. */
    static final int SHALLOW_BYTES = 48;

    /** An {@link ImagePlacement} object; the image it shows has a quota of its own and is not counted. */
    private static final int IMAGE_PLACEMENT_BYTES = 96;

    /** The entries list of an unpacked line: {@code TextEntries}, its {@code ArrayList} and array header. */
    private static final int ENTRIES_OVERHEAD_BYTES = 24 + 24 + 16;

    /**
     * The line's text and styles, or {@code null} while they are packed into {@link #myPackedBlock}
     * by the scrollback compression. Read through {@link #entries()}, which unpacks on demand.
     */
    private volatile TextEntries myTextEntries = new TextEntries();

    /** The block holding this line's packed content, or {@code null} while it is unpacked. */
    private volatile PackedLineBlock myPackedBlock;

    /** This line's index in {@link #myPackedBlock}. */
    private int myPackedIndex;

    /**
     * The bytes a memory-limited {@link LinesBuffer} counts for this line; only that buffer reads and
     * writes it, under the text buffer lock.
     */
    int myAccountedBytes;

    private boolean myWrapped = false;

    /** Created on the first highlighting: most lines never get one, and an empty list costs ~50 bytes. */
    private volatile List<TerminalLineIntervalHighlighting> myCustomHighlightings;

    private TerminalLine myTypeAheadLine;

    /** The images anchored at this line, null while there are none (the usual case). */
    private @Nullable List<ImagePlacement> myImagePlacements;

    public TerminalLine() {
    }

    public TerminalLine(@NotNull TextEntry entry) {
        myTextEntries.add(entry);
    }

    public static TerminalLine createEmpty() {
        return new TerminalLine();
    }

    public @NotNull String getText() {
        TextEntries entries = myTextEntries;
        if (entries == null) {
            // Packed: decode the text alone and leave the line packed, so that reading the scrollback
            // as text (copy, export, embedders) does not undo the compression.
            PackedLineBlock block = myPackedBlock;
            String text = block != null ? block.lineText(this, myPackedIndex) : null;
            if (text != null) {
                return text;
            }
            entries = entries();
        }
        StringBuilder result = new StringBuilder(entries.myLength);
        for (TerminalLine.TextEntry textEntry : entries) {
            // NUL can only be at the end
            if (textEntry.getText().isNul()) {
                break;
            }
            result.append(textEntry.getText());
        }
        return result.toString();
    }

    public @NotNull TerminalLine copy() {
        TerminalLine result = new TerminalLine();
        for (TextEntry entry : entries()) {
            result.myTextEntries.add(entry);
        }
        result.myWrapped = myWrapped;
        return result;
    }

    public char charAt(int x) {
        TerminalLine typeAheadLine = myTypeAheadLine;
        if (typeAheadLine != null) {
            return typeAheadLine.charAt(x);
        }
        String text = getText();
        return x < text.length() ? text.charAt(x) : CharUtils.EMPTY_CHAR;
    }

    public boolean isWrapped() {
        return myWrapped;
    }

    public void setWrapped(boolean wrapped) {
        myWrapped = wrapped;
    }

    public void clear(@NotNull TextEntry filler) {
        TextEntries entries = entries();
        entries.clear();
        entries.add(filler);
        setWrapped(false);
    }

    public void writeString(int x, @NotNull CharBuffer str, @NotNull TextStyle style) {
        writeCharacters(x, style, str);
    }

    public void insertString(int x, @NotNull CharBuffer str, @NotNull TextStyle style) {
        insertCharacters(x, style, str);
    }

    public TerminalLine getTypeAheadLine() {
        return myTypeAheadLine;
    }

    public void setTypeAheadLine(TerminalLine typeAheadLine) {
        this.myTypeAheadLine = typeAheadLine;
    }

    private void writeCharacters(int x, @NotNull TextStyle style, @NotNull CharBuffer characters) {
        TextEntries entries = entries();
        int len = entries.length();
        if (x >= len) {
            // fill the gap
            if (x - len > 0) {
                entries.add(new TextEntry(TextStyle.EMPTY, new CharBuffer(CharUtils.NUL_CHAR, x - len)));
            }
            entries.add(new TextEntry(style, characters));
        } else {
            len = Math.max(len, x + characters.length());
            myTextEntries = merge(x, characters, style, entries, len);
        }
    }

    private void insertCharacters(int x, @NotNull TextStyle style, @NotNull CharBuffer characters) {
        TextEntries entries = entries();
        int length = entries.length();
        if (x > length) {
            writeCharacters(x, style, characters);
            return;
        }
        Pair<char[], TextStyle[]> pair = toBuf(entries, length + characters.length());
        for (int i = length - 1; i >= x; i--) {
            pair.getFirst()[i + characters.length()] = pair.getFirst()[i];
            pair.getSecond()[i + characters.length()] = pair.getSecond()[i];
        }
        for (int i = 0; i < characters.length(); i++) {
            pair.getFirst()[i + x] = characters.charAt(i);
            pair.getSecond()[i + x] = style;
        }
        myTextEntries = collectFromBuffer(pair.getFirst(), pair.getSecond());
    }

    private static TextEntries merge(int x, @NotNull CharBuffer str, @NotNull TextStyle style,
                                     @NotNull TextEntries entries, int lineLength) {
        Pair<char[], TextStyle[]> pair = toBuf(entries, lineLength);
        for (int i = 0; i < str.length(); i++) {
            pair.getFirst()[i + x] = str.charAt(i);
            pair.getSecond()[i + x] = style;
        }
        return collectFromBuffer(pair.getFirst(), pair.getSecond());
    }

    private static Pair<char[], TextStyle[]> toBuf(TextEntries entries, int lineLength) {
        Pair<char[], TextStyle[]> pair = new Pair<>(new char[lineLength], new TextStyle[lineLength]);
        int p = 0;
        for (TextEntry entry : entries) {
            for (int i = 0; i < entry.getLength(); i++) {
                pair.getFirst()[p + i] = entry.getText().charAt(i);
                pair.getSecond()[p + i] = entry.getStyle();
            }
            p += entry.getLength();
        }
        return pair;
    }

    private static TextEntries collectFromBuffer(char[] buf, @NotNull TextStyle[] styles) {
        TextEntries result = new TextEntries();
        TextStyle curStyle = styles[0];
        int start = 0;
        for (int i = 1; i < buf.length; i++) {
            if (styles[i] != curStyle) {
                result.add(new TextEntry(curStyle, new CharBuffer(buf, start, i - start)));
                curStyle = styles[i];
                start = i;
            }
        }
        result.add(new TextEntry(curStyle, new CharBuffer(buf, start, buf.length - start)));
        return result;
    }

    public void deleteCharacters(int x) {
        deleteCharacters(x, TextStyle.EMPTY);
    }

    public void deleteCharacters(int x, @NotNull TextStyle style) {
        deleteCharacters(x, entries().length() - x, style);
        // delete to the end of line : line is no more wrapped
        setWrapped(false);
    }

    public void deleteCharacters(int x, int count, @NotNull TextStyle style) {
        int p = 0;
        TextEntries newEntries = new TextEntries();
        int remaining = count;
        for (TextEntry entry : entries()) {
            if (remaining == 0) {
                newEntries.add(entry);
                continue;
            }
            int len = entry.getLength();
            if (p + len <= x) {
                p += len;
                newEntries.add(entry);
                continue;
            }
            int dx = x - p; //>=0
            if (dx > 0) {
                //part of entry before x
                newEntries.add(new TextEntry(entry.getStyle(), entry.getText().subBuffer(0, dx)));
                p = x;
            }
            if (dx + remaining < len) {
                //part that left after deleting count
                newEntries.add(new TextEntry(entry.getStyle(), entry.getText().subBuffer(dx + remaining, len - (dx + remaining))));
                remaining = 0;
            } else {
                remaining -= (len - dx);
                p = x;
            }
        }
        if (count > 0 && style != TextStyle.EMPTY) { // apply style to the end of the line
            newEntries.add(new TextEntry(style, new CharBuffer(CharUtils.NUL_CHAR, count)));
        }
        myTextEntries = newEntries;
    }

    public void insertBlankCharacters(int x, int count, int maxLen, @NotNull TextStyle style) {
        TextEntries entries = entries();
        int len = entries.length();
        len = Math.min(len + count, maxLen);
        char[] buf = new char[len];
        TextStyle[] styles = new TextStyle[len];
        int p = 0;
        for (TextEntry entry : entries) {
            for (int i = 0; i < entry.getLength() && p < len; i++) {
                if (p == x) {
                    for (int j = 0; j < count && p < len; j++) {
                        buf[p] = CharUtils.EMPTY_CHAR;
                        styles[p] = style;
                        p++;
                    }
                }
                if (p < len) {
                    buf[p] = entry.getText().charAt(i);
                    styles[p] = entry.getStyle();
                    p++;
                }
            }
            if (p >= len) {
                break;
            }
        }
        // if not inserted yet (ie. x > len)
        for (; p < x && p < len; p++) {
            buf[p] = CharUtils.EMPTY_CHAR;
            styles[p] = TextStyle.EMPTY;
            p++;
        }
        for (; p < x + count && p < len; p++) {
            buf[p] = CharUtils.EMPTY_CHAR;
            styles[p] = style;
            p++;
        }
        myTextEntries = collectFromBuffer(buf, styles);
    }

    public void clearArea(int leftX, int rightX, @NotNull TextStyle style) {
        int length = entries().length();
        if (rightX == -1) {
            rightX = Math.max(length, leftX);
        }
        writeCharacters(leftX, style, new CharBuffer(
                rightX >= length ? CharUtils.NUL_CHAR : CharUtils.EMPTY_CHAR,
                rightX - leftX));
    }

    public @Nullable TextStyle getStyleAt(int x) {
        int i = 0;
        for (TextEntry te : entries()) {
            if (x >= i && x < i + te.getLength()) {
                return te.getStyle();
            }
            i += te.getLength();
        }
        return null;
    }

    public void process(int y, StyledTextConsumer consumer, int startRow) {
        int x = 0;
        int nulIndex = -1;
        List<TerminalLineIntervalHighlighting> highlightings = myCustomHighlightings;
        TerminalLineIntervalHighlighting highlighting = highlightings != null
                ? highlightings.stream().findFirst().orElse(null) : null;
        TerminalLine typeAheadLine = myTypeAheadLine;
        TextEntries textEntries = typeAheadLine != null ? typeAheadLine.entries() : entries();
        for (TextEntry te : textEntries) {
            if (te.getText().isNul()) {
                if (nulIndex < 0) {
                    nulIndex = x;
                }
                consumer.consumeNul(x, y, nulIndex, te.getStyle(), te.getText(), startRow);
            } else {
                if (highlighting != null && te.getLength() > 0 && highlighting.intersectsWith(x, x + te.getLength())) {
                    processIntersection(x, y, te, consumer, startRow, highlighting);
                } else {
                    consumer.consume(x, y, te.getStyle(), te.getText(), startRow);
                }
            }
            x += te.getLength();
        }
        consumer.consumeQueue(x, y, nulIndex < 0 ? x : nulIndex, startRow);
    }

    private void processIntersection(int startTextOffset, int y, @NotNull TextEntry te,
                                     @NotNull StyledTextConsumer consumer, int startRow,
                                     @NotNull TerminalLineIntervalHighlighting highlighting) {
        CharBuffer text = te.getText();
        int endTextOffset = startTextOffset + text.length();
        int[] offsets = new int[]{startTextOffset, endTextOffset, highlighting.getStartOffset(), highlighting.getEndOffset()};
        Arrays.sort(offsets);
        int startTextOffsetInd = Arrays.binarySearch(offsets, startTextOffset);
        int endTextOffsetInd = Arrays.binarySearch(offsets, endTextOffset);
        if (startTextOffsetInd < 0 || endTextOffsetInd < 0) {
            logger.error("Cannot find " + Arrays.toString(new int[]{startTextOffset, endTextOffset})
                    + " in " + Arrays.toString(offsets) + ": " + Arrays.toString(new int[]{startTextOffsetInd, endTextOffsetInd}));
            consumer.consume(startTextOffset, y, te.getStyle(), text, startRow);
            return;
        }
        for (int i = startTextOffsetInd; i < endTextOffsetInd; i++) {
            int length = offsets[i + 1] - offsets[i];
            if (length == 0) continue;
            CharBuffer subText = new SubCharBuffer(text, offsets[i] - startTextOffset, length);
            if (highlighting.intersectsWith(offsets[i], offsets[i + 1])) {
                consumer.consume(offsets[i], y, highlighting.mergeWith(te.getStyle()), subText, startRow);
            } else {
                consumer.consume(offsets[i], y, te.getStyle(), subText, startRow);
            }
        }
    }

    public boolean isNul() {
        for (TextEntry e : entries()) {
            if (!e.isNul()) {
                return false;
            }
        }
        return true;
    }

    public boolean isEmpty() {
        for (TextEntry e : entries()) {
            if (!e.isNul() && e.getLength() > 0) {
                return false;
            }
        }
        return true;
    }

    public boolean isNulOrEmpty() {
        return isNul() || isEmpty();
    }

    public void forEachEntry(@NotNull Consumer<TextEntry> action) {
        entries().forEach(action);
    }

    public @NotNull List<TextEntry> getEntries() {
        return Collections.unmodifiableList(entries().entries());
    }

    void appendEntry(@NotNull TextEntry entry) {
        entries().add(entry);
    }

    /**
     * The line's entries, unpacked first if the scrollback compression packed them. Unpacking
     * restores the whole block the line was packed with; the line object, its styles and its wrapped
     * flag never change, only fresh {@link CharBuffer}s replace the packed text.
     */
    private @NotNull TextEntries entries() {
        TextEntries entries = myTextEntries;
        while (entries == null) {
            PackedLineBlock block = myPackedBlock;
            if (block != null) {
                block.unpack();
            }
            entries = myTextEntries;
        }
        return entries;
    }

    /** Whether the scrollback compression currently holds this line's content packed. */
    boolean isPacked() {
        return myTextEntries == null;
    }

    /**
     * Whether the scrollback compression may pack this line: it is unpacked and nothing is attached
     * to its live entries, i.e. no type-ahead prediction and no custom highlighting.
     */
    boolean isPackable() {
        List<TerminalLineIntervalHighlighting> highlightings = myCustomHighlightings;
        return myTextEntries != null && myTypeAheadLine == null
                && (highlightings == null || highlightings.isEmpty());
    }

    /** The entries a {@link PackedLineBlock} encodes; only called for an unpacked line, under the buffer lock. */
    @NotNull List<TextEntry> entriesToPack() {
        return entries().entries();
    }

    /** Hands the content over to {@code block}; the block was built from {@link #entriesToPack()}. */
    void pack(@NotNull PackedLineBlock block, int index) {
        myPackedIndex = index;
        myPackedBlock = block;
        myTextEntries = null;
    }

    /** Restores content decoded by {@code block}; called by the block, holding its monitor. */
    void unpacked(@NotNull List<TextEntry> entries) {
        TextEntries restored = new TextEntries();
        for (TextEntry entry : entries) {
            restored.append(entry);
        }
        myTextEntries = restored;
        myPackedBlock = null;
    }

    /**
     * An estimate of the heap this line holds: its own object plus either its entries (text arrays
     * included) or its share of the packed block. Styles are shared and not counted.
     */
    int estimateBytes() {
        TextEntries entries = myTextEntries;
        if (entries == null) {
            PackedLineBlock block = myPackedBlock;
            if (block != null) {
                return SHALLOW_BYTES + block.bytesPerLine();
            }
            entries = entries();
        }
        List<TextEntry> list = entries.entries();
        long bytes = SHALLOW_BYTES + ENTRIES_OVERHEAD_BYTES + 4L * Math.max(10, list.size());
        for (int i = 0, n = list.size(); i < n; i++) {
            bytes += TextEntry.estimateBytes(list.get(i).getLength());
        }
        List<TerminalLineIntervalHighlighting> highlightings = myCustomHighlightings;
        if (highlightings != null) {
            bytes += 48;
        }
        List<ImagePlacement> placements = myImagePlacements;
        if (placements != null) {
            bytes += 24 + 16 + 4L * Math.max(1, placements.size()) + (long) IMAGE_PLACEMENT_BYTES * placements.size();
        }
        return (int) Math.min(Integer.MAX_VALUE, bytes);
    }

    /**
     * @return the images anchored at this line (kitty graphics protocol), in the order they were placed. Erasing
     * or overwriting the text does not remove them; they go with the line.
     */
    public @NotNull List<ImagePlacement> getImagePlacements() {
        List<ImagePlacement> placements = myImagePlacements;
        return placements != null ? Collections.unmodifiableList(placements) : Collections.emptyList();
    }

    public boolean hasImagePlacements() {
        return myImagePlacements != null;
    }

    /**
     * @return how many rows the images anchored at this line cover, starting with this line; 0 for none
     */
    int getImageRows() {
        int rows = 0;
        if (myImagePlacements != null) {
            for (ImagePlacement placement : myImagePlacements) {
                rows = Math.max(rows, placement.getRows());
            }
        }
        return rows;
    }

    void addImagePlacement(@NotNull ImagePlacement placement) {
        if (myImagePlacements == null) {
            myImagePlacements = new ArrayList<>(1);
        } else if (myImagePlacements.size() >= KittyGraphicsProtocol.MAX_PLACEMENTS_PER_LINE) {
            // a program placing without moving the cursor (C=1) would otherwise pile up placements without end
            myImagePlacements.remove(0);
        }
        myImagePlacements.add(placement);
    }

    /**
     * @return the removed placements, those the filter accepts
     */
    @NotNull List<ImagePlacement> removeImagePlacements(@NotNull Predicate<ImagePlacement> filter) {
        List<ImagePlacement> placements = myImagePlacements;
        if (placements == null) {
            return Collections.emptyList();
        }
        List<ImagePlacement> removed = new ArrayList<>();
        placements.removeIf(placement -> filter.test(placement) && removed.add(placement));
        if (placements.isEmpty()) {
            myImagePlacements = null;
        }
        return removed;
    }

    @SuppressWarnings("unused") // used by IntelliJ
    public @NotNull TerminalLineIntervalHighlighting addCustomHighlighting(int startOffset, int length,
                                                                           @NotNull TextStyle textStyle) {
        TerminalLineIntervalHighlighting highlighting =
                new TerminalLineIntervalHighlighting(this, startOffset, length, textStyle) {

                    @Override
                    protected void doDispose() {
                        List<TerminalLineIntervalHighlighting> highlightings = myCustomHighlightings;
                        if (highlightings != null) {
                            highlightings.remove(this);
                        }
                    }
                };
        customHighlightings().add(highlighting);
        return highlighting;
    }

    private @NotNull List<TerminalLineIntervalHighlighting> customHighlightings() {
        List<TerminalLineIntervalHighlighting> highlightings = myCustomHighlightings;
        if (highlightings == null) {
            synchronized (this) {
                highlightings = myCustomHighlightings;
                if (highlightings == null) {
                    highlightings = new CopyOnWriteArrayList<>();
                    myCustomHighlightings = highlightings;
                }
            }
        }
        return highlightings;
    }

    @Override
    public String toString() {
        TextEntries entries = entries();
        return entries.length() + " chars, " +
                (myWrapped ? "wrapped, " : "") +
                entries.myTextEntries.size() + " entries: " +
                entries.myTextEntries.stream()
                        .map(entry -> entry.getText().toString())
                        .collect(Collectors.joining("|"));
    }

    public static class TextEntry {

        private final TextStyle myStyle;

        private final CharBuffer myText;

        public TextEntry(@NotNull TextStyle style, @NotNull CharBuffer text) {
            myStyle = style;
            myText = text.clone();
        }

        /** Takes {@code text} as it is; for buffers nobody else holds, such as freshly decoded ones. */
        TextEntry(@NotNull TextStyle style, @NotNull CharBuffer text, boolean owned) {
            myStyle = style;
            myText = owned ? text : text.clone();
        }

        /** Entry object, its {@link CharBuffer}, the char array with header and padding, and the list slot. */
        static long estimateBytes(int length) {
            return 24 + 24 + ((16 + 2L * length + 7) & ~7L);
        }

        public TextStyle getStyle() {
            return myStyle;
        }

        public CharBuffer getText() {
            return myText;
        }

        public int getLength() {
            return myText.length();
        }

        public boolean isNul() {
            return myText.isNul();
        }

        @Override
        public String toString() {
            return myText.length() + " chars, style: " + myStyle + ", text: " + myText;
        }
    }

    private static class TextEntries implements Iterable<TextEntry> {

        private final List<TextEntry> myTextEntries = new ArrayList<>();

        private int myLength = 0;

        public void add(TextEntry entry) {
            // NUL can only be at the end of the line
            if (!entry.getText().isNul()) {
                for (TextEntry t : myTextEntries) {
                    if (t.getText().isNul()) {
                        t.getText().unNullify();
                    }
                }
            }
            myTextEntries.add(entry);
            myLength += entry.getLength();
        }

        /** Appends without the NUL fix-up of {@link #add}: restores entries exactly as they were packed. */
        void append(TextEntry entry) {
            myTextEntries.add(entry);
            myLength += entry.getLength();
        }

        private List<TextEntry> entries() {
            return myTextEntries;
        }

        @NotNull
        public Iterator<TextEntry> iterator() {
            return myTextEntries.iterator();
        }

        public int length() {
            return myLength;
        }

        public void clear() {
            myTextEntries.clear();
            myLength = 0;
        }
    }
}
