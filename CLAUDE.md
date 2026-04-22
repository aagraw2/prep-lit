# PrepLit - AI Mock Interview Platform

## Project Overview
PrepLit is a real-time AI-powered mock interview platform. It uses voice input, streams LLM responses via SSE, and speaks them back via TTS. The backend is **Spring WebFlux (reactive/Netty)** — NOT Spring MVC. This matters for file uploads, return types, etc.

## Tech Stack
- **Frontend**: React + TypeScript + Vite (port 3000)
- **Backend**: Java Spring Boot + WebFlux (port 8080)
- **LLM**: Configurable via `ai.provider` — supports `anthropic`, `openai`, `sarvam`
- **LLM SDK**: LangChain4j (`dev.langchain4j`) — NOT the raw Anthropic SDK
- **PDF Parsing**: Apache PDFBox
- **Session/Message storage**: PostgreSQL (via Spring Data JPA)
- **Interview state storage**: Redis (24h TTL) — `InterviewContext` lives here, NOT in Postgres
- **Containerization**: Docker Compose
- **RAG**: Redis Vector Search + LangChain4j embeddings (interview-guide submodule)

## Interview Types (enum `InterviewType`)
- `DSA` — Data Structures & Algorithms
- `HLD` — High Level System Design
- `LLD` — Low Level Design
- `RESUME_GRILLING` — Resume Deep Dive (requires PDF upload; resume text stored in Redis + Postgres)
- `CULTURE_FIT` — Culture Fit & Behavioral

## Roles (enum `SdeRole`): `SDE1`, `SDE2`, `SDE3`

---

## Project Structure

```
prep-lit/
├── client/src/
│   ├── App.tsx
│   ├── types.ts                              # TS interfaces: Session, Message, FeedbackReport, etc.
│   ├── api/client.ts                         # All API calls + SSE streaming
│   ├── components/
│   │   ├── InterviewSession.tsx              # Main UI, orchestrates everything
│   │   ├── MessageList.tsx                   # Renders conversation
│   │   ├── VoiceControls.tsx                 # Mic button, voice input UI
│   │   └── FeedbackModal.tsx                 # Post-interview feedback display
│   └── hooks/
│       ├── useSpeechToText.ts                # Web Speech API wrapper
│       └── useTextToSpeech.ts                # TTS wrapper
│
└── server/src/main/java/com/preplit/
    ├── PrepLitApplication.java
    ├── config/
    │   ├── RedisConfig.java                  # RedisTemplate + JedisPooled for vector search
    │   └── EmbeddingConfig.java              # Provider-aware embedding model factory
    ├── controller/
    │   ├── InterviewController.java          # All REST endpoints + SSE streaming
    │   └── GlobalExceptionHandler.java       # @RestControllerAdvice error handling
    ├── service/
    │   ├── InterviewOrchestrator.java        # Coordinates interview flow, saves to Redis
    │   ├── InterviewPromptService.java       # Builds prompts from phase-specific .txt templates + context
    │   ├── LLMStateAnalyzer.java             # Calls LLM to analyze candidate state → StateAnalysis record
    │   ├── FeedbackGenerator.java            # Generates FeedbackReport from InterviewContext
    │   ├── ResumeParserService.java          # PDF → text via PDFBox; takes FilePart (reactive)
    │   ├── SessionService.java               # CRUD for Session + Message (Postgres)
    │   ├── RAGService.java                   # Phase-aware retrieval from interview guide via vector search
    │   └── SessionNotFoundException.java
    ├── rag/
    │   ├── DocumentChunk.java                # Record: chunk content + metadata + embedding
    │   ├── InterviewGuideParser.java         # Parses interview-guide markdown into chunks
    │   ├── InterviewGuideIndexer.java        # Async startup indexing with per-file hash diff + resume
    │   └── RedisVectorStore.java             # Redis Vector Search operations (RediSearch)
    ├── model/
    │   ├── InterviewContext.java             # Full session state — stored in Redis; includes chosenProblem
    │   ├── CandidateStateModel.java          # record: confidence, engagement, frustration, stuckTurns
    │   ├── EvaluationScores.java             # record: 5 scores [0-100], totalScore() = avg
    │   ├── Session.java                      # JPA entity — sessions table (Postgres)
    │   ├── Message.java                      # JPA entity — messages table (Postgres)
    │   ├── FeedbackReport.java               # summary, strengths, weaknesses, verdict, nextSteps
    │   ├── InterviewPhase.java               # INTRO → CLARIFICATION → APPROACH → IMPLEMENTATION → DEEP_DIVE → WRAP_UP
    │   ├── InterviewerState.java             # EXPLORING | STUCK | PROGRESSING | DISENGAGED | WRAPPING_UP
    │   ├── InterviewType.java                # DSA | HLD | LLD | RESUME_GRILLING | CULTURE_FIT
    │   ├── SdeRole.java                      # SDE1 | SDE2 | SDE3
    │   ├── DifficultyLevel.java
    │   ├── Verdict.java                      # HIRE | BORDERLINE | NO_HIRE
    │   ├── HintLevel.java                    # L1 | L2 | L3
    │   ├── HintRecord.java
    │   ├── AntiPattern.java                  # JUMPED_TO_CODE | NO_EDGE_CASES | ASKED_FOR_ANSWER | NO_CLARIFICATION
    │   ├── AntiPatternEvent.java
    │   ├── ConfidenceLevel.java
    │   ├── EngagementLevel.java
    │   └── FrustrationLevel.java
    ├── repository/
    │   ├── InterviewContextRepository.java   # Redis — key: "interview:context:{sessionId}", TTL 24h
    │   ├── SessionRepository.java            # JpaRepository<Session, UUID>
    │   └── MessageRepository.java            # JpaRepository<Message, UUID> + findBySessionIdOrderByCreatedAtAsc
    └── router/
        ├── ModelRouter.java                  # Interface: streamChat(history, systemPrompt) → Flux<String>
        ├── LangChain4jModelRouter.java       # Impl using LangChain4j streaming
        └── ModelRouterFactory.java           # @Configuration — creates StreamingChatLanguageModel + ChatLanguageModel beans
```

