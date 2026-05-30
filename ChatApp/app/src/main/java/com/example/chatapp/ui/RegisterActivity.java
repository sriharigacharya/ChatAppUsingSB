package com.example.chatapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.chatapp.api.ApiClient;
import com.example.chatapp.api.ApiService;
import com.example.chatapp.api.SessionManager;
import com.example.chatapp.databinding.ActivityRegisterBinding;
import com.example.chatapp.models.AuthRequest;
import com.example.chatapp.models.AuthResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {
    private ActivityRegisterBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        binding.btnRegister.setOnClickListener(v -> registerUser());
        binding.tvBackToLogin.setOnClickListener(v -> finish());
    }

    private void registerUser() {
        String username = binding.etRegUsername.getText().toString().trim();
        String password = binding.etRegPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnRegister.setEnabled(false);
        Toast.makeText(this, "Connecting to server… this may take up to 60s on first launch", Toast.LENGTH_LONG).show();
        apiService.register(new AuthRequest(username, password)).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                binding.btnRegister.setEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse user = response.body();
                    // Store user id + username — backend has no JWT, uses session-based auth
                    sessionManager.saveUserDetails(String.valueOf(user.getId()), user.getUsername());
                    Toast.makeText(RegisterActivity.this, "Registration Successful! Welcome, " + user.getUsername(), Toast.LENGTH_SHORT).show();
                    finish(); // Go back to login
                } else {
                    String msg = response.code() == 409 ? "Username already taken" : "Registration failed: " + response.code();
                    Toast.makeText(RegisterActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                binding.btnRegister.setEnabled(true);
                Toast.makeText(RegisterActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}

