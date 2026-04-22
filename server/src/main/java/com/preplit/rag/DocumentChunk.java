package com.preplit.rag;

/**
 * Represents a chunk of content from the interview guide for RAG retrieval.
 */
public record DocumentChunk(
    String id,              // Unique chunk ID (hash of sourcePath + index)
    String content,         // Text content of the chunk
    String interviewType,   // DSA, HLD, LLD
    String category,        // problem, concept, example-system, design-pattern
    String sourcePath,      // Original file path
    String title,           // Extracted title/header
    String section,         // Section name (e.g., "Arrays & Hashing", "Core Responsibilities")
    float[] embedding       // Vector embedding (null until embedded)
) {
    /**
     * Creates a chunk without embedding (for parsing phase).
     */
    public static DocumentChunk of(String id, String content, String interviewType,
                                   String category, String sourcePath, String title, String section) {
        return new DocumentChunk(id, content, interviewType, category, sourcePath, title, section, null);
    }

    /**
     * Creates a new chunk with embedding attached.
     */
    public DocumentChunk withEmbedding(float[] embedding) {
        return new DocumentChunk(id, content, interviewType, category, sourcePath, title, section, embedding);
    }
}
