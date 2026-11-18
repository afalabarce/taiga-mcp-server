package dev.afalabarce.mcp

import dev.afalabarce.mcp.taiga.model.TaigaEpicRelatedUserStory
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaigaEpicRelatedUserStoryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testEpicRelatedUserStoryDeserializesWithoutId() {
        val payloadWithoutId = """{"user_story": 416, "epic": 111, "order": 1}"""
        val decoded = json.decodeFromString<TaigaEpicRelatedUserStory>(payloadWithoutId)

        assertNull(decoded.id)
        assertEquals(111L, decoded.epic)
        assertEquals(416L, decoded.userStory)
        assertEquals(1L, decoded.order)

        val listPayload = """[{"user_story": 416, "epic": 111, "order": 1}, {"id": 99, "user_story": 417, "epic": 111, "order": 2}]"""
        val decodedList = json.decodeFromString<List<TaigaEpicRelatedUserStory>>(listPayload)

        assertEquals(2, decodedList.size)
        assertNull(decodedList[0].id)
        assertEquals(416L, decodedList[0].userStory)
        assertEquals(99L, decodedList[1].id)
        assertEquals(417L, decodedList[1].userStory)
    }
}
