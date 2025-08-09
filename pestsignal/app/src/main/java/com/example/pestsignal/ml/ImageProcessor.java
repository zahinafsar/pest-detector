package com.example.pestsignal.ml;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.IOException;

public class ImageProcessor {
    
    public static Bitmap loadBitmapFromUri(Context context, String uri) {
        try {
            android.net.Uri imageUri = Uri.parse(uri);
            java.io.InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            if (inputStream != null) {
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();
                return bitmap;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static Bitmap resizeBitmap(Bitmap bitmap, int targetWidth, int targetHeight) {
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
    }
    
    public static Bitmap preprocessForYolo(Bitmap bitmap) {
        return resizeBitmap(bitmap, 640, 640);
    }
} 