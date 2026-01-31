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
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class First extends AppCompatActivity {

    public ImageButton Meas;
    public Button acc;
    public EditText ed1, ed2;
    private Toast mainToast;
    public static String passStr, usrStr, checkpassStr, usrStrlow;
    private UserDB check; // moved here
    private CheckBox chkRememberMe;
    private SharedPreferences loginPreferences;
    private SharedPreferences.Editor loginPrefsEditor;
    private Boolean saveLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);

        // Initialize UserDB safely
        check = new UserDB(this);

        // 🌈 Start animated gradient background
        View root = findViewById(android.R.id.content);
        Drawable background = root.getBackground();
        if (background instanceof AnimationDrawable) {
            AnimationDrawable anim = (AnimationDrawable) background;
            anim.setEnterFadeDuration(2000);
            anim.setExitFadeDuration(2000);
            anim.start();
        }

        // 🔹 Find views
        Meas = findViewById(R.id.prime);
        acc = findViewById(R.id.newacc);
        ed1 = findViewById(R.id.edtu1);
        ed2 = findViewById(R.id.edtp1);
        chkRememberMe = findViewById(R.id.checkBoxRemember);

        // Prevent null crash
        if (Meas == null || acc == null || ed1 == null || ed2 == null) {
            showToast("Layout error: some UI elements not found.");
            return;
        }

        // 🔹 SharedPreferences setup
        loginPreferences = getSharedPreferences("loginPrefs", MODE_PRIVATE);
        loginPrefsEditor = loginPreferences.edit();
        saveLogin = loginPreferences.getBoolean("saveLogin", false);

        if (saveLogin) {
            ed1.setText(loginPreferences.getString("username", ""));
            ed2.setText(loginPreferences.getString("password", ""));
            chkRememberMe.setChecked(true);
        }

        // 🔹 Login button click
        Meas.setOnClickListener(v -> {
            usrStrlow = ed1.getText().toString();
            passStr = ed2.getText().toString();
            usrStr = usrStrlow.toLowerCase();

            if (usrStr.isEmpty() || passStr.isEmpty()) {
                showToast("Please enter your Username and Password");
                return;
            }

            if (usrStr.length() < 3 || usrStr.length() > 20) {
                showToast("Username length must be between 3–20 characters");
                return;
            }

            if (passStr.length() < 3 || passStr.length() > 20) {
                showToast("Password length must be between 3–20 characters");
                return;
            }

            checkpassStr = check.checkPass(usrStr);

            if (passStr.equals(checkpassStr)) {
                if (chkRememberMe.isChecked()) {
                    loginPrefsEditor.putBoolean("saveLogin", true);
                    loginPrefsEditor.putString("username", usrStr);
                    loginPrefsEditor.putString("password", passStr);
                    loginPrefsEditor.apply();
                } else {
                    loginPrefsEditor.clear();
                    loginPrefsEditor.commit();
                }

                Intent i = new Intent(v.getContext(), Primary.class);
                i.putExtra("Usr", usrStr);
                startActivity(i);
                finish();
            } else {
                showToast("Username or password incorrect");
            }
        });

        // 🔹 New account button click
        acc.setOnClickListener(v -> {
            Intent i = new Intent(v.getContext(), Login.class);
            startActivity(i);
            finish();
        });
    }

    private void showToast(String message) {
        if (mainToast != null) mainToast.cancel();
        mainToast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
        mainToast.show();
    }
}
