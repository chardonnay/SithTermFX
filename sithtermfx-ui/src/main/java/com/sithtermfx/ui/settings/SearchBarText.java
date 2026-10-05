package com.sithtermfx.ui.settings;

import org.jetbrains.annotations.NotNull;

/**
 * The strings of the find bar ({@code Ctrl+F} / {@code Cmd+F}), for embedders that localize it. Every
 * method has the English default, so an implementation overrides only what it translates, e.g. from
 * its own resource bundle:
 * <pre>{@code
 * public SearchBarText getSearchBarText() {
 *     return new SearchBarText() {
 *         public String placeholder() { return bundle.getString("find.placeholder"); }
 *         public String matchCounter(int current, int total) {
 *             return MessageFormat.format(bundle.getString("find.counter"), current, total);
 *         }
 *     };
 * }
 * }</pre>
 * The library ships no translations; see {@link SystemSettingsProvider#getSearchBarText()}.
 */
public interface SearchBarText {

    /** The English strings, as the find bar showed them before they could be changed. */
    SearchBarText ENGLISH = new SearchBarText() {
    };

    /** @return the prompt text shown in the empty search field */
    default @NotNull String placeholder() {
        return "Find";
    }

    /** @return the label of the case-insensitive search check box */
    default @NotNull String ignoreCase() {
        return "Ignore Case";
    }

    /** @return the tooltip of the case-insensitive search check box */
    default @NotNull String ignoreCaseTooltip() {
        return "Match upper and lower case alike";
    }

    /**
     * @param current the one-based number of the selected match
     * @param total   the number of matches, at least 1
     * @return the match counter, e.g. "3 of 12"
     */
    default @NotNull String matchCounter(int current, int total) {
        return current + " of " + total;
    }

    /** @return the counter text when the search text is not found */
    default @NotNull String noMatches() {
        return "No matches";
    }

    /** @return the text of the button that selects the next match (below) */
    default @NotNull String nextMatch() {
        return "▼";
    }

    /** @return the tooltip of the button that selects the next match */
    default @NotNull String nextMatchTooltip() {
        return "Next match (Enter, Down)";
    }

    /** @return the text of the button that selects the previous match (above) */
    default @NotNull String previousMatch() {
        return "▲";
    }

    /** @return the tooltip of the button that selects the previous match */
    default @NotNull String previousMatchTooltip() {
        return "Previous match (Up)";
    }
}
