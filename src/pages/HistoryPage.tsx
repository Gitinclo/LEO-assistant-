import React, { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ChevronLeft, Plus, Trash2 } from "lucide-react";
import { ChatSession, leoApi } from "@/api/leo";
import { colors, fonts, radius, spacing } from "@/theme";

function groupByDate(sessions: ChatSession[]) {
  const now = new Date();
  const today = now.toISOString().slice(0, 10);
  const y = new Date(now);
  y.setDate(now.getDate() - 1);
  const yesterday = y.toISOString().slice(0, 10);

  const groups: { label: string; data: ChatSession[] }[] = [];
  const todayItems: ChatSession[] = [];
  const yItems: ChatSession[] = [];
  const earlier: ChatSession[] = [];

  for (const s of sessions) {
    const d = s.updated_at.slice(0, 10);
    if (d === today) todayItems.push(s);
    else if (d === yesterday) yItems.push(s);
    else earlier.push(s);
  }
  if (todayItems.length) groups.push({ label: "TODAY", data: todayItems });
  if (yItems.length) groups.push({ label: "YESTERDAY", data: yItems });
  if (earlier.length) groups.push({ label: "EARLIER", data: earlier });
  return groups;
}

export default function HistoryPage() {
  const navigate = useNavigate();
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const list = await leoApi.listSessions();
      setSessions(list);
    } catch (e) {
      console.warn("listSessions failed", e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const onDelete = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setSessions((prev) => prev.filter((s) => s.id !== id));
    try {
      await leoApi.deleteSession(id);
    } catch (err) {
      console.warn("delete failed", err);
      load();
    }
  };

  const startNew = async () => {
    try {
      const s = await leoApi.createSession();
      navigate(`/chat/${encodeURIComponent(s.id)}`);
    } catch (e) {
      console.warn(e);
    }
  };

  const groups = groupByDate(sessions);

  return (
    <div
      data-testid="history-screen"
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
            data-testid="history-back-button"
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
            Conversations
          </h1>

          <button
            type="button"
            data-testid="new-chat-button"
            onClick={startNew}
            aria-label="New conversation"
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
              color: colors.primary,
            }}
          >
            <Plus size={26} />
          </button>
        </header>

        <main
          style={{
            flex: 1,
            padding: spacing(3),
            display: "flex",
            flexDirection: "column",
            gap: spacing(3),
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
          ) : groups.length === 0 ? (
            <div
              style={{
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                paddingTop: spacing(8),
                gap: spacing(1.5),
              }}
            >
              <div
                style={{
                  fontFamily: fonts.serif,
                  fontSize: 22,
                  color: colors.textPrimary,
                }}
              >
                No conversations yet
              </div>
              <div
                style={{
                  fontSize: 14,
                  color: colors.textSecondary,
                  textAlign: "center",
                  padding: `0 ${spacing(4)}px`,
                }}
              >
                Start a new chat and your history will gather here.
              </div>
              <button
                type="button"
                data-testid="empty-new-chat-button"
                onClick={startNew}
                style={{
                  marginTop: spacing(2),
                  display: "flex",
                  flexDirection: "row",
                  alignItems: "center",
                  gap: spacing(1),
                  backgroundColor: colors.primary,
                  color: colors.primaryFg,
                  border: "none",
                  padding: `${spacing(1.5)}px ${spacing(2.5)}px`,
                  borderRadius: radius.pill,
                  fontSize: 15,
                  fontWeight: 500,
                  fontFamily: fonts.sans,
                  cursor: "pointer",
                }}
              >
                <Plus size={20} />
                <span>Start a conversation</span>
              </button>
            </div>
          ) : (
            groups.map((group) => (
              <div
                key={group.label}
                style={{
                  display: "flex",
                  flexDirection: "column",
                  gap: spacing(1.25),
                }}
              >
                <div
                  style={{
                    fontSize: 11,
                    letterSpacing: 2,
                    color: colors.textMuted,
                    marginLeft: spacing(0.5),
                  }}
                >
                  {group.label}
                </div>
                <div
                  style={{
                    display: "flex",
                    flexDirection: "column",
                    gap: spacing(1),
                  }}
                >
                  {group.data.map((s) => (
                    <div
                      key={s.id}
                      data-testid={`session-row-${s.id}`}
                      onClick={() => navigate(`/chat/${encodeURIComponent(s.id)}`)}
                      style={{
                        display: "flex",
                        flexDirection: "row",
                        alignItems: "center",
                        gap: spacing(1.5),
                        padding: `${spacing(2)}px`,
                        backgroundColor: colors.surface,
                        borderRadius: radius.md,
                        border: `1px solid ${colors.border}`,
                        cursor: "pointer",
                      }}
                    >
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div
                          style={{
                            fontSize: 15,
                            color: colors.textPrimary,
                            fontWeight: 500,
                            whiteSpace: "nowrap",
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                          }}
                        >
                          {s.title || "Untitled"}
                        </div>
                        {s.preview ? (
                          <div
                            style={{
                              fontSize: 13,
                              color: colors.textMuted,
                              marginTop: 4,
                              lineHeight: "18px",
                              display: "-webkit-box",
                              WebkitLineClamp: 2,
                              WebkitBoxOrient: "vertical",
                              overflow: "hidden",
                            }}
                          >
                            {s.preview}
                          </div>
                        ) : null}
                      </div>
                      <button
                        type="button"
                        data-testid={`delete-session-${s.id}`}
                        onClick={(e) => onDelete(s.id, e)}
                        aria-label="Delete session"
                        style={{
                          width: 36,
                          height: 36,
                          borderRadius: 18,
                          border: "none",
                          background: "transparent",
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "center",
                          cursor: "pointer",
                          color: colors.textMuted,
                          flexShrink: 0,
                        }}
                      >
                        <Trash2 size={18} />
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            ))
          )}
        </main>
      </div>
    </div>
  );
}
