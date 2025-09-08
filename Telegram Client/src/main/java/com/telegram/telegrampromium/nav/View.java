package com.telegram.telegrampromium.nav;

/** Declares views: FXML path + title used for the window's title bar. */
public enum View {
    LOGIN     ("/ui/fxml/login.fxml",     "Login"),
    SIGN_UP   ("/ui/fxml/sign_up.fxml",   "Sign Up"),
    CHAT_LIST ("/ui/fxml/chat_list.fxml", "Chats"),
    CHAT      ("/ui/fxml/chat.fxml",      "Chat"),
    NEW_CHAT  ("/ui/fxml/new_chat.fxml",  "New Chat"),
    ADD_CONTACT ("/ui/fxml/add_contact.fxml", "Add Contact"),
    PROFILE   ("/ui/fxml/profile.fxml",   "Profile");


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
