package com.example.chatapp.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.chatapp.api.ApiClient;
import com.example.chatapp.api.ApiService;
import com.example.chatapp.api.SessionManager;
import com.example.chatapp.api.StompClient;
import com.example.chatapp.databinding.ActivityChatBinding;
import com.example.chatapp.models.ChatMessage;
import com.google.gson.Gson;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity implements StompClient.StompListener {

    private ActivityChatBinding binding;
    private ChatAdapter chatAdapter;
    private String recipientId;
    private String recipientUsername;
    private String currentUserId;
    
    private ApiService apiService;
    private SessionManager sessionManager;
    private StompClient stompClient;
    private Gson gson;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        recipientId = getIntent().getStringExtra("RECIPIENT_ID");
        recipientUsername = getIntent().getStringExtra("RECIPIENT_USERNAME");

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(recipientUsername);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        sessionManager = new SessionManager(this);
        currentUserId = sessionManager.getUserId();
        apiService = ApiClient.getApiService(this);
        gson = new Gson();
        mainHandler = new Handler(Looper.getMainLooper());

        setupRecyclerView();
        fetchChatHistory();
        setupWebSocket();

        binding.btnSend.setOnClickListener(v -> sendMessage());
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // show latest messages at bottom
        binding.rvMessages.setLayoutManager(layoutManager);
        
        chatAdapter = new ChatAdapter(currentUserId);
        binding.rvMessages.setAdapter(chatAdapter);
    }

    private void fetchChatHistory() {
        apiService.getChatMessages(currentUserId, recipientId).enqueue(new Callback<List<ChatMessage>>() {
            @Override
            public void onResponse(Call<List<ChatMessage>> call, Response<List<ChatMessage>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    chatAdapter.setMessages(response.body());
                    binding.rvMessages.scrollToPosition(chatAdapter.getItemCount() - 1);
                }
            }

            @Override
            public void onFailure(Call<List<ChatMessage>> call, Throwable t) {
                Toast.makeText(ChatActivity.this, "Failed to load history", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupWebSocket() {
        String wsUrl = ApiClient.BASE_URL;
        stompClient = new StompClient(wsUrl, sessionManager.fetchAuthToken(), this);
        stompClient.connect();
    }

    private void sendMessage() {
        String content = binding.etMessage.getText().toString().trim();
        if (content.isEmpty()) return;

        ChatMessage message = new ChatMessage(currentUserId, recipientId, content);
        String json = gson.toJson(message);
        
        stompClient.send("/app/chat", json);
        binding.etMessage.setText("");

        // Optimistically add message
        chatAdapter.addMessage(message);
        binding.rvMessages.scrollToPosition(chatAdapter.getItemCount() - 1);
    }

    @Override
    public void onConnected() {
        Log.d("ChatActivity", "STOMP Connected");
        // Subscribe to user specific queue
        stompClient.subscribe("/user/" + currentUserId + "/queue/messages", "sub-0");
    }

    @Override
    public void onMessageReceived(String destination, String messageStr) {
        Log.d("ChatActivity", "Message received: " + messageStr);
        ChatMessage msg = gson.fromJson(messageStr, ChatMessage.class);
        
        mainHandler.post(() -> {
            if (msg.getSenderId().equals(recipientId)) {
                chatAdapter.addMessage(msg);
                binding.rvMessages.scrollToPosition(chatAdapter.getItemCount() - 1);
            }
        });
    }

    @Override
    public void onClosed() {
        Log.d("ChatActivity", "STOMP Closed");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (stompClient != null) {
            stompClient.disconnect();
        }
    }
}
