package com.sithtermfx.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.sithtermfx.ui.settings.SearchBarText;

import java.util.Objects;

final class FindComponent {

    private final HBox pane = new HBox();

    private final TextField textField = new TextField();

    private final Label label = new Label();

    private final CheckBox ignoreCaseCheckBox;

    private final SearchBarText text;

    public FindComponent(SithTermFxWidget jediTermWidget) {
        this.text = Objects.requireNonNullElse(jediTermWidget.mySettingsProvider.getSearchBarText(),
                SearchBarText.ENGLISH);
        this.ignoreCaseCheckBox = new CheckBox(text.ignoreCase());
        this.ignoreCaseCheckBox.setTooltip(new Tooltip(text.ignoreCaseTooltip()));
        this.ignoreCaseCheckBox.setSelected(true);
        this.textField.setPromptText(text.placeholder());
        Button next = new Button(text.nextMatch());
        next.setTooltip(new Tooltip(text.nextMatchTooltip()));
        next.setOnAction(e -> onResultUpdated(jediTermWidget.getTerminalPanel().selectNextFindResultItem()));
        Button prev = new Button(text.previousMatch());
        prev.setTooltip(new Tooltip(text.previousMatchTooltip()));
        prev.setOnAction(e -> onResultUpdated(jediTermWidget.getTerminalPanel().selectPrevFindResultItem()));
        var charSize = jediTermWidget.myTerminalPanel.myCharSize;
        HBox.setHgrow(textField, Priority.ALWAYS);
        pane.setMaxSize(
                charSize.getWidth() * 60,
                charSize.getHeight() + 14);
        pane.setAlignment(Pos.CENTER_LEFT);
        pane.setPadding(new Insets(0, charSize.getWidth() / 2, 0, charSize.getWidth() / 2));
        pane.setStyle("-fx-background-color: -fx-background");
        textField.setEditable(true);
        updateLabel(null);
        this.pane.getChildren().add(textField);
        this.pane.getChildren().add(ignoreCaseCheckBox);
        HBox.setMargin(ignoreCaseCheckBox, new Insets(0, charSize.getWidth(), 0, charSize.getWidth()));
        this.pane.getChildren().add(label);
        this.pane.getChildren().add(next);
        this.pane.getChildren().add(prev);
        this.pane.focusedProperty().addListener((ov, oldV, newV) -> {
            if (newV) {
                this.textField.requestFocus();
            }
        });
    }

    TextField getTextField() {
        return textField;
    }

    CheckBox getIgnoreCaseCheckBox() {
        return ignoreCaseCheckBox;
    }

    private void updateLabel(@Nullable FindResult result) {
        label.setText(labelText(text, textField.getText(), result));
    }

    /**
     * The counter beside the search field: empty while there is nothing to search for, "no matches"
     * when the search text is not found (also after stepping through no results), else the number of
     * the selected match and the number of matches.
     */
    static @NotNull String labelText(@NotNull SearchBarText text, @Nullable String pattern,
                                     @Nullable FindResult result) {
        if (pattern == null || pattern.isEmpty()) {
            return "";
        }
        if (result == null || result.getItems().isEmpty()) {
            return text.noMatches();
        }
        return text.matchCounter(result.selectedItem().getIndex(), result.getItems().size());
    }

    void onResultUpdated(FindResult results) {
        updateLabel(results);
    }

    @NotNull Pane getPane() {
        return this.pane;
    }

    void requestFocus() {
        textField.requestFocus();
    }
}
