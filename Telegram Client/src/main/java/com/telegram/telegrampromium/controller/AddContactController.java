package com.telegram.telegrampromium.controller;

import com.telegram.telegrampromium.app.App;
import com.telegram.telegrampromium.nav.Navigator;
import com.telegram.telegrampromium.nav.View;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import com.telegram.telegrampromium.api.ContactsAPI;
import com.telegram.telegrampromium.core.Client;
import com.telegram.telegrampromium.app.App;
import com.telegram.telegrampromium.nav.Navigator;

import java.io.IOException;
import java.util.Objects;

public class AddContactController {
    @FXML private TextField nameInput;
    @FXML private TextField phoneInput;
    @FXML private Button    saveBtn;
    @FXML private Label     statusLabel;
    private final App app;
    private final Navigator nav;
    private final ContactsAPI contactsApi;

    public AddContactController(App app, Navigator nav) {
        this.app = Objects.requireNonNull(app);
        this.nav = Objects.requireNonNull(nav);
        Client client = app.client();
        this.contactsApi = new ContactsAPI(client);
    }

    @FXML private void onBack() { if (nav != null) nav.back(); }

    @FXML private void onSave() {
        String name  = safe(nameInput.getText());
        String phone = normalizePhone(safe(phoneInput.getText()));
        if (name.isEmpty() || phone.isEmpty()) {
            showError("نام و شماره را کامل کنید.");
            return;
        }
        setBusy(true);
        contactsApi.addContact(name, phone).whenComplete((res, err) -> {
            javafx.application.Platform.runLater(() -> {
                setBusy(false);
                if (err != null) {
                    showError("خطا در ارتباط. دوباره تلاش کنید.");
                    return;
                }
                switch (res.status) {
                    case OK -> {
                        showSuccess("با موفقیت اضافه شد.");
                        if (nav != null) nav.back(); // برگرد به New Chat
                    }
                    case ALREADY_EXISTS -> {
                        showError("قبلاً در مخاطبین شما وجود دارد.");
                    }
                    case NOT_REGISTERED -> showError("این شماره در سیستم ثبت‌نام نشده است.");
                    case ERROR -> showError(res.message != null ? res.message : "خطای نامشخص.");
                }
            });
        });
    }

    private void setBusy(boolean b) {
        if (saveBtn != null) saveBtn.setDisable(b);
    }
    private void showError(String s) {
        if (statusLabel == null) return;
        statusLabel.setText(s != null ? s : "");
        statusLabel.getStyleClass().remove("ok");
        if (!statusLabel.getStyleClass().contains("error")) statusLabel.getStyleClass().add("error");
    }
    private void showSuccess(String s) {
        if (statusLabel == null) return;
        statusLabel.setText(s != null ? s : "");
        statusLabel.getStyleClass().remove("error");
        if (!statusLabel.getStyleClass().contains("ok")) statusLabel.getStyleClass().add("ok");
    }
    private static String safe(String s) { return s == null ? "" : s.trim(); }
    private static String normalizePhone(String p) {
        if (p == null) return "";
        p = p.replaceAll("[\\s-]", "");
        if (p.startsWith("00")) p = "+" + p.substring(2);
        return p;
    }
}
