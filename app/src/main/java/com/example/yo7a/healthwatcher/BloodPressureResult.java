package com.example.yo7a.healthwatcher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.text.DecimalFormat;

public class BloodPressureResult extends AppCompatActivity {

    private TextView tvSystolic, tvDiastolic, tvCategory, tvAdvice;
    private ImageButton btnShare, btnCopy;
    private Button btnRetake;

    private static final DecimalFormat INT_FMT = new DecimalFormat("0");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blood_pressure_result);

        // Initialize all UI elements
        tvSystolic = findViewById(R.id.BP_systolic);
        tvDiastolic = findViewById(R.id.BP_diastolic);
        tvCategory = findViewById(R.id.BP_category);
        tvAdvice = findViewById(R.id.BP_advice);
        btnShare = findViewById(R.id.SendBP);
        btnCopy = findViewById(R.id.CopyBP);
        btnRetake = findViewById(R.id.RetakeBP);

        // Get data from previous activity
        Intent intent = getIntent();
        int sp = intent.getIntExtra("SP", 0);  // systolic
        int dp = intent.getIntExtra("DP", 0);  // diastolic
        String user = intent.getStringExtra("Usr");

        // Validate measurement
        if (sp < 60 || dp < 40 || sp > 250 || dp > 200) {
            showToast("Measurement error — please retake.");
            finish();
            return;
        }

        // Display readings
        tvSystolic.setText(INT_FMT.format(sp) + " mmHg");
        tvDiastolic.setText(INT_FMT.format(dp) + " mmHg");

        // Classify BP levels
        CategoryResult cat = classifyBloodPressure(sp, dp);
        tvCategory.setText(cat.label);
        tvCategory.setTextColor(cat.color);
        tvAdvice.setText(cat.advice);

        // Share result
        btnShare.setOnClickListener(v -> {
            String text = buildShareText(sp, dp, cat.label, user);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(send, "Share Blood Pressure via"));
        });

        // Copy result
        btnCopy.setOnClickListener(v -> {
            String text = buildShareText(sp, dp, cat.label, user);
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Blood Pressure", text));
                showToast("Blood pressure copied to clipboard");
            }
        });

        // Retake measurement
        btnRetake.setOnClickListener(v -> {
            Intent i = new Intent(BloodPressureResult.this, StartVitalSigns.class);
            i.putExtra("Usr", user);
            i.putExtra("Page", 2);  // open BP page directly
            startActivity(i);
            finish();
        });
    }

    /** Classify blood pressure using AHA (American Heart Association) standards **/
    private CategoryResult classifyBloodPressure(int sp, int dp) {
        // Crisis
        if (sp >= 180 || dp >= 120)
            return new CategoryResult("Hypertensive Crisis",
                    Color.parseColor("#B71C1C"),
                    "⚠️ Seek emergency care immediately!");

        // Stage 2 Hypertension
        if (sp >= 140 || dp >= 90)
            return new CategoryResult("Stage 2 Hypertension",
                    Color.parseColor("#D32F2F"),
                    "Consult your doctor for medication and regular monitoring.");

        // Stage 1 Hypertension
        if ((sp >= 130 && sp < 140) || (dp >= 80 && dp < 90))
            return new CategoryResult("Stage 1 Hypertension",
                    Color.parseColor("#F57C00"),
                    "Lifestyle adjustments and follow-up are advised.");

        // Elevated
        if (sp >= 120 && sp < 130 && dp < 80)
            return new CategoryResult("Elevated",
                    Color.parseColor("#FBC02D"),
                    "Maintain a balanced diet, reduce sodium, and exercise regularly.");

        // Normal
        return new CategoryResult("Normal",
                Color.parseColor("#388E3C"),
                "✅ Your blood pressure is healthy. Keep up your good habits!");
    }

    /** Format text for sharing or copying **/
    private String buildShareText(int sp, int dp, String category, String user) {
        String who = (user != null && !user.isEmpty()) ? ("User: " + user + "\n") : "";
        return who + "Blood Pressure Reading:\n" +
                "Systolic: " + sp + " mmHg\n" +
                "Diastolic: " + dp + " mmHg\n" +
                "Category: " + category + "\n\n" +
                "Measured via HealthWatcher App — Highly Accurate Results ✅";
    }

    /** Toast helper **/
    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** Helper structure for categorized results **/
    private static class CategoryResult {
        final String label;
        final int color;
        final String advice;
        CategoryResult(String label, int color, String advice) {
            this.label = label;
            this.color = color;
            this.advice = advice;
        }
    }
}
