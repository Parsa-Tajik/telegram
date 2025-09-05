package com.telegram.telegrampromium.app;

import com.telegram.telegrampromium.api.AuthenticationAPI;
import com.telegram.telegrampromium.core.Client;
import com.telegram.telegrampromium.core.EventBus;
import com.telegram.telegrampromium.core.ResponseRouter;
import com.telegram.telegrampromium.nav.Navigator;

import java.io.IOException;
import java.util.Objects;

/**
 * Application context.
 * Holds shared services: theming, navigation, and the networking stack (Client/Router/EventBus).
 * API singletons are built on top of the networking primitives.
 */
public final class App {

    private Navigator navigator;
    private final ThemeManager themeManager = new ThemeManager(Theme.DARK);

    private EventBus eventBus;
    private ResponseRouter router;
    private Client client;

    private AuthenticationAPI authApi;

    public Navigator navigator() { return navigator; }
    public void setNavigator(Navigator n) { this.navigator = n; }

    public ThemeManager theme() { return themeManager; }
    public EventBus eventBus()  { return eventBus; }
    public ResponseRouter router() { return router; }
    public Client client() { return client; }

    public AuthenticationAPI auth() { return authApi; }

    /**
     * Initializes and connects the networking stack (TCP + NDJSON).
     *
     * @param host target host (e.g., "localhost")
     * @param port TCP port (e.g., 1688)
     * @throws IOException if the connection cannot be established
     */
    public void startNetworking(String host, int port) throws IOException {
        Objects.requireNonNull(host, "host");
        if (client != null) return;

        this.eventBus = new EventBus();
        this.router   = new ResponseRouter();
        this.client   = new Client(host, port, router, eventBus);

        client.connect();

        // Build API singletons once the transport is ready.
        this.authApi = new AuthenticationAPI(client, router);
    }

    /**
     * Closes the TCP client gracefully and clears API singletons.
     */
    public void stopNetworking() {
        Client c = this.client;
        this.client = null;
        this.authApi = null;
        if (c != null) {
            try { c.close(); } catch (IOException ignore) {}
        }
    }
}
