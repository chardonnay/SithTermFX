package com.sithtermfx.core.model.hyperlinks;

import com.sithtermfx.core.HyperlinkStyle;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.model.CharBuffer;
import com.sithtermfx.core.model.LinesBuffer;
import com.sithtermfx.core.model.TerminalLine;
import com.sithtermfx.core.model.TerminalTextBuffer;
import com.sithtermfx.core.util.CharUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * @author Daniel Mengel
 */
public class TextProcessing {

    private static final Logger logger = LoggerFactory.getLogger(TextProcessing.class);

    private final List<HyperlinkFilter> myHyperlinkFilter;

    private final TextStyle myHyperlinkColor;

    private final HyperlinkStyle.HighlightMode myHighlightMode;

    private TerminalTextBuffer myTerminalTextBuffer;

    private LinkInfoProvider myLinkInfoProvider;

    public TextProcessing(@NotNull TextStyle hyperlinkColor, @NotNull HyperlinkStyle.HighlightMode highlightMode) {
        myHyperlinkColor = hyperlinkColor;
        myHighlightMode = highlightMode;
        myHyperlinkFilter = new ArrayList<>();
    }

    public void setTerminalTextBuffer(@NotNull TerminalTextBuffer terminalTextBuffer) {
        myTerminalTextBuffer = terminalTextBuffer;
    }

    public void processHyperlinks(@NotNull LinesBuffer buffer, @NotNull TerminalLine updatedLine) {
        if (myHyperlinkFilter.isEmpty()) return;
        doProcessHyperlinks(buffer, updatedLine);
    }

    private void doProcessHyperlinks(@NotNull LinesBuffer buffer, @NotNull TerminalLine updatedLine) {
        myTerminalTextBuffer.lock();
        try {
            int updatedLineInd = findLineInd(buffer, updatedLine);
            if (updatedLineInd == -1) {
                // When lines arrive fast enough, the line might be pushed to the history buffer already.
                updatedLineInd = findHistoryLineInd(myTerminalTextBuffer.getHistoryBuffer(), updatedLine);
                if (updatedLineInd == -1) {
                    logger.debug("Cannot find line for links processing");
                    return;
                }
                buffer = myTerminalTextBuffer.getHistoryBuffer();
            }
            int startLineInd = updatedLineInd;
            while (startLineInd > 0 && buffer.getLine(startLineInd - 1).isWrapped()) {
                startLineInd--;
            }
            String lineStr = joinLines(buffer, startLineInd, updatedLineInd);
            for (HyperlinkFilter filter : myHyperlinkFilter) {
                LinkResult result = filter.apply(lineStr);
                if (result != null) {
                    for (LinkResultItem item : result.getItems()) {
                        TextStyle style = new HyperlinkStyle(myHyperlinkColor.getForeground(),
                                myHyperlinkColor.getBackground(), item.getLinkInfo(), myHighlightMode, null, null);
                        if (item.getStartOffset() < 0 || item.getEndOffset() > lineStr.length()) continue;
                        int prevLinesLength = 0;
                        for (int lineInd = startLineInd; lineInd <= updatedLineInd; lineInd++) {
                            int startLineOffset = Math.max(prevLinesLength, item.getStartOffset());
                            int endLineOffset = Math.min(prevLinesLength + myTerminalTextBuffer.getWidth(), item.getEndOffset());
                            if (startLineOffset < endLineOffset) {
                                var x = startLineOffset - prevLinesLength;
                                var finalStr = lineStr.substring(startLineOffset, endLineOffset);
                                var line = buffer.getLine(lineInd);
                                if (myHighlightMode.isOriginalColorUsed()) {
                                    var stylesByPosition = getStylesByPostion(line, x, finalStr.length());
                                    var entries = stylesByPosition.entrySet().stream().collect(Collectors.toList());
                                    for (var i = 0; i < entries.size(); i++) {
                                        var currentEntry = entries.get(i);
                                        Map.Entry<Integer, TextStyle> nextEntry = null;
                                        if (i + 1 < entries.size()) {
                                            nextEntry = entries.get(i + 1);
                                        }
                                        String str = null;
                                        if (nextEntry == null) {
                                            str = finalStr.substring(currentEntry.getKey() - x);
                                        } else {
                                            str = finalStr.substring(currentEntry.getKey() - x, nextEntry.getKey() - x);
                                        }
                                        TextStyle linkStyle = styleForLink(currentEntry.getValue(), item.getLinkInfo());
                                        var charBuf = new CharBuffer(str);
                                        line.writeString(currentEntry.getKey(), charBuf, linkStyle);
                                    }
                                } else {
                                    writeOverAllButExplicitLinks(line, x, finalStr, style);
                                }
                            }
                            prevLinesLength += myTerminalTextBuffer.getWidth();
                        }
                    }
                }
            }
        } finally {
            myTerminalTextBuffer.unlock();
        }
    }

    /**
     * Writes a link found by a filter in a custom-colour mode over {@code [x, x + str.length())},
     * except over the cells of an explicit OSC 8 link, which is the program's own and is kept, as in
     * the original-colour modes.
     */
    private static void writeOverAllButExplicitLinks(@NotNull TerminalLine line, int x, @NotNull String str,
                                                     @NotNull TextStyle linkStyle) {
        var runs = new ArrayList<>(getStylesByPostion(line, x, str.length()).entrySet());
        if (runs.stream().noneMatch(run -> isExplicitLink(run.getValue()))) {
            line.writeString(x, new CharBuffer(str), linkStyle);
            return;
        }
        for (int i = 0; i < runs.size(); i++) {
            var run = runs.get(i);
            if (isExplicitLink(run.getValue())) {
                continue;
            }
            int start = run.getKey() - x;
            int end = i + 1 < runs.size() ? runs.get(i + 1).getKey() - x : str.length();
            line.writeString(run.getKey(), new CharBuffer(str.substring(start, end)), linkStyle);
        }
    }

