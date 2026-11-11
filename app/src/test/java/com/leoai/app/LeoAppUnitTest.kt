package com.leoai.app

import com.leoai.app.data.ChatMessage
import com.leoai.app.data.ChatMessageDao
import com.leoai.app.data.ChatSession
import com.leoai.app.data.InputBoundsValidator
import com.leoai.app.data.LeoCompanionEngine
import com.leoai.app.data.MessageValidator
import com.leoai.app.data.TrainingDatasetExporter
import com.leoai.app.data.TrainingProfile
import com.leoai.app.data.TrainingQaPair
import com.leoai.app.data.greetingForCurrentHour
import com.leoai.app.data.groupSessionsByDate
import com.leoai.app.data.local.Aes256GcmCrypto
import com.leoai.app.data.local.Aes256GcmRoomConverters
import com.leoai.app.data.local.ChatMessageCrypto
import com.leoai.app.data.local.EncryptedMessageContent
import com.leoai.app.data.local.entity.ChatMessageEntity
import com.leoai.app.data.local.entity.ChatSessionEntity
import com.leoai.app.data.local.entity.TrainingProfileEntity
import com.leoai.app.data.local.entity.TrainingQaPairEntity
import com.leoai.app.ui.components.ComplianceDocTab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import javax.crypto.AEADBadTagException

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
    fun testChatSessionEntityDomainMappingAndPreviewEncryption() {
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
        assertTrue(
            "Session preview should be encrypted at rest with v1: AES-256-GCM prefix",
            ChatMessageCrypto.isEncryptedPayload(entity.preview)
        )
        assertNotEquals(session.preview, entity.preview)

        val restored = entity.toDomain()
        assertEquals(session, restored)
    }

    @Test
    fun testChatMessageEntityEncryptsAtRestAndDecryptsToDomain() {
        val message = ChatMessage(
            id = "msg-999",
            sessionId = "session-1",
            role = "user",
            content = "Help me draft a calm reply",
            createdAt = "2026-10-24T10:01:00Z"
        )

        val entity1 = ChatMessageEntity.fromDomain(message)
        val entity2 = ChatMessageEntity.fromDomain(message)

        assertEquals(message.id, entity1.id)
        assertEquals(message.sessionId, entity1.sessionId)
        assertEquals(message.role, entity1.role)
        assertEquals(message.createdAt, entity1.createdAt)

        // Verify AES-256-GCM encryption at rest and unique 12-byte IV per encryption
        assertTrue(ChatMessageCrypto.isEncryptedPayload(entity1.content))
        assertNotEquals(message.content, entity1.content)
        assertNotEquals(
            "Two encryptions of identical plaintext must differ due to fresh 12-byte random IVs",
            entity1.content,
            entity2.content
        )

        val restored = entity1.toDomain()
        assertEquals(message, restored)
    }

    @Test
    fun testAes256GcmAadContextBindingPreventsCrossSessionTampering() {
        val plaintext = "Sensitive personal reflection"
        val encryptedForSessionA = ChatMessageCrypto.encrypt(
            plaintext = plaintext,
            associatedData = "session-A"
        )

        // Decrypting with matching AAD succeeds
        assertEquals(
            plaintext,
            ChatMessageCrypto.decrypt(encryptedForSessionA, associatedData = "session-A")
        )

        // Decrypting with mismatched sessionId AAD must throw AEADBadTagException
        var threwBadTag = false
        try {
            ChatMessageCrypto.decrypt(encryptedForSessionA, associatedData = "session-B")
        } catch (_: AEADBadTagException) {
            threwBadTag = true
        }
        assertTrue(
            "Decrypting with wrong sessionId AAD must fail GCM tag verification",
            threwBadTag
        )

        // Legacy plaintext without v1: prefix is returned as-is for backward compatibility
        assertEquals(
            "Legacy unencrypted note",
            ChatMessageCrypto.decrypt("Legacy unencrypted note", associatedData = "session-A")
        )
    }

    @Test
    fun testAes256GcmCryptoUtilityPayloadInspectionKeyRotationAndRoomConverters() {
        val crypto = Aes256GcmCrypto("test_alias_aes256_gcm")
        val plaintext = "Room message encrypted with Android Keystore AES-256-GCM"
        val aad = "session-xyz-001"

        val cipherPayload = crypto.encrypt(plaintext, associatedData = aad)
        assertTrue(crypto.isEncryptedPayload(cipherPayload))

        val metadata = crypto.inspectPayload(cipherPayload)
        assertNotNull(metadata)
        assertEquals(Aes256GcmCrypto.PAYLOAD_PREFIX, metadata?.versionPrefix)
        assertEquals(12, metadata?.ivLengthBytes)
        assertEquals(128, metadata?.tagLengthBits)
        assertTrue((metadata?.ciphertextAndTagLengthBytes ?: 0) >= 16)

        assertEquals(plaintext, crypto.decrypt(cipherPayload, associatedData = aad))

        // Verify Room TypeConverter round-trip
        val converters = Aes256GcmRoomConverters()
        val storedColumn = converters.fromEncryptedMessageContent(EncryptedMessageContent(plaintext))
        assertNotNull(storedColumn)
        assertTrue(Aes256GcmCrypto.isEncryptedPayload(storedColumn!!))
        val restoredContent = converters.toEncryptedMessageContent(storedColumn)
        assertEquals(plaintext, restoredContent?.plaintext)

        // Verify key rotation invalidates prior ciphertext and decryptSafely returns fallback notice
        crypto.rotateKey()
        assertEquals(
            Aes256GcmCrypto.INTEGRITY_FAILURE_FALLBACK,
            crypto.decryptSafely(cipherPayload, associatedData = aad)
        )
    }

    @Test
    fun testInputBoundsValidatorEnforcesExactLimits() {
        assertEquals(
            InputBoundsValidator.MAX_MESSAGE_CONTENT_LEN,
            InputBoundsValidator.sanitizeMessageContent("M".repeat(5_000)).length
        )
        assertEquals(
            InputBoundsValidator.MAX_SESSION_TITLE_LEN,
            InputBoundsValidator.sanitizeSessionTitle("T".repeat(150)).length
        )
        assertEquals(
            InputBoundsValidator.MAX_SESSION_PREVIEW_LEN,
            InputBoundsValidator.sanitizeSessionPreview("P".repeat(250)).length
        )
        assertEquals(
            InputBoundsValidator.DEFAULT_SESSION_TITLE,
            InputBoundsValidator.sanitizeSessionTitle("   \n  ")
        )
        assertEquals("user", InputBoundsValidator.sanitizeRole("USER"))
        assertEquals("assistant", InputBoundsValidator.sanitizeRole("Assistant"))
        assertEquals("user", InputBoundsValidator.sanitizeRole("unknown_role"))
    }

    @Test
    fun testMessageValidatorSanitizesControlCharsNewlinesAndEnforcesCharacterLimits() {
        // 1. Control character stripping and CRLF / excessive newline normalization
        val dirtyInput = "  Hello\u0000\u0007 Leo\r\nLine 2\r\n\r\n\r\n\r\nLine 3\u001F  "
        val sanitized = MessageValidator.sanitize(dirtyInput)
        assertEquals("Hello Leo\nLine 2\n\nLine 3", sanitized)

        // 2. Empty or whitespace/control-char-only input is rejected
        val emptyResult = MessageValidator.validate("   \u0000\n\t  ")
        assertFalse(emptyResult.isValid)
        assertEquals("", emptyResult.sanitizedContent)
        assertEquals(MessageValidator.ValidationError.EMPTY_OR_BLANK, emptyResult.errorReason)

        // 3. Over-limit message with truncateIfExceeded = true is truncated to 4,000 chars
        val oversized = "A".repeat(4_500)
        val truncatedResult = MessageValidator.validate(oversized, truncateIfExceeded = true)
        assertTrue(truncatedResult.isValid)
        assertTrue(truncatedResult.wasTruncated)
        assertEquals(MessageValidator.MAX_MESSAGE_LENGTH, truncatedResult.sanitizedLength)
        assertEquals(4_500, truncatedResult.originalLength)

        // 4. Over-limit message with truncateIfExceeded = false is rejected with EXCEEDS_MAX_LENGTH
        val strictResult = MessageValidator.validate(oversized, truncateIfExceeded = false)
        assertFalse(strictResult.isValid)
        assertFalse(strictResult.wasTruncated)
        assertEquals(MessageValidator.ValidationError.EXCEEDS_MAX_LENGTH, strictResult.errorReason)

        // 5. Live input clamping preserves mid-word spaces while stripping control chars and capping length
        val liveClamped = MessageValidator.clampLiveInput("Typing with trailing space \u0000")
        assertEquals("Typing with trailing space ", liveClamped)
        assertEquals(
            MessageValidator.MAX_MESSAGE_LENGTH,
            MessageValidator.clampLiveInput("X".repeat(4_200)).length
        )

        // 6. Remaining characters & near-limit threshold checks
        assertEquals(400, MessageValidator.remainingCharacters("Y".repeat(3_600)))
        assertTrue(MessageValidator.isNearCharacterLimit("Y".repeat(3_600)))
        assertFalse(MessageValidator.isNearCharacterLimit("Y".repeat(100)))
    }

    @Test
    fun testChatMessageDaoOperationsOrderAndFilterCorrectly() = runBlocking {
        val dao = InMemoryChatMessageDao()

        val msg1 = ChatMessageEntity.fromDomain(
            ChatMessage(
                id = "m1",
                sessionId = "s1",
                role = "user",
                content = "First question",
                createdAt = "2026-10-24T10:00:00Z"
            )
        )
        val msg2 = ChatMessageEntity.fromDomain(
            ChatMessage(
                id = "m2",
                sessionId = "s1",
                role = "assistant",
                content = "First calm answer",
                createdAt = "2026-10-24T10:00:05Z"
            )
        )
        val msg3 = ChatMessageEntity.fromDomain(
            ChatMessage(
                id = "m3",
                sessionId = "s2",
                role = "user",
                content = "Second session prompt",
                createdAt = "2026-10-24T09:00:00Z"
            )
        )

        dao.insertMessage(msg1)
        dao.insert(msg2)
        dao.insertMessages(listOf(msg3))

        assertEquals(2, dao.getMessageCountForSession("s1"))
        assertEquals(1, dao.getMessageCountForSession("s2"))
        assertNotNull(dao.getMessageById("m1"))

        val s1History = dao.getChatHistoryForSession("s1").first().map { it.toDomain() }
        assertEquals(listOf("First question", "First calm answer"), s1History.map { it.content })

        val allOrdered = dao.getChatHistoryOrderedByTimestamp().first()
        assertEquals(listOf("m3", "m1", "m2"), allOrdered.map { it.id })

        dao.deleteMessageHistoryBySession("s1")
        assertEquals(0, dao.getMessageCountForSession("s1"))
        assertEquals(1, dao.getAllChatHistorySnapshot().size)

        dao.deleteMessageHistory()
        assertNull(dao.getMessageById("m3"))
        assertTrue(dao.getAllChatHistory().first().isEmpty())
    }

    @Test
    fun testComplianceDocTabsCoverAllEightGovernanceFiles() {
        assertEquals(8, ComplianceDocTab.entries.size)
        ComplianceDocTab.entries.forEach { tab ->
            assertFalse(tab.label.isBlank())
            assertFalse(tab.fileName.isBlank())
            assertFalse(tab.summaryTitle.isBlank())
            assertFalse(tab.content.isBlank())
        }
    }

    @Test
    fun testLeoCompanionEngineConfiguresGeminiModelAndSanitizesPrompts() = runBlocking {
        assertEquals("gemini-3.8-flash", LeoCompanionEngine.MODEL_NAME)
        assertEquals(60_000L, LeoCompanionEngine.INFERENCE_TIMEOUT_MS)

        val engine = LeoCompanionEngine { sanitizedPrompt, history ->
            "Gemini response to [$sanitizedPrompt] with ${history.size} prior messages"
        }
        val prior = listOf(
            ChatMessage(id = "p1", sessionId = "test-session", role = "user", content = "Prior turn")
        )
        val reply = engine.generateReply(
            sessionId = "test-session",
            userContent = "  Help me draft a calm reply\u0000  ",
            priorHistory = prior
        )

        assertEquals(
            "Gemini response to [Help me draft a calm reply] with 1 prior messages",
            reply
        )
    }

    @Test
    fun testTrainingEntitiesEncryptAtRestAndExportChatMlJsonlAndQloraScripts() {
        val profile = TrainingProfile(
            id = "prof-101",
            name = "Support Playbook",
            domainDescription = "Customer support & billing guidelines",
            sourceNotes = "Refunds are processed within 14 days.",
            systemPrompt = "Always cite the 14-day refund window.",
            isActive = true
        )
        val profileEntity = TrainingProfileEntity.fromDomain(profile)
        assertTrue(Aes256GcmCrypto.isEncryptedPayload(profileEntity.domainDescription))
        assertTrue(Aes256GcmCrypto.isEncryptedPayload(profileEntity.sourceNotes))
        assertTrue(Aes256GcmCrypto.isEncryptedPayload(profileEntity.systemPrompt))
        assertEquals(profile, profileEntity.toDomain())

        val qaPair = TrainingQaPair(
            id = "qa-1",
            profileId = profile.id,
            question = "How long do refunds take?",
            answer = "Refunds are processed within 14 days.",
            qualityScore = 9,
            judgeRationale = "Accurate to policy."
        )
        val qaEntity = TrainingQaPairEntity.fromDomain(qaPair)
        assertTrue(Aes256GcmCrypto.isEncryptedPayload(qaEntity.question))
        assertTrue(Aes256GcmCrypto.isEncryptedPayload(qaEntity.answer))
        assertTrue(Aes256GcmCrypto.isEncryptedPayload(qaEntity.judgeRationale))
        assertEquals(qaPair, qaEntity.toDomain())

        val jsonl = TrainingDatasetExporter.exportToChatMlJsonl(profile, listOf(qaPair))
        assertTrue(jsonl.contains("\"role\":\"system\""))
        assertTrue(jsonl.contains("How long do refunds take?"))
        assertTrue(jsonl.contains("\"quality_score\":9"))

        val alpaca = TrainingDatasetExporter.exportToAlpacaJson(profile, listOf(qaPair))
        assertTrue(alpaca.contains("\"instruction\""))
        assertTrue(alpaca.contains("Refunds are processed within 14 days."))

        val engine = LeoCompanionEngine()
        val trainedInstruction = engine.buildTrainedSystemInstruction(profile, listOf(qaPair))
        assertTrue(trainedInstruction.contains("Support Playbook"))
        assertTrue(trainedInstruction.contains("How long do refunds take?"))
    }

    private class InMemoryChatMessageDao : ChatMessageDao {
        private val store = MutableStateFlow<Map<String, ChatMessageEntity>>(emptyMap())

        override suspend fun insertMessage(message: ChatMessageEntity) {
            store.value = store.value + (message.id to message)
        }

        override suspend fun insert(message: ChatMessageEntity) {
            insertMessage(message)
        }

        override suspend fun insertMessages(messages: List<ChatMessageEntity>) {
            store.value = store.value + messages.associateBy { it.id }
        }

        override suspend fun getMessageById(id: String): ChatMessageEntity? = store.value[id]

        override suspend fun deleteMessageById(id: String) {
            store.value = store.value - id
        }

        override suspend fun deleteMessageHistory() {
            store.value = emptyMap()
        }

        override suspend fun deleteMessageHistoryBySession(sessionId: String) {
            store.value = store.value.filterValues { it.sessionId != sessionId }
        }

        override fun getAllChatHistory(): Flow<List<ChatMessageEntity>> =
            store.map { map -> map.values.sortedBy { it.createdAt } }

        override fun getChatHistoryOrderedByTimestamp(): Flow<List<ChatMessageEntity>> =
            getAllChatHistory()

        override suspend fun getAllChatHistorySnapshot(): List<ChatMessageEntity> =
            store.value.values.sortedBy { it.createdAt }

        override fun getChatHistoryForSession(sessionId: String): Flow<List<ChatMessageEntity>> =
            store.map { map ->
                map.values.filter { it.sessionId == sessionId }.sortedBy { it.createdAt }
            }

        override suspend fun getChatHistoryForSessionSnapshot(sessionId: String): List<ChatMessageEntity> =
            store.value.values.filter { it.sessionId == sessionId }.sortedBy { it.createdAt }

        override suspend fun getMessageCountForSession(sessionId: String): Int =
            store.value.values.count { it.sessionId == sessionId }
    }
}
