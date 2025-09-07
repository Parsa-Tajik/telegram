package com.telegram.telegrampromium.controller;

import com.telegram.telegrampromium.app.App;
import com.telegram.telegrampromium.model.ChatSummary;
import com.telegram.telegrampromium.nav.Navigator;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;

import java.util.Objects;

/**
 * Chat screen skeleton: header + messages list + composer.
 * No history loading or send logic here yet (will be added in 4.2/4.3).
 */
public final class ChatController {

    private final App app;
    private final Navigator nav;

    // Chat context (set by Navigator after load)
    private String chatId;
    private ChatSummary.Kind chatKind;
    private String chatTitle;

    @FXML private Button   backBtn;
    @FXML private StackPane headerAvatar;
    @FXML private Label    headerTitle;
    @FXML private Label    headerSubtitle;

    @FXML private ListView<com.telegram.telegrampromium.model.Message> messagesList;
    @FXML private TextArea messageInput;
    @FXML private Button   sendBtn;

    public ChatController(App app, Navigator nav) {
        this.app = Objects.requireNonNull(app);
        this.nav = Objects.requireNonNull(nav);
    }

    @FXML
    private void initialize() {
        // Disable send until text exists (send logic comes in 4.3)
        sendBtn.disableProperty().bind(messageInput.textProperty().isEmpty());

        backBtn.setOnAction(e -> nav.back()); // go back to previous view
    }

    /** Called by Navigator right after FXML load. */
    public void setChatContext(String chatId, ChatSummary.Kind kind, String title) {
        this.chatId   = chatId;
        this.chatKind = kind;
        this.chatTitle= title;

        headerTitle.setText(title != null ? title : "");
        // Subtitle will be wired in later stages (presence/last seen)
        headerSubtitle.setText(" ");
        // Avatar letter
        headerAvatar.setUserData(title != null && !title.isBlank()
                ? title.substring(0,1).toUpperCase() : "?");

        // Prepare list cell factory (actual bubbles come in 4.2)
        messagesList.setPlaceholder(new Label("No messages yet"));
    }
}
