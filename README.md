# PrepLit

An AI-powered mock interview platform that simulates real technical interviews. You talk, it listens, it responds like an actual interviewer would.

## What it does

- Conducts mock interviews for DSA, High Level Design, Low Level Design, Resume Deep Dive, and Culture Fit
- Streams responses in real time via SSE
- Supports voice input and text-to-speech so it feels like a real conversation
- Tracks your performance across the interview and gives detailed feedback at the end
- Uses RAG to ground questions in a curated interview guide so problems are relevant and realistic

## Tech Stack

- **Frontend** - React + TypeScript + Vite
- **Backend** - Java Spring Boot (WebFlux/reactive)
- **LLM** - Configurable: Anthropic, OpenAI, or any OpenAI-compatible API
- **Embeddings** - Voyage AI (Anthropic), text-embedding-3-small (OpenAI), or nomic-embed-text via Ollama (local)
- **Storage** - PostgreSQL for sessions and messages, Redis for interview state and vector search
- **Containerization** - Docker Compose

## Running locally

Make sure you have Docker installed. Copy `.env.example` to `.env` and fill in your API keys, then:

```bash
docker-compose --profile dev up
```

Frontend runs on port 3000, backend on port 8080.

## Interview Types

| Type | What it covers |
|------|---------------|
| DSA | Data structures and algorithms problems with hints and code review |
| HLD | High level system design with architecture walkthrough |
| LLD | Low level design with class modeling and design patterns |
| Resume Deep Dive | Questions based on your actual resume (upload a PDF) |
| Culture Fit | Behavioral questions using STAR format |

## Interview Guide

The `interview-guide` folder is a git submodule with curated notes for DSA, HLD, and LLD. The backend indexes this at startup and uses it to pick problems and fetch relevant content during interviews. See the [interview-guide README](interview-guide/README.md) for what's inside.

---

Still a work in progress. Feedback welcome.
