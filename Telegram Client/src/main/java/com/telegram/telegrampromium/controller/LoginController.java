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

/**
 * Login screen controller: validates input, hashes the password (PHC),
 * sends LOGIN to the server, and navigates to Chat List on success.
 */
public final class LoginController {

    private final App app;
    private final Navigator nav;

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button signInButton;
    @FXML private Hyperlink signUpLink;
    @FXML private Label statusLabel;

    @FXML private Button lightBtn;
    @FXML private Button darkBtn;

    public LoginController(App app, Navigator nav) {
        this.app = Objects.requireNonNull(app);
        this.nav = Objects.requireNonNull(nav);
    }

    @FXML
    private void initialize() {
        status("");
        reflectThemeButtons(app.theme().current());
    }

    @FXML
    private void onSignInClick() {
        final String username = safeText(usernameField).trim();
        final String password = safeText(passwordField);

        if (username.isEmpty() || password.isEmpty()) {
            statusError("Username and password are required.");
            return;
        }

        setBusy(true);
        final String phc = PasswordHasher.phc(username, password.toCharArray());
        AuthenticationAPI api = app.auth();

        api.login(username, phc).whenComplete((resp, err) -> Platform.runLater(() -> {
            setBusy(false);
            if (err != null) {
                statusError("Network error.");
                return;
            }
            final String type = get(resp, "type");
            if ("LOGIN_OK".equals(type)) {
                statusOk("Signed in.");
                nav.replace(View.CHAT_LIST);
            } else if ("LOGIN_FAIL".equals(type)) {
                final String reason = get(resp, "reason");
                statusError(reason == null ? "Login failed." : reason);
            } else {
                statusError("Unexpected response.");
            }
        }));
    }

    @FXML
    private void onSignUpClick() {
        nav.push(View.SIGN_UP);
    }

    @FXML private void onLightClick() { applyTheme(Theme.LIGHT); }
    @FXML private void onDarkClick()  { applyTheme(Theme.DARK);  }

    private void applyTheme(Theme t) {
        Node any = signInButton;
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

    private void setBusy(boolean busy) {
        signInButton.setDisable(busy);
        usernameField.setDisable(busy);
        passwordField.setDisable(busy);
    }

    private void statusOk(String s)   { statusLabel.getStyleClass().removeAll("status-err"); statusLabel.getStyleClass().add("status-ok");  status(s); }
    private void statusError(String s){ statusLabel.getStyleClass().removeAll("status-ok");  statusLabel.getStyleClass().add("status-err"); status(s); }
    private void status(String s)     { statusLabel.setText(s == null ? "" : s); }

    private static String safeText(TextInputControl c) { return c.getText() == null ? "" : c.getText(); }

    private static String get(JsonObject o, String key) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
