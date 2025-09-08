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
import java.util.stream.Collectors;

import com.telegram.telegrampromium.api.ContactsAPI;
import com.telegram.telegrampromium.api.ContactsAPI.ContactBrief;
import com.telegram.telegrampromium.core.Client;
import com.telegram.telegrampromium.nav.Navigator;
import com.telegram.telegrampromium.app.App;

public class NewChatController {

    private final App app;
    private final Navigator nav;
    private ContactsAPI contactsApi;
    @FXML private ListView<ContactItem> contactsList;

    public NewChatController(App app, Navigator nav) {
        this.app = Objects.requireNonNull(app);
        this.nav = Objects.requireNonNull(nav);
    }

    @FXML
    private void initialize() {
        contactsList.setCellFactory(lv -> {
            ContactCell cell = new ContactCell();
            cell.setOnMouseClicked(ev -> {
                if (!cell.isEmpty()) {
                    openProfile(cell.getItem());
                }
            });
            return cell;
        });

        if (contactsApi == null) {
            var client = app.client();
            contactsApi = new ContactsAPI(client);
        }

        refreshContacts();
    }

    private void refreshContacts() {
        // Placeholder برای تجربهٔ بهتر
        contactsList.getItems().setAll(List.of(
                new ContactItem(null, "در حال بارگیری مخاطبین...", "")
        ));
        contactsApi.listContacts().whenComplete((list, err) -> {
            javafx.application.Platform.runLater(() -> {
                if (err != null) {
                    // اگر خواستی اینجا Toast/Alert هم نشان بدهیم
                    contactsList.getItems().setAll(List.of(
                            new ContactItem(null, "عدم دسترسی به سرور", "بعداً دوباره تلاش کنید.")
                    ));
                    return;
                }
                List<ContactItem> items = list.stream()
                        .map(this::toItem)
                        .collect(Collectors.toList());
                contactsList.getItems().setAll(items);
            });
        });
    }

    private ContactItem toItem(ContactBrief b) {
        String last = (b.lastSeenText != null && !b.lastSeenText.isBlank())
                ? b.lastSeenText
                : (b.lastSeen != null ? "last seen " + b.lastSeen : "");
        return new ContactItem(b.userId, b.displayName != null ? b.displayName : (b.username != null ? b.username : "کاربر"), last);
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
