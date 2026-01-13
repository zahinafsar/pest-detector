package com.example.pestsignal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;


public class Yolo11 {
    // Model constants
    private static final Size INPUT_SIZE = new Size(320, 320);
    private static final int[] OUTPUT_SIZE = new int[]{1, 13, 2100};
    private static final String LABEL_FILE = "label.txt";

    // Detection thresholds
    private static final float DETECT_THRESHOLD = 0.25f;
    private static final float IOU_THRESHOLD = 0.45f;
    private static final float IOU_CLASS_DUPLICATED_THRESHOLD = 0.7f;

    private String modelFile;
    private int bitmapHeight;
    private int bitmapWidth;

    private Interpreter tflite;
    private List<String> labels;
    private final Interpreter.Options options = new Interpreter.Options();

    public void setModelFile(String modelFile) {
        this.modelFile = modelFile;
        Log.d("Yolo11", "Model file set: " + modelFile);
    }


    /**
     * Initialize the TFLite model and labels
     * @param context Android context
     */
    public void initialModel(Context context) {
        try {
            Log.d("Yolo11", "Loading model: " + modelFile);
            ByteBuffer tfliteModel = FileUtil.loadMappedFile(context, modelFile);
            tflite = new Interpreter(tfliteModel, options);
            Log.i("Yolo11", "Model loaded successfully");

            // Print input and output tensor shapes
            int inputTensorCount = tflite.getInputTensorCount();
            int outputTensorCount = tflite.getOutputTensorCount();
            Log.d("Yolo11", "Input tensor count: " + inputTensorCount);
            Log.d("Yolo11", "Output tensor count: " + outputTensorCount);

            for (int i = 0; i < inputTensorCount; i++) {
                int[] inputShape = tflite.getInputTensor(i).shape();
                Log.d("Yolo11", "Input tensor " + i + " shape: " + Arrays.toString(inputShape));
            }

            for (int i = 0; i < outputTensorCount; i++) {
                int[] outputShape = tflite.getOutputTensor(i).shape();
                DataType outputType = tflite.getOutputTensor(i).dataType();
                Log.d("Yolo11", "Output tensor " + i + " shape: " + Arrays.toString(outputShape) + ", type: " + outputType);
            }

            // Load labels
            try {
                labels = FileUtil.loadLabels(context, LABEL_FILE);
                Log.i("Yolo11", "Labels loaded successfully. Count: " + labels.size());
            } catch (IOException e) {
                Log.e("Yolo11", "Error loading labels file: " + LABEL_FILE, e);
                Toast.makeText(context, "Error loading labels: " + LABEL_FILE + " - " + e.getMessage(), Toast.LENGTH_LONG).show();
                throw e; // Re-throw to prevent using uninitialized labels
            }
        } catch (IOException e) {
            Log.e("Yolo11", "Error loading model or labels", e);
            String errorMsg = "Error loading model: " + e.getMessage();
            if (e.getMessage() != null && e.getMessage().contains(LABEL_FILE)) {
                errorMsg = "Error loading labels file (" + LABEL_FILE + "). Make sure it exists in assets folder.";
            } else if (e.getMessage() != null && e.getMessage().contains(modelFile)) {
                errorMsg = "Error loading model file (" + modelFile + "). Make sure it exists in assets folder.";
            }
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Detect objects in the provided bitmap
     * @param bitmap Input image
     * @return List of detected objects
     */
    public ArrayList<Recognition> detect(Bitmap bitmap) {
        bitmapHeight = bitmap.getHeight();
        bitmapWidth = bitmap.getWidth();

        TensorBuffer outputBuffer = runInference(bitmap);
        ArrayList<Recognition> allRecognitions = parseOutput(outputBuffer.getFloatArray());
        ArrayList<Recognition> nmsRecognitions = nms(allRecognitions);
        ArrayList<Recognition> finalRecognitions = nmsAllClass(nmsRecognitions);

        updateLabels(finalRecognitions);
        return finalRecognitions;
    }

    /**
     * Run TFLite inference on the bitmap
     */
    private TensorBuffer runInference(Bitmap bitmap) {
        TensorImage inputImage = preprocessImage(bitmap);
        TensorBuffer outputBuffer = createOutputBuffer();

        if (tflite != null) {
            tflite.run(inputImage.getBuffer(), outputBuffer.getBuffer());

            // Debug: Print output shape and first few values
            float[] output = outputBuffer.getFloatArray();
            Log.d("Yolo11", "Output array length: " + output.length);
            Log.d("Yolo11", "Expected: " + (OUTPUT_SIZE[0] * OUTPUT_SIZE[1] * OUTPUT_SIZE[2]));
            Log.d("Yolo11", "First 20 values: " + Arrays.toString(Arrays.copyOfRange(output, 0, Math.min(20, output.length))));
        }

        return outputBuffer;
    }

    /**
     * Preprocess the input image
     */
    private TensorImage preprocessImage(Bitmap bitmap) {
        ImageProcessor imageProcessor;
        TensorImage tensorImage;

        imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(INPUT_SIZE.getHeight(), INPUT_SIZE.getWidth(), ResizeOp.ResizeMethod.BILINEAR))
                .add(new NormalizeOp(0, 255))
                .build();
        tensorImage = new TensorImage(DataType.FLOAT32);

        tensorImage.load(bitmap);
        return imageProcessor.process(tensorImage);
    }

    /**
     * Create output buffer for inference results
     */
    private TensorBuffer createOutputBuffer() {
        DataType dataType = DataType.FLOAT32;
        return TensorBuffer.createFixedSize(OUTPUT_SIZE, dataType);
    }

    /**
     * Parse model output to Recognition objects
     * YOLO11 output format: [1, num_features, num_detections]
     * For this model: [1, 9, 2100] where 9 = 4 (bbox) + 5 (classes)
     * Memory layout: all values for feature 0, then feature 1, etc.
     * Features: [x, y, w, h, class0, class1, ..., class4]
     */
    private ArrayList<Recognition> parseOutput(float[] outputArray) {
        ArrayList<Recognition> recognitions = new ArrayList<>();

        int numDetections = OUTPUT_SIZE[2];
        int numFeatures = OUTPUT_SIZE[1];

        for (int i = 0; i < numDetections; i++) {
            // Access pattern: feature_index * numDetections + detection_index
            float x = outputArray[0 * numDetections + i] * bitmapWidth;
            float y = outputArray[1 * numDetections + i] * bitmapHeight;
            float w = outputArray[2 * numDetections + i] * bitmapWidth;
            float h = outputArray[3 * numDetections + i] * bitmapHeight;

            int xmin = (int) Math.max(0, x - w / 2.);
            int ymin = (int) Math.max(0, y - h / 2.);
            int xmax = (int) Math.min(bitmapWidth, x + w / 2.);
            int ymax = (int) Math.min(bitmapHeight, y + h / 2.);

            int len = OUTPUT_SIZE[1] - 4;
            float[] classScores = new float[len];
            for (int c = 0; c < len; c++) {
                classScores[c] = outputArray[(4 + c) * numDetections + i];
            }

            int labelId = getMaxScoreIndex(classScores);
            float maxScore = classScores[labelId];

            // For YOLO11, use the max class score as confidence
            float confidence = maxScore;

            recognitions.add(new Recognition(
                    labelId,
                    "",
                    maxScore,
                    confidence,
                    new RectF(xmin, ymin, xmax, ymax)));
        }

        return recognitions;
    }

    /**
     * Get index of maximum score in array
     */
    private int getMaxScoreIndex(float[] scores) {
        int maxIndex = 0;
        float maxScore = 0.f;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > maxScore) {
                maxScore = scores[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    /**
     * Update recognition labels
     */
    private void updateLabels(ArrayList<Recognition> recognitions) {
        for (Recognition recognition : recognitions) {
            int labelId = recognition.getLabelId();
            String labelName = labels.get(labelId);
            recognition.setLabelName(labelName);
        }
    }


    /**
     * Non-maximum suppression for each class
     * @param allRecognitions All detected objects
     * @return Filtered recognitions after NMS
     */
    private ArrayList<Recognition> nms(ArrayList<Recognition> allRecognitions) {
        ArrayList<Recognition> nmsRecognitions = new ArrayList<>();

        // Number of classes = total features - 4 (bbox coordinates)
        int numClasses = OUTPUT_SIZE[1] - 4;

        for (int classId = 0; classId < numClasses; classId++) {
            PriorityQueue<Recognition> queue = createRecognitionQueue();

            // Filter by class and confidence threshold
            for (Recognition recognition : allRecognitions) {
                if (recognition.getLabelId() == classId && recognition.getConfidence() > DETECT_THRESHOLD) {
                    queue.add(recognition);
                }
            }

            // Apply NMS
            nmsRecognitions.addAll(applyNms(queue, IOU_THRESHOLD));
        }

        return nmsRecognitions;
    }

    /**
     * Non-maximum suppression across all classes
     * @param allRecognitions All detected objects
     * @return Filtered recognitions after cross-class NMS
     */
    private ArrayList<Recognition> nmsAllClass(ArrayList<Recognition> allRecognitions) {
        PriorityQueue<Recognition> queue = createRecognitionQueue();

        for (Recognition recognition : allRecognitions) {
            if (recognition.getConfidence() > DETECT_THRESHOLD) {
                queue.add(recognition);
            }
        }

        return applyNms(queue, IOU_CLASS_DUPLICATED_THRESHOLD);
    }

    /**
     * Create a priority queue sorted by confidence
     */
    private PriorityQueue<Recognition> createRecognitionQueue() {
        return new PriorityQueue<>(100, new Comparator<Recognition>() {
            @Override
            public int compare(Recognition r1, Recognition r2) {
                return Float.compare(r2.getConfidence(), r1.getConfidence());
            }
        });
    }

    /**
     * Apply NMS algorithm with given IOU threshold
     */
    private ArrayList<Recognition> applyNms(PriorityQueue<Recognition> queue, float iouThreshold) {
        ArrayList<Recognition> result = new ArrayList<>();

        while (!queue.isEmpty()) {
            Recognition[] detections = queue.toArray(new Recognition[0]);
            Recognition maxConfidence = detections[0];
            result.add(maxConfidence);
            queue.clear();

            for (int i = 1; i < detections.length; i++) {
                if (boxIou(maxConfidence.getLocation(), detections[i].getLocation()) < iouThreshold) {
                    queue.add(detections[i]);
                }
            }
        }

        return result;
    }

    /**
     * Calculate IoU (Intersection over Union) between two boxes
     */
    private float boxIou(RectF a, RectF b) {
        float intersection = boxIntersection(a, b);
        float union = boxUnion(a, b);
        return union <= 0 ? 1 : intersection / union;
    }

    /**
     * Calculate intersection area between two boxes
     */
    private float boxIntersection(RectF a, RectF b) {
        float maxLeft = Math.max(a.left, b.left);
        float maxTop = Math.max(a.top, b.top);
        float minRight = Math.min(a.right, b.right);
        float minBottom = Math.min(a.bottom, b.bottom);

        float w = minRight - maxLeft;
        float h = minBottom - maxTop;

        return (w < 0 || h < 0) ? 0 : w * h;
    }

    /**
     * Calculate union area between two boxes
     */
    private float boxUnion(RectF a, RectF b) {
        float intersection = boxIntersection(a, b);
        float areaA = (a.right - a.left) * (a.bottom - a.top);
        float areaB = (b.right - b.left) * (b.bottom - b.top);
        return areaA + areaB - intersection;
    }


    /**
     * Add NNAPI delegate for hardware acceleration (Android Pie+)
     */
    public void addNNApiDelegate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            NnApiDelegate nnApiDelegate = new NnApiDelegate();
            options.addDelegate(nnApiDelegate);
            Log.i("Yolo11", "Using NNAPI delegate");
        }
    }

    /**
     * Add GPU delegate for hardware acceleration
     */
    public void addGPUDelegate() {
        CompatibilityList compatibilityList = new CompatibilityList();
        if (compatibilityList.isDelegateSupportedOnThisDevice()) {
            GpuDelegate gpuDelegate = new GpuDelegate();
            options.addDelegate(gpuDelegate);
            Log.i("Yolo11", "Using GPU delegate");
        } else {
            addThread(4);
        }
    }

    /**
     * Set number of threads for inference
     * @param threadCount Number of threads
     */
    public void addThread(int threadCount) {
        options.setNumThreads(threadCount);
    }
}

