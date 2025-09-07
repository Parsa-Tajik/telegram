package com.telegram.telegrampromium.controller;

import com.google.gson.JsonObject;
import com.telegram.telegrampromium.api.ChatAPI;
import com.telegram.telegrampromium.app.App;
import com.telegram.telegrampromium.model.ChatSummary;
import com.telegram.telegrampromium.util.Formats;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;

import java.net.URL;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chat list:
 *  - Initial page pull
 *  - Infinite scroll
 *  - EVENT(message_new) handling
 *  - Context menu: Pin/Unpin chat
 */
public final class ChatListController {

    private static final Comparator<ChatSummary> ORDER =
            Comparator.<ChatSummary>comparingInt(c -> c.pinned() ? 0 : 1) // pinned first
                    .thenComparing(Comparator.comparingLong(ChatSummary::lastTs).reversed()); // newest first


    private final App app;
    private final com.telegram.telegrampromium.nav.Navigator nav;

    @FXML private Label titleLabel;
    @FXML private ListView<ChatSummary> list;

    private ChatAPI chatApi;
    private String nextCursor;
    private boolean hasMore;
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicBoolean pendingTopRefresh = new AtomicBoolean(false);
    private ChangeListener<Number> vbarListener;

    public ChatListController(App app, com.telegram.telegrampromium.nav.Navigator nav) {
        this.app = Objects.requireNonNull(app);
        this.nav = Objects.requireNonNull(nav);
    }


    @FXML
    private void initialize() {
        titleLabel.setText("Chats");
        chatApi = new ChatAPI(app.client());

        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(ChatSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); setContextMenu(null); return; }
                try {
                    URL fx = getClass().getResource("/ui/fxml/cell_chat_summary.fxml");
                    FXMLLoader l = new FXMLLoader(fx);
                    Region root = l.load();
                    var c = l.getController();
                    if (c instanceof com.telegram.telegrampromium.controller.cell.ChatSummaryCellController cell) {
                        String when = Formats.friendlyTs(item.lastTs(), ZoneId.systemDefault());
                        cell.bind(item.title(), item.lastPreview(), when, item.unread(), item.kind(), item.muted(), item.pinned());
                    }
                    setText(null);
                    setGraphic(root);

                    this.setOnMouseClicked(ev -> {
                        // Only left-click, single click, and non-empty cell
                        if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 1 && !isEmpty()) {
                            var it = getItem();
                            nav.openChat(it.id(), it.kind(), it.title());
                        }
                    });

