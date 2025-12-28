package com.example.wimap;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText editUsername;

    private EditText editPassword;

    private Button buttonLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        editUsername = findViewById(R.id.edit_username);
        editPassword = findViewById(R.id.edit_password);
        buttonLogin = findViewById(R.id.button_login);

        buttonLogin.setOnClickListener(v -> attemptLogin());

    }

    // Hardcoded for now dont forget to implement
    private void attemptLogin(){
        String username = editUsername.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if(username.equals("admin") && password.equals("admin")){
            launchMainActivity("ADMIN");
        }
        else if(username.equals("user") && password.equals("user")){
            launchMainActivity("USER");
        }
        else{
            Toast.makeText(this, "Invalid username or password", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchMainActivity(String userRole){
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("USER_ROLE", userRole);
        startActivity(intent);
        finish();

    }
}
