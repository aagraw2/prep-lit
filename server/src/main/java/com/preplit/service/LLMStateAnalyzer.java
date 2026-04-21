package com.preplit.service;

import com.preplit.model.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Uses LLM to analyze candidate responses and detect states intelligently.
 */
@Service
public class LLMStateAnalyzer {
    
    private static final Logger log = LoggerFactory.getLogger(LLMStateAnalyzer.class);
    
    private final ChatLanguageModel chatModel;
    private final InterviewPromptService interviewPromptService;
    
    public LLMStateAnalyzer(ChatLanguageModel chatModel, InterviewPromptService interviewPromptService) {
        this.chatModel = chatModel;
        this.interviewPromptService = interviewPromptService;
    }
    
    /**
     * Analyzes candidate response asynchronously and returns state analysis.
     */
    public CompletableFuture<StateAnalysis> analyzeAsync(String candidateInput, InterviewContext context) {
        return CompletableFuture.supplyAsync(() -> analyze(candidateInput, context));
    }
    
    /**
     * Analyzes candidate response and returns comprehensive state analysis.
     */
    public StateAnalysis analyze(String candidateInput, InterviewContext context) {
        String prompt = buildAnalysisPrompt(candidateInput, context);
        
        try {
            Response<AiMessage> response = chatModel.generate(
                new SystemMessage("You are an expert interview analyzer. Respond ONLY with valid JSON."),
                new UserMessage(prompt)
            );
            
            return parseAnalysisResponse(response.content().text(), context);
        } catch (Exception e) {
            log.error("LLM state analysis failed: {}", e.getMessage(), e);
            return StateAnalysis.fallback();
        }
    }
    
    private String buildAnalysisPrompt(String candidateInput, InterviewContext context) {
        return interviewPromptService.buildStateAnalysisPrompt(candidateInput, context);
    }
    
    private StateAnalysis parseAnalysisResponse(String response, InterviewContext context) {
        // Remove markdown code blocks if present
        String json = response.trim();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        }
        if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        json = json.trim();
        
        // Simple JSON parsing (you can use Jackson for production)
        InterviewerState interviewerState = extractEnum(json, "interviewerState", InterviewerState.class, InterviewerState.EXPLORING);
        ConfidenceLevel confidence = extractEnum(json, "confidence", ConfidenceLevel.class, ConfidenceLevel.MEDIUM);
        EngagementLevel engagement = extractEnum(json, "engagement", EngagementLevel.class, EngagementLevel.MEDIUM);
        FrustrationLevel frustration = extractEnum(json, "frustration", FrustrationLevel.class, FrustrationLevel.LOW);
        
        boolean isStuck = extractBoolean(json, "isStuck", false);
        boolean isProgressing = extractBoolean(json, "isProgressing", false);
        boolean hasCorrectApproach = extractBoolean(json, "hasCorrectApproach", false);
        boolean mentionsEdgeCases = extractBoolean(json, "mentionsEdgeCases", false);
        boolean hasCodePatterns = extractBoolean(json, "hasCodePatterns", false);
        boolean asksForAnswer = extractBoolean(json, "asksForAnswer", false);
        boolean shouldTransitionPhase = extractBoolean(json, "shouldTransitionPhase", false);
        
        InterviewPhase suggestedPhase = extractEnum(json, "suggestedPhase", InterviewPhase.class, context.getCurrentPhase());
        
        // Extract score deltas
        ScoreDeltas scoreDeltas = extractScoreDeltas(json);
        
        String reasoning = extractString(json, "reasoning", "");
        
