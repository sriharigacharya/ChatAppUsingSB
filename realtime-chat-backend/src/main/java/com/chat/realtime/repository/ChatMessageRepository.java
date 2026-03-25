package com.chat.realtime.repository;

import com.chat.realtime.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySenderIdAndRecipientIdOrRecipientIdAndSenderIdOrderByCreatedAtAsc(
            Long senderId1, Long recipientId1,
            Long recipientId2, Long senderId2
    );
}
