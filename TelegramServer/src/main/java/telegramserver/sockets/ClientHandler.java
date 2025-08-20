package telegramserver.sockets;

import telegramserver.models.Message;
import telegramserver.services.ChatService;
import telegramserver.services.ChannelService;
import telegramserver.services.MessageService;

import java.io.*;
import java.net.Socket;
import java.sql.Timestamp;
import java.util.StringTokenizer;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                handleCommand(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (username != null) {
                ClientRegistry.removeClient(username);
            }
        }
    }

    private void handleCommand(String line) throws IOException {
        StringTokenizer st = new StringTokenizer(line);
        String cmd = st.nextToken();

        switch (cmd) {
            case "LOGIN":
                username = st.nextToken();
                ClientRegistry.addClient(username, writer);
                sendMessage("✅ Logged in as " + username);
                break;

            case "JOIN":
                int chatId = Integer.parseInt(st.nextToken());
                ChatService.joinChat(chatId, username);
                sendMessage("✅ Joined chat " + chatId);
                break;

            case "LEAVE":
                chatId = Integer.parseInt(st.nextToken());
                ChatService.leaveChat(chatId, username);
                sendMessage("✅ Left chat " + chatId);
                break;

            case "SEND":
                chatId = Integer.parseInt(st.nextToken());
                String content = line.substring(line.indexOf(" ", line.indexOf(" ") + 1) + 1);
                Message msg = new Message(0, content, 0, chatId, 0,
                        new Timestamp(System.currentTimeMillis()), false, false);
                MessageService.saveMessage(msg);

                for (String member : ChatService.getMembers(chatId)) {
                    BufferedWriter w = ClientRegistry.getWriter(member);
                    if (w != null) {
                        w.write("💬 [" + chatId + "] " + username + ": " + content + "\n");
                        w.flush();
                    }
                }
                break;

            case "CREATE_CHANNEL":
                int channelId = Integer.parseInt(st.nextToken());
                ChannelService.createChannel("Channel" + channelId, 0);
                sendMessage("✅ Created channel " + channelId);
                break;

            case "JOIN_CHANNEL":
                channelId = Integer.parseInt(st.nextToken());
                ChannelService.joinChannel(channelId, username);
                sendMessage("✅ Joined channel " + channelId);
                break;

            case "SEND_CHANNEL":
                channelId = Integer.parseInt(st.nextToken());
                String msgContent = line.substring(line.indexOf(" ", line.indexOf(" ") + 1) + 1);
                Message channelMsg = new Message(0, msgContent, 0, channelId, 0,
                        new Timestamp(System.currentTimeMillis()), false, false);
                MessageService.saveMessage(channelMsg);

                for (String member : ChannelService.getMembers(channelId)) {
                    BufferedWriter w = ClientRegistry.getWriter(member);
                    if (w != null) {
                        w.write("📢 [Channel " + channelId + "] " + username + ": " + msgContent + "\n");
                        w.flush();
                    }
                }
                break;

            default:
                sendMessage("❌ Unknown command: " + cmd);
        }
    }

    private void sendMessage(String msg) throws IOException {
        writer.write(msg + "\n");
        writer.flush();
    }
}
