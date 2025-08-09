package com.example.pestsignal;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
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

    private Button detectButton;
    private Button yoloDetectButton;
    private ImageButton settingsButton;
    private ImageView imageView;
    private TextView placeholderText;
    private ScrollView detectionScrollView;
    private TextView insectName;
    private TextView insectType;
    private TextView insectDescription;
    private TextView preventionMethods;
    private OkHttpClient client;
    private Bitmap selectedImage;
    private static final int PERMISSION_REQUEST_CODE = 100;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null && data.getData() != null) {
                        handleImageSelection(data.getData());
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply saved language preference
        applySavedLanguage();
        
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        // Initialize views
        detectButton = findViewById(R.id.detectButton);
        yoloDetectButton = findViewById(R.id.yoloDetectButton);
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
        
        // Set up button click listeners
        detectButton.setOnClickListener(v -> checkPermissionAndPickImage());
        yoloDetectButton.setOnClickListener(v -> openYoloDetection());
        settingsButton.setOnClickListener(v -> openSettings());
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
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
    
    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
    
    private void openYoloDetection() {
        Intent intent = new Intent(this, YoloDetectionActivity.class);
        startActivity(intent);
    }
    
    private void checkPermissionAndPickImage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 
                    PERMISSION_REQUEST_CODE);
        } else {
            pickImage();
        }
    }
    
    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }
    
    private void handleImageSelection(Uri imageUri) {
        try {
            selectedImage = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            // Don't display the selected image immediately, wait for backend response
            detectPests();
        } catch (IOException e) {
            Toast.makeText(this, "Error loading image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void detectPests() {
        if (selectedImage == null) {
            Toast.makeText(this, getString(R.string.please_select_image), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading state
        detectButton.setEnabled(false);
        detectButton.setText(getString(R.string.processing));
        
        // Convert bitmap to file
        File imageFile = bitmapToFile(selectedImage);
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
                    
                    insectName.setText(name);
                    insectType.setText(type);
                    insectDescription.setText(description);
                    preventionMethods.setText(prevention);
                    
                    // Show the detection scroll view
                    detectionScrollView.setVisibility(View.VISIBLE);
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
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickImage();
            } else {
                Toast.makeText(this, getString(R.string.permission_denied_images), Toast.LENGTH_SHORT).show();
            }
        }
    }
}