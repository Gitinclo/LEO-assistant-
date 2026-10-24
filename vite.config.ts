import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";
import crypto from "crypto";
import { GoogleGenAI } from "@google/genai";
import type { IncomingMessage, ServerResponse } from "http";

interface ChatSession {
  id: string;
  title: string;
  created_at: string;
  updated_at: string;
  preview: string;
}

interface ChatMessage {
  id: string;
  session_id: string;
  role: "user" | "assistant";
  content: string;
  created_at: string;
}

const SYSTEM_PROMPT =
  "You are LEO, a wise, calm, and thoughtful AI companion. " +
  "Speak clearly and warmly, like a trusted friend who also happens to be deeply knowledgeable. " +
  "Keep answers focused and well-structured. Use markdown sparingly: short paragraphs, " +
  "occasional bullet lists, and code blocks when sharing code. Avoid filler and corporate jargon.";

// In-memory store replacing MongoDB (per AI Studio Web Migration guidelines)
const sessionsStore = new Map<string, ChatSession>();
const messagesStore = new Map<string, ChatMessage[]>();

function utcnowIso(): string {
  return new Date().toISOString();
}

function readJsonBody(req: IncomingMessage): Promise<Record<string, unknown>> {
  return new Promise((resolve) => {
    let data = "";
    req.on("data", (chunk) => {
      data += chunk;
    });
    req.on("end", () => {
      try {
        resolve(data ? JSON.parse(data) : {});
      } catch {
        resolve({});
      }
    });
    req.on("error", () => resolve({}));
  });
}

function sendJson(res: ServerResponse, status: number, payload: unknown) {
  res.statusCode = status;
  res.setHeader("Content-Type", "application/json");
  res.end(JSON.stringify(payload));
}