        return new StateAnalysis(
            interviewerState,
            confidence,
            engagement,
            frustration,
            isStuck,
            isProgressing,
            hasCorrectApproach,
            mentionsEdgeCases,
            hasCodePatterns,
            asksForAnswer,
            shouldTransitionPhase,
            suggestedPhase,
            scoreDeltas,
            reasoning
        );
    }
    
    private ScoreDeltas extractScoreDeltas(String json) {
        try {
            int scoreDeltasStart = json.indexOf("\"scoreDeltas\"");
            if (scoreDeltasStart == -1) return ScoreDeltas.zero();
            
            int braceStart = json.indexOf("{", scoreDeltasStart);
            int braceEnd = json.indexOf("}", braceStart);
            if (braceStart == -1 || braceEnd == -1) return ScoreDeltas.zero();
            
            String deltasJson = json.substring(braceStart, braceEnd + 1);
            
            return new ScoreDeltas(
                extractIntFromSubJson(deltasJson, "problemUnderstanding", 0),
                extractIntFromSubJson(deltasJson, "approach", 0),
                extractIntFromSubJson(deltasJson, "correctness", 0),
                extractIntFromSubJson(deltasJson, "communication", 0),
                extractIntFromSubJson(deltasJson, "optimization", 0)
            );
        } catch (Exception e) {
            return ScoreDeltas.zero();
        }
    }
    
    private int extractIntFromSubJson(String json, String key, int defaultValue) {
        try {
            String value = extractString(json, key, "");
            if (value.isEmpty()) return defaultValue;
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private <T extends Enum<T>> T extractEnum(String json, String key, Class<T> enumClass, T defaultValue) {
        try {
            String value = extractString(json, key, "");
            if (value.isEmpty()) return defaultValue;
            return Enum.valueOf(enumClass, value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private boolean extractBoolean(String json, String key, boolean defaultValue) {
        try {
            String value = extractString(json, key, "");
            if (value.isEmpty()) return defaultValue;
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private String extractString(String json, String key, String defaultValue) {
        try {
            String searchKey = "\"" + key + "\"";
            int keyIndex = json.indexOf(searchKey);
            if (keyIndex == -1) return defaultValue;
            
            int colonIndex = json.indexOf(":", keyIndex);
            if (colonIndex == -1) return defaultValue;
            
            int valueStart = colonIndex + 1;
            while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
                valueStart++;
            }
            
            if (valueStart >= json.length()) return defaultValue;
            
            char firstChar = json.charAt(valueStart);
            if (firstChar == '"') {
                // String value
                int valueEnd = json.indexOf('"', valueStart + 1);
                if (valueEnd == -1) return defaultValue;
                return json.substring(valueStart + 1, valueEnd);
            } else {
                // Boolean or other value
                int valueEnd = valueStart;
                while (valueEnd < json.length() && 
                       json.charAt(valueEnd) != ',' && 
                       json.charAt(valueEnd) != '}' &&
                       json.charAt(valueEnd) != '\n') {
                    valueEnd++;
                }
                return json.substring(valueStart, valueEnd).trim();
            }
        } catch (Exception e) {
            return defaultValue;
        }
    }
}

/**
 * Result of LLM state analysis.
 */
record StateAnalysis(
    InterviewerState interviewerState,
    ConfidenceLevel confidence,
    EngagementLevel engagement,
    FrustrationLevel frustration,
    boolean isStuck,
    boolean isProgressing,
    boolean hasCorrectApproach,
    boolean mentionsEdgeCases,
    boolean hasCodePatterns,
    boolean asksForAnswer,
    boolean shouldTransitionPhase,
    InterviewPhase suggestedPhase,
    ScoreDeltas scoreDeltas,
    String reasoning
) {
    static StateAnalysis fallback() {
        return new StateAnalysis(
            InterviewerState.EXPLORING,
            ConfidenceLevel.MEDIUM,
            EngagementLevel.MEDIUM,
            FrustrationLevel.LOW,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            InterviewPhase.CLARIFICATION,
            ScoreDeltas.zero(),
            "Fallback due to analysis error"
        );
    }
}

/**
 * Score deltas from LLM analysis.
 */
record ScoreDeltas(
    int problemUnderstanding,
    int approach,
    int correctness,
    int communication,
    int optimization
) {
    static ScoreDeltas zero() {
        return new ScoreDeltas(0, 0, 0, 0, 0);
    }
}
