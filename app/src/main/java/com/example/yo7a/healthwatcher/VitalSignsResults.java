package com.example.yo7a.healthwatcher;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class VitalSignsResults extends AppCompatActivity {

    private String user;
    private String measurementDate;

    private int systolicBP, diastolicBP, respirationRate, heartRate, oxygenSaturation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vital_signs_results);

        // Initialize date
        DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        Date today = Calendar.getInstance().getTime();
        measurementDate = df.format(today);

        // UI elements
        TextView tvRespiration = findViewById(R.id.RRV);
        TextView tvBloodPressure = findViewById(R.id.BP2V);
        TextView tvHeartRate = findViewById(R.id.HRV);
        TextView tvOxygen = findViewById(R.id.O2V);
        ImageButton btnSendAll = findViewById(R.id.SendAll);

        // Retrieve data from Intent
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            respirationRate = bundle.getInt("RR", 0);
            heartRate = bundle.getInt("HR", 0);
            systolicBP = bundle.getInt("SP", 0);
            diastolicBP = bundle.getInt("DP", 0);
            oxygenSaturation = bundle.getInt("SpO2", 0);
            user = bundle.getString("Usr", "User");

            // Update UI
            tvRespiration.setText(String.valueOf(respirationRate));
            tvHeartRate.setText(String.valueOf(heartRate));
            tvBloodPressure.setText(systolicBP + " / " + diastolicBP);
            tvOxygen.setText(String.valueOf(oxygenSaturation));
        }

        // Send all measurements via email
        btnSendAll.setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SEND);
            emailIntent.setType("message/rfc822");
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"recipient@example.com"});
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Health Watcher Vital Signs Report");
            emailIntent.putExtra(Intent.EXTRA_TEXT,
                    user + "'s latest measurements (" + measurementDate + "):\n\n" +
                            "Heart Rate: " + heartRate + " BPM\n" +
                            "Blood Pressure: " + systolicBP + " / " + diastolicBP + " mmHg\n" +
                            "Respiration Rate: " + respirationRate + " breaths/min\n" +
                            "Oxygen Saturation: " + oxygenSaturation + "%\n"
            );
            try {
                startActivity(Intent.createChooser(emailIntent, "Send via email..."));
            } catch (android.content.ActivityNotFoundException ex) {
                Toast.makeText(this, "No email clients installed.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onBackPressed() {
        // Return to main menu
        Intent i = new Intent(VitalSignsResults.this, Primary.class);
        i.putExtra("Usr", user);
        startActivity(i);
        finish();
    }
}

