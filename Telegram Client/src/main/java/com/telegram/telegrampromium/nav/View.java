package com.telegram.telegrampromium.nav;

/** Declares views: FXML path + title used for the window's title bar. */
public enum View {
    LOGIN     ("/ui/fxml/login.fxml",     "Login"),
    SIGN_UP   ("/ui/fxml/sign_up.fxml",   "Sign Up"),
    CHAT_LIST ("/ui/fxml/chat_list.fxml", "Chats"),
    CHAT      ("/ui/fxml/chat.fxml",      "Chat");   // <-- add this

    private final String fxmlPath;
    private final String title;

    View(String fxmlPath, String title) {
        this.fxmlPath = fxmlPath;
        this.title = title;
    }

    /** Used by Navigator to load FXML */
    public String fxml()  { return fxmlPath; }

    /** Used by Navigator to set window title (fallback when no per-screen title) */
    public String title() { return title; }
}
