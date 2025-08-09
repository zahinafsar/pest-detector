package com.example.pestsignal;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity {

    private ImageButton backButton;
    private TextInputEditText nameEditText;
    private TextInputEditText pinEditText;
    private TextInputLayout nameInputLayout;
    private TextInputLayout pinInputLayout;
    private Button loginButton;
    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize OkHttp client
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

        // Initialize views
        backButton = findViewById(R.id.backButton);
        nameEditText = findViewById(R.id.nameEditText);
        pinEditText = findViewById(R.id.pinEditText);
        nameInputLayout = findViewById(R.id.nameInputLayout);
        pinInputLayout = findViewById(R.id.pinInputLayout);
        loginButton = findViewById(R.id.loginButton);

        // Set up click listeners
        backButton.setOnClickListener(v -> finish());
        loginButton.setOnClickListener(v -> handleLogin());

        // Set up PIN input listener for auto-submit
        pinEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                handleLogin();
                return true;
            }
            return false;
        });
    }

    private void handleLogin() {
        // Get input values
        String username = nameEditText.getText().toString().trim();
        String pin = pinEditText.getText().toString().trim();

        // Clear previous errors
        nameInputLayout.setError(null);
        pinInputLayout.setError(null);

        // Validate inputs
        if (TextUtils.isEmpty(username)) {
            nameInputLayout.setError(getString(R.string.name_required));
            nameEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(pin)) {
            pinInputLayout.setError(getString(R.string.pin_required));
            pinEditText.requestFocus();
            return;
        }

        if (pin.length() != 4) {
            pinInputLayout.setError(getString(R.string.pin_length_error));
            pinEditText.requestFocus();
            return;
        }

        // Show loading state
        loginButton.setEnabled(false);
        loginButton.setText(getString(R.string.processing));

        // Make API call
        performLogin(username, pin);
    }

    private void performLogin(String username, String pin) {
        try {
            // Create JSON request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("username", username);
            requestBody.put("pin", pin);

            // Create HTTP request
            RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                requestBody.toString()
            );

            Request request = new Request.Builder()
                    .url("http://10.0.2.2:8001/auth/login")
                    .post(body)
                    .build();

            // Log the request for debugging
            System.out.println("Making request to: " + request.url());
            System.out.println("Request body: " + requestBody.toString());

            // Make the API call
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        loginButton.setEnabled(true);
                        loginButton.setText(getString(R.string.login_button));
                        String errorMsg = "Login failed: " + e.getMessage();
                        System.out.println("Network error: " + errorMsg);
                        Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body().string();
                    final int responseCode = response.code();
                    
                    System.out.println("Response code: " + responseCode);
                    System.out.println("Response body: " + responseBody);
                    
                    runOnUiThread(() -> {
                        loginButton.setEnabled(true);
                        loginButton.setText(getString(R.string.login_button));
                        
                        if (response.isSuccessful()) {
                            try {
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                String message = jsonResponse.optString("message", "");
                                String token = jsonResponse.optString("token", "");
                                
                                if (jsonResponse.has("user")) {
                                    JSONObject user = jsonResponse.getJSONObject("user");
                                    String userId = user.optString("id", "");
                                    String userName = user.optString("username", "");
                                    
                                    // Save login data
                                    getSharedPreferences("PestSignalPrefs", MODE_PRIVATE)
                                            .edit()
                                            .putBoolean("isLoggedIn", true)
                                            .putString("userName", userName)
                                            .putString("userId", userId)
                                            .putString("authToken", token)
                                            .apply();
                                    
                                    Toast.makeText(LoginActivity.this, 
                                        message, Toast.LENGTH_SHORT).show();
                                    finish(); // Return to settings page
                                } else {
                                    Toast.makeText(LoginActivity.this, 
                                        "Invalid response format", Toast.LENGTH_SHORT).show();
                                }
                            } catch (JSONException e) {
                                Toast.makeText(LoginActivity.this, 
                                    "Error parsing response: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            try {
                                JSONObject errorResponse = new JSONObject(responseBody);
                                String errorMessage = errorResponse.optString("message", "Login failed");
                                Toast.makeText(LoginActivity.this, 
                                    errorMessage, Toast.LENGTH_LONG).show();
                            } catch (JSONException e) {
                                Toast.makeText(LoginActivity.this, 
                                    "HTTP Error " + responseCode + ": " + responseBody, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            });
        } catch (JSONException e) {
            loginButton.setEnabled(true);
            loginButton.setText(getString(R.string.login_button));
            Toast.makeText(this, "Error creating request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
} 