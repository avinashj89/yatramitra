package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.ChatMessage
import com.avinash.yatramitra.model.ItinerarySuggestion
import com.avinash.yatramitra.model.RouteSuggestion
import com.avinash.yatramitra.model.SuggestionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupChatTest {

    @Test
    fun `old suggestions and new chat messages form one timeline, oldest first`() {
        val timeline = GroupChat.timeline(
            chat = listOf(ChatMessage(id = "c1", authorName = "Avi", text = "new", createdAtMillis = 300)),
            routeSuggestions = listOf(RouteSuggestion(id = "r1", authorName = "Ravi", text = "route idea", createdAtMillis = 100)),
            itinerarySuggestions = listOf(
                ItinerarySuggestion(id = "i1", authorName = "Sita", text = "lunch later", status = SuggestionStatus.DISMISSED, createdAtMillis = 200)
            )
        )
        assertEquals(listOf("route idea", "lunch later", "new"), timeline.map { it.text })
        // Ids stay unique even if a route and an itinerary suggestion share one.
        assertEquals(3, timeline.map { it.id }.toSet().size)
    }

    @Test
    fun `messages sent in the same millisecond keep a stable order`() {
        val a = ChatMessage(id = "a", createdAtMillis = 5)
        val b = ChatMessage(id = "b", createdAtMillis = 5)
        assertEquals(listOf("a", "b"), GroupChat.timeline(listOf(b, a), emptyList(), emptyList()).map { it.id })
    }

    @Test
    fun `blank messages are not sent and long ones are capped`() {
        assertNull(GroupChat.cleanMessage("   \n "))
        assertEquals("hi", GroupChat.cleanMessage("  hi  "))
        assertEquals(GroupChat.MAX_LENGTH, GroupChat.cleanMessage("x".repeat(GroupChat.MAX_LENGTH + 50))!!.length)
    }

    @Test
    fun `the sender's name shows once per run of their messages`() {
        val first = ChatMessage(authorMemberId = "r", createdAtMillis = 0)
        val quickFollowUp = ChatMessage(authorMemberId = "r", createdAtMillis = 60_000)
        val muchLater = ChatMessage(authorMemberId = "r", createdAtMillis = 60_000 + 6 * 60_000)
        val someoneElse = ChatMessage(authorMemberId = "s", createdAtMillis = 61_000)
        assertTrue(GroupChat.showsAuthor(null, first))
        assertFalse(GroupChat.showsAuthor(first, quickFollowUp))
        assertTrue(GroupChat.showsAuthor(quickFollowUp, muchLater))
        assertTrue(GroupChat.showsAuthor(quickFollowUp, someoneElse))
    }
}
