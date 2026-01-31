package com.example.yo7a.healthwatcher;

import android.widget.CheckBox;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class First extends AppCompatActivity {

    private Button acc, Meas;
    private EditText ed1, ed2;
    private CheckBox chkRememberMe;
    private Toast mainToast;
    private UserDB check;
    private SharedPreferences loginPreferences;
    private SharedPreferences.Editor loginPrefsEditor;
    private boolean saveLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);

        // 🚀 REDIRECT TO LOGIN SCREEN
        startActivity(new Intent(First.this, Login.class));
        finish();
        return;

        /*
        ===============================
        BELOW CODE IS KEPT (UNCHANGED)
        ===============================
        */

        // Initialize database
        // check = new UserDB(getApplicationContext());

        // Animated background
        // View root = findViewById(android.R.id.content);
        // Drawable background = root.getBackground();
        // if (background instanceof AnimationDrawable) {
        //     AnimationDrawable anim = (AnimationDrawable) background;
        //     anim.setEnterFadeDuration(2000);
        //     anim.setExitFadeDuration(2000);
        //     anim.start();
        // }

        // Meas = findViewById(R.id.btnMeasure);
        // acc = findViewById(R.id.btnCreateAcc);
        // ed1 = findViewById(R.id.edtUsername);
        // ed2 = findViewById(R.id.edtPassword);
        // chkRememberMe = findViewById(R.id.chkRememberMe);
    }

    private void showToast(String message) {
        if (mainToast != null) mainToast.cancel();
        mainToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        mainToast.show();
    }
}