---

## Prompt Templates (`server/src/main/resources/prompts/`)
All loaded by `InterviewPromptService` via `ClassPathResource`. Variables substituted as `{{varName}}`.

### Base templates (always loaded)

| File | Purpose |
|------|---------|
| `interviewer-system.txt` | Base interviewer persona; vars: `{{interviewType}}`, `{{roleLevel}}` |
| `interviewer-state-context.txt` | Injected per-turn with live state; vars: phase, scores, hints, etc. |
| `state-analysis.txt` | Prompt for `LLMStateAnalyzer` — expects JSON response with `chosenProblem` field |

### Phase-specific prompts (loaded dynamically per turn)

Organized by interview type. `InterviewPromptService.buildPhasePrompt()` loads `{type}/{phase}.txt` based on the current `InterviewPhase`.

```
prompts/
├── dsa/
│   ├── intro.txt
│   ├── clarification.txt
│   ├── approach.txt
│   ├── deep_dive.txt
│   ├── implementation.txt
│   └── wrap_up.txt
├── hld/
│   └── (same 6 files)
├── lld/
│   └── (same 6 files)
├── culture_fit/
│   └── (same 6 files)
└── resume_grilling/
    └── (same 6 files)
```

`InterviewPromptService.buildInterviewerPrompt()` assembles: `interviewer-system.txt` + phase prompt (`{type}/{phase}.txt`) + resume section (if RESUME_GRILLING) + `interviewer-state-context.txt` + RAG context.

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/sessions` | Create session — body: `{type, role}` |
| POST | `/api/sessions/with-resume` | Create session with PDF — `multipart/form-data`: `type`, `role`, `resume` (FilePart) |
| GET | `/api/sessions` | List all sessions (with saved feedback if present) |
| GET | `/api/sessions/{id}` | Get session + messages |
| POST | `/api/sessions/{id}/messages` | Send message — body: `{content}` — returns `text/event-stream` SSE |
| POST | `/api/sessions/{id}/end` | End interview — returns `FeedbackReport` |

---

## Key Data Flows

### Message Flow
```
1. User speaks → useSpeechToText.ts (Web Speech API) → transcript
2. InterviewSession.tsx calls sendMessage() in client.ts
3. POST /api/sessions/{id}/messages
4. InterviewController: adds user message to Postgres
5. InterviewOrchestrator.processCandidateResponse() — SYNCHRONOUS, runs before RAG/prompt
   → LLMStateAnalyzer updates phase, chosenProblem, scores, candidate state in Redis
