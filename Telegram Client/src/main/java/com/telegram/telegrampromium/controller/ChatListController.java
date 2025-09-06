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
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.Region;

import java.net.URL;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chat list with:
 *  - Initial load on show
 *  - Infinite scroll (auto "load more" near bottom, debounced)
 *  - Auto refresh on chat-related EVENTS from the server
 *
 * Buttons from Stage-3 (Refresh/Load more) حذف شده‌اند؛
 * اما رفتارشان به‌صورت خودکار و ایمن (با پرهیز از درخواست‌های تکراری) اعمال می‌شود.
 */
public final class ChatListController {

    private final App app;

    @FXML private Label titleLabel;
    @FXML private ListView<ChatSummary> list;

    private ChatAPI chatApi;
    private String nextCursor;
    private boolean hasMore;
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicBoolean pendingTopRefresh = new AtomicBoolean(false);

    // Keep a listener ref to avoid multiple registrations
    private ChangeListener<Number> vbarListener;

    public ChatListController(App app, com.telegram.telegrampromium.nav.Navigator nav) {
        this.app = Objects.requireNonNull(app);
    }

    @FXML
    private void initialize() {
        titleLabel.setText("Chats");
        chatApi = new ChatAPI(app.client());

        // FXML-based cell factory for consistent styling
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(ChatSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                try {
                    URL fx = getClass().getResource("/ui/fxml/cell_chat_summary.fxml");
                    FXMLLoader l = new FXMLLoader(fx);
                    Region root = l.load();
                    var c = l.getController();
                    if (c instanceof com.telegram.telegrampromium.controller.cell.ChatSummaryCellController cell) {
                        String when = Formats.friendlyTs(item.lastTs(), ZoneId.systemDefault());
                        cell.bind(item.title(), item.lastPreview(), when, item.unread(), item.kind(), item.muted());
                    }
                    setText(null);
                    setGraphic(root);
                } catch (Exception e) {
                    setText(item.title());
                    setGraphic(null);
                }
            }
        });

        // 1) Initial load
        refreshTop();

        // 2) Infinite scroll (auto load-more when reaching ~85% of the list)
        hookInfiniteScroll();

        // 3) Auto-refresh on events (generic: any CHAT_* or MESSAGE_* touches list)
        app.eventBus().subscribe(this::onEvent);
    }

    /* ---- Auto refresh on server events ---- */

    private void onEvent(JsonObject evt) {
        if (evt == null) return;
        String type = str(evt, "type");
        if (type == null) return;

        // Heuristic: if event is chat-related, refresh the top page (cheap, deterministic).
        if (type.startsWith("CHAT_") || type.startsWith("MESSAGE_")) {
            // Debounce multiple events in a short burst
            if (pendingTopRefresh.compareAndSet(false, true)) {
                Platform.runLater(() -> {
                    pendingTopRefresh.set(false);
                    refreshTop();
                });
            }
        }
    }

    /* ---- Paging ---- */

    private void refreshTop() {
        if (!loading.compareAndSet(false, true)) return;
        nextCursor = null;
        hasMore = false;
        chatApi.list(null, 20).whenComplete((res, err) -> Platform.runLater(() -> {
            loading.set(false);
            if (err != null || res == null) return;
            list.getItems().setAll(res.items());
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
            nextCursor = res.nextCursor();
            hasMore    = res.hasMore();
        }));
    }

    private void hookInfiniteScroll() {
        // Wait until skin exists and the vertical scrollbar is constructed.
        list.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            Platform.runLater(() -> {
                ScrollBar vbar = (ScrollBar) list.lookup(".scroll-bar:vertical");
                if (vbar == null) return;

                // Remove previous listener if any (defensive on scene reloads)
                if (vbarListener != null) vbar.valueProperty().removeListener(vbarListener);

                vbarListener = (o, ov, nv) -> {
                    // When scrolled past ~85%, try to fetch next page
                    if (nv.doubleValue() > 0.85 && hasMore && !loading.get()) {
                        loadMore();
                    }
                };
                vbar.valueProperty().addListener(vbarListener);
            });
        });
    }

    /* ---- helpers ---- */

    private static String str(JsonObject o, String k) {
        return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }
}
