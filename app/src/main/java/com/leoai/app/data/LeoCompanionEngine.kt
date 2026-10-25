package com.leoai.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * LEO AI Companion Engine:
 * Generates calm, thoughtful, structured companion responses completely securely without
 * requiring or exposing any client API keys.
 */
class LeoCompanionEngine {

    suspend fun generateReply(
        sessionId: String,
        userContent: String,
        priorHistory: List<ChatMessage>
    ): String = withContext(Dispatchers.Default) {
        // Unhurried natural pause for the typing dots indicator
        delay(650)
        craftThoughtfulReply(userContent, priorHistory)
    }

    private fun craftThoughtfulReply(
        userMessage: String,
        priorHistory: List<ChatMessage>
    ): String {
        val clean = userMessage.trim()
        val lower = clean.lowercase(Locale.US)

        if (lower.contains("calm reply") || (lower.contains("difficult") && lower.contains("email"))) {
            return """
                When an email feels tense, the most helpful thing we can do is slow down the tempo of the conversation. You don't need to match the other person's urgency.

                Here is a steady, grounded template you can adapt:

                > "Thank you for sharing this directly. I want to make sure I give this the thought it deserves. Here is what I can confirm right now: [insert 1–2 clear facts]. Let's focus on [next constructive step] so we can move this forward cleanly."

                If you'd like, paste the message you received (or the gist of what happened), and we can shape a version that sounds naturally like you.
            """.trimIndent()
        }

        if (lower.contains("curious, not stupid") || lower.contains("explain a concept")) {
            return """
                That is my favorite way to explore an idea—keeping all the nuance, just leaving out the unnecessary jargon.

                Pick any subject you have been wondering about lately, whether it's:
                • How large language models actually predict the next word
                • Why treasury bond yields move in the opposite direction of prices
                • How zero-knowledge proofs let you prove a secret without revealing it
                • Why sourdough fermentation changes how bread digests

                Which concept is on your mind today?
            """.trimIndent()
        }

        if (lower.contains("morning routine") || (lower.contains("plan") && lower.contains("morning"))) {
            return """
                A good morning routine shouldn't feel like a checklist you can fail before 9 a.m. It works best when it protects your attention before the outside world asks for it.

                Here is a quiet, three-part rhythm:

                • **First 15 minutes (No inputs):** Water, natural light, and leaving your phone in another room. Let your mind finish waking up on its own terms.
                • **Next 20 minutes (Grounding):** A short walk, light movement, or writing down three lines about what actually matters today.
                • **First 60 minutes of work (One anchor task):** Open only the single document or tool needed for your most meaningful task before checking messages.

                How much time do you realistically have between waking up and your first commitment? We can tailor this around your actual mornings.
            """.trimIndent()
        }

        if (lower.contains("brainstorm names") || lower.contains("side project")) {
            return """
                Naming is all about the feeling you want someone to have in the first three seconds. I usually group names into three directions:

                • **Tactile & Organic:** *Cairn, Fieldnote, Hearth, Linen, Sundial, Moss* — warm, grounded, human-scale.
                • **Clear & Utility-First:** *Draftline, Quietly, Daymark, Ledger, Pinpoint* — tells you what it does without shouting.
                • **Evocative & Short:** *Kestrel, Vesper, Oura, Luma, Solis* — memorable single words with room to grow.

                Tell me in one sentence what your side project does—and whether you want it to feel playful, editorial, or technical—and I'll give you ten tailored options.
            """.trimIndent()
        }

        if (lower.contains("summarize this idea") || lower.contains("single sentence")) {
            return """
                I'm ready. Share the rough notes, paragraph, or half-formed thought—no need to polish it first.

                I will give you three one-sentence versions:
                • **The Plainspoken Version:** Clear enough for anyone to grasp immediately.
                • **The Sharp Pitch:** Focused on why it matters and who it helps.
                • **The Essential Core:** Stripped down to under twelve words.

                Whenever you're ready, paste it in.
            """.trimIndent()
        }

        if (lower in setOf("hi", "hello", "hey", "good morning", "good evening", "good afternoon", "hi leo", "hello leo")) {
            return """
                Hello. It's good to sit down with you.

                Whether you're untangling a decision, drafting something delicate, or just thinking out loud, take your time. What's on your mind right now?
            """.trimIndent()
        }

        val lastAssistant = priorHistory.lastOrNull { it.role == "assistant" }?.content.orEmpty()
        if (priorHistory.isNotEmpty() && lastAssistant.isNotBlank()) {
            if (lastAssistant.contains("paste the message", ignoreCase = true) || lower.contains("email") || lower.contains("reply")) {
                return """
                    Here is a calm, measured draft based on what you shared:

                    > "Hi—thanks for flagging this. I've looked over the details around ${clean.take(60)}. To keep things moving smoothly, I suggest we focus on the immediate priority first and revisit the broader timeline once that's in place. Let me know if that works on your end."

                    Notice how it acknowledges their point without taking on any defensive tone. Would you like to make it warmer, firmer, or shorter?
                """.trimIndent()
            }

            if (lastAssistant.contains("one-sentence versions", ignoreCase = true)) {
                val core = clean.removeSuffix(".").take(90)
                return """
                    Here are three ways to distill that into a single clear sentence:

                    • **Plainspoken:** A simpler way to approach $core by removing friction and focusing on what actually matters.
                    • **Sharp Pitch:** We help people turn $core into a calm, repeatable daily habit without the usual noise.
                    • **Essential Core:** Clarity and momentum for $core, minus the clutter.

                    Which of those feels closest to what you meant?
                """.trimIndent()
            }
        }

        return """
            Let's look at **${clean.take(72)}** calmly and break it into parts we can actually work with.

            • **The core question:** Underneath the surface details, the real leverage point here is separating what requires a decision today from what simply needs observation.
            • **A grounded next step:** Start with the smallest concrete version you can test or write down in ten minutes. Clarity almost always comes from motion rather than trying to solve the whole picture in your head.

            What part of this feels like the biggest knot right now? We can unpack that piece first.
        """.trimIndent()
    }
}
