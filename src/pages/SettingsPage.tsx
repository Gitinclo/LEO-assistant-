import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ChevronLeft, Trash2 } from "lucide-react";
import { leoApi } from "@/api/leo";
import { AI_AVATAR, colors, fonts, radius, spacing } from "@/theme";

export default function SettingsPage() {
  const navigate = useNavigate();
  const [clearing, setClearing] = useState(false);
  const [cleared, setCleared] = useState(false);

  const onClearAll = async () => {
    setClearing(true);
    try {
      const sessions = await leoApi.listSessions();
      await Promise.all(sessions.map((s) => leoApi.deleteSession(s.id)));
      setCleared(true);
      setTimeout(() => setCleared(false), 2500);
    } catch (e) {
      console.warn(e);
    } finally {
      setClearing(false);
    }
  };

  return (
    <div
      data-testid="settings-screen"
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
        <header
          style={{
            display: "flex",
            flexDirection: "row",
            alignItems: "center",
            justifyContent: "space-between",
            padding: `${spacing(1)}px ${spacing(1.5)}px`,
            borderBottom: `1px solid ${colors.border}`,
          }}
        >
          <button
            type="button"
            data-testid="settings-back-button"
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

          <h1
            style={{
              margin: 0,
              fontFamily: fonts.serif,
              fontSize: 20,
              fontWeight: 500,
              color: colors.textPrimary,
            }}
          >
            Settings
          </h1>

          <div style={{ width: 44, height: 44 }} />
        </header>

        <main
          style={{
            flex: 1,
            padding: spacing(3),
            display: "flex",
            flexDirection: "column",
            gap: spacing(2.5),
          }}
        >
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              padding: `${spacing(3)}px 0`,
              gap: spacing(1),
            }}
          >
            <img
              src={AI_AVATAR}
              alt="LEO"
              style={{
                width: 86,
                height: 86,
                borderRadius: 43,
                marginBottom: spacing(1),
                objectFit: "cover",
              }}
            />
            <div
              style={{
                fontFamily: fonts.serif,
                fontSize: 28,
                color: colors.textPrimary,
                letterSpacing: 2,
              }}
            >
              LEO
            </div>
            <div
              style={{
                fontSize: 13,
                color: colors.textSecondary,
                fontStyle: "italic",
              }}
            >
              A quieter kind of AI
            </div>
          </div>

          <div
            style={{
              fontSize: 11,
              letterSpacing: 2,
              color: colors.textMuted,
              marginTop: spacing(1),
            }}
          >
            ABOUT
          </div>

          <div
            style={{
              backgroundColor: colors.surface,
              border: `1px solid ${colors.border}`,
              borderRadius: radius.md,
              padding: spacing(2.5),
            }}
          >
            <p
              style={{
                margin: 0,
                fontSize: 15,
                color: colors.textPrimary,
                lineHeight: "22px",
              }}
            >
              LEO is a calm, thoughtful AI companion built to help you think out
              loud. Powered by Claude Sonnet 4.5.
            </p>
          </div>

          <div
            style={{
              fontSize: 11,
              letterSpacing: 2,
              color: colors.textMuted,
              marginTop: spacing(1),
            }}
          >
            DATA
          </div>

          <button
            type="button"
            data-testid="clear-conversations-button"
            onClick={onClearAll}
            disabled={clearing}
            style={{
              display: "flex",
              flexDirection: "row",
              alignItems: "center",
              gap: spacing(1.5),
              padding: `${spacing(2)}px 0`,
              border: "none",
              borderBottom: `1px solid ${colors.border}`,
              background: "transparent",
              cursor: clearing ? "default" : "pointer",
              color: colors.danger,
              fontFamily: fonts.sans,
              fontSize: 15,
              fontWeight: 500,
              textAlign: "left",
            }}
          >
            <Trash2 size={20} color={colors.danger} />
            <span>{cleared ? "Cleared." : "Clear all conversations"}</span>
          </button>

          <div
            style={{
              marginTop: "auto",
              textAlign: "center",
              color: colors.textMuted,
              fontSize: 12,
              letterSpacing: 1,
            }}
          >
            v1.0 · Built with care
          </div>
        </main>
      </div>
    </div>
  );
}
