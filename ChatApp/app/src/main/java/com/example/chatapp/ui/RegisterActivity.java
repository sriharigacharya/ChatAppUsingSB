package com.example.chatapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.chatapp.api.ApiClient;
import com.example.chatapp.api.ApiService;
import com.example.chatapp.databinding.ActivityRegisterBinding;
import com.example.chatapp.models.AuthRequest;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {
    private ActivityRegisterBinding binding;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(this);

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
        apiService.register(new AuthRequest(username, password)).enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                binding.btnRegister.setEnabled(true);
                if (response.isSuccessful()) {
                    Toast.makeText(RegisterActivity.this, "Registration Successful", Toast.LENGTH_SHORT).show();
                    finish(); // Go back to login
                } else {
                    Toast.makeText(RegisterActivity.this, "Registration failed: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                binding.btnRegister.setEnabled(true);
                Toast.makeText(RegisterActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
