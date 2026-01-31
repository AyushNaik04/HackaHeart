package com.example.yo7a.healthwatcher;

import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;

public class Login extends AppCompatActivity {

    private EditText username, password;
    private Button loginButton;
    private TextInputLayout passwordLayout;
    private boolean passwordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // --------------------------------------------------
        // Start animated gradient background (B1 – STEP 2)
        // --------------------------------------------------
        View root = findViewById(R.id.loginRoot);
        if (root != null) {
            Drawable background = root.getBackground();
            if (background instanceof AnimationDrawable) {
                AnimationDrawable anim = (AnimationDrawable) background;
                anim.setEnterFadeDuration(2000);
                anim.setExitFadeDuration(2000);
                anim.start();
            }
        }

        // --------------------------------------------------
        // Bind views
        // --------------------------------------------------
        username = findViewById(R.id.loginUsername);
        password = findViewById(R.id.loginPassword);
        loginButton = findViewById(R.id.loginButton);
        passwordLayout = findViewById(R.id.passwordLayout);

        // --------------------------------------------------
        // Password eye toggle
        // --------------------------------------------------
        passwordLayout.setEndIconOnClickListener(v -> {
            passwordVisible = !passwordVisible;

            if (passwordVisible) {
                password.setInputType(
                        InputType.TYPE_CLASS_TEXT |
                                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                );
                passwordLayout.setEndIconDrawable(R.drawable.ic_eye_off);
            } else {
                password.setInputType(
                        InputType.TYPE_CLASS_TEXT |
                                InputType.TYPE_TEXT_VARIATION_PASSWORD
                );
                passwordLayout.setEndIconDrawable(R.drawable.ic_eye);
            }

            password.setSelection(password.getText().length());
        });

        // --------------------------------------------------
        // Login button
        // --------------------------------------------------
        loginButton.setOnClickListener(v -> handleLogin());
    }

    private void handleLogin() {
        String usrStr = username.getText().toString().trim().toLowerCase();
        String passStr = password.getText().toString().trim();

        if (usrStr.isEmpty() || passStr.isEmpty()) {
            toast("Please enter your Username and Password");
            return;
        }

        if (usrStr.length() < 3 || usrStr.length() > 20) {
            toast("Username must be between 3–20 characters");
            return;
        }

        if (passStr.length() < 3 || passStr.length() > 20) {
            toast("Password must be between 3–20 characters");
            return;
        }

        UserDB check = new UserDB(getApplicationContext());
        String dbPass = check.checkPass(usrStr);

        if ("Not found".equals(dbPass)) {
            toast("User not found — please create an account first");
            return;
        }

        if (passStr.equals(dbPass)) {
            Intent i = new Intent(Login.this, Primary.class);
            i.putExtra("Usr", usrStr);
            startActivity(i);
            finish();
        } else {
            toast("Username or password incorrect");
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
