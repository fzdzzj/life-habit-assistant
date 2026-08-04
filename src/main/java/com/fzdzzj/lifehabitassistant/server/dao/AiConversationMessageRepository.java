package com.fzdzzj.lifehabitassistant.server.dao;

import com.fzdzzj.lifehabitassistant.pojo.AiConversationMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiConversationMessageRepository extends JpaRepository<AiConversationMessage, Long> {
    List<AiConversationMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    List<AiConversationMessage> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    void deleteByConversationId(Long conversationId);
}
