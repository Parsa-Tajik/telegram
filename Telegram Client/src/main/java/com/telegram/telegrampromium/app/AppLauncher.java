package com.telegram.telegrampromium.app;

import com.telegram.telegrampromium.nav.Navigator;
import com.telegram.telegrampromium.nav.View;
import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * JavaFX entry point. Boots UI, theming, and the networking stack.
 * Also installs lightweight console logging for inbound events and responses.
 */
public final class AppLauncher extends Application {

    @Override
    public void start(Stage stage) {
        App app = new App();
        Navigator nav = new Navigator(stage, app);
        app.setNavigator(nav);

        nav.start(View.LOGIN);

        stage.setTitle("Telegram PROmium");
        stage.show();

        app.theme().apply(stage.getScene(), app.theme().current());

        try {
            // Connect to local dev server on TCP 1688
            app.startNetworking("localhost", 1688);

            // Console logging: all EVENTS
            app.eventBus().subscribe(evt -> System.out.println("[EVENT] " + evt));

            // Console logging: all RESPONSES (after futures are completed)
            app.router().setOnComplete(resp -> System.out.println("[RESP ] " + resp));

        } catch (IOException e) {
            System.err.println("[Client] Failed to connect: " + e.getMessage());
        }

        stage.setOnCloseRequest(ev -> app.stopNetworking());
    }

    public static void main(String[] args) { launch(args); }
}
