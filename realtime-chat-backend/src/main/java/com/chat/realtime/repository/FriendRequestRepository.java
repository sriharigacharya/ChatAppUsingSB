package com.chat.realtime.repository;

import com.chat.realtime.model.FriendRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    List<FriendRequest> findByRecipientIdAndStatus(Long recipientId, FriendRequest.Status status);

    @Query("SELECT fr FROM FriendRequest fr WHERE (fr.senderId = :userId OR fr.recipientId = :userId) AND fr.status = 'ACCEPTED'")
    List<FriendRequest> findAcceptedFriends(@Param("userId") Long userId);

    @Query("SELECT fr FROM FriendRequest fr WHERE ((fr.senderId = :userId1 AND fr.recipientId = :userId2) OR (fr.senderId = :userId2 AND fr.recipientId = :userId1)) AND fr.status <> 'REJECTED'")
    Optional<FriendRequest> findExistingRequest(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