                    // Context menu per item (Pin/Unpin)
                    ContextMenu menu = new ContextMenu();
                    MenuItem pin = new MenuItem(item.pinned() ? "Unpin" : "Pin");
                    pin.setGraphic(new Label("📌"));
                    pin.setOnAction(e -> onPinToggle(item));
                    menu.getItems().add(pin);
                    setContextMenu(menu);

                } catch (Exception e) {
                    setText(item.title());
                    setGraphic(null);
                    setContextMenu(null);
                }
            }
        });

        refreshTop();
        hookInfiniteScroll();
        app.eventBus().subscribe(this::onEvent);
    }

    /* ===================== Events (UI thread) ===================== */

    private void onEvent(JsonObject evt) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> onEvent(evt));
            return;
        }
        if (evt == null) return;
        String type = str(evt, "type");
        if (!"EVENT".equals(type)) return;

        String ev = str(evt, "event");
        if (ev == null) return;

        if ("message_new".equals(ev)) {
            handleMessageNew(evt);
        }
    }

    private void handleMessageNew(JsonObject evt) {
        final String chatId = str(evt, "chatId");
        if (chatId == null || chatId.isBlank()) return;

        JsonObject msg = evt.has("msg") && evt.get("msg").isJsonObject() ? evt.getAsJsonObject("msg") : null;
        if (msg == null) return;

        final long ts = msg.has("ts") ? safeLong(msg.get("ts")) : System.currentTimeMillis();
        final String preview = previewFromMsg(msg);
        final int unread = evt.has("unread") ? safeInt(evt.get("unread")) : 0;

        int idx = indexOfChat(chatId);
        if (idx >= 0) {
            ChatSummary old = list.getItems().get(idx);
            ChatSummary updated = new ChatSummary(
                    old.id(), old.kind(), old.title(),
                    preview != null ? preview : old.lastPreview(),
                    ts, unread, old.muted(), old.pinned()
            );
            list.getItems().set(idx, updated);
            list.getItems().sort(ORDER);
        } else {
            debounceTopRefresh();
        }
    }

    /* ===================== Pin toggle ===================== */

    private void onPinToggle(ChatSummary item) {
        final boolean target = !item.pinned();

        // Optimistic UI update
        int idx = indexOfChat(item.id());
        if (idx >= 0) {
            ChatSummary upd = new ChatSummary(
                    item.id(), item.kind(), item.title(),
                    item.lastPreview(), item.lastTs(), item.unread(), item.muted(), target
            );
            list.getItems().set(idx, upd);
            list.getItems().sort(ORDER);
        }

        chatApi.setPinned(item.id(), target).whenComplete((ok, err) -> {
            if (err != null || !Boolean.TRUE.equals(ok)) {
                // Revert on failure
                Platform.runLater(() -> {
                    int i = indexOfChat(item.id());
                    if (i >= 0) {
                        ChatSummary revert = new ChatSummary(
                                item.id(), item.kind(), item.title(),
                                item.lastPreview(), item.lastTs(), item.unread(), item.muted(), item.pinned()
                        );
                        list.getItems().set(i, revert);
                        list.getItems().sort(ORDER);
                    }
                });
            }
        });
    }

    /* ===================== Paging ===================== */

    private void refreshTop() {
        if (!loading.compareAndSet(false, true)) return;
        nextCursor = null;
        hasMore = false;

        chatApi.list(null, 20).whenComplete((res, err) -> Platform.runLater(() -> {
            loading.set(false);
            if (err != null || res == null) return;

            list.getItems().setAll(res.items());
            list.getItems().sort(ORDER);

            nextCursor = res.nextCursor();
            hasMore    = res.hasMore();
        }));
    }

    private void loadMore() {
        if (!hasMore) return;
        if (!loading.compareAndSet(false, true)) return;

        chatApi.list(nextCursor, 20).whenComplete((res, err) -> Platform.runLater(() -> {
            loading.set(false);
            if (err != null || res == null) return;

            list.getItems().addAll(res.items());
            list.getItems().sort(ORDER);

            nextCursor = res.nextCursor();
            hasMore    = res.hasMore();
        }));
    }

    private void hookInfiniteScroll() {
        list.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            Platform.runLater(() -> {
                ScrollBar vbar = (ScrollBar) list.lookup(".scroll-bar:vertical");
                if (vbar == null) return;

                if (vbarListener != null) vbar.valueProperty().removeListener(vbarListener);

                vbarListener = (o, ov, nv) -> {
                    if (nv.doubleValue() > 0.85 && hasMore && !loading.get()) {
                        loadMore();
                    }
                };
                vbar.valueProperty().addListener(vbarListener);
            });
        });
    }

    private void debounceTopRefresh() {
        if (pendingTopRefresh.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                pendingTopRefresh.set(false);
                refreshTop();
            });
        }
    }

    /* ===================== Helpers ===================== */

    private int indexOfChat(String chatId) {
        var items = list.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (chatId.equals(items.get(i).id())) return i;
        }
        return -1;
    }

    private static String previewFromMsg(JsonObject msg) {
        String kind = str(msg, "kind");
        if (kind == null) return null;
        switch (kind) {
            case "text" -> {
                String t = str(msg, "text");
                return t != null ? t : "";
            }
            case "image" -> {
                String cap = str(msg, "caption");
                return cap != null && !cap.isBlank() ? cap : "[Photo]";
            }
            case "video" -> {
                String cap = str(msg, "caption");
                return cap != null && !cap.isBlank() ? cap : "[Video]";
            }
            case "audio" -> { return "[Audio]"; }
            case "file"  -> {
                String name = str(msg, "name");
                return name != null ? "[File] " + name : "[File]";
            }
            default -> { return "[" + kind + "]"; }
        }
    }

    private static String str(JsonObject o, String k) {
        return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }
    private static long safeLong(com.google.gson.JsonElement e) {
        try { return e.getAsLong(); } catch (Exception ignore) { return 0L; }
    }
    private static int safeInt(com.google.gson.JsonElement e) {
        try { return e.getAsInt(); } catch (Exception ignore) { return 0; }
    }
}
