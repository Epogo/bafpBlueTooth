package com.example.BafpBlue;

import static android.content.Context.MODE_PRIVATE;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class SettingsActivity extends AppCompatActivity {

    private ToggleButton backgroundToggleButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        backgroundToggleButton = findViewById(R.id.background_toggle_button);

        // Load the saved preference
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isRunningInBackground = sharedPreferences.getBoolean("RUNNING_IN_BACKGROUND", false);
        backgroundToggleButton.setChecked(isRunningInBackground);

        backgroundToggleButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("RUNNING_IN_BACKGROUND", isChecked);
            editor.apply();

            // Notify MainActivity about the change
            Intent intent = new Intent("BackgroundServiceToggle");
            intent.putExtra("isRunningInBackground", isChecked);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        });
    }
}
