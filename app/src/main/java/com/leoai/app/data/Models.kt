package com.leoai.app.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object LeoMediaAssets {
    const val AI_AVATAR =
        "https://images.unsplash.com/photo-1646038572806-3d5f4252c567?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NjA1ODR8MHwxfHNlYXJjaHwxfHxjYWxtJTIwZ3JhZGllbnQlMjBzcGhlcmV8ZW58MHx8fHwxNzgyMzQxMTIwfDA&ixlib=rb-4.1.0&q=85"

    const val BG_BOTANICAL =
        "https://images.unsplash.com/photo-1584282617200-32f377e493ef?crop=entropy&cs=srgb&fm=jpg&ixid=M3w3NTY2OTV8MHwxfHNlYXJjaHwyfHxib3RhbmljYWwlMjBpbGx1c3RyYXRpb24lMjB2aW50YWdlfGVufDB8fHx8MTc4MjM0MTExNnww&ixlib=rb-4.1.0&q=85"

    val SUGGESTIONS = listOf(
        "Help me draft a calm reply to a difficult email",
        "Explain a concept like I'm curious, not stupid",
        "Plan a focused morning routine",
        "Brainstorm names for a small side project",
        "Summarize this idea into a single sentence"
    )
}

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New conversation",
    val createdAt: String = nowIsoString(),
    val updatedAt: String = nowIsoString(),
    val preview: String = ""
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: String, // "user" | "assistant"
    val content: String,
    val createdAt: String = nowIsoString()
)

data class SendMessageResponse(
    val userMessage: ChatMessage,
    val assistantMessage: ChatMessage,
    val session: ChatSession
)

data class SessionDateGroup(
    val label: String,
    val data: List<ChatSession>
)

private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private val dayDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

fun nowIsoString(): String = synchronized(isoDateFormat) {
    isoDateFormat.format(Date())
}

fun greetingForCurrentHour(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> "Late night thoughts?"
        hour < 12 -> "Good morning."
        hour < 17 -> "Good afternoon."
        hour < 21 -> "Good evening."
        else -> "Working late?"
    }
}

fun groupSessionsByDate(sessions: List<ChatSession>): List<SessionDateGroup> {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    val today = synchronized(dayDateFormat) { dayDateFormat.format(cal.time) }
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = synchronized(dayDateFormat) { dayDateFormat.format(cal.time) }

    val todayItems = mutableListOf<ChatSession>()
    val yesterdayItems = mutableListOf<ChatSession>()
    val earlierItems = mutableListOf<ChatSession>()

    for (s in sessions) {
        val datePrefix = s.updatedAt.take(10)
        when (datePrefix) {
            today -> todayItems.add(s)
            yesterday -> yesterdayItems.add(s)
            else -> earlierItems.add(s)
        }
    }

    val groups = mutableListOf<SessionDateGroup>()
    if (todayItems.isNotEmpty()) {
        groups.add(SessionDateGroup(label = "TODAY", data = todayItems))
    }
    if (yesterdayItems.isNotEmpty()) {
        groups.add(SessionDateGroup(label = "YESTERDAY", data = yesterdayItems))
    }
    if (earlierItems.isNotEmpty()) {
        groups.add(SessionDateGroup(label = "EARLIER", data = earlierItems))
    }
    return groups
}
