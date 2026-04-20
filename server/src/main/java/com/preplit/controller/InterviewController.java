package com.preplit.controller;

import com.preplit.model.Message;
import com.preplit.model.MessageRole;
import com.preplit.model.Session;
import com.preplit.router.ModelRouter;
import com.preplit.service.PromptBuilder;
import com.preplit.service.RAGService;
import com.preplit.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.preplit.model.InterviewType;
import com.preplit.model.SdeRole;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class InterviewController {

    private static final Logger log = LoggerFactory.getLogger(InterviewController.class);

    private final SessionService sessionService;
    private final RAGService ragService;
    private final ModelRouter modelRouter;
    private final PromptBuilder promptBuilder;
    private final boolean devMode;

    public InterviewController(SessionService sessionService, RAGService ragService,
                               ModelRouter modelRouter, PromptBuilder promptBuilder,
                               @Value("${app.dev-mode:false}") boolean devMode) {
        this.sessionService = sessionService;
        this.ragService = ragService;
        this.modelRouter = modelRouter;
        this.promptBuilder = promptBuilder;
        this.devMode = devMode;
    }

    record CreateSessionRequest(InterviewType type, SdeRole role) {}
    record SessionResponse(UUID id, InterviewType type, SdeRole role, String status) {}
    record MessageRequest(String content) {}
    record MessageResponse(UUID id, String role, String content, Instant createdAt) {}
    record SessionWithMessagesResponse(UUID id, InterviewType type, SdeRole role, String status, List<MessageResponse> messages) {}

    @PostMapping("/sessions")
    public SessionResponse createSession(@RequestBody CreateSessionRequest req) {
        Session session = sessionService.createSession("anonymous", req.type(), req.role());

        // Add greeting message
        String greeting = String.format(
            "Hello! I'm your PrepLit interviewer for today's %s interview. " +
            "Could you please introduce yourself briefly?",
            req.type()
        );
        sessionService.addMessage(session.getId(), MessageRole.ASSISTANT, greeting);

        return new SessionResponse(session.getId(), session.getType(), session.getRole(), session.getStatus().name());
    }

    @PostMapping(value = "/sessions/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sendMessage(@PathVariable UUID id,
                                                      @RequestBody MessageRequest req) {
        if (devMode) {
            log.info("===== RECEIVED MESSAGE =====");
            log.info("Session ID: {}", id);
            log.info("User message: {}", req.content());
        }

        if (req.content() == null || req.content().isBlank()) {
            throw new IllegalArgumentException("Message content is required");
        }

        sessionService.addMessage(id, MessageRole.USER, req.content());

        Session session = sessionService.getSession(id);
        List<Message> history = sessionService.getHistory(id);

        if (devMode) {
            log.info("History size: {}", history.size());
            for (Message msg : history) {
                log.info("  [{}]: {}", msg.getRole(), msg.getContent().substring(0, Math.min(100, msg.getContent().length())) + "...");
            }
        }

        String ragContext;
        try {
            ragContext = ragService.buildContext(req.content(), 5);
        } catch (Exception e) {
            if (devMode) {
                log.warn("RAG context failed: {}", e.getMessage());
            }
            ragContext = "";
        }

        String systemPrompt = promptBuilder.build(session.getType(), session.getRole(), ragContext);

        if (devMode) {
            log.info("System prompt length: {} chars", systemPrompt.length());
            log.info("Calling AI model...");
        }

        StringBuilder accumulated = new StringBuilder();
        StringBuilder buffer = new StringBuilder();
        boolean[] insideThinkTag = {false};

        return modelRouter.streamChat(history, systemPrompt)
                .doOnNext(token -> {
                    if (devMode) {
                        log.debug("Token received: {}", token);
                    }
                })
                .flatMap(token -> {
                    accumulated.append(token);
                    buffer.append(token);

                    // Process buffer to filter out <think>...</think> content
                    String result = processThinkTags(buffer, insideThinkTag);

                    if (result.isEmpty()) {
                        return Flux.empty();
                    }
                    return Flux.just(result);
                })
                .doOnComplete(() -> {
                    if (devMode) {
                        log.info("Stream completed. Total response length: {}", accumulated.length());
                    }
                })
                .doFinally(signal -> {
                    if (devMode) {
                        log.info("Stream finalized with signal: {}", signal);
                    }
                    String assistantContent = stripThinkTags(accumulated.toString());
                    if (!assistantContent.isBlank()) {
                        sessionService.addMessage(id, MessageRole.ASSISTANT, assistantContent);
                    }
                })
                .map(token -> ServerSentEvent.<String>builder(token).build())
                .onErrorResume(e -> {
                    log.error("Error in streamChat: {}", e.getMessage(), e);
                    return Flux.just(ServerSentEvent.<String>builder("I'm sorry, I didn't catch that. Could you please repeat?").build());
                });
    }
    
    private String processThinkTags(StringBuilder buffer, boolean[] insideThinkTag) {
        StringBuilder result = new StringBuilder();
        String content = buffer.toString();

        while (true) {
            if (insideThinkTag[0]) {
                // Looking for </think>
                int endIdx = content.indexOf("</think>");
                if (endIdx == -1) {
                    // Haven't found end tag yet, keep buffering
                    // But check if we might have a partial tag
                    if (content.length() > 8) {
                        // Keep last 8 chars in case </think> is split
                        buffer.setLength(0);
                        buffer.append(content.substring(content.length() - 8));
                    }
                    return result.toString();
                } else {
                    // Found end tag, skip everything up to and including it
                    insideThinkTag[0] = false;
                    content = content.substring(endIdx + 8);
                    buffer.setLength(0);
                    buffer.append(content);
                }
            } else {
                // Looking for <think>
                int startIdx = content.indexOf("<think>");
                if (startIdx == -1) {
                    // No think tag, but check for partial tag at end
                    int partialIdx = -1;
                    for (int i = 1; i <= 7 && i <= content.length(); i++) {
                        String suffix = content.substring(content.length() - i);
                        if ("<think>".startsWith(suffix)) {
                            partialIdx = content.length() - i;
                            break;
                        }
                    }

                    if (partialIdx != -1) {
                        // Output everything before the partial tag
                        result.append(content.substring(0, partialIdx));
                        buffer.setLength(0);
                        buffer.append(content.substring(partialIdx));
                    } else {
                        // No partial tag, output everything
                        result.append(content);
                        buffer.setLength(0);
                    }
                    return result.toString();
                } else {
                    // Found start tag
                    result.append(content.substring(0, startIdx));
                    insideThinkTag[0] = true;
                    content = content.substring(startIdx + 7);
                    buffer.setLength(0);
                    buffer.append(content);
                }
            }
        }
    }

    private String stripThinkTags(String content) {
        return content.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    @GetMapping("/sessions/{id}")
    public SessionWithMessagesResponse getSession(@PathVariable UUID id) {
        Session session = sessionService.getSession(id);
        List<Message> messages = sessionService.getHistory(id);
        List<MessageResponse> messageResponses = messages.stream()
                .map(m -> new MessageResponse(m.getId(), m.getRole().name(), m.getContent(), m.getCreatedAt()))
                .toList();
        return new SessionWithMessagesResponse(session.getId(), session.getType(), session.getRole(),
                session.getStatus().name(), messageResponses);
    }
}
