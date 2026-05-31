package com.example.chatapp.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatapp.R;
import com.example.chatapp.models.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private List<ChatMessage> messages = new ArrayList<>();
    private final String currentUserId;

    public ChatAdapter(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage message) {
        this.messages.add(message);
        notifyItemInserted(this.messages.size() - 1);
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
        TextView tvMessageContent;
        TextView tvTime;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            llMessageContainer = itemView.findViewById(R.id.llMessageContainer);
            tvMessageContent = itemView.findViewById(R.id.tvMessageContent);
            tvTime = itemView.findViewById(R.id.tvTime);
        }

        public void bind(ChatMessage message) {
            tvMessageContent.setText(message.getContent());
            tvTime.setText(message.getCreatedAt() != null ? message.getCreatedAt() : "");

            boolean isSentByMe = message.getSenderId() != null &&
                    message.getSenderId().equals(Long.parseLong(currentUserId));
            
            // Layout params to align left or right inside RelativeLayout
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
        }
    }
}
