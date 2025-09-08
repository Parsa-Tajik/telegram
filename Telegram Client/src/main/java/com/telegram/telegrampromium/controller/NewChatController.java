package com.telegram.telegrampromium.controller;

import com.telegram.telegrampromium.app.App;
import com.telegram.telegrampromium.nav.View;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.geometry.Pos;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import com.telegram.telegrampromium.nav.Navigator;
import com.telegram.telegrampromium.app.App;

public class NewChatController {

    private final App app;
    private final Navigator nav;
    @FXML private ListView<ContactItem> contactsList;

    public NewChatController(App app, Navigator nav) {
        this.app = Objects.requireNonNull(app);
        this.nav = Objects.requireNonNull(nav);
    }

    @FXML
    private void initialize() {
        // نمونه‌دیتا (سکلت UI) — بعداً با API کانتکت‌ها جایگزین می‌کنیم
        contactsList.getItems().setAll(List.of(
                new ContactItem("u_alice", "Alice", "لحظاتی پیش"),
                new ContactItem("u_bob", "Bob", "۲ ساعت پیش"),
                new ContactItem("u_charlie", "Charlie", "دیروز")
        ));
        contactsList.setCellFactory(lv -> new ContactCell());
        contactsList.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) openProfile(contactsList.getSelectionModel().getSelectedItem());
        });
    }

    // === Actions ===
    @FXML private void onBack() { if (nav != null) nav.back(); }
    @FXML private void onAddContact() { if (nav != null) nav.push(View.ADD_CONTACT); }
    @FXML private void onCreateGroup() { /* بعداً: گروه‌سازی */ info(); }
    @FXML private void onCreateChannel() { /* بعداً: کانال‌سازی */ info(); }

    private void openProfile(ContactItem ci) {
        if (ci == null) return;
        if (nav != null) nav.push(View.PROFILE);
    }

    private void info() {
        // Placeholder تا زمانی‌که منطق ساخته شود
        Alert a = new Alert(Alert.AlertType.INFORMATION, "در مراحل بعدی پیاده‌سازی می‌شود.", ButtonType.OK);
        a.showAndWait();
    }

    // === Minimal contact item for UI skeleton ===
    public static class ContactItem {
        public final String id, name, lastSeen;
        public ContactItem(String id, String name, String lastSeen) {
            this.id = id; this.name = name; this.lastSeen = lastSeen;
        }
    }

    private static class ContactCell extends ListCell<ContactItem> {
        @Override protected void updateItem(ContactItem it, boolean empty) {
            super.updateItem(it, empty);
            if (empty || it == null) { setText(null); setGraphic(null); return; }

            Label avatar = new Label(it.name != null && !it.name.isEmpty() ? it.name.substring(0,1).toUpperCase() : "?");
            avatar.getStyleClass().add("avatar");

            Label name = new Label(it.name);     name.getStyleClass().add("name");
            Label last = new Label(it.lastSeen); last.getStyleClass().add("lastseen");
            VBox v = new VBox(2, name, last);
            HBox row = new HBox(10, avatar, v);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("nc-contact");
            setGraphic(row);
            setText(null);
        }
    }
}
