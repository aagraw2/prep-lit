package com.preplit.service;

import com.preplit.model.*;
import com.preplit.repository.InterviewContextRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Main orchestrator for state-aware interview system.
 * Uses LLM for all state detection and scoring.
 */
@Service
public class InterviewOrchestrator {
    
    private static final Logger log = LoggerFactory.getLogger(InterviewOrchestrator.class);
    
    private final LLMStateAnalyzer stateAnalyzer;
    private final FeedbackGenerator feedbackGenerator;
    private final InterviewContextRepository contextRepository;
    
    public InterviewOrchestrator(
            LLMStateAnalyzer stateAnalyzer,
            FeedbackGenerator feedbackGenerator,
            InterviewContextRepository contextRepository) {
        this.stateAnalyzer = stateAnalyzer;
        this.feedbackGenerator = feedbackGenerator;
        this.contextRepository = contextRepository;
    }
    
    /**
     * Starts a new interview session.
     */
    public InterviewContext startInterview(UUID sessionId, DifficultyLevel difficulty, InterviewType interviewType) {
        InterviewContext context = new InterviewContext(sessionId, difficulty, interviewType);
        contextRepository.save(context);
        return context;
    }

    /**
     * Saves the interview context to Redis.
     */
    public void saveContext(InterviewContext context) {
        contextRepository.save(context);
    }
    
    /**
     * Processes candidate response asynchronously and updates interview state.
     * This runs in background and doesn't block the response stream.
     */
    public CompletableFuture<Void> processCandidateResponseAsync(UUID sessionId, String candidateInput) {
        return CompletableFuture.runAsync(() -> {
            try {
                processCandidateResponse(sessionId, candidateInput);
            } catch (Exception e) {
                log.error("Error processing candidate response for session {}: {}", sessionId, e.getMessage(), e);
            }
        });
    }
    
    /**
     * Processes candidate response and updates interview state using LLM analysis.
     */
    public void processCandidateResponse(UUID sessionId, String candidateInput) {
        InterviewContext context = contextRepository.findById(sessionId);
        if (context == null) {
            log.warn("Interview context not found for session: {}", sessionId);
            return;
        }
        
        // Analyze everything using LLM
        StateAnalysis analysis = stateAnalyzer.analyze(candidateInput, context);
        
        // Update candidate state
        CandidateStateModel newCandidateState = new CandidateStateModel(
            analysis.confidence(),
            analysis.engagement(),
            analysis.frustration(),
            analysis.isStuck() ? Math.min(context.getCandidateState().stuckTurns() + 1, 5) : 0,
            java.time.LocalDateTime.now()
        );
        context.setCandidateState(newCandidateState);
        
        // Set interviewer state from LLM
        context.setInterviewerState(analysis.interviewerState());
        
        // Update scores using LLM deltas
        EvaluationScores currentScores = context.getScores();
        EvaluationScores newScores = new EvaluationScores(
            currentScores.problemUnderstanding() + analysis.scoreDeltas().problemUnderstanding(),
            currentScores.approach() + analysis.scoreDeltas().approach(),
            currentScores.correctness() + analysis.scoreDeltas().correctness(),
            currentScores.communication() + analysis.scoreDeltas().communication(),
            currentScores.optimization() + analysis.scoreDeltas().optimization()
        );
        context.setScores(newScores);
        
        // Detect anti-patterns from LLM analysis
        if (analysis.hasCodePatterns() && context.getCurrentPhase() == InterviewPhase.CLARIFICATION) {
            context.addAntiPattern(AntiPatternEvent.of(AntiPattern.JUMPED_TO_CODE));
        }
        if (!analysis.mentionsEdgeCases() && context.getCurrentPhase() == InterviewPhase.APPROACH) {
            context.addAntiPattern(AntiPatternEvent.of(AntiPattern.NO_EDGE_CASES));
        }
        if (analysis.asksForAnswer()) {
            context.addAntiPattern(AntiPatternEvent.of(AntiPattern.ASKED_FOR_ANSWER));
        }
        if (context.getCurrentPhase() == InterviewPhase.APPROACH && 
            context.getClarificationQuestionsAsked() == 0) {
            context.addAntiPattern(AntiPatternEvent.of(AntiPattern.NO_CLARIFICATION));
        }
        
        // Phase transition from LLM
        if (analysis.shouldTransitionPhase()) {
            context.setCurrentPhase(analysis.suggestedPhase());
        }
        
        // Track consecutive "I don't know"
        if (analysis.isStuck()) {
            context.incrementConsecutiveIdk();
        } else if (analysis.isProgressing()) {
            context.resetConsecutiveIdk();
        }
        
        // Add observation from LLM reasoning
        if (analysis.reasoning() != null && !analysis.reasoning().isEmpty()) {
            context.addObservation(analysis.reasoning());
        }
        
        // Save to Redis
        contextRepository.save(context);
        
        log.info("Updated interview state for session {}: state={}, phase={}, score={}", 
            sessionId, context.getInterviewerState(), context.getCurrentPhase(), 
            context.getScores().totalScore());
    }
    
    /**
     * Ends the interview and generates final feedback.
     */
    public FeedbackReport endInterview(UUID sessionId) {
        InterviewContext context = contextRepository.findById(sessionId);
        if (context == null) {
            throw new IllegalArgumentException("Interview session not found: " + sessionId);
        }
        
        context.setCurrentPhase(InterviewPhase.WRAP_UP);
        contextRepository.save(context);
        
        return feedbackGenerator.generateFeedback(context);
    }
    
    /**
     * Gets current interview context from Redis.
     */
    public InterviewContext getContext(UUID sessionId) {
        return contextRepository.findById(sessionId);
    }
}
