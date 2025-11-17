package com.example.pestsignal;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Build;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final float CONFIDENCE_THRESHOLD = 0.4f;

    private ImageView imageView;
    private Bitmap bitmap;
    private Yolo11 detector;
    private Paint boxPaint;
    private Paint textPaint;

    private Button detectButton;
    private ImageButton settingsButton;
    private TextView placeholderText;
    private ScrollView detectionScrollView;
    private TextView insectName;
    private TextView insectType;
    private TextView insectDescription;
    private TextView preventionMethods;
    private OkHttpClient client;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply saved language preference
        applySavedLanguage();
        
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        // Initialize views
        initializeDetector();
        setupPaints();
        
        // Initialize pest information loader
        PestInfoLoader.initialize(this);

        detectButton = findViewById(R.id.detectButton);
        settingsButton = findViewById(R.id.settingsButton);
        imageView = findViewById(R.id.imageView);
        placeholderText = findViewById(R.id.placeholderText);
        detectionScrollView = findViewById(R.id.detectionScrollView);
        insectName = findViewById(R.id.insectName);
        insectType = findViewById(R.id.insectType);
        insectDescription = findViewById(R.id.insectDescription);
        preventionMethods = findViewById(R.id.preventionMethods);
        
        // Initialize OkHttp client with timeout
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
        // Set up image picker launcher
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        handleImageSelection(uri);
                    }
                }
        );
        
        // Set up button click listeners
        settingsButton.setOnClickListener(v -> openSettings());
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void initializeDetector() {
        detector = new Yolo11();
        detector.setModelFile("model.tflite");
        detector.initialModel(this);
        // Add hardware acceleration if available
        detector.addGPUDelegate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            detector.addNNApiDelegate();
        }
    }

    private void setupPaints() {
        boxPaint = new Paint();
        boxPaint.setStrokeWidth(5);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setColor(Color.RED);

        textPaint = new Paint();
        textPaint.setTextSize(50);
        textPaint.setColor(Color.GREEN);
        textPaint.setStyle(Paint.Style.FILL);
    }

    public void selectImage(View view) {
        imagePickerLauncher.launch("image/*");
    }

    public void predict(View view) {
        if (bitmap == null) {
            Toast.makeText(this, getString(R.string.please_select_image), Toast.LENGTH_SHORT).show();
            return;
        }

        if (detector == null) {
            Toast.makeText(this, "Detector not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        detectButton.setEnabled(false);
        detectButton.setText(getString(R.string.processing));

        // Run detection on background thread to avoid blocking UI
        new Thread(() -> {
            try {
                ArrayList<Recognition> recognitions = detector.detect(bitmap);

                // Log recognitions
                for (Recognition recognition : recognitions) {
                    Log.d("Recognition", recognition.toString());
                }

                // Update UI on main thread
                runOnUiThread(() -> {
                    Bitmap resultBitmap = drawDetections(recognitions);
                    imageView.setImageBitmap(resultBitmap);
                    
                    // Update detection info if we have recognitions
                    if (!recognitions.isEmpty()) {
                        updateDetectionInfo(recognitions);
                    } else {
                        detectionScrollView.setVisibility(View.GONE);
                        Toast.makeText(MainActivity.this, getString(R.string.no_detections_found), Toast.LENGTH_SHORT).show();
                    }
                    
                    // Reset button state
                    detectButton.setEnabled(true);
                    detectButton.setText(getString(R.string.detect_button));
                });
            } catch (Exception e) {
                Log.e("MainActivity", "Error during detection", e);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Detection error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    detectButton.setEnabled(true);
                    detectButton.setText(getString(R.string.detect_button));
                });
            }
        }).start();
    }

    private Bitmap drawDetections(ArrayList<Recognition> recognitions) {
        if (bitmap == null) {
            return null;
        }
        
        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);

        int detectionCount = 0;
        for (Recognition recognition : recognitions) {
            if (recognition.getConfidence() > CONFIDENCE_THRESHOLD) {
                RectF location = recognition.getLocation();
                String label = String.format("%s: %.1f%%", 
                    recognition.getLabelName(), 
                    recognition.getConfidence() * 100);

                // Draw bounding box
                canvas.drawRect(location, boxPaint);
                
                // Draw label with background for better visibility
                Paint labelBgPaint = new Paint();
                labelBgPaint.setColor(Color.BLACK);
                labelBgPaint.setStyle(Paint.Style.FILL);
                labelBgPaint.setAlpha(200);
                
                Paint labelTextPaint = new Paint();
                labelTextPaint.setTextSize(40);
                labelTextPaint.setColor(Color.WHITE);
                labelTextPaint.setStyle(Paint.Style.FILL);
                labelTextPaint.setAntiAlias(true);
                
                // Calculate text bounds
                android.graphics.Rect textBounds = new android.graphics.Rect();
                labelTextPaint.getTextBounds(label, 0, label.length(), textBounds);
                
                float textX = location.left;
                float textY = location.top - 10;
                
                // Draw background rectangle for text
                float padding = 8;
                canvas.drawRect(
                    textX - padding,
                    textY - textBounds.height() - padding,
                    textX + textBounds.width() + padding,
                    textY + padding,
                    labelBgPaint
                );
                
                // Draw text
                canvas.drawText(label, textX, textY, labelTextPaint);
                
                detectionCount++;
            }
        }
        
        if (detectionCount == 0) {
            Toast.makeText(this, getString(R.string.no_detections_found), Toast.LENGTH_SHORT).show();
        }

        return mutableBitmap;
    }
    
    private void applySavedLanguage() {
        String savedLanguage = getSharedPreferences("PestSignalPrefs", MODE_PRIVATE)
                .getString("language", "bn"); // Default to Bengali
        
        Locale locale = new Locale(savedLanguage);
        Locale.setDefault(locale);
        
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh pest info display if detection info is visible (in case language changed)
        if (detectionScrollView.getVisibility() == View.VISIBLE && bitmap != null) {
            // Re-run detection to update language, or just refresh the display
            // For now, we'll just refresh if there's already detection info
            // The app restarts on language change anyway, so this is mainly for edge cases
        }
    }
    
    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
    
