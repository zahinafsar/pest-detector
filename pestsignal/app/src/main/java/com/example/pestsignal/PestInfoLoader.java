package com.example.pestsignal;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to load and parse pest information from JSON file
 */
public class PestInfoLoader {
    private static final String TAG = "PestInfoLoader";
    private static final String PEST_INFO_FILE = "pest_information.json";
    
    private static Map<String, PestInfo> pestInfoMap = null;
    private static Context appContext = null;

    /**
     * Initialize the pest info loader with application context
     */
    public static void initialize(Context context) {
        appContext = context.getApplicationContext();
        loadPestInformation();
    }

    /**
     * Load pest information from JSON file in assets
     */
    private static void loadPestInformation() {
        if (appContext == null) {
            Log.e(TAG, "Context not initialized");
            return;
        }

        try {
            String jsonString = loadJSONFromAsset(appContext, PEST_INFO_FILE);
            if (jsonString == null || jsonString.isEmpty()) {
                Log.e(TAG, "Failed to load pest information JSON");
                return;
            }

            JSONArray jsonArray = new JSONArray(jsonString);
            pestInfoMap = new HashMap<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                PestInfo pestInfo = parsePestInfo(jsonObject);
                if (pestInfo != null && pestInfo.getKey() != null) {
                    pestInfoMap.put(pestInfo.getKey().toLowerCase(), pestInfo);
                }
            }

            Log.i(TAG, "Loaded " + pestInfoMap.size() + " pest information entries");
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing pest information JSON", e);
        } catch (Exception e) {
            Log.e(TAG, "Error loading pest information", e);
        }
    }

    /**
     * Parse JSON object to PestInfo
     */
    private static PestInfo parsePestInfo(JSONObject jsonObject) throws JSONException {
        PestInfo pestInfo = new PestInfo();
        
        pestInfo.setKey(jsonObject.getString("key"));

        // Parse localized name
        if (jsonObject.has("name") && jsonObject.get("name") instanceof JSONObject) {
            JSONObject nameObj = jsonObject.getJSONObject("name");
            PestInfo.LocalizedText name = new PestInfo.LocalizedText(
                nameObj.optString("en", ""),
                nameObj.optString("bn", "")
            );
            pestInfo.setName(name);
        }

        // Parse localized type
        if (jsonObject.has("type") && jsonObject.get("type") instanceof JSONObject) {
            JSONObject typeObj = jsonObject.getJSONObject("type");
            PestInfo.LocalizedText type = new PestInfo.LocalizedText(
                typeObj.optString("en", ""),
                typeObj.optString("bn", "")
            );
            pestInfo.setType(type);
        }

        // Parse localized description
        if (jsonObject.has("description") && jsonObject.get("description") instanceof JSONObject) {
            JSONObject descObj = jsonObject.getJSONObject("description");
            PestInfo.LocalizedText description = new PestInfo.LocalizedText(
                descObj.optString("en", ""),
                descObj.optString("bn", "")
            );
            pestInfo.setDescription(description);
        }

        // Parse localized prevention
        if (jsonObject.has("prevention") && jsonObject.get("prevention") instanceof JSONObject) {
            JSONObject prevObj = jsonObject.getJSONObject("prevention");
            PestInfo.LocalizedText prevention = new PestInfo.LocalizedText(
                prevObj.optString("en", ""),
                prevObj.optString("bn", "")
            );
            pestInfo.setPrevention(prevention);
        }

        return pestInfo;
    }

    /**
     * Get pest information by key (label name)
     */
    public static PestInfo getPestInfo(String key) {
        if (pestInfoMap == null) {
            Log.w(TAG, "Pest information not loaded yet");
            return null;
        }
        
        if (key == null) {
            return null;
        }
        
        return pestInfoMap.get(key.toLowerCase());
    }

    /**
     * Get current language preference
     */
    public static String getCurrentLanguage(Context context) {
        String savedLanguage = context.getSharedPreferences("PestSignalPrefs", Context.MODE_PRIVATE)
                .getString("language", "bn"); // Default to Bengali
        return savedLanguage != null ? savedLanguage : "bn";
    }

    /**
     * Load JSON file from assets folder
     */
    private static String loadJSONFromAsset(Context context, String filename) {
        String json = null;
        try {
            InputStream is = context.getAssets().open(filename);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.e(TAG, "Error loading JSON from assets: " + filename, e);
        }
        return json;
    }

    /**
     * Reload pest information (useful if language changes)
     */
    public static void reload() {
        if (appContext != null) {
            loadPestInformation();
        }
    }
}

