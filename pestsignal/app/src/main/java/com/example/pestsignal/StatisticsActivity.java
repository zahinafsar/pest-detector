package com.example.pestsignal;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StatisticsActivity extends AppCompatActivity {

    private ScrollView scrollView;
    private LinearLayout contentLayout;
    private ProgressBar progressBar;
    private ImageButton refreshButton;
    private ImageButton backButton;
    private OkHttpClient client;
    
    // Statistics views
    private TextView totalDetectionsText;
    private TextView avgConfidenceText;
    private LinearLayout topInsectsLayout;
    private LinearLayout topTypesLayout;
    private LinearLayout recentDetectionsLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply saved language preference
        applySavedLanguage();
        
        setContentView(R.layout.activity_statistics);
        
        // Initialize views
        scrollView = findViewById(R.id.statisticsScrollView);
        contentLayout = findViewById(R.id.statisticsContentLayout);
        progressBar = findViewById(R.id.statisticsProgressBar);
        refreshButton = findViewById(R.id.refreshButton);
        backButton = findViewById(R.id.backButton);
        
        // Statistics views
        totalDetectionsText = findViewById(R.id.totalDetectionsText);
        avgConfidenceText = findViewById(R.id.avgConfidenceText);
        topInsectsLayout = findViewById(R.id.topInsectsLayout);
        topTypesLayout = findViewById(R.id.topTypesLayout);
        recentDetectionsLayout = findViewById(R.id.recentDetectionsLayout);
        
        // Initialize OkHttp client
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
        // Set up buttons
        if (refreshButton != null) {
            refreshButton.setOnClickListener(v -> loadStatistics());
        }
        
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        
        // Load statistics on create
        loadStatistics();
    }
    
    private void applySavedLanguage() {
        String savedLanguage = getSharedPreferences("PestSignalPrefs", MODE_PRIVATE)
                .getString("language", null);
        
        if (savedLanguage != null) {
            Locale locale = new Locale(savedLanguage);
            Locale.setDefault(locale);
            
            Resources resources = getResources();
            Configuration config = resources.getConfiguration();
            config.setLocale(locale);
            resources.updateConfiguration(config, resources.getDisplayMetrics());
        }
    }
    
    private void loadStatistics() {
        String userId = getSharedPreferences("PestSignalPrefs", MODE_PRIVATE)
                .getString("userId", null);
        
        if (userId == null) {
            Toast.makeText(this, getString(R.string.user_not_logged_in), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        showLoading(true);
        
        Request request = new Request.Builder()
                .url("http://10.0.2.2:8001/api/detection/stats/" + userId)
                .get()
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(StatisticsActivity.this, 
                            getString(R.string.failed_to_load_statistics) + ": " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                });
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBody = response.body().string();
                runOnUiThread(() -> {
                    showLoading(false);
                    
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            boolean success = jsonResponse.optBoolean("success", false);
                            
                            if (success) {
                                JSONObject stats = jsonResponse.getJSONObject("stats");
                                displayStatistics(stats);
                            } else {
                                Toast.makeText(StatisticsActivity.this, 
                                        getString(R.string.failed_to_load_statistics), 
                                        Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            Toast.makeText(StatisticsActivity.this, 
                                    getString(R.string.error_parsing_response), 
                                    Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(StatisticsActivity.this, 
                                "HTTP Error: " + response.code(), 
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    
    private void displayStatistics(JSONObject stats) throws JSONException {
        // Total detections
        int totalDetections = stats.optInt("totalDetections", 0);
        if (totalDetectionsText != null) {
            totalDetectionsText.setText(String.valueOf(totalDetections));
        }
        
        // Average confidence
        double avgConfidence = stats.optDouble("averageConfidence", 0.0);
        if (avgConfidenceText != null) {
            avgConfidenceText.setText(String.format("%.1f%%", avgConfidence * 100));
        }
        
        // Top insects
        JSONArray detectionsByName = stats.optJSONArray("detectionsByName");
        if (detectionsByName != null && topInsectsLayout != null) {
            displayTopItems(detectionsByName, topInsectsLayout, "name");
        }
        
        // Top types
        JSONArray detectionsByType = stats.optJSONArray("detectionsByType");
        if (detectionsByType != null && topTypesLayout != null) {
            displayTopItems(detectionsByType, topTypesLayout, "type");
        }
        
        // Recent detections
        JSONArray recentDetections = stats.optJSONArray("recentDetections");
        if (recentDetections != null && recentDetectionsLayout != null) {
            displayRecentDetections(recentDetections);
        }
    }
    
    private void displayTopItems(JSONArray items, LinearLayout layout, String key) throws JSONException {
        if (layout == null || items == null) return;
        
        layout.removeAllViews();
        
        for (int i = 0; i < Math.min(items.length(), 5); i++) {
            JSONObject item = items.getJSONObject(i);
            String name = item.optString(key, "Unknown");
            int count = item.optInt("count", 0);
            
            TextView textView = new TextView(this);
            textView.setText(name + ": " + count);
            textView.setPadding(16, 8, 16, 8);
            textView.setTextSize(16);
            
            layout.addView(textView);
        }
    }
    
    private void displayRecentDetections(JSONArray recentDetections) throws JSONException {
        if (recentDetectionsLayout == null || recentDetections == null) return;
        
        recentDetectionsLayout.removeAllViews();
        
        for (int i = 0; i < Math.min(recentDetections.length(), 5); i++) {
            JSONObject detection = recentDetections.getJSONObject(i);
            String insectName = detection.optString("insectName", "Unknown");
            String insectType = detection.optString("insectType", "Unknown");
            double confidence = detection.optDouble("confidence", 0.0);
            String createdAt = detection.optString("createdAt", "");
            
            TextView textView = new TextView(this);
            String dateStr = createdAt.length() >= 10 ? createdAt.substring(0, 10) : createdAt;
            textView.setText(insectName + " (" + insectType + ") - " + 
                           String.format("%.1f%%", confidence * 100) + " - " + dateStr);
            textView.setPadding(16, 8, 16, 8);
            textView.setTextSize(14);
            
            recentDetectionsLayout.addView(textView);
        }
    }
    
    private void showLoading(boolean show) {
        if (show) {
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            if (contentLayout != null) contentLayout.setVisibility(View.GONE);
            if (refreshButton != null) refreshButton.setEnabled(false);
            if (backButton != null) backButton.setEnabled(false);
        } else {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            if (contentLayout != null) contentLayout.setVisibility(View.VISIBLE);
            if (refreshButton != null) refreshButton.setEnabled(true);
            if (backButton != null) backButton.setEnabled(true);
        }
    }
}