//    private void checkPermissionAndPickImage() {
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
//            // Android 13+ (API 33+) - use READ_MEDIA_IMAGES
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
//                    != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(this,
//                        new String[]{Manifest.permission.READ_MEDIA_IMAGES},
//                        PERMISSION_REQUEST_CODE);
//            } else {
//                selectImage();
//            }
//        } else {
//            // Android 12 and below - use READ_EXTERNAL_STORAGE
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
//                    != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(this,
//                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
//                        PERMISSION_REQUEST_CODE);
//            } else {
//                selectImage();
//            }
//        }
//    }
    
    private void handleImageSelection(Uri imageUri) {
        try {
            bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            if (bitmap != null) {
                // Display the selected image immediately
                imageView.setImageBitmap(bitmap);
                placeholderText.setVisibility(View.GONE);
                detectionScrollView.setVisibility(View.GONE);
                
                // Reset detection info
                insectName.setText("");
                insectType.setText("");
                insectDescription.setText("");
                preventionMethods.setText("");
            } else {
                Toast.makeText(this, getString(R.string.error_loading_image), Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e("MainActivity", "Error loading image", e);
            Toast.makeText(this, "Error loading image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Update detection info UI with recognition results
     */
    private void updateDetectionInfo(ArrayList<Recognition> recognitions) {
        if (recognitions.isEmpty()) {
            detectionScrollView.setVisibility(View.GONE);
            return;
        }
        
        // Get the highest confidence detection
        Recognition bestRecognition = recognitions.get(0);
        for (Recognition recognition : recognitions) {
            if (recognition.getConfidence() > bestRecognition.getConfidence()) {
                bestRecognition = recognition;
            }
        }
        
        // Update UI with detection info
        String labelName = bestRecognition.getLabelName();
        float confidence = bestRecognition.getConfidence();
        
        // Get current language preference
        String language = PestInfoLoader.getCurrentLanguage(this);
        
        // Get pest information from local JSON
        PestInfo pestInfo = PestInfoLoader.getPestInfo(labelName);
        
        if (pestInfo != null) {
            // Use localized information
            String displayName = pestInfo.getName(language);
            if (displayName.isEmpty()) {
                displayName = labelName; // Fallback to label name
            }
            
            insectName.setText(String.format("%s (%.1f%%)", displayName, confidence * 100));
            insectType.setText(pestInfo.getType(language));
            insectDescription.setText(pestInfo.getDescription(language));
            preventionMethods.setText(pestInfo.getPrevention(language));
        } else {
            // Fallback if pest info not found
            insectName.setText(String.format("%s (%.1f%%)", labelName, confidence * 100));
            insectType.setText(getInsectType(labelName, language));
            insectDescription.setText(getInsectDescription(labelName, language));
            preventionMethods.setText(getPreventionMethods(labelName, language));
        }
        
        detectionScrollView.setVisibility(View.VISIBLE);
        
        // Optionally save to backend if user is logged in
        String userId = getSharedPreferences("PestSignalPrefs", MODE_PRIVATE)
                .getString("userId", null);
        if (userId != null) {
            String typeText = pestInfo != null ? pestInfo.getType(language) : getInsectType(labelName, language);
            saveDetectionReport(labelName, typeText, confidence);
        }
    }
    
    /**
     * Get insect type based on label name (fallback method)
     */
    private String getInsectType(String labelName, String language) {
        if (labelName == null || labelName.isEmpty()) {
            return "";
        }
        // Fallback: capitalize first letter
        String capitalized = labelName.substring(0, 1).toUpperCase() + labelName.substring(1);
        if ("bn".equals(language)) {
            return capitalized; // Could add Bengali translations here if needed
        }
        return capitalized;
    }
    
    /**
     * Get insect description based on label name (fallback method)
     */
    private String getInsectDescription(String labelName, String language) {
        if (labelName == null || labelName.isEmpty()) {
            return "";
        }
        if ("bn".equals(language)) {
            return "আপনার ছবিতে " + labelName + " শনাক্ত করা হয়েছে।";
        }
        return "This is a " + labelName + " detected in your image.";
    }
    
    /**
     * Get prevention methods based on label name (fallback method)
     */
    private String getPreventionMethods(String labelName, String language) {
        if (labelName == null || labelName.isEmpty()) {
            return "";
        }
        if ("bn".equals(language)) {
            return labelName + " এর জন্য সাধারণ প্রতিরোধ পদ্ধতির মধ্যে রয়েছে সঠিক স্বাস্থ্যবিধি এবং পর্যবেক্ষণ।";
        }
        return "General prevention methods for " + labelName + " include proper sanitation and monitoring.";
    }
    
    /**
     * Optional: Use backend detection as fallback or alternative
     * This method can be called if you want to use server-side detection instead of local Yolo11
     */
    private void detectPestsWithBackend() {
        if (bitmap == null) {
            Toast.makeText(this, getString(R.string.please_select_image), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading state
        detectButton.setEnabled(false);
        detectButton.setText(getString(R.string.processing));
        
        // Convert bitmap to file
        File imageFile = bitmapToFile(bitmap);
        if (imageFile == null) {
            Toast.makeText(this, getString(R.string.error_processing_image), Toast.LENGTH_SHORT).show();
            detectButton.setEnabled(true);
            detectButton.setText(getString(R.string.detect_button));
            return;
        }
        
        // Create multipart request
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "image.jpg", 
                        RequestBody.create(MediaType.parse("image/jpeg"), imageFile))
                .build();
        
        Request request = new Request.Builder()
                .url("http://10.0.2.2:8000/predict")
                .post(requestBody)
                .build();
        
        // Make the network call
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Detection failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    detectButton.setEnabled(true);
                    detectButton.setText(getString(R.string.detect_button));
                });
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBody = response.body().string();
                runOnUiThread(() -> {
                    detectButton.setEnabled(true);
                    detectButton.setText(getString(R.string.detect_button));
                    
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            boolean success = jsonResponse.optBoolean("success", false);
                            
                            if (success) {
                                String imageBase64 = jsonResponse.optString("image", "");
                                JSONArray detections = jsonResponse.optJSONArray("detections");
                                
                                if (!imageBase64.isEmpty()) {
                                    // Decode and display the base64 image
                                    displayBase64Image(imageBase64);
                                    
                                    // Display detection information
                                    if (detections != null && detections.length() > 0) {
                                        displayDetectionInfo(detections);
                                    } else {
                                        detectionScrollView.setVisibility(View.GONE);
                                    }
                                    
                                    Toast.makeText(MainActivity.this, getString(R.string.detection_completed), Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(MainActivity.this, getString(R.string.no_image_data), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(MainActivity.this, getString(R.string.detection_failed), Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            Toast.makeText(MainActivity.this, getString(R.string.error_parsing_response), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "HTTP Error: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    
    private void displayDetectionInfo(JSONArray detections) {
        try {
            if (detections.length() > 0) {
                JSONObject firstDetection = detections.getJSONObject(0);
                JSONObject insectInfo = firstDetection.optJSONObject("insect_info");
                
                if (insectInfo != null) {
                    String name = insectInfo.optString("name", getString(R.string.unknown_insect));
                    String type = insectInfo.optString("type", "");
                    String description = insectInfo.optString("description", "");
                    String prevention = insectInfo.optString("prevention", "");
                    double confidence = firstDetection.optDouble("confidence", 0.0);
                    
                    insectName.setText(name);
                    insectType.setText(type);
                    insectDescription.setText(description);
                    preventionMethods.setText(prevention);
                    
                    // Show the detection scroll view
                    detectionScrollView.setVisibility(View.VISIBLE);
                    
                    // Save detection report to database
                    saveDetectionReport(name, type, confidence);
                } else {
                    detectionScrollView.setVisibility(View.GONE);
                }
            } else {
                detectionScrollView.setVisibility(View.GONE);
            }
        } catch (JSONException e) {
            detectionScrollView.setVisibility(View.GONE);
        }
    }
    
    private void displayBase64Image(String base64String) {
        try {
            // Remove data URL prefix if present
            if (base64String.startsWith("data:image")) {
                base64String = base64String.substring(base64String.indexOf(",") + 1);
            }
            
            // Decode base64 string to byte array
            byte[] imageBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT);
            
            // Convert to bitmap
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                // Hide the placeholder text when image is displayed
                placeholderText.setVisibility(View.GONE);
            } else {
                Toast.makeText(this, getString(R.string.error_decoding_image), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_displaying_image) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private File bitmapToFile(Bitmap bitmap) {
        try {
            File file = new File(getCacheDir(), "temp_image.jpg");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos);
            byte[] bitmapData = bos.toByteArray();
            
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bitmapData);
            fos.flush();
            fos.close();
            
            return file;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private void showPermissionSettingsDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Image access permission is required to select images for pest detection. Please enable it in app settings.")
                .setPositiveButton("Settings", (dialog, which) -> {
                    // Open app settings
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void saveDetectionReport(String insectName, String insectType, double confidence) {
        // Get current user ID from shared preferences (assuming user is logged in)
        String userId = getSharedPreferences("PestSignalPrefs", MODE_PRIVATE)
                .getString("userId", null);
        
        if (userId == null) {
            // User not logged in, skip saving
            return;
        }
        
        // Create JSON payload
        JSONObject reportData = new JSONObject();
        try {
            reportData.put("userId", userId);
            reportData.put("insectName", insectName);
            reportData.put("insectType", insectType);
            reportData.put("confidence", confidence);
            reportData.put("location", ""); // Can be enhanced later with GPS
            reportData.put("notes", ""); // Can be enhanced later
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        
        // Create request body
        RequestBody requestBody = RequestBody.create(
                MediaType.parse("application/json"), 
                reportData.toString()
        );
        
        Request request = new Request.Builder()
                .url("http://10.0.2.2:8001/api/detection/report")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build();
        
        // Make the network call
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Silently fail - don't show error to user for background operation
                System.out.println("Failed to save detection report: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    System.out.println("Failed to save detection report: HTTP " + response.code());
                }
            }
        });
    }
    
//    @Override
//    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == PERMISSION_REQUEST_CODE) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                selectImage();
//            } else {
//                // Check if permission was permanently denied
//                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
//                    if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES)) {
//                        // Permission permanently denied, show settings dialog
//                        showPermissionSettingsDialog();
//                    } else {
//                        Toast.makeText(this, getString(R.string.permission_denied_images), Toast.LENGTH_SHORT).show();
//                    }
//                } else {
//                    if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
//                        // Permission permanently denied, show settings dialog
//                        showPermissionSettingsDialog();
//                    } else {
//                        Toast.makeText(this, getString(R.string.permission_denied_images), Toast.LENGTH_SHORT).show();
//                    }
//                }
//            }
//        }
//    }
}