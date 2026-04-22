package com.preplit.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.search.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * Redis Vector Store for RAG using RediSearch.
 * Stores document chunks with vector embeddings and performs similarity search.
 */
@Component
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true", matchIfMissing = true)
public class RedisVectorStore {

    private static final Logger log = LoggerFactory.getLogger(RedisVectorStore.class);

    private static final String INDEX_NAME = "rag_vectors";
    private static final String KEY_PREFIX = "rag:chunk:";
    private static final String HASH_KEY = "rag:index:hash";
    private static final String STATUS_KEY = "rag:index:status";
    private static final String FILE_HASH_PREFIX = "rag:file:hash:";
    private static final String PENDING_FILES_KEY = "rag:index:pending";

    private final JedisPooled jedis;
    private final int embeddingDimension;

    public RedisVectorStore(JedisPooled jedis, @Qualifier("embeddingDimension") int embeddingDimension) {
        this.jedis = jedis;
        this.embeddingDimension = embeddingDimension;
    }

    /**
     * Creates the vector search index if it doesn't exist.
     */
    public void createIndexIfNotExists() {
        try {
            jedis.ftInfo(INDEX_NAME);
            log.info("Vector index '{}' already exists", INDEX_NAME);
        } catch (JedisDataException e) {
            if (e.getMessage().toLowerCase().contains("unknown index")) {
                createIndex();
            } else {
                throw e;
            }
        }
    }

    private void createIndex() {
        log.info("Creating vector index '{}' with dimension {}", INDEX_NAME, embeddingDimension);

        Map<String, Object> vectorAttrs = new HashMap<>();
        vectorAttrs.put("TYPE", "FLOAT32");
        vectorAttrs.put("DIM", embeddingDimension);
        vectorAttrs.put("DISTANCE_METRIC", "COSINE");

        Schema schema = new Schema()
                .addTextField("content", 1.0)
                .addTagField("interviewType")
                .addTagField("category")
                .addTextField("title", 1.5)
                .addTextField("section", 1.0)
                .addTextField("sourcePath", 0.5)
                .addVectorField("embedding", Schema.VectorField.VectorAlgo.HNSW, vectorAttrs);

        IndexDefinition definition = new IndexDefinition()
                .setPrefixes(KEY_PREFIX);

        jedis.ftCreate(INDEX_NAME, IndexOptions.defaultOptions().setDefinition(definition), schema);
        log.info("Vector index '{}' created successfully", INDEX_NAME);
    }

    /**
     * Stores a document chunk with its embedding.
     */
    public void store(DocumentChunk chunk) {
        String key = KEY_PREFIX + chunk.id();

        Map<String, String> fields = new HashMap<>();
        fields.put("content", chunk.content());
        fields.put("interviewType", chunk.interviewType());
        fields.put("category", chunk.category());
        fields.put("title", chunk.title());
        fields.put("section", chunk.section());
        fields.put("sourcePath", chunk.sourcePath());

        jedis.hset(key, fields);

        if (chunk.embedding() != null) {
            byte[] embeddingBytes = floatArrayToBytes(chunk.embedding());
            jedis.hset(key.getBytes(), "embedding".getBytes(), embeddingBytes);
        }
    }

    /**
     * Stores multiple chunks in a batch.
     */
    public void storeBatch(List<DocumentChunk> chunks) {
        for (DocumentChunk chunk : chunks) {
            store(chunk);
        }
        log.info("Stored {} chunks in Redis", chunks.size());
    }

    /**
     * Performs vector similarity search with optional filter by interview type.
     */
    public List<DocumentChunk> search(float[] queryEmbedding, String interviewType, int topK) {
        byte[] embeddingBytes = floatArrayToBytes(queryEmbedding);

        String filter = interviewType != null && !interviewType.isBlank()
                ? String.format("@interviewType:{%s}", interviewType)
                : "*";

        Query query = new Query(String.format("(%s)=>[KNN %d @embedding $vec AS score]", filter, topK))
                .addParam("vec", embeddingBytes)
                .returnFields("content", "interviewType", "category", "title", "section", "sourcePath", "score")
                .setSortBy("score", true)
                .dialect(2);

        SearchResult result = jedis.ftSearch(INDEX_NAME, query);

        List<DocumentChunk> chunks = new ArrayList<>();
        for (Document doc : result.getDocuments()) {
            String id = doc.getId().replace(KEY_PREFIX, "");
            String content = doc.getString("content");
            String type = doc.getString("interviewType");
            String category = doc.getString("category");
            String title = doc.getString("title");
            String section = doc.getString("section");
            String sourcePath = doc.getString("sourcePath");

            chunks.add(DocumentChunk.of(id, content, type, category, sourcePath, title, section));
        }

        return chunks;
    }