6. RAGService.buildContext() — phase-aware retrieval (see RAG section)
7. InterviewPromptService.buildInterviewerPrompt() — loads phase-specific prompt + state context
8. ModelRouter.streamChat() — streams tokens from LLM
9. SSE tokens → client accumulates → MessageList renders
10. useTextToSpeech.ts speaks the response
```

### State Update (synchronous, blocks before response)
```
processCandidateResponse()
  → LLMStateAnalyzer.analyze() — calls ChatLanguageModel (non-streaming) with state-analysis.txt prompt
  → Returns StateAnalysis record:
      interviewerState, confidence, engagement, frustration,
      scoreDeltas, shouldTransitionPhase, suggestedPhase,
      chosenProblem (extracted when interviewer introduces a problem)
  → Updates InterviewContext fields including chosenProblem (set once, never overwritten)
  → contextRepository.save() → Redis
```

### Resume Upload Flow
```
POST /api/sessions/with-resume (multipart/form-data)
  → @RequestPart("resume") FilePart  ← must be FilePart, NOT MultipartFile (WebFlux!)
  → ResumeParserService.extractText(FilePart) → Mono<String>
    → DataBufferUtils.join() → PDFBox → text
  → resumeText stored in: Session.resumeText (Postgres) + InterviewContext.resumeText (Redis)
  → Resume injected into prompt via buildResumeSection() in InterviewPromptService
```

---

## Storage Architecture

| Data | Where | Notes |
|------|-------|-------|
| `Session` (id, type, role, status) | Postgres `sessions` table | JPA entity |
| `Message` (sessionId, role, content) | Postgres `messages` table | JPA entity |
| `InterviewContext` (full state) | Redis `interview:context:{uuid}` | 24h TTL, JSON via Jackson |
| Resume text | Redis (in InterviewContext) + Postgres (in Session.resumeText) | NOT a separate table |
| RAG chunks + embeddings | Redis Vector Search `rag:chunk:*` | Persistent, re-indexed on content change |
| RAG index metadata | Redis `rag:index:status`, `rag:file:hash:*`, `rag:index:pending` | Incremental tracking + resume support |

---

## Environment Variables (via `application.properties` → env)

| Var | Purpose |
|-----|---------|
| `POSTGRES_URL` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Postgres connection |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis connection |
| `AI_PROVIDER` | `anthropic` \| `openai` \| `sarvam` |
| `AI_MODEL` | Model name (e.g. `claude-opus-4-5`) |
| `AI_API_KEY` | API key for the provider |
| `AI_BASE_URL` | Optional custom base URL (for OpenAI-compatible APIs) |
| `APP_DEV_MODE` | `true`/`false` — enables dev shortcuts |
| `RAG_ENABLED` | `true`/`false` — enables RAG (default: true) |
| `RAG_GUIDE_PATH` | Path to interview-guide (default: `interview-guide`) |
| `OLLAMA_BASE_URL` | Ollama URL for local embeddings (default: `http://localhost:11434`) |
| `VOYAGE_API_KEY` | Voyage AI key — required when `AI_PROVIDER=anthropic` |

---

## RAG (Retrieval Augmented Generation)

RAG retrieves relevant content from the `interview-guide` submodule to ground LLM responses in curated interview material.

### Embedding Model Mapping

| `AI_PROVIDER` | Embedding Model | Service | Cost |
|---------------|-----------------|---------|------|
| `anthropic` | voyage-3 | Voyage AI API | $0.06/1M tokens |
| `openai` | text-embedding-3-small | OpenAI API | $0.02/1M tokens |
| `sarvam` | nomic-embed-text | Ollama (Docker) | Free |

### Phase-Aware Retrieval Strategy

`RAGService.buildContext()` selects a retrieval strategy based on the current `InterviewPhase` and `chosenProblem`:

