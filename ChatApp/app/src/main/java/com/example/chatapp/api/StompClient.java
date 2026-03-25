package com.example.chatapp.api;

import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class StompClient extends WebSocketListener {
    private static final String TAG = "StompClient";
    private WebSocket webSocket;
    private final String url;
    private final String token;
    private StompListener listener;
    
    public interface StompListener {
        void onMessageReceived(String destination, String message);
        void onConnected();
        void onClosed();
    }

    public StompClient(String url, String token, StompListener listener) {
        this.url = url;
        this.token = token;
        this.listener = listener;
    }

    public void connect() {
        OkHttpClient client = new OkHttpClient();
        String wsUrl = url.replace("http://", "ws://").replace("https://", "wss://") + "/ws";
        Request request = new Request.Builder()
                .url(wsUrl)
                .build();
        webSocket = client.newWebSocket(request, this);
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "User disconnected");
        }
    }

    public void subscribe(String destination, String id) {
        String frame = "SUBSCRIBE\n" +
                "id:" + id + "\n" +
                "destination:" + destination + "\n\n\0";
        webSocket.send(frame);
    }

    public void send(String destination, String body) {
        String frame = "SEND\n" +
                "destination:" + destination + "\n\n" +
                body + "\0";
        webSocket.send(frame);
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        // Send STOMP CONNECT frame
        String connectFrame = "CONNECT\n" +
                "accept-version:1.1,1.0\n" +
                "heart-beat:10000,10000\n";
        if (token != null) {
            connectFrame += "Authorization:Bearer " + token + "\n";
        }
        connectFrame += "\n\0";
        webSocket.send(connectFrame);
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        Log.d(TAG, "Received: " + text);
        if (text.startsWith("CONNECTED")) {
            if (listener != null) listener.onConnected();
        } else if (text.startsWith("MESSAGE")) {
            // Parse STOMP message
            String[] parts = text.split("\n\n", 2);
            if (parts.length >= 2) {
                String headers = parts[0];
                String body = parts[1].replace("\0", "");
                
                String destination = "";
                for (String header : headers.split("\n")) {
                    if (header.startsWith("destination:")) {
                        destination = header.substring("destination:".length());
                        break;
                    }
                }
                if (listener != null) {
                    listener.onMessageReceived(destination, body);
                }
            }
        }
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        if (listener != null) listener.onClosed();
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.e(TAG, "WebSocket failure", t);
        if (listener != null) listener.onClosed();
    }
}
