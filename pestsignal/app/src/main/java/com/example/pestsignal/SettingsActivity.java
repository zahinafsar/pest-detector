package com.example.pestsignal;

import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private ListView settingsListView;
    private ImageButton backButton;
    private String[] settingsOptions;
    private int[] settingsIcons = {
        android.R.drawable.ic_menu_myplaces,
        android.R.drawable.ic_menu_info_details,
        android.R.drawable.ic_menu_edit,
        android.R.drawable.ic_menu_agenda,
        android.R.drawable.ic_menu_myplaces
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialize string array after activity is created
        settingsOptions = new String[]{
            getString(R.string.login),
            getString(R.string.developer_info),
            getString(R.string.language),
            getString(R.string.statistics),
            getString(R.string.ai_chat)
        };

        // Initialize views
        settingsListView = findViewById(R.id.settingsListView);
        backButton = findViewById(R.id.backButton);

        // Set up back button click listener
        backButton.setOnClickListener(v -> finish());

        // Set up adapter
        SettingsAdapter adapter = new SettingsAdapter();
        settingsListView.setAdapter(adapter);

        // Set up click listener
        settingsListView.setOnItemClickListener((parent, view, position, id) -> {
            handleSettingsClick(position);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update login option text based on login status
        updateLoginOption();
    }

    private void updateLoginOption() {
        boolean isLoggedIn = getSharedPreferences("PestSignalPrefs", MODE_PRIVATE)
                .getBoolean("isLoggedIn", false);
        
        if (isLoggedIn) {
            String userName = getSharedPreferences("PestSignalPrefs", MODE_PRIVATE)
                    .getString("userName", "");
            settingsOptions[0] = getString(R.string.login) + " (" + userName + ")";
        } else {
            settingsOptions[0] = getString(R.string.login);
        }
        
        // Refresh the list
        ((SettingsAdapter) settingsListView.getAdapter()).notifyDataSetChanged();
    }

    private void handleSettingsClick(int position) {
        switch (position) {
            case 0:
                handleLoginClick();
                break;
            case 1:
                handleDevInfoClick();
                break;
            case 2:
                handleLanguageClick();
                break;
            case 3:
                handleStatisticsClick();
                break;
            case 4:
                handleAIChatClick();
                break;
        }
    }

    private void handleLoginClick() {
        boolean isLoggedIn = getSharedPreferences("PestSignalPrefs", MODE_PRIVATE)
                .getBoolean("isLoggedIn", false);
        
        if (isLoggedIn) {
            // Show logout confirmation dialog
            showLogoutDialog();
        } else {
            // Launch login activity
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        }
    }

    private void showLogoutDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Clear login data
                    getSharedPreferences("PestSignalPrefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("isLoggedIn", false)
                            .remove("userName")
                            .remove("userId")
                            .remove("authToken")
                            .apply();
                    
                    Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                    updateLoginOption();
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void handleDevInfoClick() {
        showDeveloperInfoBottomSheet();
    }

    private void showDeveloperInfoBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_developer_info, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        // Set app version
        TextView appVersion = bottomSheetView.findViewById(R.id.appVersion);
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            int versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            appVersion.setText("Version " + versionName + " (" + versionCode + ")");
        } catch (Exception e) {
            appVersion.setText("Version 1.0");
        }

        bottomSheetDialog.show();
    }

    private void handleLanguageClick() {
        // Show language selection dialog
        showLanguageDialog();
    }

    private void handleStatisticsClick() {
        // Check if user is logged in
        boolean isLoggedIn = getSharedPreferences("PestSignalPrefs", MODE_PRIVATE)
                .getBoolean("isLoggedIn", false);
        
        if (isLoggedIn) {
            // Launch statistics activity
            Intent intent = new Intent(this, StatisticsActivity.class);
            startActivity(intent);
        } else {
            Toast.makeText(this, getString(R.string.please_login_first), Toast.LENGTH_SHORT).show();
        }
    }

    private void showLanguageDialog() {
        String[] languages = {getString(R.string.english), getString(R.string.bengali)};
        String[] languageCodes = {"en", "bn"};
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.language))
                .setItems(languages, (dialog, which) -> {
                    String selectedLanguage = languageCodes[which];
                    changeLanguage(selectedLanguage);
                })
                .show();
    }

    private void changeLanguage(String languageCode) {
        // Save the selected language preference
        getSharedPreferences("PestSignalPrefs", MODE_PRIVATE)
                .edit()
                .putString("language", languageCode)
                .apply();

        // Set the locale
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());

        // Restart the app to apply language change
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void handleAIChatClick() {
        Intent intent = new Intent(this, ChatActivity.class);
        startActivity(intent);
    }

    private class SettingsAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return settingsOptions.length;
        }

        @Override
        public Object getItem(int position) {
            return settingsOptions[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.settings_list_item, parent, false);
            }

            ImageView icon = convertView.findViewById(R.id.settingsIcon);
            TextView text = convertView.findViewById(R.id.settingsText);

            icon.setImageResource(settingsIcons[position]);
            text.setText(settingsOptions[position]);

            return convertView;
        }
    }
} 