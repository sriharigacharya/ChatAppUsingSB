package com.example.chatapp.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.chatapp.models.ChatMessage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public class WebSocketManager implements StompClient.StompListener {
    private static final String TAG = "WebSocketManager";
    private static WebSocketManager instance;

    private StompClient stompClient;
    private boolean isConnected = false;
    private String currentUserId;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    // Listeners
    private final List<MessageListener> messageListeners = new ArrayList<>();
    private final List<StatusListener> statusListeners = new ArrayList<>();
    private final List<ConnectionListener> connectionListeners = new ArrayList<>();

    public interface MessageListener {
        void onMessageReceived(ChatMessage message);
    }

    public interface StatusListener {
        void onStatusUpdated(Long userId, String status);
    }

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
    }

    private WebSocketManager() {}

    public static synchronized WebSocketManager getInstance() {
        if (instance == null) {
            instance = new WebSocketManager();
        }
        return instance;
    }

    public void connect(Context context) {
        if (isConnected || stompClient != null) {
            Log.d(TAG, "Already connected or connecting...");
            return;
        }

        SessionManager sessionManager = new SessionManager(context);
        currentUserId = sessionManager.getUserId();
        String username = sessionManager.getUsername();

        if (currentUserId == null || username == null) {
            Log.w(TAG, "Cannot connect: User not logged in");
            return;
        }

        String wsUrl = ApiClient.BASE_URL;
        Log.d(TAG, "Connecting to WebSocket for user: " + username);
        stompClient = new StompClient(wsUrl, sessionManager.fetchAuthToken(), username, this);
        stompClient.connect();
    }

    public void disconnect() {
        if (stompClient != null) {
            stompClient.disconnect();
            stompClient = null;
        }
        isConnected = false;
        notifyDisconnected();
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void send(String destination, String body) {
        if (stompClient != null && isConnected) {
            stompClient.send(destination, body);
        } else {
            Log.e(TAG, "Cannot send message: WebSocket not connected");
        }
    }

    // Listener Registration
    public synchronized void registerMessageListener(MessageListener listener) {
        if (!messageListeners.contains(listener)) {
            messageListeners.add(listener);
        }
    }

    public synchronized void unregisterMessageListener(MessageListener listener) {
        messageListeners.remove(listener);
    }

    public synchronized void registerStatusListener(StatusListener listener) {
        if (!statusListeners.contains(listener)) {
            statusListeners.add(listener);
        }
    }

    public synchronized void unregisterStatusListener(StatusListener listener) {
        statusListeners.remove(listener);
    }

    public synchronized void registerConnectionListener(ConnectionListener listener) {
        if (!connectionListeners.contains(listener)) {
            connectionListeners.add(listener);
        }
        if (isConnected) {
            listener.onConnected();
        }
    }

    public synchronized void unregisterConnectionListener(ConnectionListener listener) {
        connectionListeners.remove(listener);
    }

    // StompListener implementation
    @Override
    public void onConnected() {
        Log.d(TAG, "WebSocket connected successfully");
        isConnected = true;

        // Automatically subscribe to user specific messages and global status updates
        if (stompClient != null && currentUserId != null) {
            stompClient.subscribe("/topic/messages/" + currentUserId, "sub-messages");
            stompClient.subscribe("/topic/status", "sub-status");
        }

        mainHandler.post(() -> {
            synchronized (WebSocketManager.this) {
                for (ConnectionListener l : connectionListeners) {
                    l.onConnected();
                }
            }
        });
    }

    @Override
    public void onMessageReceived(String destination, String messageStr) {
        Log.d(TAG, "Received message on " + destination + ": " + messageStr);
        if (destination.equals("/topic/status")) {
            try {
                JsonObject jsonObject = JsonParser.parseString(messageStr).getAsJsonObject();
                Long userId = jsonObject.get("userId").getAsLong();
                String status = jsonObject.get("status").getAsString();

                mainHandler.post(() -> {
                    synchronized (WebSocketManager.this) {
                        for (StatusListener l : statusListeners) {
                            l.onStatusUpdated(userId, status);
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse status update", e);
            }
        } else if (destination.startsWith("/topic/messages/")) {
            try {
                ChatMessage msg = gson.fromJson(messageStr, ChatMessage.class);
                mainHandler.post(() -> {
                    synchronized (WebSocketManager.this) {
                        for (MessageListener l : messageListeners) {
                            l.onMessageReceived(msg);
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse chat message", e);
            }
        }
    }

    @Override
    public void onClosed() {
        Log.d(TAG, "WebSocket closed");
        isConnected = false;
        stompClient = null;
        notifyDisconnected();
    }

    private void notifyDisconnected() {
        mainHandler.post(() -> {
            synchronized (WebSocketManager.this) {
                for (ConnectionListener l : connectionListeners) {
                    l.onDisconnected();
                }
            }
        });
    }
}