function craftLocalLeoReply(content: string, history: ChatMessage[]): string {
  const clean = content.trim();
  const lower = clean.toLowerCase();

  if (lower.includes("calm reply") || (lower.includes("difficult") && lower.includes("email"))) {
    return [
      "When an email feels tense, the most helpful thing we can do is slow down the tempo of the conversation. You don't need to match the other person's urgency.",
      "",
      "Here is a steady, grounded template you can adapt:",
      "",
      "> \"Thank you for sharing this directly. I want to make sure I give this the thought it deserves. Here is what I can confirm right now: [insert 1–2 clear facts]. Let's focus on [next constructive step] so we can move this forward cleanly.\"",
      "",
      "If you'd like, paste the message you received (or the gist of what happened), and we can shape a version that sounds naturally like you.",
    ].join("\n");
  }

  if (lower.includes("curious, not stupid") || lower.includes("explain a concept")) {
    return [
      "That is my favorite way to explore an idea—keeping all the nuance, just leaving out the unnecessary jargon.",
      "",
      "Pick any subject you have been wondering about lately, whether it's:",
      "• How large language models actually predict the next word",
      "• Why treasury bond yields move in the opposite direction of prices",
      "• How zero-knowledge proofs let you prove a secret without revealing it",
      "• Why sourdough fermentation changes how bread digests",
      "",
      "Which concept is on your mind today?",
    ].join("\n");
  }

  if (lower.includes("morning routine") || (lower.includes("plan") && lower.includes("morning"))) {
    return [
      "A good morning routine shouldn't feel like a checklist you can fail before 9 a.m. It works best when it protects your attention before the outside world asks for it.",
      "",
      "Here is a quiet, three-part rhythm:",
      "",
      "• **First 15 minutes (No inputs):** Water, natural light, and leaving your phone in another room. Let your mind finish waking up on its own terms.",
      "• **Next 20 minutes (Grounding):** A short walk, light movement, or writing down three lines about what actually matters today.",
      "• **First 60 minutes of work (One anchor task):** Open only the single document or tool needed for your most meaningful task before checking messages.",
      "",
      "How much time do you realistically have between waking up and your first commitment? We can tailor this around your actual mornings.",
    ].join("\n");
  }

  if (lower.includes("brainstorm names") || lower.includes("side project")) {
    return [
      "Naming is all about the feeling you want someone to have in the first three seconds. I usually group names into three directions:",
      "",
      "• **Tactile & Organic:** *Cairn, Fieldnote, Hearth, Linen, Sundial, Moss* — warm, grounded, human-scale.",
      "• **Clear & Utility-First:** *Draftline, Quietly, Daymark, Ledger, Pinpoint* — tells you what it does without shouting.",
      "• **Evocative & Short:** *Kestrel, Vesper, Oura, Luma, Solis* — memorable single words with room to grow.",
      "",
      "Tell me in one sentence what your side project does—and whether you want it to feel playful, editorial, or technical—and I'll give you ten tailored options.",
    ].join("\n");
  }

  if (lower.includes("summarize this idea") || lower.includes("single sentence")) {
    return [
      "I'm ready. Share the rough notes, paragraph, or half-formed thought—no need to polish it first.",
      "",
      "I will give you three one-sentence versions:",
      "• **The Plainspoken Version:** Clear enough for anyone to grasp immediately.",
      "• **The Sharp Pitch:** Focused on why it matters and who it helps.",
      "• **The Essential Core:** Stripped down to under twelve words.",
      "",
      "Whenever you're ready, paste it in.",
    ].join("\n");
  }

  const lastAssistant = [...history].reverse().find((m) => m.role === "assistant")?.content ?? "";
  if (history.length > 0 && lastAssistant) {
    if (lastAssistant.toLowerCase().includes("paste the message") || lower.includes("email")) {
      return [
        "Here is a calm, measured draft based on what you shared:",
        "",
        `> "Hi—thanks for flagging this. I've looked over the details around ${clean.slice(0, 60)}. To keep things moving smoothly, I suggest we focus on the immediate priority first and revisit the broader timeline once that's in place. Let me know if that works on your end."`,
        "",
        "Notice how it acknowledges their point without taking on any defensive tone. Would you like to make it warmer, firmer, or shorter?",
      ].join("\n");
    }

    if (lastAssistant.toLowerCase().includes("one-sentence versions")) {
      const core = clean.replace(/\.$/, "").slice(0, 90);
      return [
        "Here are three ways to distill that into a single clear sentence:",
        "",
        `• **Plainspoken:** A simpler way to approach ${core} by removing friction and focusing on what actually matters.`,
        `• **Sharp Pitch:** We help people turn ${core} into a calm, repeatable daily habit without the usual noise.`,
        `• **Essential Core:** Clarity and momentum for ${core}, minus the clutter.`,
        "",
        "Which of those feels closest to what you meant?",
      ].join("\n");
    }
  }

  return [
    `Let's look at **${clean.slice(0, 72)}** calmly and break it into parts we can actually work with.`,
    "",
    "• **The core question:** Underneath the surface details, the real leverage point here is separating what requires a decision today from what simply needs observation.",
    "• **A grounded next step:** Start with the smallest concrete version you can test or write down in ten minutes. Clarity almost always comes from motion rather than trying to solve the whole picture in your head.",
    "",
    "What part of this feels like the biggest knot right now? We can unpack that piece first.",
  ].join("\n");
}

async function generateAssistantReply(content: string, history: ChatMessage[]): Promise<string> {
  const geminiKey = process.env.GEMINI_API_KEY?.trim();
  if (geminiKey) {
    try {
      const ai = new GoogleGenAI({ apiKey: geminiKey });
      let systemInstruction = SYSTEM_PROMPT;
      if (history.length > 0) {
        const transcript = history
          .map((m) => `${m.role === "user" ? "User" : "LEO"}: ${m.content}`)
          .join("\n");
        systemInstruction =
          `${SYSTEM_PROMPT}\n\n` +
          `Prior conversation (for your context only, do not repeat it back verbatim):\n${transcript}`;
      }
      const response = await ai.models.generateContent({
        model: "gemini-2.5-flash",
        contents: content,
        config: {
          systemInstruction,
        },
      });
      if (response.text && response.text.trim()) {
        return response.text.trim();
      }
    } catch (err) {
      console.warn("[LEO AI] Gemini API call failed, using local companion fallback:", err);
    }
  }

  // Brief natural pause to let typing dots breathe
  await new Promise((r) => setTimeout(r, 450));
  return craftLocalLeoReply(content, history);
}

