package com.telegram.telegrampromium.controller;

import com.google.gson.JsonObject;
import com.telegram.telegrampromium.api.AuthenticationAPI;
import com.telegram.telegrampromium.app.App;
import com.telegram.telegrampromium.app.Theme;
import com.telegram.telegrampromium.nav.Navigator;
import com.telegram.telegrampromium.nav.View;
import com.telegram.telegrampromium.security.PasswordHasher;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Sign-Up screen controller: validates fields, hashes password (PHC),
 * sends REGISTER, navigates to Chat List on success.
 */
public final class SignUpController {

    private static final Pattern PHONE_RE = Pattern.compile("^[0-9+][0-9\\- ]{5,}$");
    private static final Pattern USER_RE  = Pattern.compile("^[a-zA-Z0-9_]{3,32}$");

    private final App app;
    private final Navigator nav;

    @FXML private TextField displayNameField;
    @FXML private TextField phoneField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;

    @FXML private Button signUpButton;
    @FXML private Button backButton;
    @FXML private Label statusLabel;

    @FXML private Button lightBtn;
    @FXML private Button darkBtn;

    public SignUpController(App app, Navigator nav) {
        this.app = Objects.requireNonNull(app);
        this.nav = Objects.requireNonNull(nav);
    }

    @FXML
    private void initialize() {
        status("");
        reflectThemeButtons(app.theme().current());
    }

    @FXML
    private void onBackClick() {
        nav.pop();
    }

    @FXML
    private void onSignUpClick() {
        final String display = safe(displayNameField).trim();
        final String phone   = safe(phoneField).trim();
        final String user    = safe(usernameField).trim();
        final String pass    = safe(passwordField);

        if (display.isEmpty()) { error("Display name is required."); return; }
        if (!PHONE_RE.matcher(phone).matches()) { error("Phone format is invalid."); return; }
        if (!USER_RE.matcher(user).matches())   { error("Username format is invalid."); return; }
        if (pass.length() < 4) { error("Password is too short."); return; }

        setBusy(true);
        final String phc = PasswordHasher.phc(user, pass.toCharArray());
        final AuthenticationAPI api = app.auth();

        api.register(display, phone, user, phc).whenComplete((resp, err) -> Platform.runLater(() -> {
            setBusy(false);
            if (err != null) { error("Network error."); return; }
            final String type = get(resp, "type");
            if ("REGISTER_OK".equals(type)) {
                ok("Account created.");
                nav.replace(View.CHAT_LIST);
            } else if ("REGISTER_FAIL".equals(type)) {
                final String reason = get(resp, "reason");
                error(reason == null ? "Registration failed." : reason);
            } else {
                error("Unexpected response.");
            }
        }));
    }

    @FXML private void onLightClick() { applyTheme(Theme.LIGHT); }
    @FXML private void onDarkClick()  { applyTheme(Theme.DARK);  }

    private void applyTheme(Theme t) {
        Node any = signUpButton;
        if (any != null && any.getScene() != null) {
            app.theme().apply(any.getScene(), t);
            reflectThemeButtons(t);
        }
    }

    private void reflectThemeButtons(Theme t) {
        setActive(lightBtn, t == Theme.LIGHT);
        setActive(darkBtn,  t == Theme.DARK);
    }

    private static void setActive(Button btn, boolean active) {
        var styles = btn.getStyleClass();
        styles.remove("seg-btn-active");
        if (active) styles.add("seg-btn-active");
    }

    private void setBusy(boolean b) {
        signUpButton.setDisable(b);
        backButton.setDisable(b);
        displayNameField.setDisable(b);
        phoneField.setDisable(b);
        usernameField.setDisable(b);
        passwordField.setDisable(b);
    }

    private void ok(String s)    { statusLabel.getStyleClass().removeAll("status-err"); statusLabel.getStyleClass().add("status-ok");  status(s); }
    private void error(String s) { statusLabel.getStyleClass().removeAll("status-ok");  statusLabel.getStyleClass().add("status-err"); status(s); }
    private void status(String s){ statusLabel.setText(s == null ? "" : s); }

    private static String safe(TextInputControl c) { return c.getText() == null ? "" : c.getText(); }
    private static String get(JsonObject o, String k){ return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null; }
}
