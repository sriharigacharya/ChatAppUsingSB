package com.example.chatapp.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputFilter;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.chatapp.api.ApiClient;
import com.example.chatapp.api.ApiService;
import com.example.chatapp.api.CryptoManager;
import com.example.chatapp.api.SessionManager;
import com.example.chatapp.api.WebSocketManager;
import com.example.chatapp.databinding.ActivityChatBinding;
import com.example.chatapp.models.ChatMessage;
import com.example.chatapp.models.MediaFileDto;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity implements WebSocketManager.MessageListener, ChatAdapter.OnMediaClickListener {

    private static final String TAG = "ChatActivity";
    private static final long MAX_FILE_SIZE = 25L * 1024 * 1024; // 25 MB

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

    private boolean isPickingImage = false;
    private ActivityResultLauncher<String> filePickerLauncher;

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

        binding.etMessage.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});

        setupRecyclerView();
        setupFilePicker();
        fetchChatHistory();
        setupWebSocket();
        WebSocketManager.getInstance().registerMessageListener(this);

        binding.btnSend.setOnClickListener(v -> sendMessage());
        binding.btnAttach.setOnClickListener(v -> showAttachmentOptions());
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.rvMessages.setLayoutManager(layoutManager);

        chatAdapter = new ChatAdapter(currentUserId, this);
        binding.rvMessages.setAdapter(chatAdapter);
    }

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        processSelectedFile(uri, isPickingImage);
                    }
                }
        );
    }

    private void showAttachmentOptions() {
        if (recipientPublicKey == null || recipientPublicKey.isEmpty()) {
            Toast.makeText(this, "Recipient public key not available. Cannot encrypt.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] options = {"Photo / Image", "Document / File"};
        new AlertDialog.Builder(this)
                .setTitle("Attach")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        isPickingImage = true;
                        filePickerLauncher.launch("image/*");
                    } else {
                        isPickingImage = false;
                        filePickerLauncher.launch("*/*");
                    }
                })
                .show();
    }

    private void processSelectedFile(Uri uri, boolean isImage) {
        String fileName = "attachment";
        long fileSize = 0;

        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIdx != -1) fileName = cursor.getString(nameIdx);
                if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resolving file details", e);
        }

        if (fileSize > MAX_FILE_SIZE) {
            Toast.makeText(this, "File exceeds 25 MB limit", Toast.LENGTH_LONG).show();
            return;
        }

        final String finalFileName = fileName;
        final long finalFileSize = fileSize;
        final String fileType = isImage ? "IMAGE" : "FILE";
        final String clientMessageId = UUID.randomUUID().toString();

        // 1. Instantly create and display optimistic message with rotating progress bar
        ChatMessage optimisticMsg = new ChatMessage(currentUserId, recipientId, isImage ? "[Image]" : "[File]");
        optimisticMsg.setMessageType(fileType);
        optimisticMsg.setFileName(finalFileName);
        optimisticMsg.setFileSize(finalFileSize);
        optimisticMsg.setClientMessageId(clientMessageId);
        optimisticMsg.setUploading(true);

        chatAdapter.addMessage(optimisticMsg);
        binding.rvMessages.scrollToPosition(chatAdapter.getItemCount() - 1);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // 2. Copy URI to temporary plain file
                File cacheDir = getCacheDir();
                File plainFile = new File(cacheDir, "plain_" + UUID.randomUUID().toString() + "_" + finalFileName);
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(plainFile)) {
                    byte[] buffer = new byte[16 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }

                // If image, attach local path so preview renders under spinner immediately
                if (isImage) {
                    optimisticMsg.setLocalFilePath(plainFile.getAbsolutePath());
                    mainHandler.post(() -> chatAdapter.updateMessage(optimisticMsg));
                }

                // 3. Generate random 256-bit AES file key
                byte[] fileKey = cryptoManager.generateFileKey();

                // 4. Wrap file key for recipient and sender with RSA
                String recipientEncAesKey = cryptoManager.wrapKeyForUser(fileKey, recipientPublicKey);
                String senderEncAesKey = cryptoManager.wrapKeyForUser(fileKey, cryptoManager.getPublicKeyBase64());

                // 5. Stream-encrypt plainFile to cipherFile using Tink
                File cipherFile = new File(cacheDir, "enc_" + UUID.randomUUID().toString());
                cryptoManager.encryptFile(plainFile, cipherFile, fileKey);

                // 6. Upload ciphertext to backend
                RequestBody fileBody = RequestBody.create(
                        MediaType.parse("application/octet-stream"), cipherFile);
                MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                        "file", "ciphertext.enc", fileBody);

                RequestBody recipientIdPart = RequestBody.create(MediaType.parse("text/plain"), recipientId);
                RequestBody fileTypePart = RequestBody.create(MediaType.parse("text/plain"), fileType);
                RequestBody originalFileNamePart = RequestBody.create(MediaType.parse("text/plain"), finalFileName);
                RequestBody senderKeyPart = RequestBody.create(MediaType.parse("text/plain"), senderEncAesKey);
                RequestBody recipientKeyPart = RequestBody.create(MediaType.parse("text/plain"), recipientEncAesKey);

                Call<MediaFileDto> call = apiService.uploadFile(
                        filePart,
                        recipientIdPart,
                        fileTypePart,
                        originalFileNamePart,
                        senderKeyPart,
                        recipientKeyPart
                );

                Response<MediaFileDto> response = call.execute();
                if (response.isSuccessful() && response.body() != null) {
                    MediaFileDto uploaded = response.body();
                    String mediaFileId = uploaded.getId();

                    // 7. Update message: clear uploading state and assign mediaFileId
                    optimisticMsg.setMediaFileId(mediaFileId);
                    optimisticMsg.setLocalFilePath(plainFile.getAbsolutePath());
                    optimisticMsg.setUploading(false);

                    // 8. Send STOMP chat message referencing mediaFileId
                    String json = gson.toJson(optimisticMsg);
                    WebSocketManager.getInstance().send("/app/chat", json);

                    // 9. Update UI: dismiss rotating progress bar!
                    mainHandler.post(() -> {
                        chatAdapter.updateMessage(optimisticMsg);
                    });
                } else {
                    optimisticMsg.setUploading(false);
                    optimisticMsg.setUploadFailed(true);
                    mainHandler.post(() -> {
                        chatAdapter.updateMessage(optimisticMsg);
                        Toast.makeText(ChatActivity.this, "Upload failed: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }

                // Clean up temporary cipher file
                if (cipherFile.exists()) cipherFile.delete();

            } catch (Exception e) {
                Log.e(TAG, "File encryption/upload failed", e);
                optimisticMsg.setUploading(false);
                optimisticMsg.setUploadFailed(true);
                mainHandler.post(() -> {
                    chatAdapter.updateMessage(optimisticMsg);
                    Toast.makeText(ChatActivity.this, "Transfer failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void fetchChatHistory() {
        apiService.getChatMessages(currentUserId, recipientId).enqueue(new Callback<List<ChatMessage>>() {
            @Override
            public void onResponse(Call<List<ChatMessage>> call, Response<List<ChatMessage>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ChatMessage> decryptedMessages = new ArrayList<>();
                    for (ChatMessage msg : response.body()) {
                        try {
                            if (msg.isText()) {
                                if (msg.getSenderId() != null && msg.getSenderId().equals(Long.parseLong(currentUserId))) {
                                    if (msg.getSenderContent() != null && !msg.getSenderContent().isEmpty()) {
                                        String decrypted = cryptoManager.decrypt(msg.getSenderContent(), cryptoManager.getPublicKeyBase64());
                                        msg.setContent(decrypted);
                                    }
                                } else {
                                    if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                                        String decrypted = cryptoManager.decrypt(msg.getContent(), recipientPublicKey);
                                        msg.setContent(decrypted);
                                    }
                                }
                            } else {
                                // For images/files, check if already downloaded in cache
                                File cached = getLocalCachedFile(msg.getMediaFileId(), msg.getFileName());
                                if (cached.exists()) {
                                    msg.setLocalFilePath(cached.getAbsolutePath());
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to decrypt message", e);
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

        String encryptedForRecipient = cryptoManager.encrypt(content, recipientPublicKey);
        String encryptedForSender = cryptoManager.encrypt(content, cryptoManager.getPublicKeyBase64());

        if (encryptedForRecipient == null || encryptedForSender == null) {
            Toast.makeText(this, "Encryption failed. Message may be too long.", Toast.LENGTH_SHORT).show();
            return;
        }

        String clientMessageId = UUID.randomUUID().toString();
        ChatMessage message = new ChatMessage(currentUserId, recipientId, encryptedForRecipient);
        message.setSenderContent(encryptedForSender);
        message.setClientMessageId(clientMessageId);
        message.setMessageType("TEXT");

        String json = gson.toJson(message);
        WebSocketManager.getInstance().send("/app/chat", json);
        binding.etMessage.setText("");

        ChatMessage displayMessage = new ChatMessage(currentUserId, recipientId, content);
        displayMessage.setClientMessageId(clientMessageId);
        chatAdapter.addMessage(displayMessage);
        binding.rvMessages.scrollToPosition(chatAdapter.getItemCount() - 1);
    }

    @Override
    public void onMessageReceived(ChatMessage msg) {
        if (msg.getSenderId() != null && msg.getSenderId().equals(Long.parseLong(recipientId))) {
            if (msg.isText()) {
                try {
                    String decrypted = cryptoManager.decrypt(msg.getContent(), recipientPublicKey);
                    msg.setContent(decrypted);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to decrypt incoming message", e);
                    msg.setContent("[Decryption failed]");
                }
            } else {
                // If it's an image, auto-download in background
                if (msg.isImage()) {
                    downloadAndDecryptMedia(msg);
                }
            }
            chatAdapter.addMessage(msg);
            binding.rvMessages.scrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    @Override
    public void onMediaClick(ChatMessage message) {
        if (message.getLocalFilePath() != null && new File(message.getLocalFilePath()).exists()) {
            openFile(new File(message.getLocalFilePath()), message.getFileName());
        } else {
            downloadAndDecryptMedia(message);
        }
    }

    private void downloadAndDecryptMedia(ChatMessage message) {
        if (message.getMediaFileId() == null) return;

        Toast.makeText(this, "Downloading and decrypting...", Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // 1. Fetch metadata to get wrapped AES key
                Response<MediaFileDto> metaResp = apiService.getFileMetadata(message.getMediaFileId()).execute();
                if (!metaResp.isSuccessful() || metaResp.body() == null) {
                    throw new RuntimeException("Failed to get file metadata: " + metaResp.code());
                }

                MediaFileDto metadata = metaResp.body();
                String encryptedAesKey = metadata.getEncryptedAesKey();

                // 2. Unwrap 32-byte AES file key with device RSA private key
                byte[] fileKey = cryptoManager.unwrapKey(encryptedAesKey);

                // 3. Download ciphertext stream
                Response<ResponseBody> downloadResp = apiService.downloadFile(message.getMediaFileId()).execute();
                if (!downloadResp.isSuccessful() || downloadResp.body() == null) {
                    throw new RuntimeException("Download failed: " + downloadResp.code());
                }

                // 4. Stream-decrypt ciphertext to destination file
                File targetFile = getLocalCachedFile(message.getMediaFileId(), message.getFileName());
                try (InputStream stream = downloadResp.body().byteStream()) {
                    cryptoManager.decryptFile(stream, targetFile, fileKey);
                }

                // 5. Post-decrypt magic bytes validation
                String mimeType = CryptoManager.detectMimeTypeByMagicBytes(targetFile);
                Log.d(TAG, "Decrypted file magic bytes detected MIME: " + mimeType);

                // 6. Update message model and notify adapter
                message.setLocalFilePath(targetFile.getAbsolutePath());
                mainHandler.post(() -> {
                    chatAdapter.updateMessage(message);
                    Toast.makeText(ChatActivity.this, "Decrypted successfully", Toast.LENGTH_SHORT).show();
                    if (!message.isImage()) {
                        openFile(targetFile, message.getFileName());
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed downloading/decrypting media", e);
                mainHandler.post(() -> Toast.makeText(ChatActivity.this,
                        "Decryption failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private File getLocalCachedFile(String mediaFileId, String fileName) {
        File dir = new File(getFilesDir(), "media");
        if (!dir.exists()) dir.mkdirs();
        String safeName = fileName != null ? fileName : "file";
        return new File(dir, mediaFileId + "_" + safeName);
    }

    private void openFile(File file, String fileName) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            String mime = getContentResolver().getType(uri);
            if (mime == null) mime = CryptoManager.detectMimeTypeByMagicBytes(file);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open " + fileName));
        } catch (Exception e) {
            Log.e(TAG, "Cannot open file", e);
            Toast.makeText(this, "No application found to open this file", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WebSocketManager.getInstance().unregisterMessageListener(this);
    }
}
