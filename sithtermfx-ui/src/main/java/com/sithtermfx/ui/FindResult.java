package com.sithtermfx.ui;

import com.sithtermfx.core.compatibility.Point;
import com.sithtermfx.core.model.CharBuffer;
import java.util.List;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *
 * @author Daniel Mengel
 */
public interface FindResult {

    public static interface FindItem {

        @NotNull
        public String getText();

        /**
         * Returns one-based index in the result list.
         * @return
         */
        public int getIndex();

        public Point getStart();

        public Point getEnd();
    }

    public @Nullable List<Pair<Integer, Integer>> getRanges(CharBuffer characters);

    public @NotNull List<FindItem> getItems();

    public @NotNull FindItem selectedItem();

    public @NotNull FindItem nextFindItem();

    public @NotNull FindItem prevFindItem();
}
