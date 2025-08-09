# YOLOv11 Android Insect Detection

This implementation follows the [Roboflow guide](https://blog.roboflow.com/yolov11-android-app/) to create a YOLOv11 Android app for insect detection, but implemented in Java instead of Kotlin.

## Features

- **On-device YOLOv11 inference** using PyTorch Mobile
- **Insect detection** (grasshopper, beetle, aphid, snail, caterpillar)
- **Real-time image processing** with background threading
- **Modern UI** with progress indicators and results display
- **Detection summary** with confidence scores

## Project Structure

```
pestsignal/app/src/main/java/com/example/pestsignal/
├── MainActivity.java                    # Main app activity
├── YoloDetectionActivity.java          # YOLO detection activity
└── ml/
    ├── Detection.java                  # Detection result data class
    ├── ImageProcessor.java             # Image preprocessing utilities
    └── YoloModelManager.java          # PyTorch model manager
```

## Setup Instructions

### 1. Model Preparation

To use your own YOLO model:

1. **Train a YOLOv11 model** using Roboflow or your preferred method
2. **Convert to TorchScript** using the provided script:

```bash
# Install ultralytics
pip install ultralytics

# Convert your model
python convert_model.py
```

3. **Place the model** in `pestsignal/app/src/main/assets/model.torchscript`

### 2. Building the App

1. **Sync Gradle** to download PyTorch Mobile dependencies
2. **Build the project** in Android Studio
3. **Run on device** (PyTorch Mobile works best on physical devices)

## Usage

1. **Launch the app** and tap "YOLO Insect Detection"
2. **Select an image** from your gallery
3. **Wait for processing** (the model runs on-device)
4. **View results** showing detected insects and confidence scores

## Model Configuration

The app is configured to detect insects:
- **Class 0**: Grasshopper
- **Class 1**: Beetle
- **Class 2**: Aphid
- **Class 3**: Snail
- **Class 4**: Caterpillar

To modify for different objects:

1. **Update class names** in `YoloModelManager.java`:
```java
private final Map<Integer, String> classNames = new HashMap<Integer, String>() {{
    put(0, "your_class_1");
    put(1, "your_class_2");
    // ... add more classes
}};
```

2. **Update detection summary** in `getDetectionSummary()` method

## Technical Details

### PyTorch Mobile Integration

- Uses **PyTorch Mobile 1.13.1** for on-device inference
- **TorchScript format** for optimized mobile deployment
- **Background processing** to prevent UI blocking

### Image Processing

- **640x640 input size** (YOLO standard)
- **RGB normalization** with mean [0,0,0] and std [255,255,255]
- **Automatic resizing** and preprocessing

### Performance Considerations

- **Model loading** happens once at app startup
- **Background threading** for inference to keep UI responsive
- **Memory efficient** image processing

## Troubleshooting

### Common Issues

1. **Model not loading**: Ensure `model.torchscript` is in the assets folder
2. **Slow inference**: Use a physical device instead of emulator
3. **Memory issues**: Reduce image size or use smaller model

### Debug Mode

Enable debug logging in `YoloModelManager.java`:
```java
// Add logging in loadModel() method
Log.d("YoloModelManager", "Model loaded successfully");
```

## Dependencies

The app requires these additional dependencies (already added to `build.gradle.kts`):

```kotlin
implementation("org.pytorch:pytorch_android:1.13.1")
implementation("org.pytorch:pytorch_android_torchvision:1.13.1")
```

## Credits

Based on the [Roboflow YOLOv11 Android guide](https://blog.roboflow.com/yolov11-android-app/) by Aryan Vasudevan, adapted for Java implementation. 