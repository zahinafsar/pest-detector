#!/usr/bin/env python3
"""
Script to convert YOLO model to TorchScript format for Android deployment.
Based on the Roboflow guide: https://blog.roboflow.com/yolov11-android-app/
"""

from ultralytics import YOLO
import os

def convert_model_to_torchscript(model_path, output_path="model.torchscript"):
    """
    Convert a YOLO model (.pt file) to TorchScript format for Android deployment.
    
    Args:
        model_path (str): Path to the trained YOLO model (.pt file)
        output_path (str): Output path for the TorchScript model
    """
    try:
        # Load the trained YOLO model
        print(f"Loading model from: {model_path}")
        model = YOLO(model_path)
        
        # Export the model to TorchScript format
        print("Converting to TorchScript format...")
        model.export(format="torchscript")
        
        # The export creates a file with .torchscript extension
        torchscript_path = model_path.replace('.pt', '.torchscript')
        
        # Copy to the desired output path
        if os.path.exists(torchscript_path):
            import shutil
            shutil.copy2(torchscript_path, output_path)
            print(f"Model successfully converted and saved to: {output_path}")
        else:
            print(f"Error: TorchScript file not found at {torchscript_path}")
            
    except Exception as e:
        print(f"Error converting model: {e}")

def main():
    """Main function to convert model."""
    # You can specify your model path here
    model_path = "model.pt"  # Change this to your model path
    
    if os.path.exists(model_path):
        convert_model_to_torchscript(model_path)
    else:
        print(f"Model file not found: {model_path}")
        print("Please ensure you have a trained YOLO model (.pt file) in the current directory.")
        print("You can train a model using Roboflow or download a pre-trained model.")

if __name__ == "__main__":
    main() 