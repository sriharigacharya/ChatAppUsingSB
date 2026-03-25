package com.example.chatapp.api;

import com.example.chatapp.models.AuthRequest;
import com.example.chatapp.models.AuthResponse;
import com.example.chatapp.models.ChatMessage;
import com.example.chatapp.models.User;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ApiService {

    @POST("/api/auth/register")
    Call<String> register(@Body AuthRequest request);

    @POST("/api/auth/login")
    Call<AuthResponse> login(@Body AuthRequest request);

    @GET("/api/users")
    Call<List<User>> getUsers();

    @GET("/messages/{senderId}/{recipientId}")
    Call<List<ChatMessage>> getChatMessages(@Path("senderId") String senderId, @Path("recipientId") String recipientId);
}
