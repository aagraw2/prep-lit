package com.preplit.service;

import com.preplit.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for building interview-specific prompts from template files.
 */
@Service
public class InterviewPromptService {

    private static final Logger log = LoggerFactory.getLogger(InterviewPromptService.class);
    private static final String PROMPTS_DIR = "prompts/";

    /**
     * Builds the main interviewer system prompt with state context.
     */
    public String buildInterviewerPrompt(InterviewType type, SdeRole role, String ragContext, InterviewContext context) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("interviewType", getInterviewTypeDescription(type));
        variables.put("roleLevel", getRoleLevelDescription(role));

        // Base system prompt
        String systemPrompt = loadAndSubstitute("interviewer-system", variables);

        // Phase-specific instructions (replaces the static flow file)
        String phasePrompt = buildPhasePrompt(type, context, variables);

        // State context
        String stateContext = context != null ? buildStateContext(context) : "";

        // Resume section for RESUME_GRILLING
        String resumeSection = "";
        if (type == InterviewType.RESUME_GRILLING && context != null && context.getResumeText() != null) {
            resumeSection = buildResumeSection(context.getResumeText());
        }

        // RAG context
        String ragSection = (ragContext != null && !ragContext.isBlank())
            ? "\n\nReference Knowledge:\n" + ragContext
            : "";

