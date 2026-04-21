package com.preplit.service;

import com.preplit.model.*;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates comprehensive feedback for candidates.
 */
@Service
public class FeedbackGenerator {
    
    /**
     * Generates final feedback report based on interview context.
     */
    public FeedbackReport generateFeedback(InterviewContext context) {
        EvaluationScores scores = context.getScores();
        
        String summary = generateSummary(scores, context);
        List<String> strengths = generateStrengths(scores, context);
        List<String> weaknesses = generateWeaknesses(scores, context);
        Verdict verdict = determineVerdict(scores);
        List<String> nextSteps = generateNextSteps(scores, context);
        
        return new FeedbackReport(summary, strengths, weaknesses, verdict, nextSteps, scores);
    }
    
    private String generateSummary(EvaluationScores scores, InterviewContext context) {
        int total = scores.totalScore();
        
        if (total >= 70) {
            return String.format("Strong performance overall with a score of %d/100. " +
                "Demonstrated solid understanding and problem-solving skills.", total);
        } else if (total >= 50) {
            return String.format("Moderate performance with a score of %d/100. " +
                "Showed potential but needs improvement in key areas.", total);
        } else {
            return String.format("Performance needs significant improvement with a score of %d/100. " +
                "Struggled with fundamental concepts and problem-solving approach.", total);
        }
    }
    
    private List<String> generateStrengths(EvaluationScores scores, InterviewContext context) {
        List<String> strengths = new ArrayList<>();
        
        if (scores.problemUnderstanding() >= 70) {
            strengths.add("Excellent problem understanding - asked relevant clarifying questions");
        }
        if (scores.approach() >= 70) {
            strengths.add("Strong algorithmic thinking - identified efficient approach");
        }
        if (scores.correctness() >= 70) {
            strengths.add("Good implementation skills - code was mostly correct");
        }
        if (scores.communication() >= 70) {
            strengths.add("Clear communication - explained thought process well");
        }
        if (scores.optimization() >= 70) {
            strengths.add("Good optimization awareness - discussed time/space tradeoffs");
        }
        
        // Add observation-based strengths
        for (String obs : context.getObservations()) {
            if (obs.contains("good") || obs.contains("excellent") || obs.contains("strong")) {
                strengths.add(obs);
            }
        }
        
        // Ensure at least 2 strengths
        if (strengths.isEmpty()) {
            strengths.add("Showed willingness to engage with the problem");
            strengths.add("Attempted to work through the solution");
        }
        
        return strengths.subList(0, Math.min(3, strengths.size()));
    }
    
    private List<String> generateWeaknesses(EvaluationScores scores, InterviewContext context) {
        List<String> weaknesses = new ArrayList<>();
        
        if (scores.problemUnderstanding() < 50) {
            weaknesses.add("Needs improvement in problem understanding - missed key constraints");
        }
        if (scores.approach() < 50) {
            weaknesses.add("Struggled with algorithmic approach - needs more practice with data structures");
        }
        if (scores.correctness() < 50) {
            weaknesses.add("Implementation had issues - focus on edge cases and correctness");
        }
        if (scores.communication() < 50) {
            weaknesses.add("Communication could be clearer - explain reasoning more explicitly");
        }
        if (scores.optimization() < 50) {
            weaknesses.add("Limited optimization discussion - practice analyzing complexity");
        }
        
        // Add anti-pattern based weaknesses
        for (AntiPatternEvent event : context.getAntiPatterns()) {
            weaknesses.add(getAntiPatternMessage(event.pattern()));
        }
        
        // Add hint-based weakness
        if (context.getHintCount() >= 2) {
            weaknesses.add("Required multiple hints to progress - work on independent problem-solving");
        }
        
        return weaknesses.subList(0, Math.min(3, weaknesses.size()));
    }
    
    private String getAntiPatternMessage(AntiPattern pattern) {
        return switch (pattern) {
            case JUMPED_TO_CODE -> "Jumped to implementation without clarifying requirements";
            case NO_EDGE_CASES -> "Didn't consider edge cases in the approach";
            case ASKED_FOR_ANSWER -> "Asked for solution directly instead of working through it";
            case NO_CLARIFICATION -> "Didn't ask clarifying questions about the problem";
        };
    }
    
    private Verdict determineVerdict(EvaluationScores scores) {
        int total = scores.totalScore();
        
        if (total >= 70) {
            return Verdict.HIRE;
        } else if (total >= 50) {
            return Verdict.BORDERLINE;
        } else {
            return Verdict.NO_HIRE;
        }
    }
    
    private List<String> generateNextSteps(EvaluationScores scores, InterviewContext context) {
        List<String> nextSteps = new ArrayList<>();
        
        if (scores.problemUnderstanding() < 60) {
            nextSteps.add("Practice breaking down problems and asking clarifying questions");
        }
        if (scores.approach() < 60) {
            nextSteps.add("Study common data structures and algorithms (arrays, hash maps, trees)");
        }
        if (scores.correctness() < 60) {
            nextSteps.add("Focus on edge case handling and code correctness");
        }
        if (scores.communication() < 60) {
            nextSteps.add("Practice explaining your thought process out loud");
        }
        if (scores.optimization() < 60) {
            nextSteps.add("Learn to analyze time and space complexity");
        }
        
        // General recommendations
        if (nextSteps.isEmpty()) {
            nextSteps.add("Continue practicing with more complex problems");
            nextSteps.add("Focus on system design and scalability concepts");
        }
        
        return nextSteps;
    }
}
