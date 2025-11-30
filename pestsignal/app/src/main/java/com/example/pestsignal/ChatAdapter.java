package com.example.pestsignal;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<ChatMessage> messages;

    public ChatAdapter() {
        this.messages = new ArrayList<>();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ChatMessage.TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_user, parent, false);
            return new UserMessageViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_ai, parent, false);
            return new AIMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder instanceof UserMessageViewHolder) {
            ((UserMessageViewHolder) holder).bind(message);
        } else if (holder instanceof AIMessageViewHolder) {
            ((AIMessageViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void addMessages(List<ChatMessage> newMessages) {
        int startPosition = messages.size();
        messages.addAll(newMessages);
        notifyItemRangeInserted(startPosition, newMessages.size());
    }

    public void clearMessages() {
        int size = messages.size();
        messages.clear();
        notifyItemRangeRemoved(0, size);
    }

    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    public void removeLastMessage() {
        if (messages.size() > 0) {
            int position = messages.size() - 1;
            messages.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void updateLastMessage(String newText) {
        if (messages.size() > 0) {
            int position = messages.size() - 1;
            messages.get(position).setMessage(newText);
            notifyItemChanged(position);
        }
    }

    static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        private TextView messageText;

        UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }

        void bind(ChatMessage message) {
            messageText.setText(message.getMessage());
        }
    }

    static class AIMessageViewHolder extends RecyclerView.ViewHolder {
        private TextView messageText;

        AIMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }

        void bind(ChatMessage message) {
            messageText.setText(message.getMessage());
        }
    }
}
