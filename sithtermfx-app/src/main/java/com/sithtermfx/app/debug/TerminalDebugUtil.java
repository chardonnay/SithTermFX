package com.sithtermfx.app.debug;

import com.sithtermfx.core.StyledTextConsumerAdapter;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.model.CharBuffer;
import com.sithtermfx.core.model.TerminalTextBuffer;
import java.util.HashMap;
import java.util.Map;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

public final class TerminalDebugUtil {

    @NotNull
    public static final String getStyleLines(@NotNull TerminalTextBuffer textBuffer) {
        Intrinsics.checkNotNullParameter(textBuffer, "textBuffer");
        Map<Integer, Integer> style2IdMap = new HashMap();
        textBuffer.lock();
        try {
            var sb = new StringBuilder();
            textBuffer.getScreenBuffer().processLines(0, textBuffer.getHeight(), new StyledTextConsumerAdapter() {

                private int count;

                @Override
                public void consume(int x, int y, TextStyle style, CharBuffer characters, int startRow) {
                    if (x == 0) {
                        sb.append("\n");
                    }
                    var styleNum = style.hashCode();
                    if (!style2IdMap.containsKey(styleNum)) {
                        style2IdMap.put(styleNum, count++);
                    }
                    sb.append(String.format("%02d ", style2IdMap.get(styleNum)));
                }
            });
            return sb.toString();
        } finally {
            textBuffer.unlock();
        }
    }
}
