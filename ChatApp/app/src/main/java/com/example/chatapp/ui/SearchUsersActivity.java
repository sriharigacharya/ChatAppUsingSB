package com.example.chatapp.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.chatapp.api.ApiClient;
import com.example.chatapp.api.ApiService;
import com.example.chatapp.api.CryptoManager;
import com.example.chatapp.api.SessionManager;
import com.example.chatapp.databinding.ActivitySearchUsersBinding;
import com.example.chatapp.models.FriendRequest;
import com.example.chatapp.models.FriendRequestPayload;
import com.example.chatapp.models.User;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchUsersActivity extends AppCompatActivity {

    private ActivitySearchUsersBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private CryptoManager cryptoManager;
    private SearchUserAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchUsersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Search Users");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        sessionManager = new SessionManager(this);
        apiService = ApiClient.getApiService(this);
        cryptoManager = new CryptoManager(this);

        setupRecyclerView();

        binding.btnSearch.setOnClickListener(v -> searchUsers());
    }

    private void setupRecyclerView() {
        binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchUserAdapter(this::sendFriendRequest);
        binding.rvSearchResults.setAdapter(adapter);
    }

    private void searchUsers() {
        String query = binding.etSearch.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Enter a username to search", Toast.LENGTH_SHORT).show();
            return;
        }

        Long userId = Long.parseLong(sessionManager.getUserId());
        apiService.searchUsers(query, userId).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    adapter.setUsers(response.body());
                    if (response.body().isEmpty()) {
                        Toast.makeText(SearchUsersActivity.this, "No users found", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(SearchUsersActivity.this, "Search failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                Toast.makeText(SearchUsersActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendFriendRequest(User user) {
        Long senderId = Long.parseLong(sessionManager.getUserId());
        String publicKey = cryptoManager.getPublicKeyBase64();

        FriendRequestPayload payload = new FriendRequestPayload(senderId, user.getId(), publicKey);

        apiService.sendFriendRequest(payload).enqueue(new Callback<FriendRequest>() {
            @Override
            public void onResponse(Call<FriendRequest> call, Response<FriendRequest> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(SearchUsersActivity.this,
                            "Friend request sent to " + user.getUsername(), Toast.LENGTH_SHORT).show();
                    adapter.removeUser(user);
                } else {
                    String msg = response.code() == 409 ? "Request already exists" : "Failed to send request";
                    Toast.makeText(SearchUsersActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<FriendRequest> call, Throwable t) {
                Toast.makeText(SearchUsersActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
