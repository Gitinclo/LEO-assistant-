// LEO AI API client. All requests go through the /api prefix, with localStorage sync.
export type ChatSession = {
  id: string;
  title: string;
  created_at: string;
  updated_at: string;
  preview: string;
};

export type ChatMessage = {
  id: string;
  session_id: string;
  role: "user" | "assistant";
  content: string;
  created_at: string;
};

export type SendMessageResponse = {
  user_message: ChatMessage;
  assistant_message: ChatMessage;
  session: ChatSession;
};

const STORAGE_SESSIONS_KEY = "leo_ai_sessions_v1";
const STORAGE_MESSAGES_PREFIX = "leo_ai_messages_v1_";

function loadCachedSessions(): ChatSession[] {
  try {
    const raw = localStorage.getItem(STORAGE_SESSIONS_KEY);
    return raw ? (JSON.parse(raw) as ChatSession[]) : [];
  } catch {
    return [];
  }
}

function saveCachedSessions(sessions: ChatSession[]) {
  try {
    localStorage.setItem(STORAGE_SESSIONS_KEY, JSON.stringify(sessions));
  } catch {
    // Ignore storage quota errors
  }
}

function loadCachedMessages(sessionId: string): ChatMessage[] {
  try {
    const raw = localStorage.getItem(`${STORAGE_MESSAGES_PREFIX}${sessionId}`);
    return raw ? (JSON.parse(raw) as ChatMessage[]) : [];
  } catch {
    return [];
  }
}

function saveCachedMessages(sessionId: string, messages: ChatMessage[]) {
  try {
    localStorage.setItem(`${STORAGE_MESSAGES_PREFIX}${sessionId}`, JSON.stringify(messages));
  } catch {
    // Ignore storage quota errors
  }
}

async function http<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api${path}`, {
    headers: { "Content-Type": "application/json" },
    ...init,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`HTTP ${res.status}: ${text || res.statusText}`);
  }
  return (await res.json()) as T;
}

export const leoApi = {
  health: () => http<{ ok: boolean }>("/"),

  createSession: async (title?: string): Promise<ChatSession> => {
    const session = await http<ChatSession>("/sessions", {
      method: "POST",
      body: JSON.stringify({ title: title ?? null }),
    });
    const current = loadCachedSessions().filter((s) => s.id !== session.id);
    saveCachedSessions([session, ...current]);
    saveCachedMessages(session.id, []);
    return session;
  },

  listSessions: async (): Promise<ChatSession[]> => {
    const remote = await http<ChatSession[]>("/sessions");
    const cached = loadCachedSessions();
    const mergedMap = new Map<string, ChatSession>();
    for (const s of cached) mergedMap.set(s.id, s);
    for (const s of remote) mergedMap.set(s.id, s);
    const merged = Array.from(mergedMap.values()).sort((a, b) =>
      b.updated_at.localeCompare(a.updated_at),
    );
    saveCachedSessions(merged);
    return merged;
  },

  deleteSession: async (id: string): Promise<{ ok: boolean }> => {
    const res = await http<{ ok: boolean }>(`/sessions/${id}`, {
      method: "DELETE",
    });
    const updated = loadCachedSessions().filter((s) => s.id !== id);
    saveCachedSessions(updated);
    try {
      localStorage.removeItem(`${STORAGE_MESSAGES_PREFIX}${id}`);
    } catch {
      // Ignore
    }
    return res;
  },

  getMessages: async (id: string): Promise<ChatMessage[]> => {
    const remote = await http<ChatMessage[]>(`/sessions/${id}/messages`);
    if (remote.length > 0) {
      saveCachedMessages(id, remote);
      return remote;
    }
    return loadCachedMessages(id);
  },

  sendMessage: async (id: string, content: string): Promise<SendMessageResponse> => {
    const res = await http<SendMessageResponse>(`/sessions/${id}/chat`, {
      method: "POST",
      body: JSON.stringify({ content }),
    });
    const existingMsgs = loadCachedMessages(id);
    const updatedMsgs = [...existingMsgs, res.user_message, res.assistant_message];
    saveCachedMessages(id, updatedMsgs);

    const existingSessions = loadCachedSessions().filter((s) => s.id !== id);
    saveCachedSessions([res.session, ...existingSessions]);
    return res;
  },
};
