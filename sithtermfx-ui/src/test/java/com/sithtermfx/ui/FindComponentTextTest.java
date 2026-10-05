package com.sithtermfx.ui;

import com.sithtermfx.core.compatibility.Point;
import com.sithtermfx.core.model.CharBuffer;
import com.sithtermfx.ui.settings.DefaultSettingsProvider;
import com.sithtermfx.ui.settings.SearchBarText;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The find bar's strings come from the embedder's {@link SearchBarText}, English by default. */
public class FindComponentTextTest {

    private static final SearchBarText GERMAN = new SearchBarText() {
        @Override
        public @NotNull String noMatches() {
            return "Keine Treffer";
        }

        @Override
        public @NotNull String matchCounter(int current, int total) {
            return current + " von " + total;
        }
    };

    @Test
    public void theDefaultIsEnglish() {
        SearchBarText text = new DefaultSettingsProvider().getSearchBarText();
        assertSame(SearchBarText.ENGLISH, text);
        assertEquals("Find", text.placeholder());
        assertEquals("Ignore Case", text.ignoreCase());
        assertEquals("3 of 12", text.matchCounter(3, 12));
        assertEquals("No matches", text.noMatches());
        assertEquals("▼", text.nextMatch());
        assertEquals("▲", text.previousMatch());
    }

    @Test
    public void theCounterIsEmptyWithoutASearchText() {
        assertEquals("", FindComponent.labelText(GERMAN, "", null));
        assertEquals("", FindComponent.labelText(GERMAN, null, null));
    }

    @Test
    public void aSearchTextThatIsNotFoundSaysSo() {
        assertEquals("Keine Treffer", FindComponent.labelText(GERMAN, "xyz", null));
        assertEquals("Keine Treffer", FindComponent.labelText(GERMAN, "xyz", new StubResult(0, 0)));
        assertEquals("No matches", FindComponent.labelText(SearchBarText.ENGLISH, "xyz", new StubResult(0, 0)));
    }

    @Test
    public void theCounterUsesTheEmbeddersWording() {
        assertEquals("2 von 5", FindComponent.labelText(GERMAN, "a", new StubResult(5, 2)));
        assertEquals("2 of 5", FindComponent.labelText(SearchBarText.ENGLISH, "a", new StubResult(5, 2)));
    }

    @Test
    public void anEmptyTooltipTextLeavesTheTooltipOut() {
        assertNull(FindComponent.tooltip(""));
        assertNull(FindComponent.tooltip(null));
    }

    private static final class StubResult implements FindResult {

        private final List<FindItem> myItems = new ArrayList<>();

        private final int mySelected;

        StubResult(int count, int selected) {
            for (int i = 1; i <= count; i++) {
                int index = i;
                myItems.add(new FindItem() {
                    @Override
                    public @NotNull String getText() {
                        return "a";
                    }

                    @Override
                    public int getIndex() {
                        return index;
                    }

                    @Override
                    public Point getStart() {
                        return new Point(0, 0);
                    }

                    @Override
                    public Point getEnd() {
                        return new Point(1, 0);
                    }
                });
            }
            mySelected = selected;
        }

        @Override
        public @Nullable List<Pair<Integer, Integer>> getRanges(CharBuffer characters) {
            return null;
        }

        @Override
        public @NotNull List<FindItem> getItems() {
            return myItems;
        }

        @Override
        public @NotNull FindItem selectedItem() {
            return myItems.get(mySelected - 1);
        }

        @Override
        public @NotNull FindItem nextFindItem() {
            return selectedItem();
        }

        @Override
        public @NotNull FindItem prevFindItem() {
            return selectedItem();
        }
    }
}
