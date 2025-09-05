package com.telegram.telegrampromium.nav;

/** Declares views: FXML path + title used for the window's title bar. */
public enum View {
    LOGIN    ("/ui/fxml/login.fxml",     "Login"),
    SIGN_UP  ("/ui/fxml/sign_up.fxml",   "Sign Up"),
    CHAT_LIST("/ui/fxml/chat_list.fxml", "Chats");

    private final String fxmlPath;
    private final String title;

    View(String fxmlPath, String title) {
        this.fxmlPath = fxmlPath;
        this.title = title;
    }

    public String fxml()  { return fxmlPath; }
    public String title() { return title; }
}
