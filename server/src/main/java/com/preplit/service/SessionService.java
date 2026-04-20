package com.preplit.service;

import com.preplit.model.*;
import com.preplit.repository.MessageRepository;
import com.preplit.repository.SessionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;

    public SessionService(SessionRepository sessionRepository, MessageRepository messageRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    public Session createSession(String userId, InterviewType type, SdeRole role) {
        Session session = new Session();
        session.setUserId(userId);
        session.setType(type);
        session.setRole(role);
        session.setStatus(SessionStatus.CREATED);
        return sessionRepository.save(session);
    }

    public Session getSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    public Message addMessage(UUID sessionId, MessageRole role, String content) {
        Message message = new Message();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        return messageRepository.save(message);
    }

    public List<Message> getHistory(UUID sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }
}