        return systemPrompt + "\n\n" + phasePrompt + resumeSection + "\n\n" + stateContext + ragSection;
    }

    /**
     * Loads the phase-specific prompt for the current interview type and phase.
     * Falls back to the legacy flow file if no phase prompt exists.
     */
    private String buildPhasePrompt(InterviewType type, InterviewContext context, Map<String, Object> variables) {
        String typeFolder = type.name().toLowerCase();
        InterviewPhase phase = context != null ? context.getCurrentPhase() : InterviewPhase.INTRO;
        String phaseFile = phaseToFileName(phase);
        return loadAndSubstitute(typeFolder + "/" + phaseFile, variables);
    }

    private String phaseToFileName(InterviewPhase phase) {
        return switch (phase) {
            case INTRO -> "intro";
            case CLARIFICATION -> "clarification";
            case APPROACH -> "approach";
            case DEEP_DIVE -> "deep_dive";
            case IMPLEMENTATION -> "implementation";
            case WRAP_UP -> "wrap_up";
            case REQUIREMENTS -> "requirements";
            case API_DESIGN -> "api_design";
            case SCHEMA_DESIGN -> "schema_design";
        };
    }
    
    /**
     * Builds state analysis prompt for LLM.
     */
    public String buildStateAnalysisPrompt(String candidateInput, InterviewContext context) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("candidateInput", candidateInput);
        variables.put("phase", context.getCurrentPhase());
        variables.put("stuckTurns", context.getCandidateState().stuckTurns());
        variables.put("consecutiveIdk", context.getConsecutiveIdkCount());
        variables.put("clarificationQuestions", context.getClarificationQuestionsAsked());
        variables.put("confidence", context.getCandidateState().confidence());
        variables.put("engagement", context.getCandidateState().engagement());
        variables.put("frustration", context.getCandidateState().frustration());
        variables.put("scoreProblem", context.getScores().problemUnderstanding());
        variables.put("scoreApproach", context.getScores().approach());
        variables.put("scoreCorrectness", context.getScores().correctness());
        variables.put("scoreCommunication", context.getScores().communication());
        variables.put("scoreOptimization", context.getScores().optimization());
        
        return loadAndSubstitute("state-analysis", variables);
    }
    
    private String buildStateContext(InterviewContext context) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("phase", context.getCurrentPhase());
        variables.put("interviewerState", context.getInterviewerState());
        variables.put("confidence", context.getCandidateState().confidence());
        variables.put("engagement", context.getCandidateState().engagement());
        variables.put("frustration", context.getCandidateState().frustration());
        variables.put("stuckTurns", context.getCandidateState().stuckTurns());
        variables.put("scoreProblem", context.getScores().problemUnderstanding());
        variables.put("scoreApproach", context.getScores().approach());
        variables.put("scoreCorrectness", context.getScores().correctness());
        variables.put("scoreCommunication", context.getScores().communication());
        variables.put("scoreOptimization", context.getScores().optimization());
        variables.put("scoreTotal", context.getScores().totalScore());
        variables.put("hintsGiven", context.getHintCount());
        variables.put("clarificationQuestions", context.getClarificationQuestionsAsked());
        variables.put("stateInstructions", buildStateInstructions(context));
        
        return loadAndSubstitute("interviewer-state-context", variables);
    }
    
    private String buildStateInstructions(InterviewContext context) {
        StringBuilder instructions = new StringBuilder();
        instructions.append("--------------------------------\n");
        instructions.append("STATE-BASED INSTRUCTIONS\n");
        instructions.append("--------------------------------\n");

        if (context.getCandidateState().frustration() == FrustrationLevel.HIGH) {
            instructions.append("CANDIDATE IS FRUSTRATED — MANDATORY ACTION:\n");
            instructions.append("- Drop the current topic immediately. Do not ask about it again.\n");
            instructions.append("- Acknowledge briefly (one sentence max) and move to a completely different topic.\n");
            instructions.append("- Example: 'Got it, let's move on.' then ask about something new.\n");
            return instructions.toString();
        }

        switch (context.getInterviewerState()) {
            case STUCK -> {
                if (context.canGiveMoreHints()) {
                    instructions.append("CANDIDATE IS STUCK:\n");
                    instructions.append(String.format("- Give ONE small nudge or hint (hints remaining: %d/3)\n", 3 - context.getHintCount()));
                    instructions.append("- If they still can't answer after this, move to the next topic.\n");
                } else {
                    instructions.append("CANDIDATE IS STUCK — NO MORE HINTS:\n");
                    instructions.append("- Move to the next topic. Do not probe this point further.\n");
                }
            }
            case PROGRESSING -> instructions.append("CANDIDATE IS PROGRESSING:\n- Ask one deeper follow-up question.\n");
            case DISENGAGED -> instructions.append("CANDIDATE IS DISENGAGED:\n- Simplify your question or pivot to a new topic.\n");
            case WRAPPING_UP -> instructions.append("WRAPPING UP:\n- Ask one final wrap-up question, then end the interview.\n");
            default -> {}
        }

        return instructions.toString();
    }
    
    /**
     * Loads prompt template from file and substitutes variables.
     */
    private String loadAndSubstitute(String promptName, Map<String, Object> variables) {
        String template = loadTemplate(promptName);
        return substituteVariables(template, variables);
    }
    
    /**
     * Loads a prompt template from resources/prompts directory.
     */
    private String loadTemplate(String promptName) {
        try {
            ClassPathResource resource = new ClassPathResource(PROMPTS_DIR + promptName + ".txt");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt template: " + promptName, e);
        }
    }
    
    /**
     * Substitutes {{variable}} placeholders with actual values.
     */
    private String substituteVariables(String template, Map<String, Object> variables) {
        String result = template;
        
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                result = result.replace(placeholder, value);
            }
        }
        
        return result;
    }
    
    private String buildResumeSection(String resumeText) {
        return """

            ================================
            CANDIDATE'S RESUME
            ================================
            Below is the candidate's resume. Use this to:
            - Ask specific questions about projects and experience
            - Verify claims and probe for depth
            - Identify areas to explore further

            ---RESUME START---
            %s
            ---RESUME END---

            IMPORTANT: Reference specific details from the resume in your questions.
            """.formatted(resumeText);
    }

    private String getInterviewTypeDescription(InterviewType type) {
        return switch (type) {
            case DSA -> "Data Structures and Algorithms";
            case HLD -> "High Level System Design";
            case LLD -> "Low Level Design";
            case RESUME_GRILLING -> "Resume Deep Dive";
            case CULTURE_FIT -> "Culture Fit and Behavioral";
            case API_AND_DATABASE_DESIGN -> "API and Database Design";
        };
    }
    
    private String getRoleLevelDescription(SdeRole role) {
        return switch (role) {
            case SDE1 -> "a junior engineer";
            case SDE2 -> "a mid-level engineer";
            case SDE3 -> "a senior engineer";
        };
    }
}
