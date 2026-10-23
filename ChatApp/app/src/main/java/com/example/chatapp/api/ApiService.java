package com.example.chatapp.api;

import com.example.chatapp.models.AcceptRequestPayload;
import com.example.chatapp.models.AuthRequest;
import com.example.chatapp.models.AuthResponse;
import com.example.chatapp.models.ChatMessage;
import com.example.chatapp.models.FriendRequest;
import com.example.chatapp.models.FriendRequestPayload;
import com.example.chatapp.models.MediaFileDto;
import com.example.chatapp.models.User;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Streaming;

public interface ApiService {

    @POST("/api/auth/register")
    Call<AuthResponse> register(@Body AuthRequest request);

    @POST("/api/auth/login")
    Call<AuthResponse> login(@Body AuthRequest request);

    @GET("/api/auth/users")
    Call<List<User>> getUsers();

    @GET("/api/messages/{senderId}/{recipientId}")
    Call<List<ChatMessage>> getChatMessages(@Path("senderId") String senderId, @Path("recipientId") String recipientId);

    // ── File and media transfer endpoints ──────────────────────────────────

    @Multipart
    @POST("/api/files/upload")
    Call<MediaFileDto> uploadFile(
            @Part MultipartBody.Part file,
            @Part("recipientId") RequestBody recipientId,
            @Part("fileType") RequestBody fileType,
            @Part("originalFileName") RequestBody originalFileName,
            @Part("senderEncryptedAesKey") RequestBody senderEncryptedAesKey,
            @Part("recipientEncryptedAesKey") RequestBody recipientEncryptedAesKey
    );

    @GET("/api/files/{id}/metadata")
    Call<MediaFileDto> getFileMetadata(@Path("id") String mediaFileId);

    @Streaming
    @GET("/api/files/{id}/download")
    Call<ResponseBody> downloadFile(@Path("id") String mediaFileId);

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
