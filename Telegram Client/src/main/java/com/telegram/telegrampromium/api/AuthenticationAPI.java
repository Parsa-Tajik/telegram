package com.telegram.telegrampromium.api;

import com.google.gson.JsonObject;
import com.telegram.telegrampromium.core.Client;
import com.telegram.telegrampromium.core.Ids;
import com.telegram.telegrampromium.core.ResponseRouter;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Authentication facade over NDJSON transport.
 * Provides REGISTER and LOGIN with single-field PHC password ("pwd").
 */
public final class AuthenticationAPI {

    private final Client client;
    private final ResponseRouter router;

    public AuthenticationAPI(Client client, ResponseRouter router) {
        this.client = Objects.requireNonNull(client, "client");
        this.router = Objects.requireNonNull(router, "router");
    }

    /**
     * Sends a REGISTER request.
     *
     * @param displayName user-facing name
     * @param phone       phone number (format enforced server-side)
     * @param username    unique handle
     * @param pwdPhc      PHC string from client-side hasher
     * @return future with server response object
     */
    public CompletableFuture<JsonObject> register(String displayName, String phone, String username, String pwdPhc) {
        JsonObject req = new JsonObject();
        req.addProperty("type", "REGISTER");
        req.addProperty("id", Ids.req("reg"));
        req.addProperty("displayName", displayName);
        req.addProperty("phone", phone);
        req.addProperty("username", username);
        req.addProperty("pwd", pwdPhc);

        return client.request(req).orTimeout(15, TimeUnit.SECONDS);
    }

    /**
     * Sends a LOGIN request.
     *
     * @param username user handle
     * @param pwdPhc   PHC string
     * @return future with server response object
     */
    public CompletableFuture<JsonObject> login(String username, String pwdPhc) {
        JsonObject req = new JsonObject();
        req.addProperty("type", "LOGIN");
        req.addProperty("id", Ids.req("login"));
        req.addProperty("username", username);
        req.addProperty("pwd", pwdPhc);

        return client.request(req).orTimeout(15, TimeUnit.SECONDS);
    }
}
