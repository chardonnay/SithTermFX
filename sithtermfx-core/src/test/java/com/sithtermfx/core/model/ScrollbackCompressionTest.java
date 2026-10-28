package com.sithtermfx.core.model;

import com.sithtermfx.core.HyperlinkStyle;
import com.sithtermfx.core.StyledTextConsumerAdapter;
import com.sithtermfx.core.TerminalColor;
import com.sithtermfx.core.RequestOrigin;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.model.hyperlinks.LinkInfo;
import com.sithtermfx.core.util.BackBufferDisplay;
import com.sithtermfx.core.util.CharUtils;
import com.sithtermfx.core.util.TermSize;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The history memory limit and the scrollback compression: packed lines must read back exactly as
 * they were written (text, style identity, wrapping, NUL tails), keep their identity, and only lines
 * outside the newest screenful and the shown rows may be packed.
 */
public class ScrollbackCompressionTest {

    private static final int WIDTH = 80;

    private static final int HEIGHT = 24;

    @Test
    public void packedLinesReadBackExactly() {
        TextStyle red = new TextStyle(TerminalColor.index(1), null);
        TextStyle link = new HyperlinkStyle(red, new LinkInfo(() -> { }));
        List<TerminalLine> lines = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            TerminalLine line = new TerminalLine();
            line.writeString(0, new CharBuffer("line " + i + " äöü € 漢" + CharUtils.DWC + " 😀 "), TextStyle.EMPTY);
            line.writeString(30, new CharBuffer("red " + i), new TextStyle(TerminalColor.index(1), null));
            line.writeString(45, new CharBuffer("https://example.org/" + i), link);
            if (i % 3 == 0) {
                line.deleteCharacters(70, red); // NUL tail in a style
            }
            line.setWrapped(i % 5 == 0);
            lines.add(line);
        }
        List<String> texts = new ArrayList<>();
        List<String> dumps = new ArrayList<>();
        List<List<TextStyle>> styles = new ArrayList<>();
        for (TerminalLine line : lines) {
            texts.add(line.getText());
            dumps.add(line.toString());
            styles.add(stylesOf(line));
        }
        LinesBuffer buffer = new LinesBuffer(-1, null);
        buffer.addLines(lines);
        buffer.setPackingAllowed(true);
        long before = buffer.getEstimatedBytes();

        int packed = buffer.packLines(0, buffer.getLineCount(), -1, -1, 1, Integer.MAX_VALUE, false);

