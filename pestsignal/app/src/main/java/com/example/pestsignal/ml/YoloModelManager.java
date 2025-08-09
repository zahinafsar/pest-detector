package com.example.pestsignal.ml;

import android.content.Context;
import android.graphics.Bitmap;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YoloModelManager {
    private Context context;
    private Module module;
    private static final String MODEL_FILENAME = "model.torchscript";
    
    private final Map<Integer, String> classNames = new HashMap<Integer, String>() {{
        put(0, "grasshopper");
        put(1, "beetle");
        put(2, "aphid");
        put(3, "snail");
        put(4, "caterpillar");
    }};
    
    public YoloModelManager(Context context) {
        this.context = context;
        loadModel();
    }
    
    private void loadModel() {
        try {
            File modelFile = copyAssetToInternalStorage(MODEL_FILENAME);
            module = Module.load(modelFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private File copyAssetToInternalStorage(String assetName) {
        File modelFile = new File(context.getFilesDir(), assetName);
        
        if (!modelFile.exists()) {
            try {
                java.io.InputStream inputStream = context.getAssets().open(assetName);
                FileOutputStream outputStream = new FileOutputStream(modelFile);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
                inputStream.close();
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        return modelFile;
    }
    
    public List<Detection> detectInsects(Bitmap bitmap) {
        if (module == null) {
            return new ArrayList<>();
        }
        
        try {
            Tensor inputTensor = preprocessImage(bitmap);
            IValue output = module.forward(IValue.from(inputTensor));
            return processOutput(output);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    private Tensor preprocessImage(Bitmap bitmap) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true);
        
        Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                resizedBitmap,
                new float[]{0f, 0f, 0f},
                new float[]{255f, 255f, 255f}
        );
        
        return inputTensor;
    }
    
    private List<Detection> processOutput(IValue output) {
        List<Detection> detections = new ArrayList<>();
        
        try {
            Tensor outputTensor = output.toTensor();
            float[] outputData = outputTensor.getDataAsFloatArray();
            long[] outputShape = outputTensor.shape();
            
            // Debug logging
            System.out.println("YOLO output shape: " + java.util.Arrays.toString(outputShape));
            System.out.println("YOLO output data length: " + outputData.length);
            
            // YOLO output format: [batch, num_detections, 6] where 6 = [x, y, w, h, confidence, class_id]
            // or [num_detections, 6] for single batch
            int numDetections;
            int dataPerDetection = 6; // x, y, w, h, confidence, class_id
            
            if (outputShape.length == 3) {
                // Format: [batch, num_detections, 6]
                numDetections = (int) outputShape[1];
            } else if (outputShape.length == 2) {
                // Format: [num_detections, 6]
                numDetections = (int) outputShape[0];
            } else {
                // Fallback: assume all data is detections
                numDetections = outputData.length / dataPerDetection;
            }
            
            for (int i = 0; i < numDetections; i++) {
                int baseIndex = i * dataPerDetection;
                if (baseIndex + 5 < outputData.length) {
                    float x = outputData[baseIndex];        // center x
                    float y = outputData[baseIndex + 1];    // center y
                    float w = outputData[baseIndex + 2];    // width
                    float h = outputData[baseIndex + 3];    // height
                    float confidence = outputData[baseIndex + 4];
                    int classId = (int) outputData[baseIndex + 5];
                    
                    // Filter by confidence and valid class ID
                    if (confidence > 0.3f && classId >= 0 && classId <= 4) {
                        String label = classNames.get(classId);
                        if (label != null) {
                            // Convert center coordinates to bounding box coordinates
                            float x1 = x - w / 2;  // left
                            float y1 = y - h / 2;  // top
                            float x2 = x + w / 2;  // right
                            float y2 = y + h / 2;  // bottom
                            
                            // Ensure coordinates are within [0, 1] range
                            x1 = Math.max(0, Math.min(1, x1));
                            y1 = Math.max(0, Math.min(1, y1));
                            x2 = Math.max(0, Math.min(1, x2));
                            y2 = Math.max(0, Math.min(1, y2));
                            
                            detections.add(new Detection(
                                    label,
                                    confidence,
                                    new float[]{x1, y1, x2, y2}
                            ));
                        }
                    }
                }
            }
            
            // Sort detections by confidence (highest first)
            detections.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
            
            // Debug logging
            System.out.println("Found " + detections.size() + " detections");
            for (Detection detection : detections) {
                System.out.println("Detection: " + detection.getLabel() + " (confidence: " + detection.getConfidence() + ")");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            // Add debug logging
            System.out.println("Error processing YOLO output: " + e.getMessage());
        }
        
        // No sample detections - return empty list if no real detections found
        
        return detections;
    }
    
    public int getTotalCount(List<Detection> detections) {
        return detections.size();
    }
    
    public String getDetectionSummary(List<Detection> detections) {
        if (detections.isEmpty()) {
            return "No insects detected";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("Detected ").append(detections.size()).append(" insect(s):\n");
        
        for (Detection detection : detections) {
            summary.append("• ").append(detection.getLabel())
                   .append(" (").append(String.format("%.1f%%", detection.getConfidence() * 100))
                   .append(" confidence)\n");
        }
        
        return summary.toString();
    }
} 