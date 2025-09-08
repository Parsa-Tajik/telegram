package com.telegram.telegrampromium.api;

import com.google.gson.JsonObject;
import com.telegram.telegrampromium.core.Client;
import com.telegram.telegrampromium.core.Ids;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import java.util.ArrayList;
import java.util.List;

public class ContactsAPI {
    private final Client client;

    public ContactsAPI(Client client) {
        this.client = Objects.requireNonNull(client);
    }

    public static final class ContactBrief {
        public final String userId;
        public final String displayName;
        public final String username;       // ممکنه null باشه
        public final String avatarUrl;      // ممکنه null باشه
        public final Long   lastSeen;       // ms (اختیاری)
        public final String lastSeenText;   // مثل "لحظاتی پیش" (اختیاری)
        public ContactBrief(String userId, String displayName, String username, String avatarUrl,
                            Long lastSeen, String lastSeenText) {
            this.userId = userId;
            this.displayName = displayName;
            this.username = username;
            this.avatarUrl = avatarUrl;
            this.lastSeen = lastSeen;
            this.lastSeenText = lastSeenText;
        }
    }

    /** دریافت یک‌بارهٔ لیست مخاطبین (بدون cursor) برای پُر کردن صفحهٔ New Chat */
    public CompletableFuture<List<ContactBrief>> listContacts() {
        String reqId = Ids.req("contacts_list");
        JsonObject req = new JsonObject();
        req.addProperty("type", "REQ");
        req.addProperty("cmd",  "contacts_list");
        req.addProperty("id",   reqId);

        return client.request(req).orTimeout(10, TimeUnit.SECONDS)
                .thenApply(resp -> {
                    String t = str(resp, "type");
                    if (!"CONTACTS_LIST_OK".equalsIgnoreCase(t)) {
                        throw new IllegalStateException("Unexpected response: " + t);
                    }
                    var arr = resp.getAsJsonArray("contacts");
                    List<ContactBrief> out = new ArrayList<>();
                    if (arr != null) {
                        arr.forEach(el -> {
                            JsonObject c = el.getAsJsonObject();
                            String userId   = str(c, "userId");
                            String name     = str(c, "displayName");
                            String username = str(c, "username");
                            String avatar   = str(c, "avatarUrl");
                            Long   lastSeen = c.has("lastSeen") && !c.get("lastSeen").isJsonNull()
                                    ? c.get("lastSeen").getAsLong() : null;
                            String lastTxt  = str(c, "lastSeen_text");
                            out.add(new ContactBrief(userId, name, username, avatar, lastSeen, lastTxt));
                        });
                    }
                    return out;
                });
    }


    // نتیجه‌ی افزودن مخاطب
    public static final class AddContactResult {
        public enum Status { OK, ALREADY_EXISTS, NOT_REGISTERED, ERROR }
        public final Status status;
        public final String userId;     // اگر سرور map کرد
        public final String message;    // متن خطا/توضیح (اختیاری)
        public AddContactResult(Status s, String userId, String message) {
            this.status = s; this.userId = userId; this.message = message;
        }
        public static AddContactResult ok(String userId) { return new AddContactResult(Status.OK, userId, null); }
        public static AddContactResult exists(String userId) { return new AddContactResult(Status.ALREADY_EXISTS, userId, "Already exists"); }
        public static AddContactResult notReg(String msg) { return new AddContactResult(Status.NOT_REGISTERED, null, msg); }
        public static AddContactResult err(String msg) { return new AddContactResult(Status.ERROR, null, msg); }
    }

    /** افزودن مخاطب با نام نمایشی و شماره (E.164 ترجیحاً). */
    public CompletableFuture<AddContactResult> addContact(String displayName, String phone) {
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(phone);

        String reqId = Ids.req("contacts_add");
        JsonObject req = new JsonObject();
        req.addProperty("type", "REQ");
        req.addProperty("cmd",  "contacts_add");
        req.addProperty("id",   reqId);

        JsonObject c = new JsonObject();
        c.addProperty("name",  displayName);
        c.addProperty("phone", phone);
        req.add("contact", c);

        return client.request(req).orTimeout(10, TimeUnit.SECONDS)
                .thenApply(resp -> {
                    String type = str(resp, "type");
                    // حالت موفق
                    if ("CONTACTS_ADD_OK".equalsIgnoreCase(type)) {
                        String userId = str(resp, "userId");
                        return AddContactResult.ok(userId);
                    }
                    // اگر بک‌اند ERROR با code برگرداند
                    if ("ERROR".equalsIgnoreCase(type) || "CONTACTS_ADD_FAIL".equalsIgnoreCase(type)) {
                        String code = str(resp, "code");
                        String msg  = str(resp, "message");
                        if ("CONTACT_ALREADY_EXISTS".equalsIgnoreCase(code)) {
                            return AddContactResult.exists(str(resp, "userId"));
                        } else if ("PHONE_NOT_REGISTERED".equalsIgnoreCase(code)) {
                            return AddContactResult.notReg(msg != null ? msg : "Phone not registered");
                        } else {
                            return AddContactResult.err(msg != null ? msg : code);
                        }
                    }
                    return AddContactResult.err("Unexpected response: " + type);
                });
    }

    private static String str(JsonObject o, String k) {
        return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }
}
