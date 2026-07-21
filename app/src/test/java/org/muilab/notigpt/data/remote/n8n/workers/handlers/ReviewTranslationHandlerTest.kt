package org.muilab.notigpt.data.remote.n8n.workers.handlers

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.muilab.notigpt.model.features.ReviewItemDraft
import org.muilab.notigpt.model.features.ReviewTranslationState
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedSubItem

class ReviewTranslationHandlerTest {
    private val item = SavedItem(
        savedItemId = "item-1",
        title = "Pay invoice",
        content = "Pay ACME invoice 42 by Friday",
        lastUpdateTimestamp = 100,
        deadlineAtMs = 900,
        whenAtMs = 800,
        isStarred = true,
        userEdited = true,
        buttons = """[{"buttonText":"Open invoice","intent":"https://example.com/i/42","type":"link"}]""",
    )
    private val sub = SavedSubItem("sub-1", "item-1", "Check amount", isCompleted = true, position = 0)
    private val pending = ReviewTranslationState.pending(
        targetLanguage = "zh-TW",
        source = ReviewItemDraft(item, listOf(sub)),
        evidenceRecordIds = listOf("record-1"),
    )

    @Test
    fun mergeTranslatedText_changesOnlyUserFacingStrings() {
        val result = ReviewTranslationHandler.mergeTranslatedText(
            expectedReviewKey = "item_item-1",
            source = pending,
            responseJson = """{
                  "reviewKey":"item_item-1",
                  "targetLanguage":"zh-TW",
                  "title":"支付發票",
                  "content":"於星期五前支付 ACME 42 號發票",
                  "subTasks":[{"savedSubItemId":"sub-1","text":"確認金額"}],
                  "buttons":[{"index":0,"buttonText":"開啟發票"}]
                }""",
        )

        val translated = requireNotNull(result.translatedItem)
        assertEquals("支付發票", translated.title)
        assertEquals("於星期五前支付 ACME 42 號發票", translated.content)
        assertEquals(item.savedItemId, translated.savedItemId)
        assertEquals(item.deadlineAtMs, translated.deadlineAtMs)
        assertEquals(item.whenAtMs, translated.whenAtMs)
        assertEquals(item.isStarred, translated.isStarred)
        assertEquals(item.userEdited, translated.userEdited)
        assertEquals("確認金額", result.translatedSubItems.single().text)
        assertEquals(true, result.translatedSubItems.single().isCompleted)

        val button = JsonParser.parseString(translated.buttons).asJsonArray[0].asJsonObject
        assertEquals("開啟發票", button.get("buttonText").asString)
        assertEquals("https://example.com/i/42", button.get("intent").asString)
        assertEquals("link", button.get("type").asString)
    }

    @Test
    fun mergeTranslatedText_rejectsStructuralChanges() {
        val response = """{
              "reviewKey":"item_item-1",
              "targetLanguage":"zh-TW",
              "title":"支付發票",
              "content":"內容",
              "subTasks":[],
              "buttons":[{"index":0,"buttonText":"開啟"}]
            }"""

        assertThrows(IllegalArgumentException::class.java) {
            ReviewTranslationHandler.mergeTranslatedText("item_item-1", pending, response)
        }
    }
}