| Phase | Strategy |
|-------|----------|
| `INTRO` | Fetch the full `problem-catalog` chunk (`LLD Systems.md` / `HLD Systems.md` / `DSA Practice Sheet`) — gives LLM the complete list to pick from |
| `CLARIFICATION` | `searchByTitle(chosenProblem)` — fetches all chunks of the chosen system's example doc |
| `APPROACH` / `IMPLEMENTATION` | `searchByTitle(chosenProblem)` (3 chunks) + enriched vector search (`chosenProblem + userMessage`) |
| `DEEP_DIVE` | Enriched vector search with **no category filter** — surfaces design patterns, HLD concepts, etc. naturally |
| `CULTURE_FIT` / `RESUME_GRILLING` | Plain vector search, no type filter |

### Document Categories (stored in Redis as `category` tag field)

| Category | Source | Used for |
|----------|--------|---------|
| `problem` | `dsa/DSA Practice Sheet.md` rows | DSA problem retrieval |
| `problem-catalog` | `lld/LLD Systems.md`, `hld/HLD Systems.md` | INTRO phase — full system list |
| `example-system` | `lld/example-systems/`, `hld/example-systems/` | Chosen problem deep-dive |
| `concept` | `hld/concepts/`, `lld/*.md` root files | HLD/LLD concept retrieval |
| `design-pattern` | `lld/design-patterns/` | LLD design pattern retrieval |

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    STARTUP (Background)                         │
├─────────────────────────────────────────────────────────────────┤
│  1. Enumerate interview-guide markdown files                    │
│  2. Compute SHA-256 per-file hashes                             │
│  3. Compare with stored Redis file hashes                        │
│     ├─ unchanged files → skip                                   │
│     ├─ changed/new files → re-parse + re-embed + upsert chunks │
│     └─ deleted files → remove chunks + file hash metadata       │
│  4. Persist pending file set during run (`rag:index:pending`)   │
│  5. Resume from pending set after interruption, then mark       │
│     `rag:index:status=complete`                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    PER-MESSAGE FLOW                             │
├─────────────────────────────────────────────────────────────────┤
│  1. RAGService.buildContext(userMessage, type, context)         │
│  2. Select strategy based on InterviewPhase + chosenProblem     │
│  3. INTRO → getCatalogChunks(typeFilter)                        │
│     CLARIFICATION → searchByTitle(chosenProblem, typeFilter)    │
│     APPROACH/IMPL → searchByTitle + enriched vector search      │
│     DEEP_DIVE → enriched vector search, no category filter      │
│  4. Return formatted context string                             │
│  5. Inject into prompt via InterviewPromptService               │
└─────────────────────────────────────────────────────────────────┘
```

### Key Components

| File | Purpose |
|------|---------|
| `EmbeddingConfig.java` | Creates embedding model bean based on `AI_PROVIDER` |
| `InterviewGuideParser.java` | Parses markdown files into `DocumentChunk` records; catalog files split into ≤4000 char chunks |
| `InterviewGuideIndexer.java` | Manages indexing lifecycle, version hash check; skips chunks exceeding embedding context limit |
| `RedisVectorStore.java` | Redis Vector Search operations; includes `searchByTitle()` and `getCatalogChunks()` |
| `RAGService.java` | Phase-aware retrieval; builds enriched queries using `chosenProblem + userMessage` |

### Interview Guide Structure

```
interview-guide/
├── dsa/
│   └── DSA Practice Sheet.md       # Problems in table format (parsed row-by-row as `problem` chunks)
├── hld/
│   ├── HLD Systems.md              # System catalog → `problem-catalog` chunks
│   ├── HLD Concepts.md             # Overview → `concept` chunk
│   ├── concepts/                   # 87 concept files → `concept` chunks (split by H2)
│   └── example-systems/            # 35+ system design examples → `example-system` chunks (split by H2)
└── lld/
    ├── LLD Systems.md              # System catalog → `problem-catalog` chunks
    ├── *.md (root)                 # SOLID, OOP, etc. → `concept` chunks
    ├── design-patterns/            # 12 design pattern files → `design-pattern` chunks
    └── example-systems/            # 24 LLD examples → `example-system` chunks (split by H2)
