package com.example.yo7a.healthwatcher;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class Primary extends AppCompatActivity {

    private String user;
    private int p;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_primary);

        // Find all buttons
        ImageButton HeartRate = findViewById(R.id.HR);
        ImageButton BloodPressure = findViewById(R.id.BP);
        ImageButton Ox2 = findViewById(R.id.O2);
        ImageButton RRate = findViewById(R.id.RR);
        ImageButton VitalSigns = findViewById(R.id.VS);
        ImageButton Abt = findViewById(R.id.About);
        ImageButton Glucose = findViewById(R.id.Glucose);  // NEW Glucose button

        // Get username from intent
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            user = extras.getString("Usr");
        }

        // About button
        Abt.setOnClickListener(v -> {
            Intent i = new Intent(v.getContext(), AboutApp.class);
            startActivity(i);
            finish();
        });

        // Heart Rate
        HeartRate.setOnClickListener(v -> {
            p = 1;
            Intent i = new Intent(v.getContext(), StartVitalSigns.class);
            i.putExtra("Usr", user);
            i.putExtra("Page", p);
            startActivity(i);
            finish();
        });

        // Blood Pressure
        BloodPressure.setOnClickListener(v -> {
            p = 2;
            Intent i = new Intent(v.getContext(), StartVitalSigns.class);
            i.putExtra("Usr", user);
            i.putExtra("Page", p);
            startActivity(i);
            finish();
        });

        // Respiratory Rate
        RRate.setOnClickListener(v -> {
            p = 3;
            Intent i = new Intent(v.getContext(), StartVitalSigns.class);
            i.putExtra("Usr", user);
            i.putExtra("Page", p);
            startActivity(i);
            finish();
        });

        // SpO2
        Ox2.setOnClickListener(v -> {
            p = 4;
            Intent i = new Intent(v.getContext(), StartVitalSigns.class);
            i.putExtra("Usr", user);
            i.putExtra("Page", p);
            startActivity(i);
            finish();
        });

        // Vital Signs
        VitalSigns.setOnClickListener(v -> {
            p = 5;
            Intent i = new Intent(v.getContext(), StartVitalSigns.class);
            i.putExtra("Usr", user);
            i.putExtra("Page", p);
            startActivity(i);
            finish();
        });

        // Glucose Estimation (NEW)
        Glucose.setOnClickListener(v -> {
            Intent i = new Intent(v.getContext(), GlucoseActivity.class);
            i.putExtra("Usr", user); // Optional: pass username
            startActivity(i);
        });

    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("Really Exit?")
                .setMessage("Are you sure you want to exit?")
                .setNegativeButton(android.R.string.no, null)
                .setPositiveButton(android.R.string.yes, (arg0, arg1) -> {
                    Primary.super.onBackPressed();
                    finish();
                    System.exit(0);
                }).create().show();
    }
}
