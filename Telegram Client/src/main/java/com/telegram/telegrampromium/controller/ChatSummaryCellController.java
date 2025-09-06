package com.telegram.telegrampromium.controller.cell;

import com.telegram.telegrampromium.model.ChatSummary;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/**
 * FXML-backed list cell for chat summaries.
 * Displays avatar placeholder, title, preview, time, unread badge, and pin icon.
 */
public final class ChatSummaryCellController {

    @FXML private StackPane avatar;
    @FXML private Label titleLabel;
    @FXML private Label previewLabel;
    @FXML private Label timeLabel;
    @FXML private HBox   unreadBadge;
    @FXML private Label  unreadCount;
    @FXML private Label  pinIcon; // 📌

    public void bind(String title, String preview, String when,
                     int unread, ChatSummary.Kind kind, boolean muted, boolean pinned) {
        titleLabel.setText(title == null ? "" : title);
        previewLabel.setText(preview == null ? "" : preview);
        timeLabel.setText(when == null ? "" : when);

        boolean showBadge = unread > 0;
        unreadBadge.setVisible(showBadge);
        unreadBadge.setManaged(showBadge);
        unreadCount.setText(showBadge ? String.valueOf(unread) : "");

        // Avatar placeholder initial
        String initial = title != null && !title.isBlank() ? title.substring(0, 1).toUpperCase() : "?";
        avatar.setUserData(initial);

        // Pin icon visibility
        pinIcon.setVisible(pinned);
        pinIcon.setManaged(pinned);
    }
}
