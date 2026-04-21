package com.preplit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public SessionService(SessionRepository sessionRepository, MessageRepository messageRepository, ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    public Session createSession(String userId, InterviewType type, SdeRole role) {
        Session session = new Session();
        session.setUserId(userId);
        session.setType(type);
        session.setRole(role);
        session.setStatus(SessionStatus.CREATED);
        return sessionRepository.save(session);
    }

    public Session createSessionWithResume(String userId, InterviewType type, SdeRole role, String resumeText) {
        Session session = new Session();
        session.setUserId(userId);
        session.setType(type);
        session.setRole(role);
        session.setStatus(SessionStatus.CREATED);
        session.setResumeText(resumeText);
        return sessionRepository.save(session);
    }

    public List<Session> listSessions() {
        return sessionRepository.findAllByOrderByCreatedAtDesc();
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

    public void saveFeedback(UUID sessionId, FeedbackReport feedback) {
        Session session = getSession(sessionId);
        try {
            session.setFeedbackJson(objectMapper.writeValueAsString(feedback));
            session.setStatus(SessionStatus.COMPLETED);
            sessionRepository.save(session);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize feedback", e);
        }
    }

    public FeedbackReport getFeedback(UUID sessionId) {
        Session session = getSession(sessionId);
        if (session.getFeedbackJson() == null) return null;
        try {
            return objectMapper.readValue(session.getFeedbackJson(), FeedbackReport.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize feedback", e);
        }
    }
}
