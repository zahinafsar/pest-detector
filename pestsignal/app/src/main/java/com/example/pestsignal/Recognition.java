package com.example.pestsignal;

import android.graphics.RectF;

/**
 * Represents a detected object with its label, confidence score, and location
 */
public class Recognition {

    private final int labelId;
    private String labelName;
    private final float labelScore;
    private final float confidence;
    private final RectF location;

    public Recognition(int labelId, String labelName, float labelScore, float confidence, RectF location) {
        this.labelId = labelId;
        this.labelName = labelName;
        this.labelScore = labelScore;
        this.confidence = confidence;
        this.location = location;
    }

    public int getLabelId() {
        return labelId;
    }

    public String getLabelName() {
        return labelName;
    }

    public float getLabelScore() {
        return labelScore;
    }

    public float getConfidence() {
        return confidence;
    }

    public RectF getLocation() {
        return new RectF(location);
    }

    public void setLabelName(String labelName) {
        this.labelName = labelName;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        
        result.append(labelId).append(" ");
        
        if (labelName != null) {
            result.append(labelName).append(" ");
        }

        result.append(String.format("(%.1f%%) ", confidence * 100.0f));

        if (location != null) {
            result.append(location).append(" ");
        }
        
        return result.toString().trim();
    }
}

