package com.example.pestsignal.ml;

public class Detection {
    private String label;
    private float confidence;
    private float[] boundingBox;

    public Detection(String label, float confidence, float[] boundingBox) {
        this.label = label;
        this.confidence = confidence;
        this.boundingBox = boundingBox;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public float[] getBoundingBox() {
        return boundingBox;
    }

    public void setBoundingBox(float[] boundingBox) {
        this.boundingBox = boundingBox;
    }
} 