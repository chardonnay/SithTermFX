package com.sithtermfx.core.model;

import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.util.CharUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * The content of a run of scrollback lines, packed by the scrollback compression into one deflated
 * byte array.
 * <p>
 * The lines stay in their {@link LinesBuffer} as the same objects; only their entries move here. A
 * block keeps its styles in a table of its own: a plain {@link TextStyle} (an immutable value) comes
 * back as one shared instance per distinct value, every subclass ({@link com.sithtermfx.core.HyperlinkStyle},
 * embedder styles) as the very same object. The first access to the entries of any of its
 * lines unpacks the whole block, so scrolling through history decodes once per block, not per line.
 * Reading a line's text alone ({@link TerminalLine#getText()}) decodes without unpacking.
 * <p>
 * Encoding, before deflating, per line: the entry count, then per entry the style index, the length
 * and the chars, all as unsigned varints (an ASCII char takes one byte).
 */
final class PackedLineBlock {

    /** Shallow size of a block plus the headers of its three arrays. */
    private static final int OVERHEAD_BYTES = 32 + 16 + 16 + 16;

    /** Counts unpacked blocks, so a compression pass knows when lines it packed came back. */
    private static final AtomicLong UNPACKS = new AtomicLong();

    /** One shared deflater: packing is short and rarely concurrent, and each instance holds ~270 KB native memory. */
    private static final Deflater DEFLATER = new Deflater(Deflater.DEFAULT_COMPRESSION, true);

    /** Decoded payloads of the blocks whose text was read last, for {@link #lineText}. */
    private static final int TEXT_CACHE_BLOCKS = 4;

