package com.telegram.telegrampromium.controller;

import com.google.gson.JsonObject;
import com.telegram.telegrampromium.api.ChatAPI;
import com.telegram.telegrampromium.app.App;
import com.telegram.telegrampromium.core.Ids;
import com.telegram.telegrampromium.model.ChatSummary;
import com.telegram.telegrampromium.model.Message;
import com.telegram.telegrampromium.model.MessageKind;
import com.telegram.telegrampromium.model.MessageStatus;
import com.telegram.telegrampromium.nav.Navigator;
import com.telegram.telegrampromium.util.Formats;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import com.google.gson.JsonElement;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import javafx.scene.control.ScrollPane;

import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.MouseEvent;
import javafx.collections.ListChangeListener;
import javafx.beans.value.ChangeListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;


/**
 * Chat screen skeleton: header + messages list + composer.
 * No history loading or send logic here yet (will be added in 4.2/4.3).
 */
public final class ChatController {

    // ----- injected by FXMLLoader (keep fx:id names as before)
    @FXML private Button backBtn;
    @FXML private Button searchBtn;
    @FXML private Label  headerTitle;
    @FXML private Label  headerSubtitle;
    @FXML private StackPane headerAvatar;
    @FXML private ListView<Object> messagesList;
    @FXML private TextArea messageInput;
    @FXML private Button   sendBtn;

    @FXML private HBox  replyBox;
    @FXML private Label replyTitle;
    @FXML private Label replySnippet;
    @FXML private Button replyClose;

    // ----- wiring
    private final App app;
    private final Navigator nav;
    private final ChatAPI chatApi;

    // ----- state
    private String chatId;
    private ChatSummary.Kind chatKind;
    private String chatTitle;
    private Message replyTarget;

    // items = messages + date separators
    private final ObservableList<Object> items = FXCollections.observableArrayList();

    private final Map<String,String> reqIdToMsgId = new ConcurrentHashMap<>(); // reqId -> (tempId یا serverId)

    private Consumer<JsonObject> eventHandler;
    private boolean atBottom = true;
    private int newCount = 0;
    @FXML private Label newChip;

    // paging
    private String  cursorNext = null;
    private boolean hasMore    = false;
    private boolean loading    = false;

    // events
    private Consumer<JsonObject> evtListener;
    // read-dedup: prevent duplicate CHAT_READ for same last message
    private String lastReadUptoId = null;

    // --- constructors for Navigator's ControllerFactory (App, Navigator) OR default
    public ChatController(App app, Navigator nav) {
        this.app = app; this.nav = nav;
        this.chatApi = new ChatAPI(app.client());
    }
    public ChatController() { this.app = null; this.nav = null; this.chatApi = null; }

