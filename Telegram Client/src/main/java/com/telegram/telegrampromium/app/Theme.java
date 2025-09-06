package com.telegram.telegrampromium.app;

/** Theme descriptor with CSS resource per theme. */
public enum Theme {
    LIGHT("/ui/css/theme_light.css"),
    DARK ("/ui/css/theme_dark.css");

    private final String cssPath;
    Theme(String cssPath) { this.cssPath = cssPath; }
    public String css() { return cssPath; }
}
