package com.leoai.app

import com.leoai.app.data.ChatMessage
import com.leoai.app.data.ChatSession
import com.leoai.app.data.LeoCompanionEngine
import com.leoai.app.data.greetingForCurrentHour
import com.leoai.app.data.groupSessionsByDate
import com.leoai.app.data.local.entity.ChatMessageEntity
import com.leoai.app.data.local.entity.ChatSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LeoAppUnitTest {

    @Test
    fun testGreetingForCurrentHourReturnsExpectedFormat() {
        val greeting = greetingForCurrentHour()
        val validGreetings = setOf(
            "Late night thoughts?",
            "Good morning.",
            "Good afternoon.",
            "Good evening.",
            "Working late?"
        )
        assertTrue(
            "Greeting '$greeting' should be one of the calm time-of-day greetings",
            validGreetings.contains(greeting)
        )
    }

    @Test
    fun testGroupSessionsByDateGroupsCorrectly() {
        val todayStr = LocalDate.now(ZoneOffset.UTC).toString()
        val yesterdayStr = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString()
        val earlierStr = LocalDate.now(ZoneOffset.UTC).minusDays(5).toString()

        val sessions = listOf(
            ChatSession(id = "s1", title = "Today Session", updatedAt = "${todayStr}T10:00:00Z"),
            ChatSession(id = "s2", title = "Yesterday Session", updatedAt = "${yesterdayStr}T15:00:00Z"),
            ChatSession(id = "s3", title = "Old Session", updatedAt = "${earlierStr}T08:00:00Z")
        )

        val groups = groupSessionsByDate(sessions)
        assertEquals(3, groups.size)
        assertEquals("TODAY", groups[0].label)
        assertEquals(1, groups[0].data.size)
        assertEquals("s1", groups[0].data[0].id)

        assertEquals("YESTERDAY", groups[1].label)
        assertEquals(1, groups[1].data.size)
        assertEquals("s2", groups[1].data[0].id)

        assertEquals("EARLIER", groups[2].label)
        assertEquals(1, groups[2].data.size)
        assertEquals("s3", groups[2].data[0].id)
    }

    @Test
    fun testChatSessionEntityDomainMapping() {
        val session = ChatSession(
            id = "test-session-123",
            title = "Morning Reflection",
            createdAt = "2026-10-24T10:00:00Z",
            updatedAt = "2026-10-24T10:05:00Z",
            preview = "Grounded morning routine preview"
        )

        val entity = ChatSessionEntity.fromDomain(session)
        assertEquals(session.id, entity.id)
        assertEquals(session.title, entity.title)
        assertEquals(session.createdAt, entity.createdAt)
        assertEquals(session.updatedAt, entity.updatedAt)
        assertEquals(session.preview, entity.preview)

        val restored = entity.toDomain()
        assertEquals(session, restored)
    }

    @Test
    fun testChatMessageEntityDomainMapping() {
        val message = ChatMessage(
            id = "msg-999",
            sessionId = "session-1",
            role = "user",
            content = "Help me draft a calm reply",
            createdAt = "2026-10-24T10:01:00Z"
        )

        val entity = ChatMessageEntity.fromDomain(message)
        assertEquals(message.id, entity.id)
        assertEquals(message.sessionId, entity.sessionId)
        assertEquals(message.role, entity.role)
        assertEquals(message.content, entity.content)
        assertEquals(message.createdAt, entity.createdAt)

        val restored = entity.toDomain()
        assertEquals(message, restored)
    }

    @Test
    fun testLeoCompanionEngineGeneratesThoughtfulStarterReply() = runBlocking {
        val engine = LeoCompanionEngine()
        val reply = engine.generateReply(
            sessionId = "test-session",
            userContent = "Help me draft a calm reply to a difficult email",
            priorHistory = emptyList()
        )

        assertNotNull(reply)
        assertTrue(
            "Reply should contain calm reply guidance",
            reply.contains("tempo of the conversation") || reply.contains("calm")
        )
    }
}
