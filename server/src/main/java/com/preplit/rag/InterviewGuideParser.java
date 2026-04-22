package com.preplit.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Parses the interview-guide markdown files into DocumentChunks for RAG indexing.
 *
 * Handles three content types:
 * - DSA problems: Parsed from markdown tables
 * - HLD/LLD concepts: Split by H2 sections
 * - Example systems: Split by major sections
 */
@Component
public class InterviewGuideParser {

    private static final Logger log = LoggerFactory.getLogger(InterviewGuideParser.class);

    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile(
            "\\|([^|]+)\\|([^|]+)\\|([^|]+)\\|([^|]+)\\|([^|]+)\\|"
    );
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,2})\\s+(.+)$", Pattern.MULTILINE);

    // Catalog files that should be stored as a single chunk (not split by section)
    private static final Set<String> CATALOG_FILE_NAMES = Set.of(
            "lld systems.md", "hld systems.md"
    );

    private final String guidePath;

    public InterviewGuideParser(@Value("${rag.interview-guide-path:interview-guide}") String guidePath) {
        this.guidePath = guidePath;
    }

    /**
     * Parses all markdown files in the interview guide directory.
     */
    public List<DocumentChunk> parseAll() throws IOException {
        List<DocumentChunk> chunks = new ArrayList<>();
        Path basePath = Path.of(guidePath);

        if (!Files.exists(basePath)) {
            log.warn("Interview guide path does not exist: {}", basePath.toAbsolutePath());
            return chunks;
        }

        // Parse DSA
        Path dsaPath = basePath.resolve("dsa");
        if (Files.exists(dsaPath)) {
            chunks.addAll(parseDsaDirectory(dsaPath));
        }

        // Parse HLD
        Path hldPath = basePath.resolve("hld");
        if (Files.exists(hldPath)) {
            chunks.addAll(parseHldDirectory(hldPath));
        }

        // Parse LLD
        Path lldPath = basePath.resolve("lld");
        if (Files.exists(lldPath)) {
            chunks.addAll(parseLldDirectory(lldPath));
        }

        log.info("Parsed {} total chunks from interview guide", chunks.size());
        return chunks;
    }

    private List<DocumentChunk> parseDsaDirectory(Path dsaPath) throws IOException {
        List<DocumentChunk> chunks = new ArrayList<>();

        try (Stream<Path> files = Files.walk(dsaPath)) {
            List<Path> mdFiles = files.filter(p -> p.toString().endsWith(".md")).toList();
            for (Path file : mdFiles) {
                chunks.addAll(parseDsaFile(file));
            }
        }

        log.info("Parsed {} DSA chunks", chunks.size());
        return chunks;
    }

    /**
     * Parses DSA markdown file with problem tables.
     * Each problem becomes a separate chunk with its concept and metadata.
     */
    private List<DocumentChunk> parseDsaFile(Path file) throws IOException {
        List<DocumentChunk> chunks = new ArrayList<>();
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String relativePath = file.toString();

        String currentSection = "General";
        String[] lines = content.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Track current section (H2 or H3)
            if (line.startsWith("## ")) {
                currentSection = line.substring(3).replace("**", "").trim();
                continue;
            }
            if (line.startsWith("### ")) {
                currentSection = line.substring(4).replace("**", "").trim();
                continue;
            }

            // Parse table rows (skip header row)
            Matcher matcher = TABLE_ROW_PATTERN.matcher(line);
            if (matcher.matches()) {
                String name = matcher.group(1).trim();
                String link = matcher.group(2).trim();
                String concept = matcher.group(3).trim();
                String priority = matcher.group(4).trim();
                String difficulty = matcher.group(5).trim();

                // Skip header rows
                if (name.equals("Name") || name.contains("---")) {
                    continue;
                }

                // Build chunk content
                String chunkContent = buildDsaProblemChunk(name, link, concept, priority, difficulty, currentSection);
                String chunkId = generateId(relativePath, name, chunkContent);

                chunks.add(DocumentChunk.of(
                        chunkId,
                        chunkContent,
                        "DSA",
                        "problem",
                        relativePath,
                        name,
                        currentSection
                ));
            }
        }

        return chunks;
    }

    private String buildDsaProblemChunk(String name, String link, String concept, String priority, String difficulty, String section) {
        return String.format("""
                Problem: %s
                Category: %s
                Difficulty: %s
                Priority: %s
                Question Link: %s
                Key Concept: %s

                This is a %s difficulty %s problem. The main approach involves: %s.
                """,
                name, section, difficulty, priority, link, concept, difficulty.toLowerCase(), section.toLowerCase(), concept);
    }

    private List<DocumentChunk> parseHldDirectory(Path hldPath) throws IOException {
        List<DocumentChunk> chunks = new ArrayList<>();

        // Parse root HLD files (HLD Systems.md as catalog, HLD Concepts.md as concept)
        try (Stream<Path> files = Files.list(hldPath)) {
            List<Path> mdFiles = files.filter(p -> p.toString().endsWith(".md")).toList();
            for (Path file : mdFiles) {
                String lowerName = file.getFileName().toString().toLowerCase();
                if (lowerName.equals("hld systems.md")) {
                    chunks.addAll(parseCatalogFile(file, "HLD"));
                } else {
                    chunks.addAll(parseConceptFile(file, "HLD", "concept"));
                }
            }
        }

        // Parse concepts
        Path conceptsPath = hldPath.resolve("concepts");
        if (Files.exists(conceptsPath)) {
            try (Stream<Path> files = Files.walk(conceptsPath, 1)) {
                List<Path> mdFiles = files.filter(p -> p.toString().endsWith(".md")).toList();
                for (Path file : mdFiles) {
                    chunks.addAll(parseConceptFile(file, "HLD", "concept"));
                }
            }
        }

        // Parse example systems
        Path examplesPath = hldPath.resolve("example-systems");
        if (Files.exists(examplesPath)) {
            try (Stream<Path> files = Files.walk(examplesPath, 1)) {
                List<Path> mdFiles = files.filter(p -> p.toString().endsWith(".md")).toList();
                for (Path file : mdFiles) {
                    chunks.addAll(parseConceptFile(file, "HLD", "example-system"));
                }
            }
        }

        log.info("Parsed {} HLD chunks", chunks.size());
        return chunks;
    }

    private List<DocumentChunk> parseLldDirectory(Path lldPath) throws IOException {
        List<DocumentChunk> chunks = new ArrayList<>();

        // Parse design patterns
        Path patternsPath = lldPath.resolve("design-patterns");
        if (Files.exists(patternsPath)) {
            try (Stream<Path> files = Files.walk(patternsPath, 1)) {
                List<Path> mdFiles = files.filter(p -> p.toString().endsWith(".md")).toList();
                for (Path file : mdFiles) {
                    chunks.addAll(parseConceptFile(file, "LLD", "design-pattern"));
                }
            }
        }

        // Parse example systems
        Path examplesPath = lldPath.resolve("example-systems");
        if (Files.exists(examplesPath)) {
            try (Stream<Path> files = Files.walk(examplesPath, 1)) {
                List<Path> mdFiles = files.filter(p -> p.toString().endsWith(".md")).toList();
                for (Path file : mdFiles) {
                    chunks.addAll(parseConceptFile(file, "LLD", "example-system"));
                }
            }
        }

        // Parse root LLD files — LLD Systems.md as catalog, others as concepts
        try (Stream<Path> files = Files.list(lldPath)) {
            List<Path> mdFiles = files.filter(p -> p.toString().endsWith(".md")).toList();
            for (Path file : mdFiles) {
                String lowerName = file.getFileName().toString().toLowerCase();
                if (lowerName.equals("lld systems.md")) {
                    chunks.addAll(parseCatalogFile(file, "LLD"));
                } else {
                    chunks.addAll(parseConceptFile(file, "LLD", "concept"));
                }
            }
        }

        log.info("Parsed {} LLD chunks", chunks.size());
        return chunks;
    }

    /**
     * Parses a catalog file (LLD Systems.md, HLD Systems.md) as a single chunk.
     * If the content exceeds the embedding token limit, splits into smaller chunks.
     */
    private List<DocumentChunk> parseCatalogFile(Path file, String interviewType) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String fileName = file.getFileName().toString();
        String title = fileName.replace(".md", "");
        String relativePath = file.toString();

        // nomic-embed-text context limit ~8192 tokens ≈ 6000 chars safe limit
        // If content fits, store as one chunk for easy catalog retrieval
        if (content.length() <= 6000) {
            String chunkId = generateId(relativePath, "catalog", content);
            return List.of(DocumentChunk.of(chunkId, content, interviewType, "problem-catalog", relativePath, title, "Full Catalog"));
        }

        // Otherwise split by rows — each row is one system entry
        List<DocumentChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n");
        StringBuilder currentChunk = new StringBuilder();
        // Preserve header lines (priority legend + table header)
        StringBuilder header = new StringBuilder();
        boolean headerDone = false;
        int chunkIndex = 0;

        for (String line : lines) {
            // Collect header until first data row
            if (!headerDone) {
                header.append(line).append("\n");
                // Table header separator row signals end of header
                if (line.startsWith("| ---") || line.startsWith("|---")) {
                    headerDone = true;
                }
                continue;
            }

            // Skip empty / non-table lines
            if (!line.trim().startsWith("|")) {
                continue;
            }

            currentChunk.append(line).append("\n");

            // Flush every ~20 rows or when approaching size limit
            if (currentChunk.length() > 4000) {
                String chunkContent = header + currentChunk.toString();
                String chunkId = generateId(relativePath, "catalog-" + chunkIndex, chunkContent);
                chunks.add(DocumentChunk.of(chunkId, chunkContent, interviewType, "problem-catalog", relativePath, title, "Catalog Part " + (chunkIndex + 1)));
                currentChunk.setLength(0);
                chunkIndex++;
            }
        }

        // Flush remainder
        if (!currentChunk.isEmpty()) {
            String chunkContent = header + currentChunk.toString();
            String chunkId = generateId(relativePath, "catalog-" + chunkIndex, chunkContent);
            chunks.add(DocumentChunk.of(chunkId, chunkContent, interviewType, "problem-catalog", relativePath, title, "Catalog Part " + (chunkIndex + 1)));
        }

        log.info("Parsed catalog file: {} into {} chunks", fileName, chunks.size());
        return chunks;
    }

    /**
     * Parses a concept/system file by splitting on H2 headers.
     * Each H2 section becomes a chunk.
     */
    private List<DocumentChunk> parseConceptFile(Path file, String interviewType, String category) throws IOException {
        List<DocumentChunk> chunks = new ArrayList<>();
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String fileName = file.getFileName().toString();
        String title = fileName.replace(".md", "").replace("-", " ");
        String relativePath = file.toString();

        // Split by top-level and section headings (H1/H2)
        Matcher matcher = HEADING_PATTERN.matcher(content);
        List<int[]> sectionBounds = new ArrayList<>();
        List<String> sectionNames = new ArrayList<>();

        while (matcher.find()) {
            String heading = matcher.group(2).trim();
            if (heading.isBlank()) {
                continue;
            }

            // Skip duplicated title heading at file start.
            if (matcher.start() == 0 && normalizeHeading(heading).equals(normalizeHeading(title))) {
                continue;
            }

            sectionBounds.add(new int[]{matcher.start(), matcher.end()});
            sectionNames.add(heading);
        }

        if (sectionBounds.isEmpty()) {
            // No H2 headers, treat whole file as one chunk
            String truncatedContent = truncateContent(content, 2000);
            String chunkId = generateId(relativePath, "full", truncatedContent);
            chunks.add(DocumentChunk.of(chunkId, truncatedContent, interviewType, category, relativePath, title, "Full Document"));
            return chunks;
        }

        // Create chunks for each section
        for (int i = 0; i < sectionBounds.size(); i++) {
            int start = sectionBounds.get(i)[1]; // After the header
            int end = (i + 1 < sectionBounds.size()) ? sectionBounds.get(i + 1)[0] : content.length();
            String sectionName = sectionNames.get(i);
            String sectionContent = content.substring(start, end).trim();

            if (sectionContent.length() < 50) {
                continue; // Skip very short sections
            }

            String chunkContent = String.format("# %s - %s\n\n%s", title, sectionName, sectionContent);
            chunkContent = truncateContent(chunkContent, 2000);
            String chunkId = generateId(relativePath, sectionName, chunkContent);

            chunks.add(DocumentChunk.of(chunkId, chunkContent, interviewType, category, relativePath, title, sectionName));
        }

        return chunks;
    }

    private String truncateContent(String content, int maxTokens) {
        // nomic-embed-text context limit is 8192 tokens. Cap at 1500 tokens (~6000 chars) to stay safe.
        int safeMax = Math.min(maxTokens, 1500);
        int maxChars = safeMax * 4;
        if (content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "...";
    }

    private String normalizeHeading(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private String generateId(String path, String identifier, String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = path + ":" + identifier + ":" + content.hashCode();
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return path.hashCode() + "_" + identifier.hashCode();
        }
    }

    /**
     * Computes a hash of all markdown files for version checking.
     */
    public String computeDirectoryHash() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            Path basePath = Path.of(guidePath);

            if (!Files.exists(basePath)) {
                return "empty";
            }

            try (Stream<Path> files = Files.walk(basePath)) {
                files.filter(p -> p.toString().endsWith(".md"))
                        .sorted()
                        .forEach(file -> {
                            try {
                                md.update(file.toString().getBytes(StandardCharsets.UTF_8));
                                md.update(Files.readAllBytes(file));
                            } catch (IOException e) {
                                log.warn("Failed to read file for hashing: {}", file, e);
                            }
                        });
            }

            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("Failed to compute directory hash", e);
            return "error-" + System.currentTimeMillis();
        }
    }

    // ========== Incremental Indexing Support ==========

    /**
     * Gets all markdown files with their content hashes.
     */
    public Map<String, String> getAllFilesWithHashes() throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        Path basePath = Path.of(guidePath);

        if (!Files.exists(basePath)) {
            return result;
        }

        try (Stream<Path> files = Files.walk(basePath)) {
            files.filter(p -> p.toString().endsWith(".md"))
                    .sorted()
                    .forEach(file -> {
                        try {
                            String hash = computeFileHash(file);
                            result.put(file.toString(), hash);
                        } catch (Exception e) {
                            log.warn("Failed to compute hash for file: {}", file, e);
                        }
                    });
        }

        return result;
    }

    /**
     * Computes SHA-256 hash of a single file's contents.
     */
    public String computeFileHash(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Files.readAllBytes(file));
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("Failed to compute file hash: {}", file, e);
            return "error-" + System.currentTimeMillis();
        }
    }

    /**
     * Parses a single file and returns its chunks.
     * Automatically determines interview type and category from path.
     */
    public List<DocumentChunk> parseFile(String filePath) throws IOException {
        Path file = Path.of(filePath);
        if (!Files.exists(file)) {
            log.warn("File does not exist: {}", filePath);
            return Collections.emptyList();
        }

        FileTypeInfo typeInfo = determineFileType(filePath);

        if (typeInfo.category.equals("problem-catalog")) {
            return parseCatalogFile(file, typeInfo.interviewType);
        } else if (typeInfo.interviewType.equals("DSA")) {
            return parseDsaFile(file);
        } else {
            return parseConceptFile(file, typeInfo.interviewType, typeInfo.category);
        }
    }

    /**
     * Determines interview type and category from file path.
     */
    private FileTypeInfo determineFileType(String filePath) {
        String normalizedPath = filePath.replace("\\", "/").toLowerCase();
        String fileName = Path.of(filePath).getFileName().toString().toLowerCase();

        if (normalizedPath.contains("/dsa/")) {
            return new FileTypeInfo("DSA", "problem");
        } else if (normalizedPath.contains("/hld/")) {
            if (fileName.equals("hld systems.md")) return new FileTypeInfo("HLD", "problem-catalog");
            if (normalizedPath.contains("/concepts/")) return new FileTypeInfo("HLD", "concept");
            if (normalizedPath.contains("/example-systems/")) return new FileTypeInfo("HLD", "example-system");
            return new FileTypeInfo("HLD", "concept");
        } else if (normalizedPath.contains("/lld/")) {
            if (fileName.equals("lld systems.md")) return new FileTypeInfo("LLD", "problem-catalog");
            if (normalizedPath.contains("/design-patterns/")) return new FileTypeInfo("LLD", "design-pattern");
            if (normalizedPath.contains("/example-systems/")) return new FileTypeInfo("LLD", "example-system");
            return new FileTypeInfo("LLD", "concept");
        }

        // Default fallback
        return new FileTypeInfo("GENERAL", "document");
    }

    private record FileTypeInfo(String interviewType, String category) {}

    /**
     * Gets the base path of the interview guide.
     */
    public String getGuidePath() {
        return guidePath;
    }
}
