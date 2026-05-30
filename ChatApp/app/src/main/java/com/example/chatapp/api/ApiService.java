package com.example.chatapp.api;

import com.example.chatapp.models.AcceptRequestPayload;
import com.example.chatapp.models.AuthRequest;
import com.example.chatapp.models.AuthResponse;
import com.example.chatapp.models.ChatMessage;
import com.example.chatapp.models.FriendRequest;
import com.example.chatapp.models.FriendRequestPayload;
import com.example.chatapp.models.User;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    @POST("/api/auth/register")
    Call<AuthResponse> register(@Body AuthRequest request);

    @POST("/api/auth/login")
    Call<AuthResponse> login(@Body AuthRequest request);

    @GET("/api/auth/users")
    Call<List<User>> getUsers();

    @GET("/api/messages/{senderId}/{recipientId}")
    Call<List<ChatMessage>> getChatMessages(@Path("senderId") String senderId, @Path("recipientId") String recipientId);

    // ── Friend request endpoints ──────────────────────────────────────────

    @POST("/api/friends/request")
    Call<FriendRequest> sendFriendRequest(@Body FriendRequestPayload payload);

    @POST("/api/friends/accept/{requestId}")
    Call<FriendRequest> acceptFriendRequest(@Path("requestId") Long requestId, @Body AcceptRequestPayload payload);

    @POST("/api/friends/reject/{requestId}")
    Call<Void> rejectFriendRequest(@Path("requestId") Long requestId);

    @GET("/api/friends/pending/{userId}")
    Call<List<FriendRequest>> getPendingRequests(@Path("userId") Long userId);

    @GET("/api/friends/list/{userId}")
    Call<List<User>> getFriends(@Path("userId") Long userId);

    @GET("/api/friends/search")
    Call<List<User>> searchUsers(@Query("query") String query, @Query("userId") Long userId);
}
