package com.example.chatapp.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.chatapp.api.ApiClient;
import com.example.chatapp.api.ApiService;
import com.example.chatapp.api.CryptoManager;
import com.example.chatapp.api.SessionManager;
import com.example.chatapp.api.WebSocketManager;
import com.example.chatapp.databinding.ActivityChatBinding;
import com.example.chatapp.models.ChatMessage;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity implements WebSocketManager.MessageListener {

    private ActivityChatBinding binding;
    private ChatAdapter chatAdapter;
    private String recipientId;
    private String recipientUsername;
    private String recipientPublicKey;
    private String currentUserId;

    private ApiService apiService;
    private SessionManager sessionManager;
    private CryptoManager cryptoManager;
    private Gson gson;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        recipientId = getIntent().getStringExtra("RECIPIENT_ID");
        recipientUsername = getIntent().getStringExtra("RECIPIENT_USERNAME");
        recipientPublicKey = getIntent().getStringExtra("RECIPIENT_PUBLIC_KEY");

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(recipientUsername);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        sessionManager = new SessionManager(this);
        currentUserId = sessionManager.getUserId();
        apiService = ApiClient.getApiService(this);
        cryptoManager = new CryptoManager(this);
        gson = new Gson();
        mainHandler = new Handler(Looper.getMainLooper());

        // Enforce 200 character limit — RSA 2048-bit with PKCS1Padding can encrypt up to 245 bytes
        binding.etMessage.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});

        setupRecyclerView();
        fetchChatHistory();
        setupWebSocket();
        WebSocketManager.getInstance().registerMessageListener(this);

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
                    List<ChatMessage> decryptedMessages = new ArrayList<>();
                    for (ChatMessage msg : response.body()) {
                        try {
                            if (msg.getSenderId() != null && msg.getSenderId().equals(Long.parseLong(currentUserId))) {
                                // I sent this — decrypt the sender's copy using my private key
                                if (msg.getSenderContent() != null && !msg.getSenderContent().isEmpty()) {
                                    String decrypted = cryptoManager.decrypt(msg.getSenderContent());
                                    msg.setContent(decrypted);
                                }
                            } else {
                                // I received this — decrypt the content using my private key
                                if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                                    String decrypted = cryptoManager.decrypt(msg.getContent());
                                    msg.setContent(decrypted);
                                }
                            }
                        } catch (Exception e) {
                            Log.e("ChatActivity", "Failed to decrypt message", e);
                            msg.setContent("[Encrypted message]");
                        }
                        decryptedMessages.add(msg);
                    }
                    chatAdapter.setMessages(decryptedMessages);
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
        WebSocketManager.getInstance().connect(this);
    }

    private void sendMessage() {
        String content = binding.etMessage.getText().toString().trim();
        if (content.isEmpty()) return;

        if (recipientPublicKey == null || recipientPublicKey.isEmpty()) {
            Toast.makeText(this, "Cannot encrypt: recipient public key not available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Encrypt with recipient's public key (so recipient can decrypt with their private key)
        String encryptedForRecipient = cryptoManager.encrypt(content, recipientPublicKey);
        // Encrypt with my own public key (so I can decrypt my own sent messages from history)
        String encryptedForSender = cryptoManager.encrypt(content, cryptoManager.getPublicKeyBase64());

        if (encryptedForRecipient == null || encryptedForSender == null) {
            Toast.makeText(this, "Encryption failed. Message may be too long.", Toast.LENGTH_SHORT).show();
            return;
        }

        ChatMessage message = new ChatMessage(currentUserId, recipientId, encryptedForRecipient);
        message.setSenderContent(encryptedForSender);
        String json = gson.toJson(message);

        WebSocketManager.getInstance().send("/app/chat", json);
        binding.etMessage.setText("");

        // Optimistically add the PLAINTEXT message for immediate display
        ChatMessage displayMessage = new ChatMessage(currentUserId, recipientId, content);
        chatAdapter.addMessage(displayMessage);
        binding.rvMessages.scrollToPosition(chatAdapter.getItemCount() - 1);
    }

    @Override
    public void onMessageReceived(ChatMessage msg) {
        if (msg.getSenderId() != null && msg.getSenderId().equals(Long.parseLong(recipientId))) {
            // Decrypt the content using my private key
            try {
                String decrypted = cryptoManager.decrypt(msg.getContent());
                msg.setContent(decrypted);
            } catch (Exception e) {
                Log.e("ChatActivity", "Failed to decrypt incoming message", e);
                msg.setContent("[Decryption failed]");
            }
            chatAdapter.addMessage(msg);
            binding.rvMessages.scrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WebSocketManager.getInstance().unregisterMessageListener(this);
    }
}