    /** An explicit OSC 8 link: it has the text style it was started over; filter links never do. */
    private static boolean isExplicitLink(@NotNull TextStyle style) {
        return style instanceof HyperlinkStyle && ((HyperlinkStyle) style).getPrevTextStyle() != null;
    }

    /**
     * The style a cell of a link found by a filter gets in an original-colour mode. A line is
     * processed again on every write to it, so the cell may already carry a link from an earlier
     * pass, made when less of the line had arrived (a URL arriving in chunks or typed at the prompt):
     * that link is replaced, keeping the original style beneath it, so its target is the one found
     * now rather than a truncated one. An explicit OSC 8 link (it has a prevTextStyle; filter links
     * never do) is the program's own and is kept.
     */
    private @NotNull TextStyle styleForLink(@NotNull TextStyle cellStyle, @NotNull LinkInfo linkInfo) {
        if (cellStyle instanceof HyperlinkStyle) {
            HyperlinkStyle existing = (HyperlinkStyle) cellStyle;
            if (isExplicitLink(existing)) {
                return existing;
            }
            if (existing.getLinkInfo() == linkInfo) {
                return existing;
            }
            cellStyle = existing.getOriginalStyle() != null ? existing.getOriginalStyle() : TextStyle.EMPTY;
        }
        return new HyperlinkStyle(myHyperlinkColor.getForeground(), myHyperlinkColor.getBackground(), linkInfo,
                myHighlightMode, null, cellStyle);
    }

    /**
     * The style runs of {@code line} within {@code [x, x + length)}: the start column of each run,
     * mapped to its style. Runs are told apart by style identity, not {@link TextStyle#equals}: equal
     * styles in separate runs are separate runs, and {@code equals} ignores what makes two links
     * differ (every filter-made {@link HyperlinkStyle} has the same null colours, whatever its
     * target and original style), which made the later links of a line take the first one's
     * colours and target.
     */
    static @NotNull Map<Integer, TextStyle> getStylesByPostion(@NotNull TerminalLine line, int x, int length) {
        var treeMap = new TreeMap<Integer, TextStyle>();
        var entriesLength = 0;
        for (var e : line.getEntries()) {
            treeMap.put(entriesLength, e.getStyle());
            entriesLength += e.getLength();
        }
        Map<Integer, TextStyle> result = new LinkedHashMap<>();
        int end = x + length - 1;
        Map.Entry<Integer, TextStyle> entry = treeMap.floorEntry(x);
        if (entry == null) {
            return result;
        }
        TextStyle runStyle = entry.getValue();
        result.put(x, runStyle);
        entry = treeMap.higherEntry(entry.getKey());
        while (entry != null && entry.getKey() <= end) {
            if (entry.getValue() != runStyle) {
                runStyle = entry.getValue();
                result.put(entry.getKey(), runStyle);
            }
            entry = treeMap.higherEntry(entry.getKey());
        }
        return result;
    }

    private int findHistoryLineInd(@NotNull LinesBuffer historyBuffer, @NotNull TerminalLine line) {
        int lastLineInd = Math.max(0, historyBuffer.getLineCount() - 200); // check only last lines in history buffer
        for (int i = historyBuffer.getLineCount() - 1; i >= lastLineInd; i--) {
            if (historyBuffer.getLine(i) == line) {
                return i;
            }
        }
        return -1;
    }

    private static int findLineInd(@NotNull LinesBuffer buffer, @NotNull TerminalLine line) {
        for (int i = 0; i < buffer.getLineCount(); i++) {
            TerminalLine l = buffer.getLine(i);
            if (l == line) {
                return i;
            }
        }
        return -1;
    }

    @NotNull
    private String joinLines(@NotNull LinesBuffer buffer, int startLineInd, int updatedLineInd) {
        StringBuilder result = new StringBuilder();
        for (int i = startLineInd; i <= updatedLineInd; i++) {
            String text = buffer.getLine(i).getText();
            if (i < updatedLineInd && text.length() < myTerminalTextBuffer.getWidth()) {
                text = text + new CharBuffer(CharUtils.NUL_CHAR, myTerminalTextBuffer.getWidth() - text.length());
            }
            result.append(text);
        }
        return result.toString();
    }

    public void addHyperlinkFilter(@NotNull HyperlinkFilter filter) {
        myHyperlinkFilter.add(filter);
    }

    /**
     * Registers the provider used to resolve explicit OSC 8 hyperlink URIs into {@link LinkInfo}.
     * Only one provider is used; a later call replaces the previous one.
     */
    public void setLinkInfoProvider(@NotNull LinkInfoProvider linkInfoProvider) {
        myLinkInfoProvider = linkInfoProvider;
    }

    /**
     * Builds a {@link LinkInfo} for an explicit OSC 8 URI via the registered
     * {@link LinkInfoProvider}.
     *
     * @return {@code null} if no provider is registered or the provider rejects the URI.
     */
    @Nullable
    public LinkInfo createExplicitLink(@NotNull String uri) {
        return myLinkInfoProvider != null ? myLinkInfoProvider.createLinkInfo(uri) : null;
    }

    public @NotNull List<LinkResultItem> applyFilter(@NotNull String line) {
        List<LinkResultItem> links = new ArrayList<>();
        for (HyperlinkFilter filter : myHyperlinkFilter) {
            LinkResult result = filter.apply(line);
            if (result != null) {
                links.addAll(result.getItems());
            }
        }
        return links;
    }
}
