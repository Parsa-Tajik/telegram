package com.telegram.telegrampromium.nav;

import com.telegram.telegrampromium.app.App;
import com.telegram.telegrampromium.controller.ChatController;
import com.telegram.telegrampromium.model.ChatSummary;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Navigator with parameterized back-stack.
 * - Public API kept: start/replace/push/pop (+ back alias)
 * - New high-level helpers: openChatList(), openChat(chatId, kind, title)
 * - Controllers are constructed via (App, Navigator) when available.
 * - Single Scene; we only swap the root to preserve theme/stage config.
 */
public final class Navigator {

    private final Stage stage;
    private final App app;

    /** Back-stack entry with optional parameters (e.g., chat context). */
    private static final class NavEntry {
        final View view;
        final String chatId;
        final ChatSummary.Kind kind;
        final String title; // chat title or view.title()

        NavEntry(View view) {
            this(view, null, null, null);
        }
        NavEntry(View view, String chatId, ChatSummary.Kind kind, String title) {
            this.view = view;
            this.chatId = chatId;
            this.kind = kind;
            this.title = title;
        }
    }

    private final Deque<NavEntry> history = new ArrayDeque<>();
    private NavEntry current;

    public Navigator(Stage stage, App app) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.app   = Objects.requireNonNull(app, "app");
    }

    /* ---------------- Public API (backward compatible) ---------------- */

    public void start(View view) {
        history.clear();
        current = new NavEntry(view);
        show(current);
    }

    public void replace(View view) {
        current = new NavEntry(view);
        show(current);
    }

    public void push(View view) {
        if (current != null) history.push(current);
        current = new NavEntry(view);
        show(current);
    }

    /** Pop previous entry and show it. */
    public void pop() {
        if (!history.isEmpty()) {
            current = history.pop();
            show(current);
        }
    }

    /** Alias (needed by ChatController and more intuitive). */
    public void back() { pop(); }

    /* ---------------- High-level helpers ---------------- */

    /** Go to chat list and push current. */
    public void openChatList() {
        if (current != null) history.push(current);
        current = new NavEntry(View.CHAT_LIST);
        show(current);
    }

    /** Go to specific chat and push current. */
    public void openChat(String chatId, ChatSummary.Kind kind, String title) {
        if (current != null) history.push(current);
        current = new NavEntry(View.CHAT, chatId, kind, title);
        show(current);
    }

    /* ---------------- Internals ---------------- */

    private void show(NavEntry entry) {
        try {
            URL fxml = getClass().getResource(entry.view.fxml());
            if (fxml == null) throw new IllegalStateException("FXML not found: " + entry.view.fxml());

            FXMLLoader loader = new FXMLLoader(fxml);
            loader.setControllerFactory(type -> {
                try {
                    // Prefer (App, Navigator) constructor
                    for (Constructor<?> c : type.getConstructors()) {
                        Class<?>[] p = c.getParameterTypes();
                        if (p.length == 2 && p[0] == App.class && p[1] == Navigator.class) {
                            return c.newInstance(app, this);
                        }
                    }
                    return type.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create controller: " + type, e);
                }
            });

            Parent root = loader.load();

            // If it's the chat view and controller is ChatController, pass context
            if (entry.view == View.CHAT) {
                Object ctl = loader.getController();
                if (ctl instanceof ChatController cc) {
                    cc.setChatContext(entry.chatId, entry.kind, entry.title);
                }
            }

            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(root, 420, 720); // mobile-like aspect
                stage.setScene(scene);
                attachBaseCss(scene); // keep base.css attached once
            } else {
                scene.setRoot(root);
            }

            // Title: prefer explicit chat title if provided; fallback to view.title()
            String ttl = (entry.title != null && !entry.title.isBlank()) ? entry.title : entry.view.title();
            if (ttl != null && !ttl.isBlank()) {
                stage.setTitle("Telegram PROmium — " + ttl);
            }

        } catch (Exception ex) {
            throw new RuntimeException("Failed to load view: " + entry.view, ex);
        }
    }

    private void attachBaseCss(Scene scene) {
        URL base = getClass().getResource("/ui/css/base.css");
        if (base != null) {
            String url = base.toExternalForm();
            if (!scene.getStylesheets().contains(url)) {
                scene.getStylesheets().add(url);
            }
        }
    }
}
