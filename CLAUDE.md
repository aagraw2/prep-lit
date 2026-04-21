# PrepLit - AI Mock Interview Platform

## Project Overview
PrepLit is a real-time AI-powered mock interview platform. It uses voice input, streams LLM responses via SSE, and speaks them back via TTS. The backend is **Spring WebFlux (reactive/Netty)** — NOT Spring MVC. This matters for file uploads, return types, etc.

## Tech Stack
- **Frontend**: React + TypeScript + Vite (port 5173)
- **Backend**: Java Spring Boot + WebFlux (port 8080)
- **LLM**: Configurable via `ai.provider` — supports `anthropic`, `openai`, `sarvam`
- **LLM SDK**: LangChain4j (`dev.langchain4j`) — NOT the raw Anthropic SDK
- **PDF Parsing**: Apache PDFBox
- **Session/Message storage**: PostgreSQL (via Spring Data JPA)
- **Interview state storage**: Redis (24h TTL) — `InterviewContext` lives here, NOT in Postgres
- **Containerization**: Docker Compose

## Interview Types (enum `InterviewType`)
- `DSA` — Data Structures & Algorithms
- `HLD` — High Level System Design
- `LLD` — Low Level Design
- `RESUME_GRILLING` — Resume Deep Dive (requires PDF upload; resume text stored in Redis only)
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
    │   └── RedisConfig.java                  # RedisTemplate with Jackson JSON serializer
    ├── controller/
    │   ├── InterviewController.java          # All REST endpoints + SSE streaming
    │   └── GlobalExceptionHandler.java       # @RestControllerAdvice error handling
    ├── service/
    │   ├── InterviewOrchestrator.java        # Coordinates interview flow, saves to Redis
    │   ├── InterviewPromptService.java       # Builds prompts from .txt templates + context
    │   ├── LLMStateAnalyzer.java             # Calls LLM to analyze candidate state → StateAnalysis record
    │   ├── FeedbackGenerator.java            # Generates FeedbackReport from InterviewContext
    │   ├── ResumeParserService.java          # PDF → text via PDFBox; takes FilePart (reactive)
    │   ├── SessionService.java               # CRUD for Session + Message (Postgres)
    │   ├── RAGService.java                   # Stub — always returns empty
    │   └── SessionNotFoundException.java
    ├── model/
    │   ├── InterviewContext.java             # Full session state — stored in Redis
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

| File | Purpose |
|------|---------|
| `interviewer-system.txt` | Base interviewer persona; vars: `{{interviewType}}`, `{{roleLevel}}` |
| `interviewer-flow-dsa.txt` | DSA-specific flow instructions |
| `interviewer-flow-hld.txt` | HLD-specific flow instructions |
| `interviewer-flow-lld.txt` | LLD-specific flow instructions |
| `interviewer-flow-resume_grilling.txt` | Resume deep dive flow |
| `interviewer-flow-culture_fit.txt` | Culture fit / STAR flow |
| `interviewer-state-context.txt` | Injected per-turn with live state; vars: phase, scores, hints, etc. |
| `state-analysis.txt` | Prompt for `LLMStateAnalyzer` — expects JSON response |

`InterviewPromptService.buildInterviewerPrompt()` assembles: system + flow + resume section (if RESUME_GRILLING) + state context + RAG (always empty).

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/sessions` | Create session — body: `{type, role}` |
| POST | `/api/sessions/with-resume` | Create session with PDF — `multipart/form-data`: `type`, `role`, `resume` (FilePart) |
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
5. InterviewOrchestrator.processCandidateResponseAsync() — fires async, doesn't block stream
6. InterviewPromptService.buildInterviewerPrompt() — builds full system prompt from templates + Redis state
7. ModelRouter.streamChat() — streams tokens from LLM
8. SSE tokens → client accumulates → MessageList renders
9. useTextToSpeech.ts speaks the response
```

### State Update (async, background)
```
processCandidateResponseAsync()
  → LLMStateAnalyzer.analyze() — calls ChatLanguageModel (non-streaming) with state-analysis.txt prompt
  → Returns StateAnalysis record (interviewerState, confidence, engagement, frustration, scoreDeltas, phase transition, etc.)
  → Updates InterviewContext fields
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

---

## Scoring
- `EvaluationScores`: 5 dimensions — `problemUnderstanding`, `approach`, `correctness`, `communication`, `optimization` — all clamped [0, 100], start at 50.
- `totalScore()` = simple average of all 5.
- Scores updated via `ScoreDeltas` from `LLMStateAnalyzer` each turn.
- `Verdict`: `HIRE` (≥70), `BORDERLINE` (≥50), `NO_HIRE` (<50).

---

## Common Issues & Fixes

### 415 UNSUPPORTED_MEDIA_TYPE on resume upload
- Cause: Using `MultipartFile` in a WebFlux controller.
- Fix: Use `FilePart` + `Mono<ResponseType>` return. `ResumeParserService.extractText()` returns `Mono<String>`.

### LLM response has `<think>` tags
- Location: `InterviewController.java` — `processThinkTags()` and `stripThinkTags()` filter them before SSE.

### Repetitive interviewer responses
- Location: `prompts/interviewer-system.txt` — prompt instructs variety in acknowledgments.

### Redis deserialization issues
- `InterviewContextRepository.findById()` handles both `InterviewContext` and `LinkedHashMap` (Jackson fallback via `objectMapper.convertValue()`).

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
docker-compose up

# Frontend only
cd client && npm install && npm run dev

# Backend only
cd server && ./mvnw spring-boot:run
```
