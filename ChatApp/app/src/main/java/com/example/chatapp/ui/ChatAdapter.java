package com.example.chatapp.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chatapp.R;
import com.example.chatapp.models.ChatMessage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    public interface OnMediaClickListener {
        void onMediaClick(ChatMessage message);
    }

    private List<ChatMessage> messages = new ArrayList<>();
    private final String currentUserId;
    private final OnMediaClickListener mediaClickListener;

    public ChatAdapter(String currentUserId, OnMediaClickListener mediaClickListener) {
        this.currentUserId = currentUserId;
        this.mediaClickListener = mediaClickListener;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage message) {
        this.messages.add(message);
        notifyItemInserted(this.messages.size() - 1);
    }

    public void updateMessage(ChatMessage message) {
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage existing = messages.get(i);
            boolean match = false;
            if (message.getClientMessageId() != null && message.getClientMessageId().equals(existing.getClientMessageId())) {
                match = true;
            } else if (message.getId() != null && message.getId().equals(existing.getId())) {
                match = true;
            }
            if (match) {
                messages.set(i, message);
                notifyItemChanged(i);
                return;
            }
        }
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        holder.bind(messages.get(position));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    class ChatViewHolder extends RecyclerView.ViewHolder {
        LinearLayout llMessageContainer;
        FrameLayout flImageContainer;
        ImageView ivImage;
        FrameLayout flImageProgressOverlay;
        ProgressBar pbImageProgress;
        LinearLayout llFileContainer;
        TextView tvFileName;
        TextView tvFileSize;
        ImageView btnDownload;
        ProgressBar pbFileProgress;
        TextView tvMessageContent;
        TextView tvTime;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            llMessageContainer = itemView.findViewById(R.id.llMessageContainer);
            flImageContainer = itemView.findViewById(R.id.flImageContainer);
            ivImage = itemView.findViewById(R.id.ivImage);
            flImageProgressOverlay = itemView.findViewById(R.id.flImageProgressOverlay);
            pbImageProgress = itemView.findViewById(R.id.pbImageProgress);
            llFileContainer = itemView.findViewById(R.id.llFileContainer);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFileSize = itemView.findViewById(R.id.tvFileSize);
            btnDownload = itemView.findViewById(R.id.btnDownload);
            pbFileProgress = itemView.findViewById(R.id.pbFileProgress);
            tvMessageContent = itemView.findViewById(R.id.tvMessageContent);
            tvTime = itemView.findViewById(R.id.tvTime);
        }

        public void bind(ChatMessage message) {
            tvTime.setText(message.getCreatedAt() != null ? message.getCreatedAt() : "");

            boolean isSentByMe = message.getSenderId() != null &&
                    message.getSenderId().equals(Long.parseLong(currentUserId));
            
            // Align bubble left or right
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) llMessageContainer.getLayoutParams();
            if (isSentByMe) {
                params.addRule(RelativeLayout.ALIGN_PARENT_END);
                params.removeRule(RelativeLayout.ALIGN_PARENT_START);
                llMessageContainer.setBackgroundResource(R.drawable.bg_msg_sent);
            } else {
                params.addRule(RelativeLayout.ALIGN_PARENT_START);
                params.removeRule(RelativeLayout.ALIGN_PARENT_END);
                llMessageContainer.setBackgroundResource(R.drawable.bg_msg_received);
            }
            llMessageContainer.setLayoutParams(params);

            // Handle IMAGE messages
            if (message.isImage()) {
                flImageContainer.setVisibility(View.VISIBLE);
                llFileContainer.setVisibility(View.GONE);

                if (message.getLocalFilePath() != null && new File(message.getLocalFilePath()).exists()) {
                    Glide.with(itemView.getContext())
                            .load(new File(message.getLocalFilePath()))
                            .into(ivImage);
                } else {
                    ivImage.setImageResource(R.drawable.ic_file_document);
                }

                // Show rotating progress bar overlay when uploading
                if (message.isUploading()) {
                    flImageProgressOverlay.setVisibility(View.VISIBLE);
                } else {
                    flImageProgressOverlay.setVisibility(View.GONE);
                }

                flImageContainer.setOnClickListener(v -> {
                    if (!message.isUploading() && mediaClickListener != null) {
                        mediaClickListener.onMediaClick(message);
                    }
                });

                if (message.getContent() != null && !message.getContent().isEmpty() &&
                    !"[Image]".equalsIgnoreCase(message.getContent())) {
                    tvMessageContent.setVisibility(View.VISIBLE);
                    tvMessageContent.setText(message.getContent());
                } else {
                    tvMessageContent.setVisibility(View.GONE);
                }
            }
            // Handle FILE messages
            else if (message.isFile()) {
                flImageContainer.setVisibility(View.GONE);
                llFileContainer.setVisibility(View.VISIBLE);
                tvMessageContent.setVisibility(View.GONE);

                tvFileName.setText(message.getFileName() != null ? message.getFileName() : "Document");

                // Show rotating progress bar when uploading
                if (message.isUploading()) {
                    pbFileProgress.setVisibility(View.VISIBLE);
                    btnDownload.setVisibility(View.GONE);
                    tvFileSize.setText("Sending...");
                } else {
                    pbFileProgress.setVisibility(View.GONE);
                    btnDownload.setVisibility(View.VISIBLE);
                    tvFileSize.setText(formatFileSize(message.getFileSize()));
                }

                llFileContainer.setOnClickListener(v -> {
                    if (!message.isUploading() && mediaClickListener != null) {
                        mediaClickListener.onMediaClick(message);
                    }
                });
            }
            // Handle TEXT messages
            else {
                flImageContainer.setVisibility(View.GONE);
                llFileContainer.setVisibility(View.GONE);
                tvMessageContent.setVisibility(View.VISIBLE);
                tvMessageContent.setText(message.getContent());
            }
        }

        private String formatFileSize(Long bytes) {
            if (bytes == null || bytes <= 0) return "";
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
            return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
