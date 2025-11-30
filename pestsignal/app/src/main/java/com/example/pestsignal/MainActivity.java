package com.example.pestsignal;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
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
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private Button galleryButton;
    private Button cameraButton;
    private Uri cameraImageUri;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

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
        galleryButton = findViewById(R.id.galleryButton);
        cameraButton = findViewById(R.id.cameraButton);
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
        
        // Set up camera permission launcher
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        launchCamera();
                    } else {
                        Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_LONG).show();
                    }
                }
        );
        
        // Set up camera launcher
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && cameraImageUri != null) {
                        handleImageSelection(cameraImageUri);
                    } else if (!success) {
                        Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show();
                    }
                }
        );
        
        // Set up button click listeners
        settingsButton.setOnClickListener(v -> openSettings());
        galleryButton.setOnClickListener(v -> openGallery());
        cameraButton.setOnClickListener(v -> openCamera());
        
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

    private void openGallery() {
        imagePickerLauncher.launch("image/*");
    }

    private void openCamera() {
        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            // Request permission
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            launchCamera();
        }
    }

    private void launchCamera() {
        try {
            File photoFile = createImageFile();
            if (photoFile != null) {
                cameraImageUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        photoFile
                );
                cameraLauncher.launch(cameraImageUri);
            } else {
                Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e("MainActivity", "Error opening camera", e);
            Toast.makeText(this, "Error opening camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException e) {
            Log.e("MainActivity", "FileProvider error", e);
            Toast.makeText(this, "Error accessing camera. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        
        // Create directory if it doesn't exist
        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs();
        }
        
        // Fallback to cache directory if external files directory is not available
        if (storageDir == null) {
            storageDir = getCacheDir();
        }
        
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        return image;
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
    
    private void handleImageSelection(Uri imageUri) {
        try {
            Bitmap selectedBitmap = null;
            
            // Handle different URI schemes
            if (imageUri.getScheme() != null && imageUri.getScheme().equals("content")) {
                // Content URI (from gallery)
                selectedBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            } else if (imageUri.getScheme() != null && imageUri.getScheme().equals("file")) {
                // File URI (from camera)
                String filePath = imageUri.getPath();
                if (filePath != null) {
                    selectedBitmap = BitmapFactory.decodeFile(filePath);
                }
            } else {
                // Try direct path
                String filePath = imageUri.getPath();
                if (filePath != null) {
                    selectedBitmap = BitmapFactory.decodeFile(filePath);
                }
            }
            
            // Fallback: try MediaStore if other methods failed
            if (selectedBitmap == null) {
                selectedBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            }
            
            if (selectedBitmap != null) {
                bitmap = selectedBitmap;
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
            // Try alternative method for camera images
            try {
                String filePath = imageUri.getPath();
                if (filePath != null) {
                    bitmap = BitmapFactory.decodeFile(filePath);
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                        placeholderText.setVisibility(View.GONE);
                        detectionScrollView.setVisibility(View.GONE);
                        
                        // Reset detection info
                        insectName.setText("");
                        insectType.setText("");
                        insectDescription.setText("");
                        preventionMethods.setText("");
                        return;
                    }
                }
            } catch (Exception ex) {
                Log.e("MainActivity", "Error loading image from file path", ex);
            }
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
}