    // ====== INIT (replace your current init body with this one; signature unchanged) ======
    public void init(String chatId, ChatSummary.Kind kind, String title) {
        this.chatId   = chatId;
        this.chatKind = kind;
        this.chatTitle= title;
        this.lastReadUptoId = null;

        headerTitle.setText(title != null ? title : "");
        headerSubtitle.setText(" "); // presence later
        headerAvatar.getChildren().setAll(buildAvatarNode(title));

        // list wiring
        messagesList.setItems(items);
        messagesList.setPlaceholder(new Label("No messages yet"));
        messagesList.setCellFactory(v -> new MsgCell());

        // scroll-top => load older
        installTopScrollLoader();

        // composer wiring
        sendBtn.setDisable(true);
        messageInput.textProperty().addListener((o,ov,nv) -> sendBtn.setDisable(nv == null || nv.isBlank()));
        sendBtn.setOnAction(e -> onSend());
        messageInput.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER -> {
                    if (e.isControlDown() || e.isMetaDown()) onSend();
                }
            }
        });

        if (backBtn != null) backBtn.setOnAction(e -> { if (nav != null){unsubscribeEvents(); nav.back();} });

        // subscribe to events
        if (app != null) {
            evtListener = evt -> {
                if (!"EVENT".equalsIgnoreCase(s(evt,"type"))) return;
                String ev = s(evt,"event"); if (ev == null) return;
                switch (ev.toLowerCase()) {
                    case "message_new" -> {
                        String cid = s(evt, "chatId");
                        if (cid == null) cid = s(evt, "chat_id");
                        if (!java.util.Objects.equals(cid, this.chatId)) break;

                        com.google.gson.JsonObject m = evt.has("msg")
                                ? evt.getAsJsonObject("msg")
                                : (evt.has("message") ? evt.getAsJsonObject("message") : null);
                        if (m == null) break;

                        Message incoming = new Message(
                                s(m, "id"),
                                cid,
                                s(m, "from"),
                                safeLong(m.get("ts")),
                                MessageKind.TEXT,
                                s(m, "text"),
                                /*outgoing*/ false,
                                MessageStatus.SENT
                        );

                        javafx.application.Platform.runLater(() -> {
                            appendWithDate(incoming);
                            autoScrollIfNearBottom();
                        });
                    }
                    case "message_status" -> {
                        String cid = s(evt, "chatId");
                        if (cid == null) cid = s(evt, "chat_id");
                        if (!java.util.Objects.equals(cid, this.chatId)) break;

                        String mid = s(evt, "messageId");
                        if (mid == null) mid = s(evt, "message_id");
                        String st  = s(evt, "status");
                        if (mid == null || st == null) break;

                        // 1) مستقیم با همینی که سرور فرستاده امتحان کن
                        int idx = indexOfMessageId(mid);
                        String targetId = mid;

                        // 2) اگر نبود، شاید هنوز tempId باشد: tmp-<reqId>
                        if (idx < 0) {
                            String tmpId = "tmp-" + mid;
                            idx = indexOfMessageId(tmpId);
                            if (idx >= 0) targetId = tmpId;
                        }

                        // 3) اگر باز هم نبود، از نگاشت reqId -> (tempId/serverId) استفاده کن
                        if (idx < 0) {
                            String mapped = reqIdToMsgId.get(mid);
                            if (mapped != null) {
                                idx = indexOfMessageId(mapped);
                                if (idx >= 0) targetId = mapped;
                            }
                        }

                        if (idx < 0) return; // پیامی با این id توی لیست نیست

                        MessageStatus ms = switch (st.toUpperCase()) {
                            case "DELIVERED" -> MessageStatus.DELIVERED;
                            case "READ"      -> MessageStatus.READ;
                            default -> null;
                        };
                        if (ms == null) return;

                        // فقط پیام‌های خودم باید تیک بگیرند
                        Object it = items.get(idx);
                        if (it instanceof Message m && m.outgoing()) {
                            Message up = new Message(m.id(), m.chatId(), m.from(), m.ts(), m.kind(), m.text(), true, ms);
                            items.set(idx, up);
                        }
                    }
                    case "message_deleted" -> {
                        String cid = s(evt,"chatId"); if (!Objects.equals(cid, this.chatId)) return;
                        String mid = s(evt,"messageId"); if (mid == null) return;
                        javafx.application.Platform.runLater(() -> {
                            int idx = indexOfMessageId(mid);
                            if (idx >= 0) items.remove(idx);
                        });
                    }
                    default -> {}
                }
            };
            app.eventBus().subscribe(evtListener);
        }

        // load first page
        loadInitialHistory();
    }

    // ===== history load =====
    private void loadInitialHistory() {
        if (chatApi == null) return;
        loading = true;
        chatApi.history(chatId, null, 30).whenComplete((page, err) -> {
            Platform.runLater(() -> {
                loading = false;
                if (err != null || page == null) return;
                // we got newest -> oldest; show as date-separated in natural order
                LocalDate last = null;
                for (Message m : page.messages) {
                    last = maybeAppendDate(last, m.ts());
                    items.add(m);
                }
                cursorNext = page.cursorNext;
                hasMore = page.hasMore;
                scrollToBottom();
                tryMarkRead(null);
            });
        });
    }

    // خواندن ایمن رشته از JsonObject
    private static String str(JsonObject obj, String key) {
        if (obj == null || key == null) return null;
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        try {
            return obj.get(key).getAsString();
        } catch (Exception ignore) {
            return String.valueOf(obj.get(key));
        }
    }

    // خواندن ایمن زمان/عدد (long) از JsonElement
    private static long safeLong(JsonElement el) {
        if (el == null || el.isJsonNull()) return 0L;
        try {
            return el.getAsLong();
        } catch (Exception e) {
            try {
                return (long) el.getAsDouble();
            } catch (Exception e2) {
                return 0L;
            }
        }
    }

    private void loadOlderIfNeeded() {
        if (!hasMore || loading || chatApi == null) return;
        loading = true;
        final int oldSize = items.size();
        chatApi.history(chatId, cursorNext, 30).whenComplete((page, err) -> {
            Platform.runLater(() -> {
                loading = false;
                if (err != null || page == null) return;

                // prepend keeping visual position roughly stable
                int added = 0;
                LocalDate beforeTop = null;
                if (!items.isEmpty() && items.get(0) instanceof Message topMsg) {
                    beforeTop = toLocal(topMsg.ts());
                }
                // insert older page at beginning (older -> newer order)
                for (int i = page.messages.size() - 1; i >= 0; i--) {
                    Message m = page.messages.get(i);
                    LocalDate d = toLocal(m.ts());
                    if (items.isEmpty() || !(items.get(0) instanceof DateSep) || !toLocalOfSep(items.get(0)).equals(d)) {
                        items.add(0, new DateSep(d));
                        added++;
                    }
                    items.add(0, m);
                    added++;
                }
                cursorNext = page.cursorNext;
                hasMore = page.hasMore;

                // keep approximate position
                messagesList.scrollTo(added);
            });
        });
    }

    // ===== send =====
    @FXML
    private void onSend() {
        String text = messageInput.getText();
        if (text == null || text.isBlank() || chatApi == null) return;

        // 1) tempId برای local-echo
        String reqId = Ids.req("msg");
        String tempId = "tmp-" + reqId;

        reqIdToMsgId.put(reqId, tempId);

        // 2) پیام محلی با وضعیت SENDING
        Message local = new Message(
                tempId,                      // id موقت
                chatId,
                "me",
                System.currentTimeMillis(),
                MessageKind.TEXT,
                text,
                /* outgoing */ true,
                MessageStatus.SENDING
        );
        items.add(local);
        scrollToBottom();
        messageInput.clear();

        // 3) ارسال واقعی با عبور tempId جهت replace دقیق
        // 3) ارسال واقعی (معمولی / ریپلای)
        String replyToId = (replyTarget != null ? replyTarget.id() : null);
        if (replyToId != null && !replyToId.isBlank()) {
            chatApi.sendTextWithReply(chatId, text, tempId, replyToId)
                    .thenAccept(res -> replaceTempWithServer(res.clientTempId, res.messageId, res.ts))
                    .exceptionally(err -> { markTempFailed(tempId); return null; });
        } else {
            chatApi.sendText(chatId, text, tempId)
                    .thenAccept(res -> replaceTempWithServer(res.clientTempId, res.messageId, res.ts))
                    .exceptionally(err -> { markTempFailed(tempId); return null; });
        }
        clearReplyTarget();
    }

    private int indexOfMessageId(String id) {
        for (int i = 0; i < items.size(); i++) {
            Object it = items.get(i);
            if (it instanceof Message m && id.equals(m.id())) {
                return i;
            }
        }
        return -1;
    }

    /** جایگزینی پیام temp با نسخهٔ رسمی سرور و وضعیت SENT (بدون افزودن آیتم جدید). */
    private void replaceTempWithServer(String tempId, String serverId, long ts) {
        Platform.runLater(() -> {
            int idx = indexOfMessageId(tempId);
            if (idx < 0) return;
            Message old = (Message) items.get(idx);

            // ❗ اگر serverId نداشتیم، همون tempId رو نگه داریم (id=null نشه)
            String newId = (serverId != null && !serverId.isBlank()) ? serverId : old.id();

            Message upgraded = new Message(
                    newId,
                    old.chatId(),
                    old.from(),
                    ts > 0 ? ts : old.ts(),
                    old.kind(),
                    old.text(),
                    true,
                    MessageStatus.SENT
            );

            // فقط اگر serverId واقعی گرفتیم، نگاشت reqId→serverId را به‌روزرسانی کن
            if (tempId != null && tempId.startsWith("tmp-") && serverId != null && !serverId.isBlank()) {
                String reqId = tempId.substring(4);
                reqIdToMsgId.put(reqId, serverId);
            }
            items.set(idx, upgraded);
        });
    }


    /** تغییر وضعیت پیام موقت به FAILED (برای نمایش Retry). */
    private void markTempFailed(String tempId) {
        Platform.runLater(() -> {
            int idx = indexOfMessageId(tempId);
            if (idx < 0) return;
            Message old = (Message) items.get(idx);
            Message failed = new Message(
                    old.id(), old.chatId(), old.from(), old.ts(),
                    old.kind(), old.text(), true, MessageStatus.FAILED
            );
            items.set(idx, failed);
        });
    }

    /** Retry: همان آیتم FAILED را به SENDING برگردان و دوباره بفرست (با tempId جدید). */
    private void retrySend(Message failed) {
        if (chatApi == null) return;
        // همان آیتم را به SENDING تبدیل کن (درجا)
        for (int i = items.size() - 1; i >= 0; i--) {
            Object it = items.get(i);
            if (it instanceof Message m && m.id().equals(failed.id())) {
                items.set(i, new Message(
                        m.id(), m.chatId(), m.from(),
                        System.currentTimeMillis() / 1000,
                        m.kind(), m.text(), m.outgoing(),
                        MessageStatus.SENDING
                ));
                break;
            }
        }
        // ارسال مجدد با همان متن
        chatApi.sendText(failed.chatId(), failed.text()).whenComplete((res, err) -> {
            Platform.runLater(() -> {
                if (err != null || res == null) {
                    // اگر نشد، همون «آخرین SENDING» رو FAILED کن (الگوی فعلی کدت)
                    markLastLocalFailed();
                    return;
                }
                // الگوی فعلی کدت: «آخرین SENDING» را به SENT ارتقا بده
                promoteLastLocal(res.messageId, res.ts);
            });
        });
    }

    // ===== items & cells =====
    private static final class DateSep {
        final LocalDate date;
        DateSep(LocalDate d){ this.date = d; }
    }
    private static LocalDate toLocal(long epochSec) {
        return Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()).toLocalDate();
    }
    private static LocalDate toLocalOfSep(Object o){
        return (o instanceof DateSep ds) ? ds.date : null;
    }

    /** Insert date separator if date changed; returns lastDate. */
    private LocalDate maybeAppendDate(LocalDate last, long ts) {
        LocalDate d = toLocal(ts);
        if (last == null || !last.equals(d)) {
            items.add(new DateSep(d));
            return d;
        }
        return last;
    }

    private void appendWithDate(Message m) {
        // ensure date separator before message if needed
        LocalDate last = null;
        if (!items.isEmpty()) {
            for (int i = items.size()-1; i >= 0; i--) {
                Object it = items.get(i);
                if (it instanceof Message mm) { last = toLocal(mm.ts()); break; }
                if (it instanceof DateSep ds) { last = ds.date; break; }
            }
        }
        LocalDate needed = toLocal(m.ts());
        if (last == null || !last.equals(needed)) {
            items.add(new DateSep(needed));
        }
        items.add(m);
    }

    private void markLastLocalFailed() {
        for (int i = items.size()-1; i >= 0; i--) {
            Object it = items.get(i);
            if (it instanceof Message m && m.status() == MessageStatus.SENDING) {
                items.set(i, new Message(m.id(), m.chatId(), m.from(), m.ts(), m.kind(), m.text(), m.outgoing(), MessageStatus.FAILED));
                break;
            }
        }
    }
    private void promoteLastLocal(String serverId, long ts) {
        for (int i = items.size()-1; i >= 0; i--) {
            Object it = items.get(i);
            if (it instanceof Message m && m.status() == MessageStatus.SENDING) {
                items.set(i, new Message(serverId, m.chatId(), m.from(), (ts>0?ts:m.ts()), m.kind(), m.text(), m.outgoing(), MessageStatus.SENT));
                break;
            }
        }
    }

    // Custom cell: minimal bubble layout honoring .bubble-in / .bubble-out
    private final class MsgCell extends ListCell<Object> {
        private final HBox root = new HBox();
        private final Label bubble = new Label();
        private final Label date  = new Label();

        MsgCell() {
            root.setSpacing(6);
            HBox.setHgrow(bubble, Priority.NEVER);
            bubble.setWrapText(true);
            bubble.setMaxWidth(280);
            root.getChildren().addAll(bubble);
        }

        @Override protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            if (item instanceof DateSep ds) {
                Label sep = new Label(ds.date.toString());
                sep.getStyleClass().setAll("date-sep");
                HBox box = new HBox(sep);
                box.setAlignment(Pos.CENTER);
                setGraphic(box);
                return;
            }
            if (item instanceof Message m) {
                String bubbleClass = m.outgoing() ? "bubble-out" : "bubble-in";

                Text textNode = new Text(m.text());
                TextFlow content = new TextFlow(textNode);
                content.getStyleClass().setAll(bubbleClass);

                // پیچش متن داخل بابل
                textNode.wrappingWidthProperty().bind(
                        Bindings.createDoubleBinding(
                                () -> Math.max(160, getListView().getWidth() * 0.72 - 24),
                                getListView().widthProperty()
                        )
                );

                // حداکثر عرض بابل ≈ 72% از عرض لیست (منهای padding)
                DoubleBinding maxBubbleWidth = Bindings.createDoubleBinding(
                        () -> Math.max(160, getListView().getWidth() * 0.72 - 24), // 24≈ حاشیه‌ها
                        getListView().widthProperty()
                );
                content.maxWidthProperty().bind(maxBubbleWidth);

                // --- متادیتا: ساعت + (فقط برای outgoing) آیکن وضعیت ---
                HBox meta = new HBox(8);
                meta.setAlignment(m.outgoing() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                Label time = new Label(Formats.friendlyTs(m.ts() * 1000L, ZoneId.systemDefault()));
                meta.getChildren().add(time);

                // ⬅️ فقط پیام‌های خودم تیک داشته باشند
                if (m.outgoing()) {
                    Label statusIcon = new Label(statusGlyph(m.status()));
                    meta.getChildren().add(statusIcon);
                }

                // بدنهٔ نهایی: بابل + متادیتا (+ Retry برای FAILED outgoing)
                VBox box = new VBox(4, content, meta);

                if (m.outgoing() && m.status() == MessageStatus.FAILED) {
                    Hyperlink retry = new Hyperlink("Retry");
                    retry.setOnAction(e -> retrySend(m));
                    HBox retryBox = new HBox(retry);
                    retryBox.setAlignment(Pos.CENTER_RIGHT);
                    box.getChildren().add(retryBox);
                }

                // جهت‌گیری چپ/راست کل ردیف
                HBox row = new HBox(box);
                row.setAlignment(m.outgoing() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                row.maxWidthProperty().bind(getListView().widthProperty().subtract(12));

                // ===== Context Menu =====
                ContextMenu menu = new ContextMenu();
                MenuItem miCopy = new MenuItem("Copy text");
                miCopy.setOnAction(ev -> {
                    ClipboardContent cc = new ClipboardContent();
                    cc.putString(m.text() != null ? m.text() : "");
                    Clipboard.getSystemClipboard().setContent(cc);
                });
                MenuItem miReply = new MenuItem("Reply");
                miReply.setOnAction(ev -> setReplyTarget(m));
                MenuItem miDelMe = new MenuItem("Delete for me");
                miDelMe.setOnAction(ev -> deleteForMe(m));
                MenuItem miDelAll = new MenuItem("Delete for everyone");
                miDelAll.setDisable(!m.outgoing()); // فقط پیام‌های خودم
                miDelAll.setOnAction(ev -> deleteForAll(m));
                menu.getItems().setAll(miCopy, miReply, new SeparatorMenuItem(), miDelMe, miDelAll);
                setContextMenu(menu);

                setGraphic(row);
                setText(null);
                return;
            }

            setGraphic(null);
        }
    }

    // ===== events / helpers =====
    private void installTopScrollLoader() {
        messagesList.skinProperty().addListener((obs, old, skin) -> {
            Platform.runLater(() -> {
                for (Node n : messagesList.lookupAll(".scroll-bar")) {
                    if (n instanceof ScrollBar sb && sb.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                        sb.valueProperty().addListener((o,ov,nv) -> {
                            if (nv.doubleValue() <= sb.getMin() + 0.0001) {
                                loadOlderIfNeeded();
                            }
                        });
                    }
                }
            });
        });
    }

    private void autoScrollIfNearBottom() {
        // crude but effective: اگر نزدیک انتها هستیم، اسکرول به آخر
        int last = items.size() - 1;
        if (last >= 0) messagesList.scrollTo(last);
    }
    private void scrollToBottom() {
        int last = items.size() - 1;
        if (last >= 0) messagesList.scrollTo(last);
    }

    private static String s(JsonObject o, String k) {
        return (o!=null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }
    private Message fromEventMessage(JsonObject msg, String cid) {
        String id   = s(msg, "id");
        String from = s(msg, "from");
        long ts     = msg.has("ts") ? msg.get("ts").getAsLong() : System.currentTimeMillis()/1000;
        String text = s(msg, "text");
        boolean outgoing = msg.has("outgoing") && msg.get("outgoing").getAsBoolean(); // fallback handled by server later
        return new Message(id, cid, from, ts, MessageKind.TEXT, text, outgoing, MessageStatus.SENT);
    }

    private Node buildAvatarNode(String title) {
        StackPane p = new StackPane();
        p.getStyleClass().add("tgm-avatar");
        Circle c = new Circle(18);
        Label l = new Label(title != null && !title.isBlank() ? title.substring(0,1).toUpperCase() : "?");
        p.getChildren().addAll(c, l);
        StackPane.setAlignment(l, Pos.CENTER);
        return p;
    }

    @FXML
    private void initialize() {
        // Disable send until text exists (send logic comes in 4.3)
        sendBtn.disableProperty().bind(messageInput.textProperty().isEmpty());

        if (messagesList.getItems() == null || messagesList.getItems().isEmpty()) {
            messagesList.setItems(items);
        }
        // تضمین داشتن cellFactory (برای رندر حباب‌ها)
        if (messagesList.getCellFactory() == null) {
            messagesList.setCellFactory(lv -> new MsgCell()); // اگر MsgCell استاتیک است: راه حل callback قبلاً گفتیم
        }

        backBtn.setOnAction(e -> {unsubscribeEvents(); nav.back();}); // go back to previous view
        if (replyClose != null) replyClose.setOnAction(e -> clearReplyTarget());
        if (replyBox != null)   replyBox.setOnMouseClicked(e -> {
            if (replyTarget != null) scrollToMessageId(replyTarget.id());
        });
    }

    /** Called by Navigator right after FXML load. */
    public void setChatContext(String chatId, ChatSummary.Kind kind, String title) {
        this.chatId   = chatId;
        this.chatKind = kind;
        this.chatTitle= title;

        headerTitle.setText(title != null ? title : "");
        // Subtitle will be wired in later stages (presence/last seen)
        headerSubtitle.setText(" ");
        // Avatar letter
        headerAvatar.setUserData(title != null && !title.isBlank()
                ? title.substring(0,1).toUpperCase() : "?");

        // Prepare list cell factory (actual bubbles come in 4.2)
        messagesList.setPlaceholder(new Label("No messages yet"));

        sendBtn.setOnAction(e -> onSend());
        messageInput.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER -> {
                    if (e.isControlDown() || e.isMetaDown()) onSend();
                }
            }
        });
        // تضمین مجدد اتصال مدل به لیست (اگر قبلاً جای دیگر setItems شده باشد)
        if (messagesList.getItems() != items) {
            messagesList.setItems(items);
        }

        installAutoGrow(messageInput, 1, 5);

        // 4.4: subscribe to events
        if (eventHandler == null) {
            eventHandler = this::onEvent;
            app.eventBus().subscribe(eventHandler);
        }
        // 4.4: install auto-scroller/at-bottom detector
        installAutoScroller();
        // چیپ را اول پنهان کن
        hideNewChip();
    }

    private static String statusGlyph(MessageStatus st) {
        return switch (st) {
            case SENDING -> "⏳";
            case SENT, DELIVERED -> "✓";
            case READ -> "✓✓";
            case FAILED -> "!";
        };
    }

    /** Auto-grow TextArea up to maxLines; wrap always; vertical scroll after that. */
    private void installAutoGrow(TextArea ta, int minLines, int maxLines) {
        ta.setWrapText(true); // بدون اسکرول افقی

        // متنِ نامرئی برای محاسبه ارتفاع لازم بعد از wrap
        Text measure = new Text(" ");
        measure.setFont(ta.getFont());

        // عرض مؤثر (عرض کنترل منهای padding تقریبی داخل TextArea)
        DoubleBinding wrappingWidth = ta.widthProperty().subtract(
                ta.getPadding().getLeft() + ta.getPadding().getRight() + 24 /* fudge */
        );
        measure.wrappingWidthProperty().bind(wrappingWidth);

        // نمونه‌های مین/مکس برای محاسبه ارتفاع یک‌خطی و ۵خطی
        Text minSample = new Text("A");
        Text maxSample = new Text("A\n".repeat(Math.max(0, maxLines - 1)) + "A");
        minSample.setFont(ta.getFont());
        maxSample.setFont(ta.getFont());
        minSample.wrappingWidthProperty().bind(wrappingWidth);
        maxSample.wrappingWidthProperty().bind(wrappingWidth);

        Runnable update = () -> {
            // متن جاری برای اندازه‌گیری
            String t = ta.getText();
            measure.setText((t == null || t.isEmpty()) ? " " : t);

            double insets = ta.getInsets().getTop() + ta.getInsets().getBottom();
            double innerPad = 10; // کمی فضای داخلی

            double needed = Math.ceil(measure.getLayoutBounds().getHeight()) + insets + innerPad;
            double minH   = Math.ceil(minSample.getLayoutBounds().getHeight()) + insets + innerPad;
            double maxH   = Math.ceil(maxSample.getLayoutBounds().getHeight()) + insets + innerPad;

            double clamped = Math.max(minH, Math.min(needed, maxH));
            ta.setPrefHeight(clamped);

            // سیاست اسکرول: افقی هرگز، عمودی فقط وقتی از ۵ خط بیشتر شد
            ScrollPane sp = (ScrollPane) ta.lookup(".scroll-pane");
            if (sp != null) {
                sp.setFitToWidth(true);
                sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                sp.setVbarPolicy(needed > maxH
                        ? ScrollPane.ScrollBarPolicy.AS_NEEDED
                        : ScrollPane.ScrollBarPolicy.NEVER);
                if (needed > maxH) ta.setScrollTop(Double.MAX_VALUE); // caret قابل‌دیدن بماند
            }
        };

        // لیسنرها
        ta.textProperty().addListener((o, ov, nv) -> update.run());
        ta.widthProperty().addListener((o, ov, nv) -> update.run());
        ta.fontProperty().addListener((o, ov, nv) -> {
            measure.setFont(ta.getFont());
            minSample.setFont(ta.getFont());
            maxSample.setFont(ta.getFont());
            update.run();
        });

        // اجرای اول پس از ساخت Skin
        Platform.runLater(update);
    }


    /** لغو سابسکرایب امن */
    private void unsubscribeEvents() {
        if (eventHandler != null) {
            app.eventBus().unsubscribe(eventHandler);
            eventHandler = null;
        }
    }

    /** هندل همهٔ رویدادهای EVENT از سرور */
    private void onEvent(JsonObject evt) {
        if (evt == null) return;
        String t = evt.has("type") ? evt.get("type").getAsString() : "";
        if (!"EVENT".equals(t)) return;
        String kind = evt.has("event") ? evt.get("event").getAsString() : "";
        switch (kind) {
            case "MESSAGE_NEW" -> handleMessageNew(evt);
            case "MESSAGE_STATUS" -> handleMessageStatus(evt);
        }
    }

    /** رویداد پیام جدید (دیگران). اگر همین چت باز است، اضافه کن؛ وگرنه بی‌خیال (ChatList خودش آپدیت می‌شود). */
    private void handleMessageNew(JsonObject evt) {
        String cid = str(evt, "chat_id");
        if (cid == null || !cid.equals(chatId)) return; // چت دیگری است
        JsonObject m = evt.getAsJsonObject("message");
        if (m == null) return;
        Message incoming = new Message(
                str(m, "id"),
                chatId,
                str(m, "from"),
                safeLong(m.get("ts")),
                MessageKind.TEXT, // اگر kind دیگری آمد، می‌توانی parseKind اضافه کنی
                str(m, "text"),
                false,
                MessageStatus.SENT
        );
        Platform.runLater(() -> {
            appendWithDate(incoming);
            if (isAtBottom()) {
                scrollToBottom();
                // وقتی پایینیم و فوکوس داریم، سریعاً read بزنیم
                tryMarkRead(null);
            } else {
                newCount++;
                showNewChip();
            }
        });
    }

    /** ارتقای وضعیت پیام‌های خودم: SENT → DELIVERED/READ */
    private void handleMessageStatus(JsonObject evt) {
        String mid = str(evt, "message_id");
        String status = str(evt, "status");
        if (mid == null || status == null) return;
        MessageStatus st = switch (status) {
            case "DELIVERED" -> MessageStatus.DELIVERED;
            case "READ" -> MessageStatus.READ;
            default -> null;
        };
        if (st == null) return;
        Platform.runLater(() -> {
            int idx = indexOfMessageId(mid);
            if (idx < 0) return;
            Message old = (Message) items.get(idx);
            if (!old.outgoing()) return; // فقط پیام‌های خودم
            Message up = new Message(
                    old.id(), old.chatId(), old.from(), old.ts(),
                    old.kind(), old.text(), true, st
            );
            items.set(idx, up);
        });
    }

    /** نصب شنونده برای تشخیص اینکه کاربر «پایین لیست» است */
    private void installAutoScroller() {
        Platform.runLater(() -> {
            ScrollBar vbar = (ScrollBar) messagesList.lookup(".scroll-bar:vertical");
            if (vbar == null) return;
            ChangeListener<Number> ln = (obs, ov, nv) -> {
                atBottom = (vbar.getValue() >= vbar.getMax() - 0.5);
                if (atBottom && newCount > 0) {
                    hideNewChip();
                    tryMarkRead(null);
                }
            };
            vbar.valueProperty().addListener(ln);
            // هر تغییری در آیتم‌ها → اگر در کف بودیم، خودکار اسکرول
            items.addListener((ListChangeListener<Object>) change -> {
                if (isAtBottom()) {
                    scrollToBottom();
                }
            });

        });
    }

    private boolean isAtBottom() { return atBottom; }

    /** چیپ «N پیام جدید» را نمایش/به‌روزرسانی کن */
    private void showNewChip() {
        if (newChip == null) return;
        newChip.setText(newCount + " پیام جدید");
        newChip.setVisible(true);
        newChip.setManaged(true);
    }

    /** چیپ را پنهان و شمارنده را صفر کن */
    private void hideNewChip() {
        if (newChip == null) return;
        newCount = 0;
        newChip.setVisible(false);
        newChip.setManaged(false);
    }

    /** کلیک روی چیپ: برو پایین، چیپ محو، و مارک‌از‌رید */
    @FXML
    private void onNewChipClick(MouseEvent e) {
        hideNewChip();
        scrollToBottom();
        tryMarkRead(null);
    }

    /** Update status of my (outgoing) message by id. */
    private void updateOutgoingStatus(String messageId, MessageStatus status) {
        int idx = indexOfMessageId(messageId);
        if (idx < 0) return;
        Object it = items.get(idx);
        if (!(it instanceof Message m)) return;
        if (!m.outgoing()) return; // فقط پیام‌های خودم
        Message up = new Message(m.id(), m.chatId(), m.from(), m.ts(), m.kind(), m.text(), true, status);
        items.set(idx, up);
    }

    /** IDِ آخرین پیام در لیست (DateSep را رد می‌کند). */
    private String lastMessageId() {
        for (int i = items.size()-1; i >= 0; i--) {
            Object it = items.get(i);
            if (it instanceof Message m) return m.id();
        }
        return null;
    }

    /** ارسال CHAT_READ تا پیام مشخص؛ برای همان lastId دو بار نمی‌فرستیم. */
    private void tryMarkRead(String upto) {
        if (chatApi == null || chatId == null || chatId.isBlank()) return;
        String target = (upto != null && !upto.isBlank()) ? upto : lastMessageId();
        if (target == null) return;
        if (target.equals(lastReadUptoId)) return; // dedup
        lastReadUptoId = target;
        chatApi.markRead(chatId, target);
    }

    // Set / clear reply target + preview
    private void setReplyTarget(Message m) {
        replyTarget = m;
        if (replyBox != null) {
            if (replyTitle != null)   replyTitle.setText(m.outgoing() ? "You" : (m.from() != null ? m.from() : "Unknown"));
            if (replySnippet != null) replySnippet.setText(m.text() != null ? m.text().strip() : "");
            replyBox.setManaged(true); replyBox.setVisible(true);
        }
    }
    private void clearReplyTarget() {
        replyTarget = null;
        if (replyBox != null) { replyBox.setManaged(false); replyBox.setVisible(false); }
    }
    private void scrollToMessageId(String id) {
        int idx = indexOfMessageId(id);
        if (idx >= 0) {
            messagesList.scrollTo(idx);
            messagesList.getSelectionModel().clearAndSelect(idx);
        }
    }

    // Deletions
    private void deleteForMe(Message m) {
        int idx = indexOfMessageId(m.id());
        if (idx >= 0) items.remove(idx);
        if (chatApi != null) chatApi.deleteForMe(chatId, m.id());
    }
    private void deleteForAll(Message m) {
        if (chatApi == null) return;
        chatApi.deleteForAll(chatId, m.id())
                .thenRun(() -> Platform.runLater(() -> {
                    int idx = indexOfMessageId(m.id());
                    if (idx >= 0) items.remove(idx);
                }))
                .exceptionally(err -> null);
    }



}