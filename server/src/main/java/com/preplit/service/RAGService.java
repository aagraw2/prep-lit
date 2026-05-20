package com.preplit.service;

import com.preplit.model.InterviewContext;
import com.preplit.model.InterviewPhase;
import com.preplit.model.InterviewType;
import com.preplit.rag.DocumentChunk;
import com.preplit.rag.InterviewGuideIndexer;
import com.preplit.rag.RedisVectorStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * RAG service — phase-aware retrieval.
 *
 * INTRO:            fetch the full problem-catalog chunk (LLD Systems.md / HLD Systems.md / DSA Practice Sheet)
 * CLARIFICATION:    fetch all chunks for the chosen problem's example-system doc by title
 * APPROACH/IMPL:    fetch chunks for chosen problem + vector search enriched with problem name
 * DEEP_DIVE:        enriched vector search across all categories (no category filter) — surfaces design patterns, concepts, etc.
 * CULTURE/RESUME:   plain vector search, no type filter
 */
@Service
public class RAGService {

    private static final Logger log = LoggerFactory.getLogger(RAGService.class);

    private final RedisVectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final InterviewGuideIndexer indexer;
    private final boolean ragEnabled;
    private final int defaultTopK;

    public RAGService(
            @Nullable RedisVectorStore vectorStore,
            @Nullable EmbeddingModel embeddingModel,
            @Nullable InterviewGuideIndexer indexer,
            @Value("${rag.enabled:true}") boolean ragEnabled,
            @Value("${rag.top-k:5}") int defaultTopK) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.indexer = indexer;
        this.ragEnabled = ragEnabled;
        this.defaultTopK = defaultTopK;
    }

    /**
     * Main entry point. Builds phase-aware RAG context string for the system prompt.
     */
    public String buildContext(String userMessage, InterviewType type, @Nullable InterviewContext context) {
        return buildContext(userMessage, type, context, defaultTopK);
    }

    public String buildContext(String userMessage, InterviewType type, @Nullable InterviewContext context, int topK) {
        if (!isAvailable()) return "";

        try {
            InterviewPhase phase = context != null ? context.getCurrentPhase() : null;
            String chosenProblem = context != null ? context.getChosenProblem() : null;
            String typeFilter = mapTypeToFilter(type);

            // INTRO — return the full catalog so the LLM can pick a problem
            if (phase == null || phase == InterviewPhase.INTRO) {
                return fetchCatalog(typeFilter);
            }

            // CULTURE_FIT / RESUME_GRILLING — plain vector search, no type filter
            if (type == InterviewType.CULTURE_FIT || type == InterviewType.RESUME_GRILLING) {
                return vectorSearch(userMessage, null, Collections.emptySet(), topK);
            }

            // API_AND_DATABASE_DESIGN — custom phase flow
            if (type == InterviewType.API_AND_DATABASE_DESIGN) {
                return buildApiDbContext(userMessage, phase, chosenProblem, typeFilter, topK);
            }

            // CLARIFICATION — fetch the full chosen problem doc by title
            if (phase == InterviewPhase.CLARIFICATION && chosenProblem != null) {
                List<DocumentChunk> chunks = vectorStore.searchByTitle(chosenProblem, typeFilter, topK);
                if (!chunks.isEmpty()) return formatContext(chunks);
            }

            // APPROACH / IMPLEMENTATION — chosen problem doc + enriched vector search
            if ((phase == InterviewPhase.APPROACH || phase == InterviewPhase.IMPLEMENTATION) && chosenProblem != null) {
                // First get the problem-specific chunks
                List<DocumentChunk> problemChunks = vectorStore.searchByTitle(chosenProblem, typeFilter, 3);
                // Then enrich query with problem name for broader concept retrieval
                String enrichedQuery = chosenProblem + " " + userMessage;
                String vectorResults = vectorSearch(enrichedQuery, typeFilter, Collections.emptySet(), topK - problemChunks.size());
                return formatContext(problemChunks) + (vectorResults.isBlank() ? "" : "\n\n" + vectorResults);
            }

            // DEEP_DIVE — enriched query, NO category filter — lets design patterns, concepts surface naturally
            if (phase == InterviewPhase.DEEP_DIVE) {
                String enrichedQuery = (chosenProblem != null ? chosenProblem + " " : "") + userMessage;
                return vectorSearch(enrichedQuery, typeFilter, Collections.emptySet(), topK);
            }

            // WRAP_UP or fallback — light vector search
            String enrichedQuery = (chosenProblem != null ? chosenProblem + " " : "") + userMessage;
            return vectorSearch(enrichedQuery, typeFilter, Collections.emptySet(), topK);

        } catch (Exception e) {
            log.warn("RAG buildContext failed: {}", e.getMessage());
            return "";
        }
    }

    // ── kept for backward compat ──────────────────────────────────────────────

    /**
     * Phase-aware RAG retrieval for API_AND_DATABASE_DESIGN interviews.
     *
     * INTRO:         fetch the full API+DB problem catalog
     * REQUIREMENTS:  vector search for the chosen problem's system context
     * API_DESIGN:    chosen problem doc (title search) + API design concept search
     * SCHEMA_DESIGN: chosen problem doc (title search) + DB design concept search
     * DEEP_DIVE:     enriched vector search, no category filter
     * WRAP_UP:       no RAG needed
     */
    private String buildApiDbContext(String userMessage, InterviewPhase phase,
                                     String chosenProblem, String typeFilter, int topK) {
        try {
            if (phase == null || phase == InterviewPhase.INTRO) {
                return fetchCatalog(typeFilter);
            }

            if (phase == InterviewPhase.WRAP_UP) {
                return "";
            }

            if (phase == InterviewPhase.REQUIREMENTS) {
                String query = chosenProblem != null ? chosenProblem + " " + userMessage : userMessage;
                return vectorSearch(query, typeFilter, Collections.emptySet(), topK);
            }

            if (phase == InterviewPhase.API_DESIGN && chosenProblem != null) {
                List<DocumentChunk> problemChunks = vectorStore.searchByTitle(chosenProblem, typeFilter, 3);
                String enrichedQuery = chosenProblem + " API design endpoints REST " + userMessage;
                String vectorResults = vectorSearch(enrichedQuery, typeFilter, Collections.emptySet(), topK - problemChunks.size());
                return formatContext(problemChunks) + (vectorResults.isBlank() ? "" : "\n\n" + vectorResults);
            }

            if (phase == InterviewPhase.SCHEMA_DESIGN && chosenProblem != null) {
                List<DocumentChunk> problemChunks = vectorStore.searchByTitle(chosenProblem, typeFilter, 3);
                String enrichedQuery = chosenProblem + " database schema design entities relationships " + userMessage;
                String vectorResults = vectorSearch(enrichedQuery, typeFilter, Collections.emptySet(), topK - problemChunks.size());
                return formatContext(problemChunks) + (vectorResults.isBlank() ? "" : "\n\n" + vectorResults);
            }

            if (phase == InterviewPhase.DEEP_DIVE) {
                String enrichedQuery = (chosenProblem != null ? chosenProblem + " " : "") + userMessage;
                return vectorSearch(enrichedQuery, null, Collections.emptySet(), topK);
            }

            // Fallback for any unhandled phase
            String enrichedQuery = (chosenProblem != null ? chosenProblem + " " : "") + userMessage;
            return vectorSearch(enrichedQuery, typeFilter, Collections.emptySet(), topK);

        } catch (Exception e) {
            log.warn("RAG buildApiDbContext failed for phase {}: {}", phase, e.getMessage());
            return "";
        }
    }

    public String buildContext(String query, int topK) {
        return buildContext(query, null, null, topK);
    }

    public String buildContext(String query, InterviewType type, int topK) {
        return buildContext(query, type, null, topK);
    }

    public String buildContext(String query, InterviewType type) {
        return buildContext(query, type, null, defaultTopK);
    }

    public List<String> retrieve(String query, int topK) {
        return retrieve(query, null, topK);
    }

    public List<String> retrieve(String query, InterviewType type, int topK) {
        if (!isAvailable()) return Collections.emptyList();
        try {
            Response<Embedding> response = embeddingModel.embed(query);
            float[] embedding = response.content().vector();
            return vectorStore.search(embedding, mapTypeToFilter(type), topK)
                    .stream().map(DocumentChunk::content).toList();
        } catch (Exception e) {
            log.warn("RAG retrieve failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    // How many problems to show the LLM per session — enough variety, not overwhelming
    private static final int CATALOG_SAMPLE_SIZE = 10;

    private String fetchCatalog(String typeFilter) {
        if (typeFilter == null) return "";
        List<DocumentChunk> chunks = vectorStore.getCatalogChunks(typeFilter);
        if (chunks.isEmpty()) {
            log.warn("No problem-catalog chunk found for type: {}", typeFilter);
            return "";
        }

        // Extract all problem/system names from the catalog table rows across all chunks
        List<String> allNames = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            allNames.addAll(extractNamesFromCatalog(chunk.content()));
        }

        if (allNames.isEmpty()) {
            // Fallback: just return the raw content of the first chunk
            return "=== Problem / System Catalog ===\n" +
                   "Pick ONE from this list. Do not invent a problem not on this list.\n\n" +
                   chunks.get(0).content() +
                   "\n=== End of Catalog ===";
        }

        // Shuffle and sample — different set every session
        Collections.shuffle(allNames);
        List<String> sample = allNames.subList(0, Math.min(CATALOG_SAMPLE_SIZE, allNames.size()));

        StringBuilder sb = new StringBuilder();
        sb.append("=== Problem / System Catalog ===\n");
        sb.append("Pick ONE from this list. Do not invent a problem not on this list.\n\n");
        for (int i = 0; i < sample.size(); i++) {
            sb.append(i + 1).append(". ").append(sample.get(i)).append("\n");
        }
        sb.append("\n=== End of Catalog ===");
        return sb.toString();
    }

    /**
     * Extracts problem/system names from a markdown table catalog.
     * Handles both formats:
     *   LLD/HLD Systems.md: | Name | Priority | Notes |
     *   DSA Practice Sheet:  | Name | Link | Concept | Priority | Difficulty |
     */
    private List<String> extractNamesFromCatalog(String content) {
        List<String> names = new ArrayList<>();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("|")) continue;
            String[] cols = trimmed.split("\\|");
            if (cols.length < 2) continue;
            String name = cols[1].trim();
            // Skip header rows and separator rows
            if (name.isBlank() || name.equals("Name") || name.startsWith("---")
                    || name.startsWith("S. No") || name.equalsIgnoreCase("s. no")) continue;
            // Strip markdown links like [Text](url)
            name = name.replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1").trim();
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    private String vectorSearch(String query, @Nullable String typeFilter, Set<String> categories, int topK) {
        if (topK <= 0) return "";
        try {
            Response<Embedding> response = embeddingModel.embed(query);
            float[] embedding = response.content().vector();
            List<DocumentChunk> chunks = categories.isEmpty()
                    ? vectorStore.search(embedding, typeFilter, topK)
                    : vectorStore.search(embedding, typeFilter, categories, topK);
            return formatContext(chunks);
        } catch (Exception e) {
            log.warn("Vector search failed: {}", e.getMessage());
            return "";
        }
    }

    private String formatContext(List<DocumentChunk> results) {
        if (results.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("=== Relevant Interview Guide Content ===\n\n");
        for (int i = 0; i < results.size(); i++) {
            DocumentChunk c = results.get(i);
            sb.append(String.format("[%d] [%s/%s] %s :: %s\n%s\n\n",
                    i + 1, c.interviewType(), c.category(), c.title(), c.section(), c.content()));
        }
        sb.append("=== End of Reference Content ===");
        return sb.toString();
    }

    private String mapTypeToFilter(InterviewType type) {
        if (type == null) return null;
        return switch (type) {
            case DSA -> "DSA";
            case HLD -> "HLD";
            case LLD -> "LLD";
            case RESUME_GRILLING, CULTURE_FIT -> null;
            case API_AND_DATABASE_DESIGN -> "API_DB";
        };
    }

    public boolean isAvailable() {
        if (!ragEnabled) return false;
        if (vectorStore == null || embeddingModel == null || indexer == null) return false;
        return indexer.isIndexingComplete();
    }

    public RagStatus getStatus() {
        if (!ragEnabled) return new RagStatus("disabled", 0, false);
        if (indexer == null) return new RagStatus("not_configured", 0, false);
        return new RagStatus(indexer.getStatus(), indexer.getChunkCount(), indexer.isIndexingComplete());
    }

    public record RagStatus(String status, long chunkCount, boolean ready) {}
}