```

### Chunk Size Limits
- All chunks capped at **1500 tokens (~6000 chars)** to stay safely under `nomic-embed-text`'s 8192 token context limit
- Catalog files split into ≤4000 char pieces (by table rows) if they exceed 6000 chars
- Indexer skips chunks that still exceed the limit and logs a warning — does not fail the whole file

### Redis Keys

| Key | Purpose |
|-----|---------|
| `rag:index:status` | `indexing` \| `complete` \| `failed` |
| `rag:index:pending` | Set of file paths still pending (resume checkpoint) |
| `rag:file:hash:{path}` | SHA-256 hash per indexed markdown file |
| `rag:chunk:{id}` | Individual chunk data (HASH with vector) |
| `rag_vectors` | RediSearch vector index name |

### Graceful Degradation

RAG is designed to fail gracefully:
- If indexing is not complete → returns empty context
- If embedding generation fails → returns empty context
- If vector search fails → returns empty context
- LLM continues to work normally, just without RAG context

---

## Critical WebFlux Rules
- **Never use `MultipartFile`** — use `FilePart` for file uploads. `MultipartFile` is Spring MVC only.
- **File upload endpoints return `Mono<T>`**, not plain `T`.
- **`ResumeParserService.extractText()`** returns `Mono<String>`, chain with `.map()` / `.flatMap()`.
- `DataBufferUtils.join(filePart.content())` to collect bytes, then release the buffer after reading.

---

## LLM Integration
- `ModelRouterFactory` creates two beans: `StreamingChatLanguageModel` (for response streaming) and `ChatLanguageModel` (for state analysis).
- Provider switching is purely config-driven (`ai.provider`). Supports `anthropic`, `openai`, `sarvam` (OpenAI-compatible).
- `LangChain4jModelRouter` wraps `StreamingChatLanguageModel` and converts to `Flux<String>`.
- `LLMStateAnalyzer` uses `ChatLanguageModel` (blocking) and parses JSON manually from the response.
- `<think>` tags from reasoning models are stripped in `InterviewController` before SSE and before saving to Postgres.

---

## Interview State Machine

### `InterviewPhase` transitions
```
INTRO → CLARIFICATION → APPROACH → IMPLEMENTATION → DEEP_DIVE → WRAP_UP
```
Transitions are detected by `LLMStateAnalyzer` each turn (`shouldTransitionPhase` + `suggestedPhase` in JSON output).

### `chosenProblem` lifecycle
- Set once when `LLMStateAnalyzer` detects the interviewer introduced a problem (`chosenProblem` field in JSON)
- Stored in `InterviewContext.chosenProblem` (Redis)
- Never overwritten once set
- Used by `RAGService` to fetch the right example system doc for all subsequent turns

### State analysis runs synchronously
`InterviewController` calls `orchestrator.processCandidateResponse()` (not async) before building RAG context and system prompt. This ensures phase transitions and `chosenProblem` are reflected in the same turn they occur.

---

## Scoring
- `EvaluationScores`: 5 dimensions — `problemUnderstanding`, `approach`, `correctness`, `communication`, `optimization` — all clamped [0, 100], start at 50.
- `totalScore()` = simple average of all 5.
- Scores updated via `ScoreDeltas` from `LLMStateAnalyzer` each turn.
- `Verdict`: `HIRE` (≥70), `BORDERLINE` (≥50), `NO_HIRE` (<50).

---

## Database Migrations (Flyway)

Schema is managed by **Flyway**. `ddl-auto=validate` — Hibernate only validates, never modifies the schema.

Migration files live in `server/src/main/resources/db/migration/` and follow the naming convention `V{n}__{description}.sql`.

**When you add a new enum value or change any column/table:**
1. Create a new migration file, e.g. `V2__add_new_interview_type.sql`
2. Write the ALTER TABLE SQL explicitly — do NOT rely on Hibernate to do it

Example — adding a new `InterviewType` value:
```sql
-- V2__add_new_interview_type.sql
ALTER TABLE sessions DROP CONSTRAINT sessions_type_check;
ALTER TABLE sessions ADD CONSTRAINT sessions_type_check
  CHECK (type IN ('DSA', 'HLD', 'LLD', 'RESUME_GRILLING', 'CULTURE_FIT', 'NEW_TYPE'));
```

`baseline-on-migrate=true` means the first time Flyway runs against an existing DB it marks V1 as already applied without re-running it.

## Running Locally
```bash
# Docker (recommended)
docker-compose --profile dev up

# Frontend only
cd client && npm install && npm run dev

# Backend only
cd server && ./mvnw spring-boot:run
```
