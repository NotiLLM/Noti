package org.muilab.notigpt.data.remote.n8n.workers.handlers

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.muilab.notigpt.model.features.ReviewItemDraft
import org.muilab.notigpt.model.features.ReviewTranslationState
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.TodoStep

class ReviewTranslationHandlerTest {
    private val item = SavedItem(
        savedItemId = "item-1",
        title = "Pay invoice",
        content = "Pay ACME invoice 42 by Friday",
        lastUpdateTimestamp = 100,
        deadlineAtMs = 900,
        isStarred = true,
        userEdited = true,
        buttons = """[{"buttonText":"Open invoice","intent":"https://example.com/i/42","type":"link"}]""",
    )
    private val sub = TodoStep("sub-1", "item-1", "Check amount", isCompleted = true, position = 0)
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
                  "steps":[{"todoStepId":"sub-1","text":"確認金額"}],
                  "buttons":[{"index":0,"buttonText":"開啟發票"}]
                }""",
        )

        val translated = requireNotNull(result.translatedItem)
        assertEquals("支付發票", translated.title)
        assertEquals("於星期五前支付 ACME 42 號發票", translated.content)
        assertEquals(item.savedItemId, translated.savedItemId)
        assertEquals(item.deadlineAtMs, translated.deadlineAtMs)
        assertEquals(item.isStarred, translated.isStarred)
        assertEquals(item.userEdited, translated.userEdited)
        assertEquals("確認金額", result.translatedSteps.single().text)
        assertEquals(true, result.translatedSteps.single().isCompleted)

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
              "steps":[],
              "buttons":[{"index":0,"buttonText":"開啟"}]
            }"""

        assertThrows(IllegalArgumentException::class.java) {
            ReviewTranslationHandler.mergeTranslatedText("item_item-1", pending, response)
        }
    }

    @Test
    fun mergeTranslatedText_translatesWholeSplitWithoutChangingOrderOrStructure() {
        val keep = item.copy(
            savedItemId = "item-2",
            title = "Door code",
            content = "Use 7788",
            itemType = "keep",
            deadlineAtMs = 0,
            buttons = "[]",
        )
        val batchPending = ReviewTranslationState.pending(
            targetLanguage = "zh-TW",
            source = ReviewItemDraft(item, listOf(sub)),
            sourceBatch = listOf(
                ReviewItemDraft(item, listOf(sub)),
                ReviewItemDraft(keep, emptyList()),
            ),
            evidenceRecordIds = listOf("record-1"),
        )
        val result = ReviewTranslationHandler.mergeTranslatedText(
            "item_item-1",
            batchPending,
            """{
              "reviewKey":"item_item-1",
              "targetLanguage":"zh-TW",
              "batchItems":[
                {"index":0,"title":"支付發票","content":"於星期五前支付 ACME 42 號發票","steps":[{"todoStepId":"sub-1","text":"確認金額"}],"buttons":[{"index":0,"buttonText":"開啟發票"}]},
                {"index":1,"title":"門禁碼","content":"使用 7788","steps":[],"buttons":[]}
              ]
            }""",
        )

        val translatedBatch = result.translatedBatchDrafts
        assertEquals(listOf("item-1", "item-2"), translatedBatch.map { it.item.savedItemId })
        assertEquals(listOf("支付發票", "門禁碼"), translatedBatch.map { it.item.title })
        assertEquals(true, translatedBatch.first().steps.single().isCompleted)
        assertEquals("keep", translatedBatch.last().item.itemType)
    }
}
