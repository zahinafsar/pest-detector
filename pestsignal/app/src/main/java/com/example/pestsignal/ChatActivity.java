package com.example.pestsignal;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView messagesRecyclerView;
    private ChatAdapter chatAdapter;
    private EditText messageInput;
    private ImageButton sendButton;
    private ImageButton backButton;
    private LinearLayout initialLoadingLayout;
    private OkHttpClient client;
    private String statisticsContext = "";
    private boolean isInitialized = false;

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String OPENAI_API_KEY = "OPENAI_API_KEY";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_chat);

        // Initialize views
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        backButton = findViewById(R.id.backButton);
        initialLoadingLayout = findViewById(R.id.initialLoadingLayout);

        // Initialize RecyclerView
        chatAdapter = new ChatAdapter();
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setAdapter(chatAdapter);

        // Initialize OkHttp client
        client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        // Disable input while loading
        messageInput.setEnabled(false);
        sendButton.setEnabled(false);
        
        // Set up button listeners
        backButton.setOnClickListener(v -> finish());
        sendButton.setOnClickListener(v -> sendMessage());
        
        // Enable/disable send button based on input
        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                sendButton.setEnabled(s.length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Handle Enter key
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND && messageInput.getText().length() > 0) {
                sendMessage();
                return true;
            }
            return false;
        });

        // Load statistics and initialize chat
        loadStatisticsAndInitialize();
    }


    private void loadStatisticsAndInitialize() {
        String userId = getSharedPreferences("PestSignalPrefs", MODE_PRIVATE)
                .getString("userId", null);

        if (userId == null) {
            // User not logged in, still allow chat but without statistics context
            initializeChat();
            return;
        }

        // Fetch statistics
        Request request = new Request.Builder()
                .url("http://10.0.2.2:8001/api/detection/stats/" + userId)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Log.e("ChatActivity", "Failed to load statistics", e);
                    // Still initialize chat without statistics
                    initializeChat();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBody = response.body().string();
                runOnUiThread(() -> {
                    try {
                        if (response.isSuccessful()) {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            boolean success = jsonResponse.optBoolean("success", false);
                            
                            if (success) {
                                JSONObject stats = jsonResponse.getJSONObject("stats");
                                statisticsContext = formatStatisticsForContext(stats);
                            }
                        }
                    } catch (JSONException e) {
                        Log.e("ChatActivity", "Error parsing statistics", e);
                    }
                    // Initialize chat after statistics are loaded (or failed)
                    initializeChat();
                });
            }
        });
    }

    private String formatStatisticsForContext(JSONObject stats) {
        try {
            StringBuilder context = new StringBuilder();
            context.append("User Statistics:\n");
            
            int totalDetections = stats.optInt("totalDetections", 0);
            double avgConfidence = stats.optDouble("averageConfidence", 0.0);
            
            context.append("- Total Detections: ").append(totalDetections).append("\n");
            context.append("- Average Confidence: ").append(String.format("%.1f%%", avgConfidence * 100)).append("\n");
            
            // Top insects
            JSONArray detectionsByName = stats.optJSONArray("detectionsByName");
            if (detectionsByName != null && detectionsByName.length() > 0) {
                context.append("- Top Detected Insects:\n");
                for (int i = 0; i < Math.min(detectionsByName.length(), 5); i++) {
                    JSONObject item = detectionsByName.getJSONObject(i);
                    String name = item.optString("name", "Unknown");
                    int count = item.optInt("count", 0);
                    context.append("  ").append(name).append(": ").append(count).append(" times\n");
                }
            }
            
            // Top types
            JSONArray detectionsByType = stats.optJSONArray("detectionsByType");
            if (detectionsByType != null && detectionsByType.length() > 0) {
                context.append("- Top Insect Types:\n");
                for (int i = 0; i < Math.min(detectionsByType.length(), 5); i++) {
                    JSONObject item = detectionsByType.getJSONObject(i);
                    String type = item.optString("type", "Unknown");
                    int count = item.optInt("count", 0);
                    context.append("  ").append(type).append(": ").append(count).append(" times\n");
                }
            }
            
            // Recent detections
            JSONArray recentDetections = stats.optJSONArray("recentDetections");
            if (recentDetections != null && recentDetections.length() > 0) {
                context.append("- Recent Detections:\n");
                for (int i = 0; i < Math.min(recentDetections.length(), 3); i++) {
                    JSONObject detection = recentDetections.getJSONObject(i);
                    String insectName = detection.optString("insectName", "Unknown");
                    String insectType = detection.optString("insectType", "Unknown");
                    double confidence = detection.optDouble("confidence", 0.0);
                    context.append("  ").append(insectName).append(" (").append(insectType)
                            .append(") - ").append(String.format("%.1f%%", confidence * 100)).append("\n");
                }
            }
            
            return context.toString();
        } catch (JSONException e) {
            Log.e("ChatActivity", "Error formatting statistics", e);
            return "";
        }
    }

    private void initializeChat() {
        if (isInitialized) return;
        isInitialized = true;
        
        // Hide initial loading and show chat
        runOnUiThread(() -> {
            initialLoadingLayout.setVisibility(View.GONE);
            messageInput.setEnabled(true);
            sendButton.setEnabled(messageInput.getText().length() > 0);
            // Add welcome message
            addWelcomeMessage();
        });
    }

    private void addWelcomeMessage() {
        String welcomeMessage = getString(R.string.ai_welcome_message);
        if (!statisticsContext.isEmpty()) {
            welcomeMessage += "\n\n" + getString(R.string.ai_context_loaded);
        }
        ChatMessage welcome = new ChatMessage(welcomeMessage, ChatMessage.TYPE_AI);
        chatAdapter.addMessage(welcome);
        scrollToBottom();
    }

    private void sendMessage() {
        String message = messageInput.getText().toString().trim();
        if (message.isEmpty()) {
            return;
        }

        // Clear input
        messageInput.setText("");

        // Get conversation history BEFORE adding the current message
        List<ChatMessage> recentMessages = getRecentMessages(10);

        // Add user message to chat
        ChatMessage userMessage = new ChatMessage(message, ChatMessage.TYPE_USER);
        chatAdapter.addMessage(userMessage);
        
        // Add loading message
        ChatMessage loadingMessage = new ChatMessage(getString(R.string.ai_thinking), ChatMessage.TYPE_AI);
        chatAdapter.addMessage(loadingMessage);
        scrollToBottom();

        // Disable send button
        sendButton.setEnabled(false);

        // Use hardcoded OpenAI API key
        String apiKey = OPENAI_API_KEY;

        // Build system message with context
        String systemMessage = "You are a helpful AI assistant for PestSignal, an insect detection application. ";
        if (!statisticsContext.isEmpty()) {
            systemMessage += "Here is the user's detection statistics:\n\n" + statisticsContext + "\n\n";
        }
        systemMessage += "Help the user with questions about pest detection, prevention methods, and their detection history.";

        // Create request to OpenAI
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "gpt-3.5-turbo");
            
            JSONArray messages = new JSONArray();
            
            // System message
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemMessage);
            messages.put(systemMsg);
            
            // Add conversation history (last 10 messages for context)
            for (ChatMessage msg : recentMessages) {
                JSONObject msgObj = new JSONObject();
                msgObj.put("role", msg.getType() == ChatMessage.TYPE_USER ? "user" : "assistant");
                msgObj.put("content", msg.getMessage());
                messages.put(msgObj);
            }
            
            // Current user message
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", message);
            messages.put(userMsg);
            
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 500);

            RequestBody body = RequestBody.create(JSON, requestBody.toString());
            Request request = new Request.Builder()
                    .url(OPENAI_API_URL)
                    .post(body)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        // Remove loading message and add error
                        chatAdapter.removeLastMessage();
                        sendButton.setEnabled(true);
                        String errorMsg = getString(R.string.ai_error) + ": " + e.getMessage();
                        ChatMessage errorMessage = new ChatMessage(errorMsg, ChatMessage.TYPE_AI);
                        chatAdapter.addMessage(errorMessage);
                        scrollToBottom();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body().string();
                    runOnUiThread(() -> {
                        // Remove loading message
                        chatAdapter.removeLastMessage();
                        sendButton.setEnabled(true);

                        try {
                            if (response.isSuccessful()) {
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                JSONArray choices = jsonResponse.getJSONArray("choices");
                                if (choices.length() > 0) {
                                    JSONObject choice = choices.getJSONObject(0);
                                    JSONObject messageObj = choice.getJSONObject("message");
                                    String aiResponse = messageObj.getString("content");
                                    
                                    ChatMessage aiMessage = new ChatMessage(aiResponse, ChatMessage.TYPE_AI);
                                    chatAdapter.addMessage(aiMessage);
                                    scrollToBottom();
                                } else {
                                    ChatMessage errorMessage = new ChatMessage(
                                            getString(R.string.ai_no_response), ChatMessage.TYPE_AI);
                                    chatAdapter.addMessage(errorMessage);
                                    scrollToBottom();
                                }
                            } else {
                                String errorMsg = getString(R.string.ai_error);
                                try {
                                    JSONObject errorJson = new JSONObject(responseBody);
                                    if (errorJson.has("error")) {
                                        JSONObject error = errorJson.getJSONObject("error");
                                        if (error.has("message")) {
                                            errorMsg += ": " + error.getString("message");
                                        } else if (error.has("type")) {
                                            errorMsg += ": " + error.getString("type");
                                        }
                                    }
                                } catch (JSONException e) {
                                    errorMsg += " (HTTP " + response.code() + ")";
                                }
                                
                                ChatMessage errorMessage = new ChatMessage(errorMsg, ChatMessage.TYPE_AI);
                                chatAdapter.addMessage(errorMessage);
                                scrollToBottom();
                            }
                        } catch (JSONException e) {
                            Log.e("ChatActivity", "Error parsing OpenAI response", e);
                            ChatMessage errorMessage = new ChatMessage(
                                    getString(R.string.ai_error) + ": " + e.getMessage(), ChatMessage.TYPE_AI);
                            chatAdapter.addMessage(errorMessage);
                            scrollToBottom();
                        }
                    });
                }
            });
        } catch (JSONException e) {
            Log.e("ChatActivity", "Error creating request", e);
            chatAdapter.removeLastMessage();
            sendButton.setEnabled(true);
            Toast.makeText(this, getString(R.string.ai_error) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private List<ChatMessage> getRecentMessages(int count) {
        List<ChatMessage> allMessages = chatAdapter.getMessages();
        // Filter out welcome/system messages and get last N messages
        List<ChatMessage> recentMessages = new ArrayList<>();
        int startIndex = Math.max(0, allMessages.size() - count);
        for (int i = startIndex; i < allMessages.size(); i++) {
            recentMessages.add(allMessages.get(i));
        }
        return recentMessages;
    }

    private void scrollToBottom() {
        messagesRecyclerView.post(() -> {
            if (chatAdapter.getItemCount() > 0) {
                messagesRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
            }
        });
    }
}