function leoApiPlugin(): Plugin {
  const attachMiddleware = (middlewares: {
    use: (fn: (req: IncomingMessage, res: ServerResponse, next: () => void) => void) => void;
  }) => {
    middlewares.use(async (req, res, next) => {
      const urlObj = new URL(req.url || "/", "http://localhost:3000");
      const pathname = urlObj.pathname;
      const method = req.method || "GET";

      if (!pathname.startsWith("/api")) {
        return next();
      }

      // GET /api/ or /api
      if ((pathname === "/api" || pathname === "/api/") && method === "GET") {
        return sendJson(res, 200, { message: "LEO AI is awake.", ok: true });
      }

      // POST /api/sessions
      if (pathname === "/api/sessions" && method === "POST") {
        const body = await readJsonBody(req);
        const now = utcnowIso();
        const title =
          typeof body.title === "string" && body.title.trim()
            ? body.title.trim()
            : "New conversation";
        const session: ChatSession = {
          id: crypto.randomUUID(),
          title,
          created_at: now,
          updated_at: now,
          preview: "",
        };
        sessionsStore.set(session.id, session);
        messagesStore.set(session.id, []);
        return sendJson(res, 200, session);
      }

      // GET /api/sessions
      if (pathname === "/api/sessions" && method === "GET") {
        const list = Array.from(sessionsStore.values()).sort((a, b) =>
          b.updated_at.localeCompare(a.updated_at),
        );
        return sendJson(res, 200, list);
      }

      // DELETE /api/sessions/:sessionId
      const deleteMatch = pathname.match(/^\/api\/sessions\/([^/]+)$/);
      if (deleteMatch && method === "DELETE") {
        const sessionId = decodeURIComponent(deleteMatch[1]);
        sessionsStore.delete(sessionId);
        messagesStore.delete(sessionId);
        return sendJson(res, 200, { ok: true });
      }

      // GET /api/sessions/:sessionId/messages
      const messagesMatch = pathname.match(/^\/api\/sessions\/([^/]+)\/messages$/);
      if (messagesMatch && method === "GET") {
        const sessionId = decodeURIComponent(messagesMatch[1]);
        const msgs = messagesStore.get(sessionId) ?? [];
        return sendJson(res, 200, msgs);
      }

      // POST /api/sessions/:sessionId/chat
      const chatMatch = pathname.match(/^\/api\/sessions\/([^/]+)\/chat$/);
      if (chatMatch && method === "POST") {
        const sessionId = decodeURIComponent(chatMatch[1]);
        let session = sessionsStore.get(sessionId);
        if (!session) {
          const now = utcnowIso();
          session = {
            id: sessionId,
            title: "New conversation",
            created_at: now,
            updated_at: now,
            preview: "",
          };
          sessionsStore.set(sessionId, session);
        }

        const body = await readJsonBody(req);
        const content = typeof body.content === "string" ? body.content.trim() : "";
        if (!content) {
          return sendJson(res, 400, { detail: "Message content is empty" });
        }

        const priorHistory = [...(messagesStore.get(sessionId) ?? [])];
        const userMessage: ChatMessage = {
          id: crypto.randomUUID(),
          session_id: sessionId,
          role: "user",
          content,
          created_at: utcnowIso(),
        };

        const assistantText = await generateAssistantReply(content, priorHistory);
        const assistantMessage: ChatMessage = {
          id: crypto.randomUUID(),
          session_id: sessionId,
          role: "assistant",
          content: assistantText,
          created_at: utcnowIso(),
        };

        const updatedMessages = [...priorHistory, userMessage, assistantMessage];
        messagesStore.set(sessionId, updatedMessages);

        let newTitle = session.title || "New conversation";
        if (newTitle === "New conversation") {
          newTitle = content.slice(0, 48) + (content.length > 48 ? "…" : "");
        }
        const preview = assistantText.slice(0, 120).replace(/\n/g, " ");
        const updatedSession: ChatSession = {
          ...session,
          title: newTitle,
          updated_at: utcnowIso(),
          preview,
        };
        sessionsStore.set(sessionId, updatedSession);

        return sendJson(res, 200, {
          user_message: userMessage,
          assistant_message: assistantMessage,
          session: updatedSession,
        });
      }

      return sendJson(res, 404, { detail: "Not found" });
    });
  };

  return {
    name: "leo-api-middleware",
    configureServer(server) {
      attachMiddleware(server.middlewares);
    },
    configurePreviewServer(server) {
      attachMiddleware(server.middlewares);
    },
  };
}

export default defineConfig({
  plugins: [react(), leoApiPlugin()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    host: "0.0.0.0",
    port: 3000,
    allowedHosts: true,
  },
  preview: {
    host: "0.0.0.0",
    port: 3000,
  },
});
