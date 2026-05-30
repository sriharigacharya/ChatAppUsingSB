package com.chat.realtime.controller;

import com.chat.realtime.dto.FriendRequestDto;
import com.chat.realtime.service.FriendRequestService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendRequestController {

    private final FriendRequestService friendRequestService;

    @PostMapping("/request")
    public ResponseEntity<FriendRequestDto> sendRequest(@RequestBody SendRequestBody body) {
        FriendRequestDto dto = friendRequestService.sendRequest(
                body.getSenderId(), body.getRecipientId(), body.getSenderPublicKey());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/accept/{requestId}")
    public ResponseEntity<FriendRequestDto> acceptRequest(
            @PathVariable Long requestId,
            @RequestBody AcceptRequestBody body) {
        FriendRequestDto dto = friendRequestService.acceptRequest(requestId, body.getRecipientPublicKey());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/reject/{requestId}")
    public ResponseEntity<Void> rejectRequest(@PathVariable Long requestId) {
        friendRequestService.rejectRequest(requestId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/pending/{userId}")
    public ResponseEntity<List<FriendRequestDto>> getPendingRequests(@PathVariable Long userId) {
        return ResponseEntity.ok(friendRequestService.getPendingRequests(userId));
    }

    @GetMapping("/list/{userId}")
    public ResponseEntity<List<FriendRequestService.FriendInfo>> getFriends(@PathVariable Long userId) {
        return ResponseEntity.ok(friendRequestService.getFriends(userId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<FriendRequestService.UserInfo>> searchUsers(
            @RequestParam String query,
            @RequestParam Long userId) {
        return ResponseEntity.ok(friendRequestService.searchUsers(query, userId));
    }

    @Data
    public static class SendRequestBody {
        private Long senderId;
        private Long recipientId;
        private String senderPublicKey;
    }

    @Data
    public static class AcceptRequestBody {
        private String recipientPublicKey;
    }
}
