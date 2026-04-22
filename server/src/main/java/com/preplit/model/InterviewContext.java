package com.preplit.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Complete context for an interview session.
 * Tracks all state, scores, hints, observations, and metadata.
 * Stored in Redis with 24-hour expiration.
 */
public class InterviewContext implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private UUID sessionId;
    private InterviewType interviewType;
    private String resumeText;
    private String chosenProblem; // name of the problem/system chosen for this session
    private InterviewPhase currentPhase;
    private InterviewerState interviewerState;
    private CandidateStateModel candidateState;
    private EvaluationScores scores;
    private DifficultyLevel difficulty;
    private List<HintRecord> hints;
    private List<AntiPatternEvent> antiPatterns;
    private List<String> observations;
    private int clarificationQuestionsAsked;
    private int consecutiveIdkCount;
    private LocalDateTime startTime;
    private LocalDateTime lastUpdateTime;
    
    // Default constructor for Jackson
    public InterviewContext() {
    }
    
    public InterviewContext(UUID sessionId, DifficultyLevel difficulty, InterviewType interviewType) {
        this.sessionId = sessionId;
        this.interviewType = interviewType;
        this.resumeText = null;
        this.currentPhase = InterviewPhase.INTRO;
        this.interviewerState = InterviewerState.EXPLORING;
        this.candidateState = CandidateStateModel.initial();
        this.scores = EvaluationScores.initial();
        this.difficulty = difficulty;
        this.hints = new ArrayList<>();
        this.antiPatterns = new ArrayList<>();
        this.observations = new ArrayList<>();
        this.clarificationQuestionsAsked = 0;
        this.consecutiveIdkCount = 0;
        this.startTime = LocalDateTime.now();
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    // Getters
    public UUID getSessionId() { return sessionId; }
    public InterviewType getInterviewType() { return interviewType; }
    public String getResumeText() { return resumeText; }
    public String getChosenProblem() { return chosenProblem; }
    public InterviewPhase getCurrentPhase() { return currentPhase; }
    public InterviewerState getInterviewerState() { return interviewerState; }
    public CandidateStateModel getCandidateState() { return candidateState; }
    public EvaluationScores getScores() { return scores; }
    public DifficultyLevel getDifficulty() { return difficulty; }
    public List<HintRecord> getHints() { return new ArrayList<>(hints); }
    public List<AntiPatternEvent> getAntiPatterns() { return new ArrayList<>(antiPatterns); }
    public List<String> getObservations() { return new ArrayList<>(observations); }
    public int getClarificationQuestionsAsked() { return clarificationQuestionsAsked; }
    public int getConsecutiveIdkCount() { return consecutiveIdkCount; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getLastUpdateTime() { return lastUpdateTime; }
    
    // Setters
    public void setCurrentPhase(InterviewPhase phase) {
        this.currentPhase = phase;
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    public void setInterviewerState(InterviewerState state) {
        this.interviewerState = state;
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    public void setCandidateState(CandidateStateModel state) {
        this.candidateState = state;
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    public void setScores(EvaluationScores scores) {
        this.scores = scores;
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    public void setDifficulty(DifficultyLevel difficulty) {
        this.difficulty = difficulty;
        this.lastUpdateTime = LocalDateTime.now();
    }

    public void setInterviewType(InterviewType interviewType) {
        this.interviewType = interviewType;
        this.lastUpdateTime = LocalDateTime.now();
    }

    public void setResumeText(String resumeText) {
        this.resumeText = resumeText;
        this.lastUpdateTime = LocalDateTime.now();
    }

    public void setChosenProblem(String chosenProblem) {
        this.chosenProblem = chosenProblem;
        this.lastUpdateTime = LocalDateTime.now();
    }

    public void addHint(HintRecord hint) {
        if (hints.size() < 3) {
            hints.add(hint);
            this.lastUpdateTime = LocalDateTime.now();
        }
    }
    
    public void addAntiPattern(AntiPatternEvent event) {
        antiPatterns.add(event);
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    public void addObservation(String observation) {
        if (observations.size() < 10) {
            observations.add(observation);
            this.lastUpdateTime = LocalDateTime.now();
        }
    }
    
    public void incrementClarificationQuestions() {
        this.clarificationQuestionsAsked++;
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    public void incrementConsecutiveIdk() {
        this.consecutiveIdkCount++;
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    public void resetConsecutiveIdk() {
        this.consecutiveIdkCount = 0;
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    public int getHintCount() {
        return hints.size();
    }
    
    public boolean canGiveMoreHints() {
        return hints.size() < 3;
    }
    
    public HintLevel getNextHintLevel() {
        return switch (hints.size()) {
            case 0 -> HintLevel.L1;
            case 1 -> HintLevel.L2;
            case 2 -> HintLevel.L3;
            default -> throw new IllegalStateException("Maximum hints already given");
        };
    }
}
