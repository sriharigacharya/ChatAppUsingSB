package com.example.chatapp.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.chatapp.api.ApiClient;
import com.example.chatapp.api.ApiService;
import com.example.chatapp.api.CryptoManager;
import com.example.chatapp.api.SessionManager;
import com.example.chatapp.databinding.ActivityFriendRequestsBinding;
import com.example.chatapp.models.AcceptRequestPayload;
import com.example.chatapp.models.FriendRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FriendRequestsActivity extends AppCompatActivity {

    private ActivityFriendRequestsBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private CryptoManager cryptoManager;
    private FriendRequestAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFriendRequestsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Friend Requests");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        sessionManager = new SessionManager(this);
        apiService = ApiClient.getApiService(this);
        cryptoManager = new CryptoManager(this);

        setupRecyclerView();
        fetchPendingRequests();
    }

    private void setupRecyclerView() {
        binding.rvFriendRequests.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FriendRequestAdapter(new FriendRequestAdapter.OnRequestActionListener() {
            @Override
            public void onAccept(FriendRequest request) {
                acceptRequest(request);
            }

            @Override
            public void onReject(FriendRequest request) {
                rejectRequest(request);
            }
        });
        binding.rvFriendRequests.setAdapter(adapter);
    }

    private void fetchPendingRequests() {
        Long userId = Long.parseLong(sessionManager.getUserId());
        apiService.getPendingRequests(userId).enqueue(new Callback<List<FriendRequest>>() {
            @Override
            public void onResponse(Call<List<FriendRequest>> call, Response<List<FriendRequest>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    adapter.setRequests(response.body());
                    if (response.body().isEmpty()) {
                        Toast.makeText(FriendRequestsActivity.this, "No pending requests", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<List<FriendRequest>> call, Throwable t) {
                Toast.makeText(FriendRequestsActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void acceptRequest(FriendRequest request) {
        String myPublicKey = cryptoManager.getPublicKeyBase64();
        AcceptRequestPayload payload = new AcceptRequestPayload(myPublicKey);

        apiService.acceptFriendRequest(request.getId(), payload).enqueue(new Callback<FriendRequest>() {
            @Override
            public void onResponse(Call<FriendRequest> call, Response<FriendRequest> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(FriendRequestsActivity.this,
                            "Accepted request from " + request.getSenderUsername(), Toast.LENGTH_SHORT).show();
                    adapter.removeRequest(request);
                } else {
                    Toast.makeText(FriendRequestsActivity.this, "Failed to accept", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<FriendRequest> call, Throwable t) {
                Toast.makeText(FriendRequestsActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void rejectRequest(FriendRequest request) {
        apiService.rejectFriendRequest(request.getId()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(FriendRequestsActivity.this,
                            "Rejected request from " + request.getSenderUsername(), Toast.LENGTH_SHORT).show();
                    adapter.removeRequest(request);
                } else {
                    Toast.makeText(FriendRequestsActivity.this, "Failed to reject", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(FriendRequestsActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
