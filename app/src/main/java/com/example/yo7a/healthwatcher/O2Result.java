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

/**
 * Displays the SpO₂ and Pulse results after measurement.
 * - Provides detailed interpretation
 * - Allows sharing via email
 * - Shows measurement date/time
 */
public class O2Result extends AppCompatActivity {

    private String user, dateStr;
    private int spo2 = 0, pulse = 0;
    private TextView spo2View, pulseView, statusView, dateView;
    private ImageButton sendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_o2_result);

        // Initialize date and time
        DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        Date today = Calendar.getInstance().getTime();
        dateStr = df.format(today);

        // Initialize UI components
        spo2View = findViewById(R.id.O2R);
        pulseView = findViewById(R.id.PulseR);
        statusView = findViewById(R.id.O2Status);
        dateView = findViewById(R.id.MeasureDate);
        sendButton = findViewById(R.id.SendO2);

        // Safely extract data from Intent
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            spo2 = bundle.getInt("o2Value", 0);
            pulse = bundle.getInt("pulse", 0);
            user = bundle.getString("Usr", "Unknown");
        }

        // Update UI with received or simulated data
        spo2View.setText(spo2 > 0 ? spo2 + " %" : "-- %");
        pulseView.setText(pulse > 0 ? pulse + " BPM" : "-- BPM");
        dateView.setText("Measured on: " + dateStr);

        // Generate interpretation
        String interpretation = getInterpretation(spo2, pulse);
        statusView.setText(interpretation);

        // Send result via email
        sendButton.setOnClickListener(v -> sendResults());
    }

    /**
     * Generates health interpretation based on SpO₂ and pulse values.
     * Values are based on WHO & Mayo Clinic clinical ranges.
     */
    private String getInterpretation(int spo2, int pulse) {
        if (spo2 == 0) return "No data received. Please remeasure.";

        StringBuilder status = new StringBuilder();

        // Oxygen interpretation
        if (spo2 >= 97 && spo2 <= 100) {
            status.append("Excellent Oxygen Level");
        } else if (spo2 >= 94) {
            status.append("Normal Oxygen Level");
        } else if (spo2 >= 90) {
            status.append("Mild Hypoxia (Low Oxygen)");
        } else if (spo2 >= 85) {
            status.append("Moderate Hypoxia — Seek Medical Attention");
        } else {
            status.append("Severe Hypoxia — Immediate Medical Attention Required");
        }

        // Pulse interpretation
        if (pulse < 50) {
            status.append("\nPulse: Very Low (Severe Bradycardia)");
        } else if (pulse < 60) {
            status.append("\nPulse: Low (Bradycardia)");
        } else if (pulse <= 100) {
            status.append("\nPulse: Normal");
        } else if (pulse <= 120) {
            status.append("\nPulse: Elevated (Tachycardia)");
        } else {
            status.append("\nPulse: Critically High (Seek Medical Advice)");
        }

        return status.toString();
    }

    /**
     * Handles sending results via email.
     */
    private void sendResults() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("message/rfc822");
        i.putExtra(Intent.EXTRA_EMAIL, new String[]{"recipient@example.com"});
        i.putExtra(Intent.EXTRA_SUBJECT, "Health Watcher - O₂ & Pulse Report");
        i.putExtra(Intent.EXTRA_TEXT,
                "Health Report for " + user + ":\n\n" +
                        "Oxygen Saturation (SpO₂): " + spo2 + " %\n" +
                        "Pulse Rate: " + pulse + " BPM\n" +
                        "Measured at: " + dateStr + "\n\n" +
                        "Interpretation:\n" + getInterpretation(spo2, pulse));

        try {
            startActivity(Intent.createChooser(i, "Send mail..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(O2Result.this, "No email clients installed.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        Intent i = new Intent(O2Result.this, Primary.class);
        i.putExtra("Usr", user);
        startActivity(i);
        finish();
    }
}


