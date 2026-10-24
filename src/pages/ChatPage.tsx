import React, { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ArrowUp, ChevronLeft, Clock } from "lucide-react";
import { ChatMessage, leoApi } from "@/api/leo";
import { AI_AVATAR, colors, fonts, radius, spacing } from "@/theme";

function TypingDots() {
  return (
    <div
      data-testid="typing-indicator"
      style={{
        display: "flex",
        flexDirection: "row",
        gap: 6,
        padding: `${spacing(1.5)}px ${spacing(2)}px`,
        marginLeft: spacing(4.5),
        alignSelf: "flex-start",
      }}
    >
      <span
        style={{
          width: 7,
          height: 7,
          borderRadius: 4,
          backgroundColor: colors.textMuted,
          opacity: 0.4,
        }}
      />
      <span
        style={{
          width: 7,
          height: 7,
          borderRadius: 4,
          backgroundColor: colors.textMuted,
          opacity: 0.7,
        }}
      />
      <span
        style={{
          width: 7,
          height: 7,
          borderRadius: 4,
          backgroundColor: colors.textMuted,
          opacity: 1,
        }}
      />
    </div>
  );
}

export default function ChatPage() {
  const { sessionId = "" } = useParams<{ sessionId: string }>();
  const [searchParams] = useSearchParams();
  const initialPrompt = searchParams.get("initialPrompt") || undefined;
  const navigate = useNavigate();

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const sentInitial = useRef(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  const scrollToEnd = useCallback(() => {
    requestAnimationFrame(() => {
      bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    });
  }, []);

  const send = useCallback(
    async (text: string) => {
      const content = text.trim();
      if (!content || sending || !sessionId) return;
      setInput("");
      setSending(true);

      const optimistic: ChatMessage = {
        id: `local-${Date.now()}`,
        session_id: sessionId,
        role: "user",
        content,
        created_at: new Date().toISOString(),
      };
      setMessages((m) => [...m, optimistic]);
      scrollToEnd();

      try {
        const res = await leoApi.sendMessage(sessionId, content);
        setMessages((m) => {
          const withoutOptimistic = m.filter((x) => x.id !== optimistic.id);
          return [...withoutOptimistic, res.user_message, res.assistant_message];
        });
      } catch {
        setMessages((m) => [
          ...m,
          {
            id: `err-${Date.now()}`,
            session_id: sessionId,
            role: "assistant",
            content:
              "I couldn't reach my thoughts just now. Please try that again in a moment.",
            created_at: new Date().toISOString(),
          },
        ]);
      } finally {
        setSending(false);
        scrollToEnd();
      }
    },
    [sessionId, sending, scrollToEnd],
  );

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const msgs = await leoApi.getMessages(sessionId);
        if (active) {
          setMessages(msgs);
        }
      } catch (e) {
        console.warn("getMessages failed", e);
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [sessionId]);

  useEffect(() => {
    if (!loading && initialPrompt && !sentInitial.current) {
      sentInitial.current = true;
      send(initialPrompt);
    }
  }, [loading, initialPrompt, send]);

  return (
    <div
      data-testid="chat-screen"
      style={{
        display: "flex",
        flexDirection: "column",
        minHeight: "100dvh",
        backgroundColor: colors.bg,
        fontFamily: fonts.sans,
      }}
    >
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          flex: 1,
          width: "100%",
          maxWidth: 680,
          margin: "0 auto",
        }}
      >
        {/* Header */}
        <header
          style={{
            display: "flex",
            flexDirection: "row",
            alignItems: "center",
            justifyContent: "space-between",
            padding: `${spacing(1)}px ${spacing(1.5)}px`,
            borderBottom: `1px solid ${colors.border}`,
            backgroundColor: colors.bg,
          }}
        >
          <button
            type="button"
            data-testid="chat-back-button"
            onClick={() => navigate(-1)}
            aria-label="Back"
            style={{
              width: 44,
              height: 44,
              borderRadius: 22,
              border: "none",
              background: "transparent",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              cursor: "pointer",
              color: colors.textPrimary,
            }}
          >
            <ChevronLeft size={24} />
          </button>

          <div
            style={{
              display: "flex",
              flexDirection: "row",
              alignItems: "center",
              gap: spacing(1.25),
              flex: 1,
              justifyContent: "center",
            }}
          >
            <img
              src={AI_AVATAR}
              alt="LEO"
              style={{
                width: 36,
                height: 36,
                borderRadius: 18,
                objectFit: "cover",
              }}
            />
            <div>
              <div
                style={{
                  fontFamily: fonts.serif,
                  fontSize: 18,
                  color: colors.textPrimary,
                  letterSpacing: 1,
                }}
              >
                LEO
              </div>
              <div
                style={{
                  fontSize: 11,
                  color: colors.textMuted,
                  letterSpacing: 0.5,
                }}
              >
                {sending ? "thinking…" : "here when you need"}
              </div>
            </div>
          </div>

          <button
            type="button"
            data-testid="chat-history-button"
            onClick={() => navigate("/history")}
            aria-label="Open history"
            style={{
              width: 44,
              height: 44,
              borderRadius: 22,
              border: "none",
              background: "transparent",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              cursor: "pointer",
              color: colors.textSecondary,
            }}
          >
            <Clock size={22} />
          </button>
        </header>

        {/* Message list */}
        <main
          style={{
            flex: 1,
            overflowY: "auto",
            padding: `${spacing(2)}px`,
            display: "flex",
            flexDirection: "column",
            gap: spacing(2),
          }}
        >
          {loading ? (
            <div
              style={{
                flex: 1,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                color: colors.textMuted,
              }}
            >
              Loading…
            </div>
          ) : (
            <>
              {messages.length === 0 && !sending && (
                <div
                  style={{
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    justifyContent: "center",
                    paddingTop: spacing(8),
                    gap: spacing(2),
                  }}
                >
                  <img
                    src={AI_AVATAR}
                    alt="LEO"
                    style={{
                      width: 72,
                      height: 72,
                      borderRadius: 36,
                      opacity: 0.85,
                      objectFit: "cover",
                    }}
                  />
                  <p
                    style={{
                      margin: 0,
                      fontFamily: fonts.serif,
                      fontSize: 20,
                      color: colors.textSecondary,
                      fontStyle: "italic",
                      textAlign: "center",
                      padding: `0 ${spacing(3)}px`,
                    }}
                  >
                    Say anything. I&apos;ll meet you where you are.
                  </p>
                </div>
              )}

              {messages.map((item) => {
                const isUser = item.role === "user";
                return (
                  <div
                    key={item.id}
                    data-testid={`message-${item.role}`}
                    style={{
                      display: "flex",
                      flexDirection: "row",
                      gap: spacing(1.25),
                      alignItems: "flex-end",
                      justifyContent: isUser ? "flex-end" : "flex-start",
                    }}
                  >
                    {!isUser && (
                      <img
                        src={AI_AVATAR}
                        alt="LEO"
                        style={{
                          width: 28,
                          height: 28,
                          borderRadius: 14,
                          marginBottom: 4,
                          objectFit: "cover",
                          flexShrink: 0,
                        }}
                      />
                    )}
                    <div
                      style={{
                        maxWidth: "82%",
                        padding: `${spacing(1.5)}px ${spacing(2)}px`,
                        backgroundColor: isUser ? colors.primary : colors.surface,
                        color: isUser ? colors.primaryFg : colors.textPrimary,
                        border: isUser ? "none" : `1px solid ${colors.border}`,
                        borderRadius: radius.lg,
                        borderTopRightRadius: isUser ? 4 : radius.lg,
                        borderTopLeftRadius: isUser ? radius.lg : 4,
                        fontSize: 15,
                        lineHeight: "22px",
                        whiteSpace: "pre-wrap",
                      }}
                    >
                      {item.content}
                    </div>
                  </div>
                );
              })}

              {sending && <TypingDots />}
              <div ref={bottomRef} />
            </>
          )}
        </main>

        {/* Input bar */}
        <footer
          data-testid="chat-input-bar"
          style={{
            display: "flex",
            flexDirection: "row",
            alignItems: "flex-end",
            gap: spacing(1.25),
            padding: `${spacing(1.25)}px ${spacing(2)}px`,
            borderTop: `1px solid ${colors.border}`,
            backgroundColor: colors.bg,
          }}
        >
          <textarea
            data-testid="chat-input-field"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Message LEO…"
            disabled={sending}
            rows={1}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                send(input);
              }
            }}
            style={{
              flex: 1,
              backgroundColor: colors.surface,
              border: `1px solid ${colors.border}`,
              borderRadius: radius.lg,
              padding: `${spacing(1.5)}px ${spacing(2)}px`,
              fontSize: 16,
              lineHeight: "22px",
              color: colors.textPrimary,
              fontFamily: fonts.sans,
              minHeight: 48,
              maxHeight: 140,
              resize: "none",
              outline: "none",
              boxSizing: "border-box",
            }}
          />
          <button
            type="button"
            data-testid="send-message-button"
            disabled={!input.trim() || sending}
            onClick={() => send(input)}
            aria-label="Send message"
            style={{
              width: 48,
              height: 48,
              borderRadius: 24,
              border: "none",
              backgroundColor: colors.primary,
              color: colors.primaryFg,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              cursor: !input.trim() || sending ? "default" : "pointer",
              opacity: !input.trim() || sending ? 0.5 : 1,
              flexShrink: 0,
            }}
          >
            <ArrowUp size={22} />
          </button>
        </footer>
      </div>
    </div>
  );
}
