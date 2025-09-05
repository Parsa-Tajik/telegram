package com.telegram.telegrampromium.controller;

import com.telegram.telegrampromium.app.App;
import com.telegram.telegrampromium.nav.Navigator;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

import java.util.Objects;

/**
 * Minimal Chat List placeholder for post-auth navigation.
 * It will be expanded with paging and live updates in the next stages.
 */
public final class ChatListController {

    private final App app;
    private final Navigator nav;

    @FXML private Label titleLabel;
    @FXML private ListView<String> list;

    public ChatListController(App app, Navigator nav) {
        this.app = Objects.requireNonNull(app);
        this.nav = Objects.requireNonNull(nav);
    }

    @FXML
    private void initialize() {
        titleLabel.setText("Chats");
        list.getItems().setAll(
                "Welcome to Telegram PROmium",
                "This is a placeholder list",
                "Authentication is wired"
        );
    }
}