        assertEquals(300, packed);
        assertEquals(300, buffer.getPackedLineCount());
        assertTrue(buffer.getEstimatedBytes() * 3 < before,
                "packed " + buffer.getEstimatedBytes() + " bytes, unpacked " + before);
        // Text alone is decoded without unpacking.
        for (int i = 0; i < lines.size(); i++) {
            assertEquals(texts.get(i), buffer.getLine(i).getText());
        }
        assertEquals(300, buffer.getPackedLineCount());
        for (int i = 0; i < lines.size(); i++) {
            TerminalLine line = buffer.getLine(i);
            assertSame(lines.get(i), line);
            assertEquals(dumps.get(i), line.toString());
            assertEquals(i % 5 == 0, line.isWrapped());
            List<TextStyle> actual = stylesOf(line);
            assertEquals(styles.get(i).size(), actual.size());
            for (int s = 0; s < actual.size(); s++) {
                TextStyle expected = styles.get(i).get(s);
                if (expected.getClass() == TextStyle.class) {
                    assertEquals(expected, actual.get(s)); // plain styles are shared by value
                } else {
                    assertSame(expected, actual.get(s)); // links and embedder styles keep their identity
                }
            }
        }
        assertEquals(0, buffer.getPackedLineCount());
    }

    @Test
    public void aLineWithACustomHighlightingOrTypeAheadStaysUnpacked() {
        LinesBuffer buffer = new LinesBuffer(-1, null);
        buffer.setPackingAllowed(true);
        for (int i = 0; i < 10; i++) {
            buffer.addNewLine(TextStyle.EMPTY, new CharBuffer("line " + i));
        }
        buffer.getLine(3).addCustomHighlighting(0, 2, TextStyle.EMPTY);
        buffer.getLine(6).setTypeAheadLine(new TerminalLine());

        buffer.packLines(0, 10, -1, -1, 1, Integer.MAX_VALUE, false);

        assertEquals(8, buffer.getPackedLineCount());
        assertFalse(buffer.getLine(3).isPacked());
        assertFalse(buffer.getLine(6).isPacked());
    }

    @Test
    public void theMemoryLimitDropsTheOldestLines() {
        LinesBuffer buffer = new LinesBuffer(100_000, null);
        buffer.setMaxBytes(100_000);
        for (int i = 0; i < 5_000; i++) {
            buffer.addNewLine(TextStyle.EMPTY, new CharBuffer(String.format("%-79d", i)));
        }
        assertTrue(buffer.getEstimatedBytes() <= 100_000);
        assertTrue(buffer.getLineCount() > 100 && buffer.getLineCount() < 1_000, "lines " + buffer.getLineCount());
        assertEquals(String.format("%-79d", 4_999), buffer.getLine(buffer.getLineCount() - 1).getText());
        int first = Integer.parseInt(buffer.getLine(0).getText().trim());
        assertEquals(5_000 - buffer.getLineCount(), first);
    }

    @Test
    public void overTheLimitACompressingBufferPacksBeforeItDrops() {
        LinesBuffer plain = new LinesBuffer(100_000, null);
        plain.setMaxBytes(200_000);
        LinesBuffer packing = new LinesBuffer(100_000, null);
        packing.setMaxBytes(200_000);
        packing.setPackingAllowed(true);
        for (int i = 0; i < 20_000; i++) {
            String text = "build step " + i + ": compiling module " + (i % 37) + " … ok";
            plain.addNewLine(TextStyle.EMPTY, new CharBuffer(text));
            packing.addNewLine(TextStyle.EMPTY, new CharBuffer(text));
        }
        assertTrue(packing.getEstimatedBytes() <= 200_000);
        assertTrue(packing.getLineCount() > plain.getLineCount() * 3,
                "packing kept " + packing.getLineCount() + ", plain " + plain.getLineCount());
        // The newest lines stay unpacked.
        for (int i = packing.getLineCount() - LinesBuffer.PRESSURE_KEEP_LINES; i < packing.getLineCount(); i++) {
            assertFalse(packing.getLine(i).isPacked());
        }
        assertEquals("build step 19999: compiling module " + (19_999 % 37) + " … ok",
                packing.getLine(packing.getLineCount() - 1).getText());
    }

    @Test
    public void compressionKeepsTheNewestScreenfulAndTheShownRows() {
        Fixture f = new Fixture(1_000);
        f.buffer.setScrollbackCompressionEnabled(true);
        try {
            int history = f.buffer.getHistoryLinesCount();
            // Show rows 200 .. 200+HEIGHT of the history, as a render scrolled back would.
            f.buffer.processHistoryAndScreenLines(200 - history, HEIGHT, new StyledTextConsumerAdapter() {
            });

            int packed = f.buffer.compressHistoryNow();

            LinesBuffer lines = f.buffer.getHistoryBuffer();
            for (int i = 0; i < history; i++) {
                boolean shown = i >= 200 - HEIGHT && i < 200 + HEIGHT + HEIGHT;
                boolean newest = i >= history - HEIGHT;
                assertEquals(!shown && !newest, lines.getLine(i).isPacked(), "row " + i);
            }
            assertEquals(history - HEIGHT - 3 * HEIGHT, packed);
            f.assertHistoryText();
        } finally {
            f.buffer.setScrollbackCompressionEnabled(false);
        }
    }

    @Test
    public void drawingAPackedRowUnpacksItsBlockWithTheSameLineObjects() {
        Fixture f = new Fixture(600);
        f.buffer.setScrollbackCompressionEnabled(true);
        try {
            f.buffer.compressHistoryNow();
            TerminalLine line = f.buffer.getLine(-500);
            assertTrue(line.isPacked());
            StringBuilder firstRow = new StringBuilder();
            f.buffer.processHistoryAndScreenLines(-500, HEIGHT, new StyledTextConsumerAdapter() {
                @Override
                public void consume(int x, int y, @NotNull TextStyle style, @NotNull CharBuffer characters, int startRow) {
                    if (y == startRow) {
                        firstRow.append(characters);
                    }
                }
            });
            assertFalse(line.isPacked());
            assertSame(line, f.buffer.getLine(-500));
            assertEquals(f.expected(f.buffer.getHistoryLinesCount() - 500), firstRow.toString());
        } finally {
            f.buffer.setScrollbackCompressionEnabled(false);
        }
    }

    @Test
    public void pausedOrDisabledCompressionPacksNothing() {
        Fixture f = new Fixture(500);
        assertEquals(0, f.buffer.compressHistoryNow());
        f.buffer.setScrollbackCompressionEnabled(true);
        try {
            f.buffer.setScrollbackCompressionPaused(true);
            assertEquals(0, f.buffer.compressHistoryNow());
            f.buffer.setScrollbackCompressionPaused(false);
            assertTrue(f.buffer.compressHistoryNow() > 0);
        } finally {
            f.buffer.setScrollbackCompressionEnabled(false);
        }
    }

    @Test
    public void aWidthChangeReflowsCompressedHistory() {
        Fixture f = new Fixture(800);
        f.buffer.setScrollbackCompressionEnabled(true);
        try {
            f.buffer.setHistoryMemoryLimit(10L * 1024 * 1024);
            f.buffer.compressHistoryNow();
            String all = f.allHistoryText();

            f.terminal.resize(new TermSize(WIDTH + 20, HEIGHT), RequestOrigin.User);
            f.buffer.compressHistoryNow();
            f.terminal.resize(new TermSize(WIDTH, HEIGHT), RequestOrigin.User);

            assertEquals(all, f.allHistoryText());
        } finally {
            f.buffer.setScrollbackCompressionEnabled(false);
        }
    }

    @Test
    public void theMainHistoryIsCompressedWhileTheAlternateScreenIsShown() {
        Fixture f = new Fixture(500);
        f.buffer.setScrollbackCompressionEnabled(true);
        try {
            f.buffer.useAlternateBuffer(true);
            assertTrue(f.buffer.compressHistoryNow() > 0);
            assertTrue(f.buffer.getCompressedHistoryLinesCount() > 0);
            f.buffer.useAlternateBuffer(false);
            f.assertHistoryText();
        } finally {
            f.buffer.setScrollbackCompressionEnabled(false);
        }
    }

    @Test
    public void lowingTheLimitDropsLinesAtOnce() {
        Fixture f = new Fixture(2_000);
        int before = f.buffer.getHistoryLinesCount();
        f.buffer.setHistoryMemoryLimit(50_000);
        assertTrue(f.buffer.getHistoryLinesCount() < before);
        assertTrue(f.buffer.getHistoryMemoryUsage() <= 50_000);
        f.assertHistoryText();
    }

    @Test
    public void theBackgroundCompressionWorksOnceTheTerminalIsIdle() throws InterruptedException {
        Fixture f = new Fixture(1_000);
        f.buffer.setScrollbackCompressionEnabled(true);
        try {
            long now = System.nanoTime();
            f.buffer.compressIdleHistory(now, ScrollbackCompressor.IDLE_NANOS, ScrollbackCompressor.SLICE_NANOS);
            assertEquals(0, f.buffer.getCompressedHistoryLinesCount(), "the terminal just had output");

            assertTrue(ScrollbackCompressor.isRunning());
            long deadline = System.currentTimeMillis() + 10_000;
            int expected = f.buffer.getHistoryLinesCount() - HEIGHT;
            while (f.buffer.getCompressedHistoryLinesCount() < expected && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
            assertEquals(expected, f.buffer.getCompressedHistoryLinesCount());
            f.assertHistoryText();
        } finally {
            f.buffer.setScrollbackCompressionEnabled(false);
        }
        assertFalse(ScrollbackCompressor.isRunning());
    }

    private static List<TextStyle> stylesOf(TerminalLine line) {
        List<TextStyle> styles = new ArrayList<>();
        line.forEachEntry(entry -> styles.add(entry.getStyle()));
        return styles;
    }

    /** A terminal that printed {@code lines} numbered lines. */
    private static final class Fixture {

        final TerminalTextBuffer buffer;

        final SithTerminal terminal;

        final int lines;

        Fixture(int lines) {
            this.lines = lines;
            StyleState state = new StyleState();
            buffer = new TerminalTextBuffer(WIDTH, HEIGHT, state, 100_000, null);
            terminal = new SithTerminal(new BackBufferDisplay(buffer), buffer, state);
            TextStyle bold = new TextStyle.Builder().setOption(TextStyle.Option.BOLD, true).build();
            for (int i = 0; i < lines; i++) {
                terminal.writeString(expected(i).substring(0, 10));
                state.setCurrent(bold);
                terminal.writeString(expected(i).substring(10));
                state.setCurrent(TextStyle.EMPTY);
                terminal.newLine();
                terminal.carriageReturn();
            }
        }

        String expected(int i) {
            return String.format("%08d: the quick brown fox jumps over the lazy dog", i);
        }

        void assertHistoryText() {
            int history = buffer.getHistoryLinesCount();
            int first = lines + 1 - HEIGHT - history;
            for (int i = 0; i < history; i++) {
                assertEquals(expected(first + i), buffer.getLine(i - history).getText(), "history row " + i);
            }
        }

        String allHistoryText() {
            StringBuilder text = new StringBuilder();
            int history = buffer.getHistoryLinesCount();
            for (int i = 0; i < history; i++) {
                TerminalLine line = buffer.getLine(i - history);
                text.append(line.getText()).append(line.isWrapped() ? "" : "\n");
            }
            return text.toString();
        }
    }
}
