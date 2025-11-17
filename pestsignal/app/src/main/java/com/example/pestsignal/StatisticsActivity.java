package com.example.pestsignal;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
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

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
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
    private BarChart topInsectsChart;
    private BarChart topTypesChart;

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
        topInsectsChart = findViewById(R.id.topInsectsChart);
        topTypesChart = findViewById(R.id.topTypesChart);
        
        // Initialize charts
        setupChart(topInsectsChart);
        setupChart(topTypesChart);
        
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
        if (detectionsByName != null) {
            if (topInsectsChart != null) {
                populateBarChart(detectionsByName, topInsectsChart, "name", Color.parseColor("#4CAF50"));
            }
            if (topInsectsLayout != null) {
                displayTopItems(detectionsByName, topInsectsLayout, "name");
            }
        }
        
        // Top types
        JSONArray detectionsByType = stats.optJSONArray("detectionsByType");
        if (detectionsByType != null) {
            if (topTypesChart != null) {
                populateBarChart(detectionsByType, topTypesChart, "type", Color.parseColor("#2196F3"));
            }
            if (topTypesLayout != null) {
                displayTopItems(detectionsByType, topTypesLayout, "type");
            }
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
        
        if (items.length() == 0) {
            TextView emptyView = new TextView(this);
            emptyView.setText(getString(R.string.no_recent_detections));
            emptyView.setTextSize(14);
            emptyView.setTextColor(Color.parseColor("#999999"));
            emptyView.setPadding(16, 16, 16, 16);
            emptyView.setGravity(android.view.Gravity.CENTER);
            layout.addView(emptyView);
            return;
        }
        
        for (int i = 0; i < Math.min(items.length(), 5); i++) {
            JSONObject item = items.getJSONObject(i);
            String name = item.optString(key, "Unknown");
            int count = item.optInt("count", 0);
            
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setPadding(12, 10, 12, 10);
            itemLayout.setBackgroundColor(Color.parseColor("#F9F9F9"));
            
            if (i > 0) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.topMargin = 6;
                itemLayout.setLayoutParams(params);
            }
            
            TextView nameView = new TextView(this);
            nameView.setText(name);
            nameView.setTextSize(15);
            nameView.setTextColor(Color.parseColor("#333333"));
            nameView.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ));
            itemLayout.addView(nameView);
            
            TextView countView = new TextView(this);
            countView.setText(String.valueOf(count));
            countView.setTextSize(15);
            countView.setTextColor(Color.parseColor("#666666"));
            countView.setTypeface(null, android.graphics.Typeface.BOLD);
            itemLayout.addView(countView);
            
            layout.addView(itemLayout);
        }
    }
    
    private void displayRecentDetections(JSONArray recentDetections) throws JSONException {
        if (recentDetectionsLayout == null || recentDetections == null) return;
        
        recentDetectionsLayout.removeAllViews();
        
        if (recentDetections.length() == 0) {
            TextView emptyView = new TextView(this);
            emptyView.setText(getString(R.string.no_recent_detections));
            emptyView.setTextSize(14);
            emptyView.setTextColor(Color.parseColor("#999999"));
            emptyView.setPadding(16, 16, 16, 16);
            emptyView.setGravity(android.view.Gravity.CENTER);
            recentDetectionsLayout.addView(emptyView);
            return;
        }
        
        for (int i = 0; i < Math.min(recentDetections.length(), 5); i++) {
            JSONObject detection = recentDetections.getJSONObject(i);
            String insectName = detection.optString("insectName", "Unknown");
            String insectType = detection.optString("insectType", "Unknown");
            double confidence = detection.optDouble("confidence", 0.0);
            String createdAt = detection.optString("createdAt", "");
            
            // Create container for each detection item
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setPadding(16, 12, 16, 12);
            itemLayout.setBackgroundColor(Color.parseColor("#F9F9F9"));
            
            if (i > 0) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.topMargin = 8;
                itemLayout.setLayoutParams(params);
            }
            
            // Insect name and type
            TextView nameView = new TextView(this);
            nameView.setText(insectName + " (" + insectType + ")");
            nameView.setTextSize(16);
            nameView.setTextColor(Color.parseColor("#333333"));
            nameView.setTypeface(null, android.graphics.Typeface.BOLD);
            itemLayout.addView(nameView);
            
            // Confidence and date
            LinearLayout detailLayout = new LinearLayout(this);
            detailLayout.setOrientation(LinearLayout.HORIZONTAL);
            detailLayout.setPadding(0, 4, 0, 0);
            
            TextView confidenceView = new TextView(this);
            confidenceView.setText(String.format("%.1f%%", confidence * 100));
            confidenceView.setTextSize(14);
            confidenceView.setTextColor(Color.parseColor("#4CAF50"));
            confidenceView.setTypeface(null, android.graphics.Typeface.BOLD);
            detailLayout.addView(confidenceView);
            
            TextView dateView = new TextView(this);
            String dateStr = createdAt.length() >= 10 ? createdAt.substring(0, 10) : createdAt;
            dateView.setText(" • " + dateStr);
            dateView.setTextSize(14);
            dateView.setTextColor(Color.parseColor("#666666"));
            detailLayout.addView(dateView);
            
            itemLayout.addView(detailLayout);
            recentDetectionsLayout.addView(itemLayout);
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
    
    private void setupChart(BarChart chart) {
        if (chart == null) return;
        
        // Disable description
        chart.getDescription().setEnabled(false);
        
        // Enable touch gestures
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(false);
        
        // Disable legend
        chart.getLegend().setEnabled(false);
        
        // Configure X-axis
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(5);
        xAxis.setTextColor(Color.parseColor("#666666"));
        xAxis.setTextSize(11f);
        
        // Configure left Y-axis
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#E0E0E0"));
        leftAxis.setTextColor(Color.parseColor("#666666"));
        leftAxis.setTextSize(11f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setGranularity(1f);
        
        // Configure right Y-axis
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);
        
        // Set animation
        chart.animateY(1000);
    }
    
    private void populateBarChart(JSONArray items, BarChart chart, String key, int color) {
        if (chart == null || items == null || items.length() == 0) {
            chart.setVisibility(View.GONE);
            return;
        }
        
        chart.setVisibility(View.VISIBLE);
        
        ArrayList<BarEntry> entries = new ArrayList<>();
        final ArrayList<String> labels = new ArrayList<>();
        
        int maxItems = Math.min(items.length(), 5);
        
        try {
            for (int i = 0; i < maxItems; i++) {
                JSONObject item = items.getJSONObject(i);
                String label = item.optString(key, "Unknown");
                int count = item.optInt("count", 0);
                
                entries.add(new BarEntry(i, count));
                labels.add(label);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        
        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColor(color);
        dataSet.setValueTextColor(Color.parseColor("#333333"));
        dataSet.setValueTextSize(11f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });
        
        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f);
        
        chart.setData(barData);
        
        // Set labels
        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setLabelCount(labels.size());
        
        chart.invalidate();
    }
}
