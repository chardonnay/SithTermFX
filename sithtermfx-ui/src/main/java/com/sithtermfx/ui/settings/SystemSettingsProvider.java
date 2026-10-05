package com.sithtermfx.ui.settings;

import com.sithtermfx.ui.TerminalActionPresentation;
import org.jetbrains.annotations.NotNull;

public interface SystemSettingsProvider {

    @NotNull
    TerminalActionPresentation getOpenUrlActionPresentation();

    @NotNull
    TerminalActionPresentation getCopyActionPresentation();

    @NotNull
    TerminalActionPresentation getPasteActionPresentation();

    @NotNull
    TerminalActionPresentation getClearBufferActionPresentation();

    @NotNull
    TerminalActionPresentation getPageUpActionPresentation();

    @NotNull
    TerminalActionPresentation getPageDownActionPresentation();

    @NotNull
    TerminalActionPresentation getLineUpActionPresentation();

    @NotNull
    TerminalActionPresentation getLineDownActionPresentation();

    @NotNull
    TerminalActionPresentation getFindActionPresentation();

    @NotNull
    TerminalActionPresentation getSelectAllActionPresentation();

    /**
     * The strings of the find bar: placeholder, check box, match counter, "no matches", buttons and
     * tooltips. The default is {@link SearchBarText#ENGLISH}; an embedder that localizes its UI
     * returns its own {@link SearchBarText}, overriding the strings it translates. The find bar reads
     * this each time it is opened.
     *
     * @return the find bar strings, never {@code null}
     */
    default @NotNull SearchBarText getSearchBarText() {
        return SearchBarText.ENGLISH;
    }
}
