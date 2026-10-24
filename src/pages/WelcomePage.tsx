import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowUp, Clock, Settings } from "lucide-react";
import { leoApi } from "@/api/leo";
import {
  AI_AVATAR,
  BG_BOTANICAL,
  SUGGESTIONS,
  colors,
  fonts,
  radius,
  spacing,
} from "@/theme";

function greetingForHour(): string {
  const h = new Date().getHours();
  if (h < 5) return "Late night thoughts?";
  if (h < 12) return "Good morning.";
  if (h < 17) return "Good afternoon.";
  if (h < 21) return "Good evening.";
  return "Working late?";
}

export default function WelcomePage() {
  const navigate = useNavigate();
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);

  const startChat = async (prompt: string) => {
    const text = prompt.trim();
    if (!text || busy) return;
    setBusy(true);
    try {
      const session = await leoApi.createSession();
      navigate(
        `/chat/${encodeURIComponent(session.id)}?initialPrompt=${encodeURIComponent(text)}`,
      );
    } catch (e) {
      console.warn("createSession failed", e);
    } finally {
      setBusy(false);
      setInput("");
    }
  };

  return (
    <div
      data-testid="welcome-screen"
      style={{
        position: "relative",
        display: "flex",
        flexDirection: "column",
        minHeight: "100dvh",
        backgroundColor: colors.bg,
        fontFamily: fonts.sans,
        overflow: "hidden",
      }}
    >
      {/* Subtle botanical background illustration (opacity 12% + bgTint 60%) */}
      <img
        src={BG_BOTANICAL}
        alt=""
        aria-hidden="true"
        style={{
          position: "absolute",
          inset: 0,
          width: "100%",
          height: "100%",
          objectFit: "cover",
          opacity: 0.12,
          filter: "blur(2px)",
          pointerEvents: "none",
        }}
      />
      <div
        style={{
          position: "absolute",
          inset: 0,
          backgroundColor: colors.bg,
          opacity: 0.6,
          pointerEvents: "none",
        }}
      />

      {/* Centered responsive shell */}
      <div
        style={{
          position: "relative",
          zIndex: 1,
          display: "flex",
          flexDirection: "column",
          flex: 1,
          width: "100%",
          maxWidth: 680,
          margin: "0 auto",
        }}
      >
        {/* Top bar */}
        <header
          style={{
            display: "flex",
            flexDirection: "row",
            justifyContent: "space-between",
            alignItems: "center",
            padding: `${spacing(1.5)}px ${spacing(3)}px`,
          }}
        >
          <div
            style={{
              display: "flex",
              flexDirection: "row",
              alignItems: "center",
              gap: spacing(1.25),
            }}
          >
            <img
              src={AI_AVATAR}
              alt="LEO avatar"
              style={{
                width: 32,
                height: 32,
                borderRadius: 16,
                objectFit: "cover",
              }}
            />
            <span
              style={{
                fontFamily: fonts.serif,
                fontSize: 22,
                fontWeight: 500,
                color: colors.textPrimary,
                letterSpacing: 1.5,
              }}
            >
              LEO
            </span>
          </div>

          <div style={{ display: "flex", flexDirection: "row", gap: spacing(0.5) }}>
            <button
              type="button"
              data-testid="open-history-button"
              onClick={() => navigate("/history")}
              aria-label="Open conversation history"
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
            <button
              type="button"
              data-testid="open-settings-button"
              onClick={() => navigate("/settings")}
              aria-label="Open settings"
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
              <Settings size={22} />
            </button>
          </div>
        </header>

        {/* Scrollable editorial content */}
        <main
          style={{
            flex: 1,
            overflowY: "auto",
            padding: `${spacing(4)}px ${spacing(3.5)}px ${spacing(3)}px`,
          }}
        >
          <div
            data-testid="welcome-eyebrow"
            style={{
              fontSize: 12,
              letterSpacing: 2.5,
              color: colors.textMuted,
              marginBottom: spacing(1.5),
            }}
          >
            LEO · A QUIETER AI
          </div>

          <h1
            data-testid="welcome-heading"
            style={{
              margin: 0,
              fontFamily: fonts.serif,
              fontSize: 38,
              lineHeight: "44px",
              color: colors.textPrimary,
              fontWeight: 500,
            }}
          >
            {greetingForHour()}
          </h1>

          <p
            style={{
              margin: 0,
              fontFamily: fonts.serif,
              fontSize: 26,
              lineHeight: "34px",
              color: colors.textSecondary,
              fontStyle: "italic",
              marginTop: spacing(0.5),
              marginBottom: spacing(5),
            }}
          >
            How can I help you think today?
          </p>

          <div
            style={{
              fontSize: 11,
              letterSpacing: 2.5,
              color: colors.textMuted,
              marginBottom: spacing(2),
            }}
          >
            TRY A STARTER
          </div>

          <div
            style={{
              display: "flex",
              flexDirection: "column",
              gap: spacing(1.5),
            }}
          >
            {SUGGESTIONS.map((s, i) => (
              <button
                key={s}
                type="button"
                data-testid={`suggestion-chip-${i}`}
                disabled={busy}
                onClick={() => startChat(s)}
                style={{
                  textAlign: "left",
                  backgroundColor: colors.surface,
                  border: `1px solid ${colors.border}`,
                  padding: `${spacing(2)}px ${spacing(2.5)}px`,
                  borderRadius: radius.md,
                  fontSize: 15,
                  lineHeight: "22px",
                  color: colors.textPrimary,
                  fontFamily: fonts.sans,
                  cursor: busy ? "default" : "pointer",
                  transition: "background-color 150ms ease",
                }}
                onMouseDown={(e) => {
                  e.currentTarget.style.backgroundColor = colors.surfaceElevated;
                }}
                onMouseUp={(e) => {
                  e.currentTarget.style.backgroundColor = colors.surface;
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.backgroundColor = colors.surface;
                }}
              >
                {s}
              </button>
            ))}
          </div>
        </main>

        {/* Bottom input bar */}
        <footer
          data-testid="welcome-input-bar"
          style={{
            display: "flex",
            flexDirection: "row",
            alignItems: "flex-end",
            gap: spacing(1.25),
            padding: `${spacing(1.25)}px ${spacing(2.5)}px`,
            borderTop: `1px solid ${colors.border}`,
            backgroundColor: colors.bg,
          }}
        >
          <textarea
            data-testid="welcome-input-field"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask LEO anything…"
            disabled={busy}
            rows={1}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                startChat(input);
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
            data-testid="welcome-send-button"
            disabled={!input.trim() || busy}
            onClick={() => startChat(input)}
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
              cursor: !input.trim() || busy ? "default" : "pointer",
              opacity: !input.trim() || busy ? 0.5 : 1,
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
