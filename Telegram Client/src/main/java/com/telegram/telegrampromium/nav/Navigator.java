package com.telegram.telegrampromium.nav;

import com.telegram.telegrampromium.app.App;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Minimal navigator with history: start / push / replace / pop
 * Controllers are constructed via (App, Navigator) if available;
 * falls back to no-arg constructor otherwise.
 */
public final class Navigator {
    private final Stage stage;
    private final App app;
    private final Deque<View> history = new ArrayDeque<>();
    private View current;

    public Navigator(Stage stage, App app) {
        this.stage = stage;
        this.app = app;
    }

    public void start(View view) {
        history.clear();
        current = view;
        load(view);
    }

    public void replace(View view) {
        current = view;
        load(view);
    }

    public void push(View view) {
        if (current != null) history.push(current);
        current = view;
        load(view);
    }

    public void pop() {
        if (!history.isEmpty()) {
            current = history.pop();
            load(current);
        }
    }

    private void load(View view) {
        try {
            URL fxml = getClass().getResource(view.fxml());
            if (fxml == null) throw new IllegalStateException("FXML not found: " + view.fxml());

            FXMLLoader loader = new FXMLLoader(fxml);
            loader.setControllerFactory(type -> {
                try {
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
            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(root, 420, 720); // mobile-like aspect
                stage.setScene(scene);
                attachBaseCss(scene);
            } else {
                scene.setRoot(root);
            }

            if (view.title() != null && !view.title().isBlank()) {
                stage.setTitle("Telegram PROmium — " + view.title());
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load view: " + view, ex);
        }
    }

    private void attachBaseCss(Scene scene) {
        URL base = getClass().getResource("/ui/css/base.css");
        if (base != null) {
            String url = base.toExternalForm();
            if (!scene.getStylesheets().contains(url)) scene.getStylesheets().add(url);
        }
    }
}
