package com.example.yo7a.healthwatcher;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class StartVitalSigns extends AppCompatActivity {

    private String user = "";
    private int page = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_vital_signs);

        // Get extras from previous activity, with defaults
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            user = extras.getString("Usr", "");
            page = extras.getInt("Page", 0);
        }

        Button startMeasurement = findViewById(R.id.startMeasurement);
        Button cancelMeasurement = findViewById(R.id.cancelMeasurement);

        // Start measurement button click
        startMeasurement.setOnClickListener(v -> startMeasurementProcess());

        // Cancel button → return to main menu
        cancelMeasurement.setOnClickListener(v -> returnToMainMenu());
    }

    /**
     * Handles starting the appropriate measurement based on the page number
     */
    private void startMeasurementProcess() {
        Intent intent = null;

        switch (page) {
            case 1:
                intent = new Intent(this, HeartRateProcess.class);
                break;
            case 2:
                intent = new Intent(this, BloodPressureProcess.class);
                break;
            case 3:
                intent = new Intent(this, RespirationProcess.class);
                break;
            case 4:
                intent = new Intent(this, O2Process.class);
                break;
            case 5:
                intent = new Intent(this, VitalSignsProcess.class);
                break;
            default:
                showToast("Invalid measurement type.");
                return; // Stop execution if page is invalid
        }

        if (intent != null) {
            intent.putExtra("Usr", user);
            intent.putExtra("Page", page);
            startActivity(intent);
            finish();
        }
    }

    /**
     * Returns to the main menu (Primary activity)
     */
    private void returnToMainMenu() {
        Intent intent = new Intent(StartVitalSigns.this, Primary.class);
        intent.putExtra("Usr", user);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Override system back → return to main menu
        returnToMainMenu();
    }

    /**
     * Utility method to show toast messages
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
