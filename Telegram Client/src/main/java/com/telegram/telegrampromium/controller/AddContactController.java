package com.telegram.telegrampromium.controller;

import com.telegram.telegrampromium.app.App;
import com.telegram.telegrampromium.nav.Navigator;
import com.telegram.telegrampromium.nav.View;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.util.Objects;

public class AddContactController {
    @FXML private TextField nameInput;
    @FXML private TextField phoneInput;
    private final App app;
    private final Navigator nav;

    public AddContactController(App app, Navigator nav) {
        this.app = Objects.requireNonNull(app);
        this.nav = Objects.requireNonNull(nav);
    }

    @FXML private void onBack() { if (nav != null) nav.back(); }

    @FXML private void onSave() {
        // TODO: در فاز منطق، به API contacts_add متصل می‌شود.
        if (nav != null) nav.back();
    }
}