    /**
     * Performs vector similarity search with interview type and category filters.
     */
    public List<DocumentChunk> search(float[] queryEmbedding, String interviewType, Set<String> categories, int topK) {
        byte[] embeddingBytes = floatArrayToBytes(queryEmbedding);

        StringBuilder filter = new StringBuilder("*");
        if (interviewType != null && !interviewType.isBlank()) {
            filter = new StringBuilder(String.format("@interviewType:{%s}", interviewType));
        }
        if (categories != null && !categories.isEmpty()) {
            String categoryFilter = String.join("|", categories);
            String combined = filter.toString().equals("*")
                    ? String.format("@category:{%s}", categoryFilter)
                    : String.format("%s @category:{%s}", filter, categoryFilter);
            filter = new StringBuilder(combined);
        }

        Query query = new Query(String.format("(%s)=>[KNN %d @embedding $vec AS score]", filter, topK))
                .addParam("vec", embeddingBytes)
                .returnFields("content", "interviewType", "category", "title", "section", "sourcePath", "score")
                .setSortBy("score", true)
                .dialect(2);

        SearchResult result = jedis.ftSearch(INDEX_NAME, query);
        return mapDocuments(result.getDocuments());
    }

    /**
     * Retrieves all chunks for a specific document title (e.g. "Parking Lot Design").
     * Used to fetch the full example system doc once a problem is chosen.
     */
    public List<DocumentChunk> searchByTitle(String title, String interviewType, int limit) {
        // Escape special characters for RediSearch tag query
        String escapedTitle = title.replace(" ", "\\ ").replace("-", "\\-");
        StringBuilder filter = new StringBuilder(String.format("@title:{%s}", escapedTitle));
        if (interviewType != null && !interviewType.isBlank()) {
            filter.insert(0, String.format("@interviewType:{%s} ", interviewType));
        }

        Query query = new Query(filter.toString())
                .returnFields("content", "interviewType", "category", "title", "section", "sourcePath")
                .limit(0, limit)
                .dialect(2);

        try {
            SearchResult result = jedis.ftSearch(INDEX_NAME, query);
            return mapDocuments(result.getDocuments());
        } catch (Exception e) {
            log.warn("searchByTitle failed for '{}': {}", title, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Retrieves the problem-catalog chunk for a given interview type.
     * Returns the full catalog document (LLD Systems.md / HLD Systems.md / DSA Practice Sheet).
     */
    public List<DocumentChunk> getCatalogChunks(String interviewType) {
        String filter = String.format("@interviewType:{%s} @category:{problem\\-catalog}", interviewType);

        Query query = new Query(filter)
                .returnFields("content", "interviewType", "category", "title", "section", "sourcePath")
                .limit(0, 20) // fetch all catalog chunks (large catalogs are split into multiple)
                .dialect(2);

        try {
            SearchResult result = jedis.ftSearch(INDEX_NAME, query);
            return mapDocuments(result.getDocuments());
        } catch (Exception e) {
            log.warn("getCatalogChunks failed for type '{}': {}", interviewType, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Retrieves chunks by metadata filters without vector similarity.
     */
    public List<DocumentChunk> searchByMetadata(String interviewType, Set<String> categories, int limit) {
        StringBuilder filter = new StringBuilder("*");
        if (interviewType != null && !interviewType.isBlank()) {
            filter = new StringBuilder(String.format("@interviewType:{%s}", interviewType));
        }
        if (categories != null && !categories.isEmpty()) {
            String categoryFilter = String.join("|", categories);
            String combined = filter.toString().equals("*")
                    ? String.format("@category:{%s}", categoryFilter)
                    : String.format("%s @category:{%s}", filter, categoryFilter);
            filter = new StringBuilder(combined);
        }

        Query query = new Query(filter.toString())
                .returnFields("content", "interviewType", "category", "title", "section", "sourcePath")
                .limit(0, limit)
                .dialect(2);

        SearchResult result = jedis.ftSearch(INDEX_NAME, query);
        return mapDocuments(result.getDocuments());
    }

    /**
     * Deletes all chunks and drops the index.
     */
    public void clear() {
        try {
            jedis.ftDropIndex(INDEX_NAME);
            log.info("Dropped index '{}'", INDEX_NAME);
        } catch (JedisDataException e) {
            // Handle case where index doesn't exist (case-insensitive check)
            if (!e.getMessage().toLowerCase().contains("unknown index")) {
                throw e;
            }
            log.debug("Index '{}' did not exist, skipping drop", INDEX_NAME);
        }

        // Delete all chunk keys
        Set<String> keys = jedis.keys(KEY_PREFIX + "*");
        if (!keys.isEmpty()) {
            jedis.del(keys.toArray(new String[0]));
            log.info("Deleted {} chunk keys", keys.size());
        }
    }

    /**
     * Gets the stored hash of the indexed content.
     */
    public String getStoredHash() {
        return jedis.get(HASH_KEY);
    }

    /**
     * Stores the hash of the indexed content.
     */
    public void setStoredHash(String hash) {
        jedis.set(HASH_KEY, hash);
    }

    /**
     * Gets the indexing status.
     */
    public String getStatus() {
        String status = jedis.get(STATUS_KEY);
        return status != null ? status : "unknown";
    }

    /**
     * Sets the indexing status.
     */
    public void setStatus(String status) {
        jedis.set(STATUS_KEY, status);
    }

    /**
     * Returns the number of indexed chunks.
     */
    public long getChunkCount() {
        try {
            Map<String, Object> info = jedis.ftInfo(INDEX_NAME);
            Object numDocs = info.get("num_docs");
            if (numDocs instanceof Number) {
                return ((Number) numDocs).longValue();
            }
            return 0;
        } catch (JedisDataException e) {
            return 0;
        }
    }

    private List<DocumentChunk> mapDocuments(List<Document> documents) {
        List<DocumentChunk> chunks = new ArrayList<>();
        for (Document doc : documents) {
            String id = doc.getId().replace(KEY_PREFIX, "");
            String content = doc.getString("content");
            String type = doc.getString("interviewType");
            String category = doc.getString("category");
            String title = doc.getString("title");
            String section = doc.getString("section");
            String sourcePath = doc.getString("sourcePath");

            chunks.add(DocumentChunk.of(id, content, type, category, sourcePath, title, section));
        }
        return chunks;
    }

    private byte[] floatArrayToBytes(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    // ========== Per-File Hash Tracking ==========

    /**
     * Gets the stored hash for a specific file.
     */
    public String getFileHash(String filePath) {
        return jedis.get(FILE_HASH_PREFIX + filePath);
    }

    /**
     * Stores the hash for a specific file.
     */
    public void setFileHash(String filePath, String hash) {
        jedis.set(FILE_HASH_PREFIX + filePath, hash);
    }

    /**
     * Deletes the hash for a specific file.
     */
    public void deleteFileHash(String filePath) {
        jedis.del(FILE_HASH_PREFIX + filePath);
    }

    /**
     * Gets all stored file paths with their hashes.
     */
    public Map<String, String> getAllFileHashes() {
        Map<String, String> result = new HashMap<>();
        Set<String> keys = jedis.keys(FILE_HASH_PREFIX + "*");
        for (String key : keys) {
            String filePath = key.substring(FILE_HASH_PREFIX.length());
            String hash = jedis.get(key);
            if (hash != null) {
                result.put(filePath, hash);
            }
        }
        return result;
    }

    // ========== Chunk Management by File ==========

    /**
     * Deletes all chunks from a specific source file.
     */
    public int deleteChunksByFile(String sourcePath) {
        Set<String> keys = jedis.keys(KEY_PREFIX + "*");
        int deleted = 0;
        for (String key : keys) {
            String storedPath = jedis.hget(key, "sourcePath");
            if (sourcePath.equals(storedPath)) {
                jedis.del(key);
                deleted++;
            }
        }
        if (deleted > 0) {
            log.debug("Deleted {} chunks for file: {}", deleted, sourcePath);
        }
        return deleted;
    }

    /**
     * Gets all chunk IDs that have embeddings (fully indexed).
     */
    public Set<String> getIndexedChunkIds() {
        Set<String> result = new HashSet<>();
        Set<String> keys = jedis.keys(KEY_PREFIX + "*");
        for (String key : keys) {
            // Check if embedding field exists
            byte[] embedding = jedis.hget(key.getBytes(), "embedding".getBytes());
            if (embedding != null && embedding.length > 0) {
                result.add(key.substring(KEY_PREFIX.length()));
            }
        }
        return result;
    }

    /**
     * Gets chunk IDs for a specific file that have embeddings.
     */
    public Set<String> getIndexedChunkIdsForFile(String sourcePath) {
        Set<String> result = new HashSet<>();
        Set<String> keys = jedis.keys(KEY_PREFIX + "*");
        for (String key : keys) {
            String storedPath = jedis.hget(key, "sourcePath");
            if (sourcePath.equals(storedPath)) {
                byte[] embedding = jedis.hget(key.getBytes(), "embedding".getBytes());
                if (embedding != null && embedding.length > 0) {
                    result.add(key.substring(KEY_PREFIX.length()));
                }
            }
        }
        return result;
    }

    // ========== Pending Files (for resumption) ==========

    /**
     * Sets the list of files pending indexing.
     */
    public void setPendingFiles(Set<String> filePaths) {
        jedis.del(PENDING_FILES_KEY);
        if (!filePaths.isEmpty()) {
            jedis.sadd(PENDING_FILES_KEY, filePaths.toArray(new String[0]));
        }
    }

    /**
     * Gets the list of files pending indexing.
     */
    public Set<String> getPendingFiles() {
        return jedis.smembers(PENDING_FILES_KEY);
    }

    /**
     * Removes a file from the pending list (after successful indexing).
     */
    public void removePendingFile(String filePath) {
        jedis.srem(PENDING_FILES_KEY, filePath);
    }

    /**
     * Clears the pending files list.
     */
    public void clearPendingFiles() {
        jedis.del(PENDING_FILES_KEY);
    }

    public void clearPendingFilesIfStale(Set<String> validFiles) {
        Set<String> pending = getPendingFiles();
        if (pending.isEmpty()) return;

        pending.retainAll(validFiles);

        if (pending.isEmpty()) {
            clearPendingFiles();
            log.info("Cleared stale pending files");
        } else {
            setPendingFiles(pending);
        }
    }

    /**
     * Checks if there are pending files (indicates interrupted indexing).
     */
    public boolean hasPendingFiles() {
        return jedis.scard(PENDING_FILES_KEY) > 0;
    }
}
