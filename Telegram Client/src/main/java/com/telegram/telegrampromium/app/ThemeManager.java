package com.telegram.telegrampromium.app;

import javafx.scene.Scene;
import java.net.URL;

public final class ThemeManager {
    private Theme current;

    public ThemeManager(Theme initial) { this.current = initial; }

    public Theme current() { return current; }

    public void apply(Scene scene, Theme theme) {
        if (scene == null) return;
        // حذف stylesheet های تم قبلی
        for (Theme t : Theme.values()) {
            URL url = getClass().getResource(t.css());
            if (url != null) scene.getStylesheets().remove(url.toExternalForm());
        }
        // افزودن تم جدید
        URL next = getClass().getResource(theme.css());
        if (next != null) scene.getStylesheets().add(next.toExternalForm());
        this.current = theme;
    }
}
