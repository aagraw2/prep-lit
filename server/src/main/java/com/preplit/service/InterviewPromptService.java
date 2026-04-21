package com.preplit.service;

import com.preplit.model.*;
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

    private static final String PROMPTS_DIR = "prompts/";

    /**
     * Builds the main interviewer system prompt with state context.
     */
    public String buildInterviewerPrompt(InterviewType type, SdeRole role, String ragContext, InterviewContext context) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("interviewType", getInterviewTypeDescription(type));
        variables.put("roleLevel", getRoleLevelDescription(role));
        
        // Build system prompt
        String systemPrompt = loadAndSubstitute("interviewer-system", variables);
        
        // Add interview flow
        String flowPrompt = loadAndSubstitute("interviewer-flow-" + type.name().toLowerCase(), new HashMap<>());
        
        // Add state context if available
        String stateContext = context != null ? buildStateContext(context) : "";

        // Add resume context for RESUME_GRILLING
        String resumeSection = "";
        if (type == InterviewType.RESUME_GRILLING && context != null && context.getResumeText() != null) {
            resumeSection = buildResumeSection(context.getResumeText());
        }

        // Add RAG context if available
        String ragSection = (ragContext != null && !ragContext.isBlank())
            ? "\n\nReference Knowledge:\n" + ragContext
            : "";

        return systemPrompt + "\n\n" + flowPrompt + resumeSection + "\n\n" + stateContext + ragSection;
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
        
        switch (context.getInterviewerState()) {
            case STUCK -> {
                instructions.append("CANDIDATE IS STUCK:\n");
                if (context.canGiveMoreHints()) {
                    instructions.append(String.format("- Give ONE small hint (hints remaining: %d/3)\n", 3 - context.getHintCount()));
                } else {
                    instructions.append("- NO MORE HINTS AVAILABLE\n");
                }
            }
            case PROGRESSING -> instructions.append("CANDIDATE IS PROGRESSING:\n- Ask deeper follow-up questions\n");
            case DISENGAGED -> instructions.append("CANDIDATE IS DISENGAGED:\n- Simplify and re-engage\n");
            case WRAPPING_UP -> instructions.append("WRAPPING UP:\n- Ask if they have questions\n");
        }
        
        if (context.getCandidateState().frustration() == FrustrationLevel.HIGH) {
            instructions.append("\nHIGH FRUSTRATION:\n- Be supportive\n");
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
