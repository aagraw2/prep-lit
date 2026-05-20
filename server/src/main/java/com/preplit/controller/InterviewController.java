package com.preplit.controller;

import com.preplit.model.Message;
import com.preplit.model.MessageRole;
import com.preplit.model.Session;
import com.preplit.router.ModelRouter;
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
import com.preplit.model.DifficultyLevel;
import com.preplit.model.InterviewContext;
import com.preplit.model.FeedbackReport;
import com.preplit.service.InterviewPromptService;
import com.preplit.service.InterviewOrchestrator;
import com.preplit.service.ResumeParserService;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class InterviewController {

    private static final Logger log = LoggerFactory.getLogger(InterviewController.class);

    private final SessionService sessionService;
    private final RAGService ragService;
    private final ModelRouter modelRouter;
    private final InterviewPromptService interviewPromptService;
    private final InterviewOrchestrator orchestrator;
    private final ResumeParserService resumeParserService;
    private final boolean devMode;

    public InterviewController(SessionService sessionService, RAGService ragService,
                               ModelRouter modelRouter, InterviewPromptService interviewPromptService,
                               InterviewOrchestrator orchestrator, ResumeParserService resumeParserService,
                               @Value("${app.dev-mode:false}") boolean devMode) {
        this.sessionService = sessionService;
        this.ragService = ragService;
        this.modelRouter = modelRouter;
        this.interviewPromptService = interviewPromptService;
        this.orchestrator = orchestrator;
        this.resumeParserService = resumeParserService;
        this.devMode = devMode;
    }

    record CreateSessionRequest(InterviewType type, SdeRole role) {}
    record SessionResponse(UUID id, InterviewType type, SdeRole role, String status, java.time.Instant createdAt, FeedbackResponse feedback) {}
    record MessageRequest(String content) {}
    record MessageResponse(UUID id, String role, String content, Instant createdAt) {}
    record SessionWithMessagesResponse(UUID id, InterviewType type, SdeRole role, String status, List<MessageResponse> messages, FeedbackResponse feedback) {}

    @PostMapping("/sessions")
    public SessionResponse createSession(@RequestBody CreateSessionRequest req) {
        Session session = sessionService.createSession("anonymous", req.type(), req.role());

        // Initialize interview context for state tracking
        DifficultyLevel difficulty = mapRoleToDifficulty(req.role());
        orchestrator.startInterview(session.getId(), difficulty, req.type());

        // Add greeting message
        String greeting = getGreetingForType(req.type());
        sessionService.addMessage(session.getId(), MessageRole.ASSISTANT, greeting);

        return new SessionResponse(session.getId(), session.getType(), session.getRole(), session.getStatus().name(), session.getCreatedAt(), null);
    }

    @PostMapping(value = "/sessions/with-resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<SessionResponse> createSessionWithResume(
            @RequestPart("type") String typeStr,
            @RequestPart("role") String roleStr,
            @RequestPart("resume") FilePart resumeFile) {

        InterviewType type = InterviewType.valueOf(typeStr);
        SdeRole role = SdeRole.valueOf(roleStr);

        if (type != InterviewType.RESUME_GRILLING) {
            throw new IllegalArgumentException("Resume upload only supported for RESUME_GRILLING interviews");
        }

        return resumeParserService.extractText(resumeFile)
                .map(resumeText -> {
                    Session session = sessionService.createSessionWithResume("anonymous", type, role, resumeText);

                    DifficultyLevel difficulty = mapRoleToDifficulty(role);
                    InterviewContext context = orchestrator.startInterview(session.getId(), difficulty, type);
                    context.setResumeText(resumeText);
                    orchestrator.saveContext(context);

                    String greeting = "Hello! I've reviewed your resume. Let's dive in. Tell me briefly about your current role and what you've been working on recently.";
                    sessionService.addMessage(session.getId(), MessageRole.ASSISTANT, greeting);

                    return new SessionResponse(session.getId(), session.getType(), session.getRole(), session.getStatus().name(), session.getCreatedAt(), null);
                })
                .onErrorMap(e -> new RuntimeException("Failed to parse resume: " + e.getMessage(), e));
    }

    @GetMapping("/sessions")
    public List<SessionResponse> listSessions() {
        return sessionService.listSessions().stream()
                .map(s -> new SessionResponse(s.getId(), s.getType(), s.getRole(), s.getStatus().name(), s.getCreatedAt(),
                        toFeedbackResponse(sessionService.getFeedback(s.getId()))))
                .toList();
    }

    private String getGreetingForType(InterviewType type) {
        return switch (type) {
            case DSA -> "Hello! I'm your PrepLit interviewer. Before we start, please introduce yourself briefly.";
            case HLD -> "Hello! I'm your PrepLit interviewer for today's system design interview. Before we start, please introduce yourself briefly.";
            case LLD -> "Hello! I'm your PrepLit interviewer for today's low-level design interview. Before we start, please introduce yourself briefly.";
            case CULTURE_FIT -> "Hello! I'm your PrepLit interviewer for today's culture fit interview. Let's start — tell me about yourself and what you're looking for in your next role.";
            case RESUME_GRILLING -> "Hello! I've reviewed your resume. Let's dive in. Tell me briefly about your current role and what you've been working on recently.";
            case API_AND_DATABASE_DESIGN -> "Hello! I'm your PrepLit interviewer for today's API and database design interview. Before we start, please introduce yourself briefly.";
        };
    }

    private DifficultyLevel mapRoleToDifficulty(SdeRole role) {
        return switch (role) {
            case SDE1 -> DifficultyLevel.EASY;
            case SDE2 -> DifficultyLevel.MEDIUM;
            case SDE3 -> DifficultyLevel.HARD;
        };
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

        // Process candidate response SYNCHRONOUSLY so phase + chosenProblem are up to date
        // before we build the RAG context and system prompt for this turn
        InterviewContext context = orchestrator.getContext(id);
        if (context != null) {
            orchestrator.processCandidateResponse(id, req.content());
            context = orchestrator.getContext(id); // re-fetch updated context
        }

        if (devMode) {
            log.info("History size: {}", history.size());
            if (context != null) {
                log.info("Interview State: {}", context.getInterviewerState());
                log.info("Phase: {}", context.getCurrentPhase());
                log.info("Scores: {}", context.getScores().totalScore());
            }
            for (Message msg : history) {
                log.info("  [{}]: {}", msg.getRole(), msg.getContent().substring(0, Math.min(100, msg.getContent().length())) + "...");
            }
        }

        String ragContext;
        try {
            ragContext = ragService.buildContext(req.content(), session.getType(), context, 5);
        } catch (Exception e) {
            if (devMode) {
                log.warn("RAG context failed: {}", e.getMessage());
            }
            ragContext = "";
        }

        String systemPrompt = interviewPromptService.buildInterviewerPrompt(session.getType(), session.getRole(), ragContext, context);

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
                    accumulated.append(token);
                })
                .flatMap(token -> {
                    buffer.append(token);
                    String result = processThinkTags(buffer, insideThinkTag);
                    if (result == null || result.isEmpty()) {
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
                .flatMap(token -> token == null
                        ? Flux.empty()
                        : Flux.just(ServerSentEvent.<String>builder(token).build()))
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
                int endIdx = content.indexOf("</think>");
                if (endIdx == -1) {
                    if (content.length() > 8) {
                        buffer.setLength(0);
                        buffer.append(content.substring(content.length() - 8));
                    }
                    return result.toString();
                } else {
                    insideThinkTag[0] = false;
                    content = content.substring(endIdx + 8);
                    buffer.setLength(0);
                    buffer.append(content);
                }
            } else {
                int startIdx = content.indexOf("<think>");
                if (startIdx == -1) {
                    int partialIdx = -1;
                    for (int i = 1; i <= 7 && i <= content.length(); i++) {
                        String suffix = content.substring(content.length() - i);
                        if ("<think>".startsWith(suffix)) {
                            partialIdx = content.length() - i;
                            break;
                        }
                    }
                    if (partialIdx != -1) {
                        result.append(content, 0, partialIdx);
                        buffer.setLength(0);
                        buffer.append(content.substring(partialIdx));
                    } else {
                        result.append(content);
                        buffer.setLength(0);
                    }
                    return result.toString();
                } else {
                    result.append(content, 0, startIdx);
                    insideThinkTag[0] = true;
                    content = content.substring(startIdx + 7);
                    buffer.setLength(0);
                    buffer.append(content);
                }
            }
        }
    }

    private String stripThinkTags(String content) {
        // Remove complete <think>...</think> blocks
        String result = content.replaceAll("(?s)<think>.*?</think>", "");
        // Remove any unclosed <think> block (model stopped mid-think)
        int openIdx = result.indexOf("<think>");
        if (openIdx != -1) {
            result = result.substring(0, openIdx);
        }
        return result.trim();
    }

    @GetMapping("/sessions/{id}")
    public SessionWithMessagesResponse getSession(@PathVariable UUID id) {
        Session session = sessionService.getSession(id);
        List<Message> messages = sessionService.getHistory(id);
        List<MessageResponse> messageResponses = messages.stream()
                .map(m -> new MessageResponse(m.getId(), m.getRole().name(), m.getContent(), m.getCreatedAt()))
                .toList();
        return new SessionWithMessagesResponse(session.getId(), session.getType(), session.getRole(),
                session.getStatus().name(), messageResponses,
                toFeedbackResponse(sessionService.getFeedback(id)));
    }

    @PostMapping("/sessions/{id}/end")
    public FeedbackResponse endInterview(@PathVariable UUID id) {
        FeedbackReport feedback = orchestrator.endInterview(id);
        sessionService.saveFeedback(id, feedback);
        return toFeedbackResponse(feedback);
    }

    private FeedbackResponse toFeedbackResponse(FeedbackReport f) {
        if (f == null) return null;
        return new FeedbackResponse(
            f.summary(), f.strengths(), f.weaknesses(), f.verdict().name(), f.nextSteps(),
            new ScoresResponse(f.scores().problemUnderstanding(), f.scores().approach(),
                f.scores().correctness(), f.scores().communication(),
                f.scores().optimization(), f.scores().totalScore())
        );
    }

    record FeedbackResponse(
        String summary,
        List<String> strengths,
        List<String> weaknesses,
        String verdict,
        List<String> nextSteps,
        ScoresResponse scores
    ) {}

    record ScoresResponse(
        int problemUnderstanding,
        int approach,
        int correctness,
        int communication,
        int optimization,
        int total
    ) {}
}
