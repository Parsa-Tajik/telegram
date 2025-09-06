package com.telegram.telegrampromium.controller.cell;

import com.telegram.telegrampromium.model.ChatSummary;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/**
 * FXML-backed list cell for chat summaries.
 * Displays avatar placeholder, title, preview, time, and unread badge.
 */
public final class ChatSummaryCellController {

    @FXML private StackPane avatar;
    @FXML private Label titleLabel;
    @FXML private Label previewLabel;
    @FXML private Label timeLabel;
    @FXML private HBox   unreadBadge;
    @FXML private Label  unreadCount;

    public void bind(String title, String preview, String when,
                     int unread, ChatSummary.Kind kind, boolean muted) {
        titleLabel.setText(title == null ? "" : title);
        previewLabel.setText(preview == null ? "" : preview);
        timeLabel.setText(when == null ? "" : when);

        // Unread badge visibility
        boolean showBadge = unread > 0;
        unreadBadge.setVisible(showBadge);
        unreadBadge.setManaged(showBadge);
        unreadCount.setText(showBadge ? String.valueOf(unread) : "");

        // Avatar placeholder with initial letter
        String initial = title != null && !title.isBlank() ? title.substring(0, 1).toUpperCase() : "?";
        avatar.setUserData(initial); // optional; could be used by a future skin

        // Muted chats can be styled via CSS pseudo-class if needed later.
    }
}
