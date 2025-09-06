package com.telegram.telegrampromium.app;

import javafx.scene.Scene;

import java.net.URL;

/**
 * Applies light/dark theme. Order matters:
 * 1) theme_*.css defines tokens (e.g. -color-bg)
 * 2) base.css consumes tokens
 */
public final class ThemeManager {

    private Theme current;

    public ThemeManager(Theme initial) { this.current = initial; }

    public Theme current() { return current; }

    public void apply(Scene scene, Theme theme) {
        this.current = theme;
        var sheets = scene.getStylesheets();
        sheets.clear();
        sheets.add(resource(theme == Theme.DARK ? "/ui/css/theme_dark.css" : "/ui/css/theme_light.css"));
        sheets.add(resource("/ui/css/base.css"));
    }

    private static String resource(String path) {
        URL u = ThemeManager.class.getResource(path);
        if (u == null) throw new IllegalStateException("CSS not found: " + path);
        return u.toExternalForm();
    }
}
