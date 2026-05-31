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
import com.example.chatapp.api.CryptoManager;
import com.example.chatapp.api.SessionManager;
import com.example.chatapp.databinding.ActivityMainBinding;
import com.example.chatapp.models.User;
import com.example.chatapp.ui.ChatActivity;
import com.example.chatapp.ui.FriendRequestsActivity;
import com.example.chatapp.ui.LoginActivity;
import com.example.chatapp.ui.SearchUsersActivity;
import com.example.chatapp.ui.UserAdapter;
import com.example.chatapp.api.WebSocketManager;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private CryptoManager cryptoManager;
    private UserAdapter userAdapter;
    private final WebSocketManager.StatusListener statusListener = new WebSocketManager.StatusListener() {
        @Override
        public void onStatusUpdated(Long userId, String status) {
            if (userAdapter != null) {
                userAdapter.updateUserStatus(userId, status);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Friends");
        }

        sessionManager = new SessionManager(this);
        if (sessionManager.getUserId() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        apiService = ApiClient.getApiService(this);
        cryptoManager = new CryptoManager(this); // Generate/load RSA keypair on launch

        setupRecyclerView();
        WebSocketManager.getInstance().registerStatusListener(statusListener);
        WebSocketManager.getInstance().connect(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchFriends(); // Refresh friends list whenever returning from another activity
        WebSocketManager.getInstance().connect(this);
    }

    private void setupRecyclerView() {
        binding.rvUsers.setLayoutManager(new LinearLayoutManager(this));
        userAdapter = new UserAdapter(user -> {
            Intent intent = new Intent(MainActivity.this, ChatActivity.class);
            intent.putExtra("RECIPIENT_ID", String.valueOf(user.getId()));
            intent.putExtra("RECIPIENT_USERNAME", user.getUsername());
            intent.putExtra("RECIPIENT_PUBLIC_KEY", user.getPublicKey());
            startActivity(intent);
        });
        binding.rvUsers.setAdapter(userAdapter);
    }

    private void fetchFriends() {
        Long userId = Long.parseLong(sessionManager.getUserId());
        apiService.getFriends(userId).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    userAdapter.setUsers(response.body());
                } else {
                    Toast.makeText(MainActivity.this, "Failed to load friends", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WebSocketManager.getInstance().unregisterStatusListener(statusListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Search Users");
        menu.add(0, 2, 1, "Friend Requests");
        menu.add(0, 3, 2, "Logout");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                startActivity(new Intent(this, SearchUsersActivity.class));
                return true;
            case 2:
                startActivity(new Intent(this, FriendRequestsActivity.class));
                return true;
            case 3:
                WebSocketManager.getInstance().disconnect();
                sessionManager.clearSession();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}