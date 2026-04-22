package com.preplit.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true", matchIfMissing = true)
public class InterviewGuideIndexer {

    private static final Logger log = LoggerFactory.getLogger(InterviewGuideIndexer.class);
    private static final int BATCH_SIZE = 10;

    private final InterviewGuideParser parser;
    private final RedisVectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    private final AtomicBoolean indexingComplete = new AtomicBoolean(false);
    private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);

    public InterviewGuideIndexer(InterviewGuideParser parser,
                                 RedisVectorStore vectorStore,
                                 EmbeddingModel embeddingModel) {
        this.parser = parser;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        CompletableFuture.runAsync(this::indexIfNeeded);
    }

    public void indexIfNeeded() {
        if (!indexingInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            vectorStore.createIndexIfNotExists();

            Map<String, String> currentFiles = parser.getAllFilesWithHashes();
            Map<String, String> storedFiles = vectorStore.getAllFileHashes();

            Set<String> changedFiles = new HashSet<>();
            Set<String> deletedFiles = new HashSet<>(storedFiles.keySet());

            // detect changes
            for (var entry : currentFiles.entrySet()) {
                String file = entry.getKey();
                String hash = entry.getValue();

                if (!hash.equals(storedFiles.get(file))) {
                    changedFiles.add(file);
                }
                deletedFiles.remove(file);
            }

            log.info("Total files: {}", currentFiles.size());
            log.info("Already indexed files: {}", storedFiles.size());
            log.info("Files to reprocess (changed/new): {}", changedFiles.size());
            log.info("Deleted files: {}", deletedFiles.size());

            // resume ONLY if actually pending exists
            if (vectorStore.hasPendingFiles()) {
                Set<String> pending = vectorStore.getPendingFiles();

                pending.retainAll(currentFiles.keySet());

                if (!pending.isEmpty()) {
                    changedFiles = pending;
                    log.warn("Resuming interrupted indexing for {} files", changedFiles.size());
                } else {
                    vectorStore.clearPendingFiles(); // stale pending cleanup
                }
            }

            if (changedFiles.isEmpty() && deletedFiles.isEmpty()) {
                log.info("No changes detected. Skipping indexing.");
                indexingComplete.set(true);
                vectorStore.setStatus("complete");
                return;
            }

            vectorStore.setStatus("indexing");

            // handle deletions
            for (String file : deletedFiles) {
                vectorStore.deleteChunksByFile(file);
                vectorStore.deleteFileHash(file);
            }

            // checkpoint
            vectorStore.setPendingFiles(changedFiles);

            int processed = 0;
            int total = changedFiles.size();

            for (String file : changedFiles) {
                try {
                    processFile(file, currentFiles.get(file));
                    vectorStore.removePendingFile(file);

                    processed++;
                    log.info("Progress: {}/{} files processed", processed, total);

                } catch (Exception e) {
                    log.error("Failed processing file: {}", file, e);
                }
            }

            vectorStore.clearPendingFiles();
            vectorStore.setStatus("complete");
            indexingComplete.set(true);

            log.info("Incremental indexing complete. Total chunks: {}", vectorStore.getChunkCount());

        } catch (Exception e) {
            log.error("Indexing failed", e);
            vectorStore.setStatus("failed");
        } finally {
            indexingInProgress.set(false);
        }
    }

    private void processFile(String filePath, String hash) throws IOException {
        log.info("Processing file: {}", filePath);

        vectorStore.deleteChunksByFile(filePath);

        List<DocumentChunk> chunks = parser.parseFile(filePath);

        List<DocumentChunk> embedded = generateEmbeddings(chunks);

        vectorStore.storeBatch(embedded);

        vectorStore.setFileHash(filePath, hash);
    }

    private List<DocumentChunk> generateEmbeddings(List<DocumentChunk> chunks) {
        List<DocumentChunk> result = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, chunks.size());
            List<DocumentChunk> batch = chunks.subList(i, end);

            List<TextSegment> segments = batch.stream()
                    .map(chunk -> TextSegment.from(chunk.content()))
                    .toList();

            try {
                Response<List<Embedding>> response = embeddingModel.embedAll(segments);
                List<Embedding> embeddings = response.content();

                for (int j = 0; j < batch.size(); j++) {
                    result.add(batch.get(j).withEmbedding(embeddings.get(j).vector()));
                }

            } catch (Exception e) {
                log.error("Embedding batch failed: {}", e.getMessage());
                // Check if it's a context length error — if so, skip oversized chunks
                if (e.getMessage() != null && e.getMessage().contains("context length")) {
                    log.warn("Skipping {} chunks due to context length — content too large for embedding model", batch.size());
                    for (DocumentChunk chunk : batch) {
                        log.debug("Skipped chunk: {} (length: {} chars)", chunk.title(), chunk.content().length());
                    }
                } else {
                    // For other errors, still skip but log more details
                    log.error("Unexpected embedding error, skipping batch", e);
                }
            }
        }

        return result;
    }

    public boolean isIndexingComplete() {
        return indexingComplete.get();
    }

    public String getStatus() {
        if (indexingInProgress.get()) return "indexing";
        return vectorStore.getStatus();
    }

    public long getChunkCount() {
        return vectorStore.getChunkCount();
    }
}