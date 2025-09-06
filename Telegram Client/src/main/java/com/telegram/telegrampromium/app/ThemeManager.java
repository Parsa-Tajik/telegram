package com.telegram.telegrampromium.app;

import javafx.scene.Scene;

import java.net.URL;

/**
 * Applies/removes theme stylesheets while keeping base.css attached.
 * Keep it minimal; full theming will expand later.
 */
public final class ThemeManager {
    private Theme current;

    public ThemeManager(Theme initial) { this.current = initial; }

    public Theme current() { return current; }

    public void apply(Scene scene, Theme theme) {
        if (scene == null) return;

        // Remove all known theme stylesheets
        for (Theme t : Theme.values()) {
            URL url = getClass().getResource(t.css());
            if (url != null) scene.getStylesheets().remove(url.toExternalForm());
        }

        // Add selected theme
        URL next = getClass().getResource(theme.css());
        if (next != null) scene.getStylesheets().add(next.toExternalForm());

        this.current = theme;
    }
}
