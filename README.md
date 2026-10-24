# LEO AI

LEO AI is a calm, thoughtful AI chat companion built with React, TypeScript, and Vite.

## Features

- **Welcome Screen (`/`)**: Time-of-day greeting, 5 starter prompt chips, and bottom message input that creates a new conversation session.
- **Chat Screen (`/chat/:sessionId`)**: Multi-turn conversation interface with optimistic user message rendering, animated typing indicator, persistent session messages, and server-side `/api/sessions/:id/chat` integration.
- **History Screen (`/history`)**: Conversation list grouped by `TODAY`, `YESTERDAY`, and `EARLIER`, with one-tap resume and delete actions.
- **Settings Screen (`/settings`)**: Brand overview, about card, and "Clear all conversations" action.

## Development

```bash
npm install
npm run dev
```

Runs on `http://0.0.0.0:3000`.
