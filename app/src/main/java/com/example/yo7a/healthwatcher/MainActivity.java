package com.example.yo7a.healthwatcher;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView tvHeartRate;
    private TextView tvSpO2;
    private TextView tvGlucose;
    private Button btnStart;

    private final Handler uiHandler = new Handler();
    private final long POLL_MS = 500; // polling interval

    private double mealFactor = 1.0; // default

    private final Runnable uiUpdater = new Runnable() {
        @Override
        public void run() {
            double hr = AppSensorData.getInstance().getLatestHeartRate();
            double spo2 = AppSensorData.getInstance().getLatestSpO2();
            double vIR = AppSensorData.getInstance().getLatestIR();

            // Update Heart Rate
            String hrText = (hr > 0) ? String.format("Heart Rate: %.0f bpm", hr) : "Heart Rate: -- bpm";
            tvHeartRate.setText(hrText);

            // Update SpO2
            String spo2Text = (spo2 > 0) ? String.format("SpO₂: %.1f %%", spo2) : "SpO₂: -- %";
            tvSpO2.setText(spo2Text);

            // Update estimated glucose
            double glucoseFromSpO2 = (spo2 > 0) ? GlucoseEstimator.estimateGlucoseFromSpO2(spo2, mealFactor) : Double.NaN;
            double glucoseFromPPG = (vIR > 0) ? GlucoseEstimator.estimateGlucoseFuzzy(vIR, mealFactor) : Double.NaN;

            String glucoseText;
            if (!Double.isNaN(glucoseFromSpO2) && !Double.isNaN(glucoseFromPPG)) {
                glucoseText = String.format("Estimated Glucose: %.2f mg/dL", (glucoseFromSpO2 + glucoseFromPPG) / 2.0);
            } else if (!Double.isNaN(glucoseFromSpO2)) {
                glucoseText = String.format("Estimated Glucose: %.2f mg/dL", glucoseFromSpO2);
            } else {
                glucoseText = "Estimated Glucose: -- mg/dL";
            }
            tvGlucose.setText(glucoseText);

            uiHandler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvHeartRate = findViewById(R.id.tvHeartRate);
        tvSpO2 = findViewById(R.id.tvSpO2);
        tvGlucose = findViewById(R.id.tvGlucose);
        btnStart = findViewById(R.id.btnStart);

        // Ask user about meal timing when app starts
        showMealDialog();

        btnStart.setOnClickListener(v -> {
            // start the existing measurement flow activity
            Intent i = new Intent(MainActivity.this, StartVitalSigns.class);
            startActivity(i);
        });
    }

    private void showMealDialog() {
        final String[] mealOptions = getResources().getStringArray(R.array.meal_times);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Meal Timing")
                .setSingleChoiceItems(mealOptions, 0, null)
                .setPositiveButton("OK", (dialog, which) -> {
                    int selectedPos = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                    switch (selectedPos) {
                        case 0: mealFactor = 1.0; break;      // Fasting
                        case 1: mealFactor = 1.1; break;      // Breakfast
                        case 2: mealFactor = 1.05; break;     // Lunch
                        case 3: mealFactor = 1.05; break;     // Dinner
                        case 4: mealFactor = 1.02; break;     // Snack
                        default: mealFactor = 1.0;
                    }
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        uiHandler.post(uiUpdater);
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(uiUpdater);
    }
}
