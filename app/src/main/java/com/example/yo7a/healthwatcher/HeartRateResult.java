package com.example.yo7a.healthwatcher;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class HeartRateResult extends AppCompatActivity {

    private String user;
    private int HR;
    private DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    private Date today = Calendar.getInstance().getTime();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heart_rate_result);

        String currentDate = df.format(today);

        // Updated IDs matching the XML layout
        TextView RHR = findViewById(R.id.HRR);
        TextView MHR = findViewById(R.id.MHR);
        TextView AHR = findViewById(R.id.AHR);
        Button btnBack = findViewById(R.id.btnBack);

        // Get intent data
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            HR = bundle.getInt("bpm", 0);
            user = bundle.getString("Usr", "Unknown");
            Log.d("DEBUG_TAG", "User: " + user);
            RHR.setText("Resting Heart Rate: " + HR + " bpm");

            // Optional placeholders for MHR and AHR
            MHR.setText("Max Heart Rate: " + (HR + 20) + " bpm");       // Replace with real value if available
            AHR.setText("Average Heart Rate: " + (HR - 5) + " bpm");    // Replace with real value if available
        }

        // Back button listener
        btnBack.setOnClickListener(v -> {
            Intent i = new Intent(HeartRateResult.this, Primary.class);
            i.putExtra("Usr", user);
            startActivity(i);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        // Override to navigate to Primary activity
        Intent i = new Intent(HeartRateResult.this, Primary.class);
        i.putExtra("Usr", user);
        startActivity(i);
        finish();
    }
}
