package com.example.chatapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.chatapp.api.ApiClient;
import com.example.chatapp.api.ApiService;
import com.example.chatapp.api.SessionManager;
import com.example.chatapp.databinding.ActivityMainBinding;
import com.example.chatapp.models.User;
import com.example.chatapp.ui.ChatActivity;
import com.example.chatapp.ui.LoginActivity;
import com.example.chatapp.ui.UserAdapter;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private UserAdapter userAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Contacts");
        }

        sessionManager = new SessionManager(this);
        if (sessionManager.fetchAuthToken() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        apiService = ApiClient.getApiService(this);

        setupRecyclerView();
        fetchUsers();
    }

    private void setupRecyclerView() {
        binding.rvUsers.setLayoutManager(new LinearLayoutManager(this));
        userAdapter = new UserAdapter(user -> {
            Intent intent = new Intent(MainActivity.this, ChatActivity.class);
            intent.putExtra("RECIPIENT_ID", user.getId());
            intent.putExtra("RECIPIENT_USERNAME", user.getUsername());
            startActivity(intent);
        });
        binding.rvUsers.setAdapter(userAdapter);
    }

    private void fetchUsers() {
        apiService.getUsers().enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String currentUserId = sessionManager.getUserId();
                    List<User> otherUsers = new ArrayList<>();
                    for (User u : response.body()) {
                        if (!u.getId().equals(currentUserId)) {
                            otherUsers.add(u);
                        }
                    }
                    userAdapter.setUsers(otherUsers);
                } else {
                    Toast.makeText(MainActivity.this, "Failed to load users", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Logout");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            sessionManager.clearSession();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}