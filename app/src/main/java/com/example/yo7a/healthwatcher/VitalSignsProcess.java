package com.example.yo7a.healthwatcher;

import android.content.ContentValues;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class VitalSignsProcess extends AppCompatActivity {

    private ProgressBar progVital;
    private Toast mainToast;
    private String user;

    private int HR = 0, RR = 0, SP = 0, DP = 0, SpO2 = 0;
    private int step = 0; // Track which measurement step we are on
    private static final int TOTAL_STEPS = 5;
    private float progressPerStep;

    private UserDB userDB; // Database reference

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vital_signs_process);

        userDB = new UserDB(getApplicationContext()); // Initialize DB
        user = getIntent().getStringExtra("Usr");

        progVital = findViewById(R.id.VSPB);
        if (progVital != null) {
            progVital.setProgress(0);
            progressPerStep = 100f / TOTAL_STEPS;
        }

        startNextMeasurement();
    }

    private void startNextMeasurement() {
        switch (step) {
            case 0:
                launchActivityForResult(HeartRateProcess.class, result -> {
                    HR = result;
                    if (!validateHR(HR)) return;
                    storeMeasurement("HR", HR);
                    incrementProgress();
                    step++;
                    startNextMeasurement();
                });
                break;
            case 1:
                launchActivityForResult(RespirationProcess.class, result -> {
                    RR = result;
                    if (!validateRR(RR)) return;
                    storeMeasurement("RR", RR);
                    incrementProgress();
                    step++;
                    startNextMeasurement();
                });
                break;
            case 2:
                launchActivityForResult(O2Process.class, result -> {
                    SpO2 = result;
                    if (!validateSpO2(SpO2)) return;
                    storeMeasurement("SpO2", SpO2);
                    incrementProgress();
                    step++;
                    startNextMeasurement();
                });
                break;
            case 3:
                launchActivityForResult(BloodPressureProcess.class, result -> {
                    SP = result >> 16; // High 16 bits = Systolic
                    DP = result & 0xFFFF; // Low 16 bits = Diastolic
                    if (!validateBP(SP, DP)) return;
                    storeMeasurement("SP", SP);
                    storeMeasurement("DP", DP);
                    incrementProgress();
                    step++;
                    startNextMeasurement();
                });
                break;
            case 4:
                incrementProgress();
                showResults();
                break;
        }
    }

    // Generic launcher for any vital sign activity returning an int
    private void launchActivityForResult(Class<?> activityClass, VitalResultCallback callback) {
        ActivityResultLauncher<Intent> launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        int value = result.getData().getIntExtra("Value", 0);
                        callback.onResult(value);
                    } else {
                        showShortToast("Measurement canceled or failed. Try again.");
                        startNextMeasurement(); // Retry
                    }
                }
        );
        Intent i = new Intent(this, activityClass);
        launcher.launch(i);
    }

    private void incrementProgress() {
        if (progVital != null) {
            int progress = Math.min(100, (int) ((step + 1) * progressPerStep));
            progVital.setProgress(progress);
        }
    }

    private boolean validateHR(int hr) {
        if (hr < 45 || hr > 200) {
            showShortToast("Heart Rate out of range. Please retry.");
            step--;
            return false;
        }
        return true;
    }

    private boolean validateRR(int rr) {
        if (rr < 10 || rr > 30) {
            showShortToast("Respiration Rate out of range. Please retry.");
            step--;
            return false;
        }
        return true;
    }

    private boolean validateSpO2(int spO2) {
        if (spO2 < 70 || spO2 > 100) {
            showShortToast("SpO2 out of range. Please retry.");
            step--;
            return false;
        }
        return true;
    }

    private boolean validateBP(int sp, int dp) {
        if (sp < 80 || sp > 200 || dp < 50 || dp > 120) {
            showShortToast("Blood Pressure out of range. Please retry.");
            step--;
            return false;
        }
        return true;
    }

    // Store each measurement in the database
    private void storeMeasurement(String column, int value) {
        userDB.updateField(user, column, value);
    }

    private void showResults() {
        Intent i = new Intent(VitalSignsProcess.this, VitalSignsResults.class);
        i.putExtra("HR", HR);
        i.putExtra("RR", RR);
        i.putExtra("SpO2", SpO2);
        i.putExtra("SP", SP);
        i.putExtra("DP", DP);
        i.putExtra("Usr", user);
        startActivity(i);
        finish();
    }

    private void showShortToast(String msg) {
        if (mainToast != null) mainToast.cancel();
        mainToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        mainToast.show();
    }

    private interface VitalResultCallback {
        void onResult(int value);
    }
}

