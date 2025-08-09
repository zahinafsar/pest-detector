package com.example.pestsignal;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.pestsignal.ml.Detection;
import com.example.pestsignal.ml.ImageProcessor;
import com.example.pestsignal.ml.YoloModelManager;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class YoloDetectionActivity extends AppCompatActivity {

    private Button selectImageButton;
    private ImageView imageView;
    private TextView placeholderText;
    private ScrollView resultsScrollView;
    private TextView resultsTextView;
    private ProgressBar progressBar;
    private TextView totalValueTextView;
    
    private YoloModelManager yoloModelManager;
    private ExecutorService executorService;
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
        setContentView(R.layout.activity_yolo_detection);
        
        // Initialize views
        selectImageButton = findViewById(R.id.selectImageButton);
        imageView = findViewById(R.id.imageView);
        placeholderText = findViewById(R.id.placeholderText);
        resultsScrollView = findViewById(R.id.resultsScrollView);
        resultsTextView = findViewById(R.id.resultsTextView);
        progressBar = findViewById(R.id.progressBar);
        totalValueTextView = findViewById(R.id.totalValueTextView);
        
        // Initialize YOLO model manager
        yoloModelManager = new YoloModelManager(this);
        executorService = Executors.newSingleThreadExecutor();
        
        // Set up button click listeners
        selectImageButton.setOnClickListener(v -> checkPermissionAndPickImage());
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
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
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            processImage(bitmap);
        } catch (IOException e) {
            Toast.makeText(this, "Error loading image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void processImage(Bitmap bitmap) {
        // Show loading state
        progressBar.setVisibility(View.VISIBLE);
        selectImageButton.setEnabled(false);
        resultsScrollView.setVisibility(View.GONE);
        
        // Process image in background thread
        executorService.execute(() -> {
            try {
                // Preprocess image for YOLO
                Bitmap preprocessedBitmap = ImageProcessor.preprocessForYolo(bitmap);
                
                // Run detection
                List<Detection> detections = yoloModelManager.detectInsects(preprocessedBitmap);
                int totalCount = yoloModelManager.getTotalCount(detections);
                String summary = yoloModelManager.getDetectionSummary(detections);
                
                // Update UI on main thread
                runOnUiThread(() -> {
                    if (detections.isEmpty()) {
                        // Show error message when no detections found
                        imageView.setImageBitmap(bitmap);
                        placeholderText.setVisibility(View.GONE);
                        resultsScrollView.setVisibility(View.GONE);
                        totalValueTextView.setVisibility(View.GONE);
                        Toast.makeText(YoloDetectionActivity.this, 
                                "No insects detected in this image. Try a different image.", 
                                Toast.LENGTH_LONG).show();
                    } else {
                        displayResults(bitmap, detections, totalCount, summary);
                    }
                    progressBar.setVisibility(View.GONE);
                    selectImageButton.setEnabled(true);
                });
                
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(YoloDetectionActivity.this, 
                            "Detection failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    progressBar.setVisibility(View.GONE);
                    selectImageButton.setEnabled(true);
                });
            }
        });
    }

    private void displayResults(Bitmap bitmap, List<Detection> detections, int totalCount, String summary) {
        // Display the image
        imageView.setImageBitmap(bitmap);
        placeholderText.setVisibility(View.GONE);
        
        // Display detection summary
        resultsTextView.setText(summary);
        totalValueTextView.setText(String.format("Total Insects: %d", totalCount));
        totalValueTextView.setVisibility(View.VISIBLE);
        
        // Show results
        resultsScrollView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickImage();
            } else {
                Toast.makeText(this, "Permission denied for accessing images", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
} 