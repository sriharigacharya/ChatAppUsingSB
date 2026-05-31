package com.chat.realtime.service;

import com.chat.realtime.dto.FriendRequestDto;
import com.chat.realtime.model.FriendRequest;
import com.chat.realtime.model.User;
import com.chat.realtime.repository.FriendRequestRepository;
import com.chat.realtime.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendRequestService {

    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;
    private final UserStatusService userStatusService;

    public FriendRequestDto sendRequest(Long senderId, Long recipientId, String senderPublicKey) {
        userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        userRepository.findById(recipientId)
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        friendRequestRepository.findExistingRequest(senderId, recipientId)
                .ifPresent(existing -> {
                    throw new RuntimeException("Friend request already exists");
                });

        FriendRequest request = FriendRequest.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .senderPublicKey(senderPublicKey)
                .status(FriendRequest.Status.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        request = friendRequestRepository.save(request);

        String senderUsername = userRepository.findById(senderId)
                .map(User::getUsername).orElse("");

        return FriendRequestDto.builder()
                .id(request.getId())
                .senderId(request.getSenderId())
                .recipientId(request.getRecipientId())
                .senderUsername(senderUsername)
                .senderPublicKey(request.getSenderPublicKey())
                .status(request.getStatus().name())
                .createdAt(request.getCreatedAt())
                .build();
    }

    public FriendRequestDto acceptRequest(Long requestId, String recipientPublicKey) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (request.getStatus() != FriendRequest.Status.PENDING) {
            throw new RuntimeException("Request is no longer pending");
        }

        request.setStatus(FriendRequest.Status.ACCEPTED);
        request.setRecipientPublicKey(recipientPublicKey);
        request = friendRequestRepository.save(request);

        String senderUsername = userRepository.findById(request.getSenderId())
                .map(User::getUsername).orElse("");
        String recipientUsername = userRepository.findById(request.getRecipientId())
                .map(User::getUsername).orElse("");

        return FriendRequestDto.builder()
                .id(request.getId())
                .senderId(request.getSenderId())
                .recipientId(request.getRecipientId())
                .senderUsername(senderUsername)
                .recipientUsername(recipientUsername)
                .senderPublicKey(request.getSenderPublicKey())
                .recipientPublicKey(request.getRecipientPublicKey())
                .status(request.getStatus().name())
                .createdAt(request.getCreatedAt())
                .build();
    }

    public void rejectRequest(Long requestId) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        request.setStatus(FriendRequest.Status.REJECTED);
        friendRequestRepository.save(request);
    }

    public List<FriendRequestDto> getPendingRequests(Long userId) {
        return friendRequestRepository.findByRecipientIdAndStatus(userId, FriendRequest.Status.PENDING)
                .stream()
                .map(request -> {
                    String senderUsername = userRepository.findById(request.getSenderId())
                            .map(User::getUsername).orElse("");
                    return FriendRequestDto.builder()
                            .id(request.getId())
                            .senderId(request.getSenderId())
                            .recipientId(request.getRecipientId())
                            .senderUsername(senderUsername)
                            .senderPublicKey(request.getSenderPublicKey())
                            .status(request.getStatus().name())
                            .createdAt(request.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    public List<FriendInfo> getFriends(Long userId) {
        return friendRequestRepository.findAcceptedFriends(userId)
                .stream()
                .map(request -> {
                    Long friendId;
                    String friendPublicKey;

                    if (request.getSenderId().equals(userId)) {
                        friendId = request.getRecipientId();
                        friendPublicKey = request.getRecipientPublicKey();
                    } else {
                        friendId = request.getSenderId();
                        friendPublicKey = request.getSenderPublicKey();
                    }

                    User friend = userRepository.findById(friendId).orElse(null);
                    if (friend == null) return null;

                    boolean isOnline = userStatusService.isUserOnline(friend.getUsername());
                    String status = isOnline ? "ONLINE" : "OFFLINE";
                    String lastSeenStr = friend.getLastSeen() != null ? friend.getLastSeen().toString() : null;

                    return new FriendInfo(friendId, friend.getUsername(), friendPublicKey, status, lastSeenStr);
                })
                .filter(friendInfo -> friendInfo != null)
                .collect(Collectors.toList());
    }

    public List<UserInfo> searchUsers(String query, Long currentUserId) {
        List<User> matchingUsers = userRepository.findByUsernameContainingIgnoreCase(query);

        return matchingUsers.stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .filter(u -> friendRequestRepository.findExistingRequest(currentUserId, u.getId()).isEmpty())
                .map(u -> new UserInfo(u.getId(), u.getUsername()))
                .collect(Collectors.toList());
    }

    @Data
    @AllArgsConstructor
    public static class FriendInfo {
        private Long id;
        private String username;
        private String publicKey;
        private String status;
        private String lastSeen;
    }

    @Data
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String username;
    }
}