    private static final Map<PackedLineBlock, int[]> TEXT_CACHE_OFFSETS =
            new LinkedHashMap<>(TEXT_CACHE_BLOCKS * 2, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<PackedLineBlock, int[]> eldest) {
                    if (size() > TEXT_CACHE_BLOCKS) {
                        TEXT_CACHE_RAW.remove(eldest.getKey());
                        return true;
                    }
                    return false;
                }
            };

    private static final Map<PackedLineBlock, byte[]> TEXT_CACHE_RAW = new IdentityHashMap<>();

    /** The packed lines in order, or {@code null} once unpacked. */
    private TerminalLine[] myLines;

    private byte[] myData;

    private TextStyle[] myStyles;

    private final int myRawLength;

    private final boolean myDeflated;

    private final int myBytesPerLine;

    private PackedLineBlock(@NotNull TerminalLine[] lines, @NotNull byte[] data, int rawLength, boolean deflated,
                            @NotNull TextStyle[] styles) {
        myLines = lines;
        myData = data;
        myRawLength = rawLength;
        myDeflated = deflated;
        myStyles = styles;
        long bytes = OVERHEAD_BYTES + data.length + 4L * styles.length + 4L * lines.length;
        myBytesPerLine = (int) ((bytes + lines.length - 1) / lines.length);
    }

    /**
     * Packs {@code lines}, all of them {@linkplain TerminalLine#isPackable() packable}, into one new
     * block. Called under the text buffer lock.
     */
    static @NotNull PackedLineBlock pack(@NotNull List<TerminalLine> lines) {
        Map<TextStyle, Integer> styleIndex = new IdentityHashMap<>();
        Map<TextStyle, Integer> plainStyleIndex = new HashMap<>();
        List<TextStyle> styles = new ArrayList<>();
        ByteSink raw = new ByteSink(64 * lines.size());
        for (TerminalLine line : lines) {
            List<TerminalLine.TextEntry> entries = line.entriesToPack();
            raw.writeVarInt(entries.size());
            for (TerminalLine.TextEntry entry : entries) {
                TextStyle style = entry.getStyle();
                Integer index = styleIndex.get(style);
                if (index == null) {
                    // Equal plain styles share one instance: each scrolled line used to bring its own.
                    boolean plain = style.getClass() == TextStyle.class;
                    index = plain ? plainStyleIndex.get(style) : null;
                    if (index == null) {
                        index = styles.size();
                        styles.add(style);
                        if (plain) {
                            plainStyleIndex.put(style, index);
                        }
                    }
                    styleIndex.put(style, index);
                }
                raw.writeVarInt(index);
                CharBuffer text = entry.getText();
                int length = text.length();
                raw.writeVarInt(length);
                for (int i = 0; i < length; i++) {
                    raw.writeVarInt(text.charAt(i));
                }
            }
        }
        byte[] packed = deflate(raw.myBytes, raw.mySize);
        boolean deflated = packed != null;
        byte[] data = deflated ? packed : Arrays.copyOf(raw.myBytes, raw.mySize);
        TerminalLine[] packedLines = lines.toArray(new TerminalLine[0]);
        PackedLineBlock block = new PackedLineBlock(packedLines, data, raw.mySize, deflated,
                styles.toArray(new TextStyle[0]));
        for (int i = 0; i < packedLines.length; i++) {
            packedLines[i].pack(block, i);
        }
        return block;
    }

    /** The heap this block costs per line, the line objects themselves excluded. */
    int bytesPerLine() {
        return myBytesPerLine;
    }

    /** The number of blocks unpacked so far, in this JVM. */
    static long unpackCount() {
        return UNPACKS.get();
    }

    /** Gives every line of the block its entries back; a no-op once done. */
    synchronized void unpack() {
        TerminalLine[] lines = myLines;
        if (lines == null) {
            return;
        }
        byte[] raw = rawBytes();
        int[] pos = {0};
        for (TerminalLine line : lines) {
            line.unpacked(decodeLine(raw, pos));
        }
        myLines = null;
        myData = null;
        myStyles = null;
        UNPACKS.incrementAndGet();
        synchronized (TEXT_CACHE_OFFSETS) {
            TEXT_CACHE_OFFSETS.remove(this);
            TEXT_CACHE_RAW.remove(this);
        }
    }

    /**
     * The text of {@code line}, decoded without unpacking it, or {@code null} when the block was
     * unpacked meanwhile (the caller then reads the line's own entries).
     */
    @Nullable String lineText(@NotNull TerminalLine line, int index) {
        byte[] raw;
        int[] offsets;
        synchronized (this) {
            TerminalLine[] lines = myLines;
            if (lines == null || index < 0 || index >= lines.length || lines[index] != line) {
                return null;
            }
            synchronized (TEXT_CACHE_OFFSETS) {
                offsets = TEXT_CACHE_OFFSETS.get(this);
                raw = TEXT_CACHE_RAW.get(this);
            }
            if (offsets == null || raw == null) {
                raw = rawBytes();
                offsets = lineOffsets(raw, lines.length);
                synchronized (TEXT_CACHE_OFFSETS) {
                    TEXT_CACHE_RAW.put(this, raw);
                    TEXT_CACHE_OFFSETS.put(this, offsets);
                }
            }
        }
        int[] pos = {offsets[index]};
        int entryCount = readVarInt(raw, pos);
        StringBuilder text = new StringBuilder();
        for (int e = 0; e < entryCount; e++) {
            readVarInt(raw, pos); // style
            int length = readVarInt(raw, pos);
            int start = text.length();
            for (int i = 0; i < length; i++) {
                text.append((char) readVarInt(raw, pos));
            }
            // As TerminalLine.getText(): NUL can only be at the end, and ends the text.
            if (length > 0 && text.charAt(start) == CharUtils.NUL_CHAR) {
                text.setLength(start);
                break;
            }
        }
        return text.toString();
    }

    private @NotNull List<TerminalLine.TextEntry> decodeLine(byte[] raw, int[] pos) {
        int entryCount = readVarInt(raw, pos);
        List<TerminalLine.TextEntry> entries = new ArrayList<>(entryCount);
        for (int e = 0; e < entryCount; e++) {
            TextStyle style = myStyles[readVarInt(raw, pos)];
            int length = readVarInt(raw, pos);
            char[] chars = new char[length];
            for (int i = 0; i < length; i++) {
                chars[i] = (char) readVarInt(raw, pos);
            }
            entries.add(new TerminalLine.TextEntry(style, new CharBuffer(chars, 0, length), true));
        }
        return entries;
    }

    private static int[] lineOffsets(byte[] raw, int lineCount) {
        int[] offsets = new int[lineCount];
        int[] pos = {0};
        for (int l = 0; l < lineCount; l++) {
            offsets[l] = pos[0];
            int entryCount = readVarInt(raw, pos);
            for (int e = 0; e < entryCount; e++) {
                readVarInt(raw, pos);
                int length = readVarInt(raw, pos);
                for (int i = 0; i < length; i++) {
                    readVarInt(raw, pos);
                }
            }
        }
        return offsets;
    }

    private byte[] rawBytes() {
        if (!myDeflated) {
            return myData;
        }
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(myData);
            byte[] raw = new byte[myRawLength];
            int n = 0;
            while (n < myRawLength) {
                int read = inflater.inflate(raw, n, myRawLength - n);
                if (read == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                n += read;
            }
            if (n != myRawLength) {
                throw new IllegalStateException("Packed scrollback block is corrupt: " + n + " of " + myRawLength + " bytes");
            }
            return raw;
        } catch (DataFormatException e) {
            throw new IllegalStateException("Packed scrollback block is corrupt", e);
        } finally {
            inflater.end();
        }
    }

    /** The deflated form of {@code bytes}, or {@code null} when deflating does not make it smaller. */
    private static byte[] deflate(byte[] bytes, int length) {
        synchronized (DEFLATER) {
            DEFLATER.reset();
            DEFLATER.setInput(bytes, 0, length);
            DEFLATER.finish();
            byte[] out = new byte[Math.max(64, length / 4)];
            int n = 0;
            while (!DEFLATER.finished()) {
                if (n == out.length) {
                    if (out.length >= length) {
                        return null;
                    }
                    out = Arrays.copyOf(out, Math.min(length, out.length * 2));
                }
                n += DEFLATER.deflate(out, n, out.length - n);
            }
            return n < length ? Arrays.copyOf(out, n) : null;
        }
    }

    private static int readVarInt(byte[] bytes, int[] pos) {
        int value = 0;
        int shift = 0;
        int p = pos[0];
        while (true) {
            int b = bytes[p++];
            value |= (b & 0x7f) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
        }
        pos[0] = p;
        return value;
    }

    private static final class ByteSink {

        private byte[] myBytes;

        private int mySize;

        ByteSink(int capacity) {
            myBytes = new byte[Math.max(16, capacity)];
        }

        void writeVarInt(int value) {
            if (mySize + 5 > myBytes.length) {
                myBytes = Arrays.copyOf(myBytes, Math.max(myBytes.length * 2, mySize + 5));
            }
            while ((value & ~0x7f) != 0) {
                myBytes[mySize++] = (byte) ((value & 0x7f) | 0x80);
                value >>>= 7;
            }
            myBytes[mySize++] = (byte) value;
        }
    }
}
