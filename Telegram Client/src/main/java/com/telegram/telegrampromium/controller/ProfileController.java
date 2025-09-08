package com.telegram.telegrampromium.controller;

import com.telegram.telegrampromium.app.App;
import com.telegram.telegrampromium.nav.Navigator;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;

import java.io.IOException;
import java.util.Objects;

public class ProfileController {
    @FXML private Label avatar;
    @FXML private Label displayName;
    @FXML private Label username;
    @FXML private Label lastSeen;

    private final App app;
    private final Navigator nav;

    public ProfileController(App app, Navigator nav) {
        this.app = Objects.requireNonNull(app);
        this.nav = Objects.requireNonNull(nav);
    }

    @FXML private void initialize() {
        // TODO: فاز منطق → بارگذاری اطلاعات واقعی با userId
        // فعلاً نمونه:
        if (avatar != null) avatar.setText("A");
    }

    @FXML private void onBack() { if (nav != null) nav.back(); }
    @FXML private void onMessage() { /* TODO: DM create or get */ }
    @FXML private void onAdd()     { /* TODO: contacts_add */ }

}
