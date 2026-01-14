package org.muilab.notigpt.domain.esm

/**
 * IRB Short Survey v2 (2026-01-09).
 *
 * For now this is a hardcoded definition so the end-to-end ESM pipeline is runnable.
 * Next step: move this to a JSON asset and load dynamically.
 */
object IRBShortSurveyV2 {

    const val questionnaireId: String = "short_v2"
    const val questionnaireVersion: Int = 1

    enum class QType {
        YES_NO,
        LIKERT_5,
        SINGLE_CHOICE,
        MULTI_CHOICE,
    }

    data class Option(val id: String, val label: String, val isOther: Boolean = false)

    data class Question(
        val id: String,
        val text: String,
        val type: QType,
        val options: List<Option> = emptyList(),
        val required: Boolean = true,
    )

    // Likert values are 1..5
    val likertOptions = listOf(
        Option("1", "非常不同意"),
        Option("2", "不同意"),
        Option("3", "普通"),
        Option("4", "同意"),
        Option("5", "非常同意"),
    )

    val questions: List<Question> = listOf(
        Question(
            id = "q1",
            text = "1. 這則通知中是否包含您需要處理的待辦事項？",
            type = QType.YES_NO,
            options = listOf(Option("yes", "有"), Option("no", "沒有")),
        ),
        Question(
            id = "q2_1",
            text = "2-1. 這則提醒內容有讓我注意到我可能需要處理這個待辦事項。",
            type = QType.LIKERT_5,
            options = likertOptions,
        ),
        Question(
            id = "q2_2",
            text = "2-2. 這則提醒讓我以為這個通知裡有待辦事項，事實上沒有。",
            type = QType.LIKERT_5,
            options = likertOptions,
        ),
        Question(
            id = "q3",
            text = "3. 這則提醒內容有正確指出我需要處理的待辦事項。",
            type = QType.LIKERT_5,
            options = likertOptions,
        ),
        Question(
            id = "q4",
            text = "4. 若您覺得提醒內容不正確，主要是哪些問題？（可複選）",
            type = QType.MULTI_CHOICE,
            options = listOf(
                Option("a", "有寫出明確行動，但行動內容不正確"),
                Option("b", "漏掉我需要做的關鍵行動或步驟"),
                Option("c", "把「需要我處理」的事寫成不需要我處理"),
                Option("d", "把「不需要我處理」的事寫成需要我處理"),
                Option("other", "其他（請簡述）", isOther = true),
            ),
        ),
        Question(
            id = "q5",
            text = "5. 這則提醒內容把待辦事項描述得足夠清楚。",
            type = QType.LIKERT_5,
            options = likertOptions,
        ),
        Question(
            id = "q6",
            text = "6. 若您覺得提醒內容不夠清楚，主要是哪些問題？（可複選）",
            type = QType.MULTI_CHOICE,
            options = listOf(
                Option("a", "沒有寫出明確的行動，只是概念或關鍵字"),
                Option("b", "缺少執行待辦事項所需的必要細節，讓我無法進一步執行"),
                Option("c", "寫了太多不必要的內容，讓重點不清楚"),
                Option("other", "其他（請簡述）", isOther = true),
            ),
        ),
        Question(
            id = "q7",
            text = "7. 這個待辦事項對您來說是否有明確的完成期限？",
            type = QType.SINGLE_CHOICE,
            options = listOf(
                Option("yes", "有"),
                Option("no", "沒有明確期限"),
                Option("unsure", "我不確定"),
            ),
        ),
        Question(
            id = "q8",
            text = "8. 通知文字中是否有可用來判斷期限的時間資訊？",
            type = QType.SINGLE_CHOICE,
            options = listOf(
                Option("yes", "有"),
                Option("no", "沒有／看不出來"),
                Option("unsure", "我不確定"),
            ),
        ),
        Question(
            id = "q9",
            text = "9. 提醒內容中的完成期限和通知文字中的時間資訊相比，較接近哪一種？",
            type = QType.SINGLE_CHOICE,
            options = listOf(
                Option("same", "日期與時間一致"),
                Option("earlier", "有對應，但期限比通知中的時間點更早"),
                Option("later", "有對應，但期限比通知中的時間點更晚"),
                Option("wrong", "日期或時間明顯寫錯"),
                Option("none", "看不出有對應"),
                Option("unsure", "我不確定"),
                Option("other", "其他（請簡述）", isOther = true),
            ),
        ),
        Question(
            id = "q10",
            text = "10. 在提醒內容中給的完成期限，和您自己希望完成這個待辦事項的時間相比如何？",
            type = QType.SINGLE_CHOICE,
            options = listOf(
                Option("earlier", "我希望更早完成"),
                Option("same", "差不多，就是我希望的時間"),
                Option("later", "我希望更晚完成"),
                Option("na", "我沒有想過希望何時完成／無法判斷"),
            ),
        ),
        Question(
            id = "q11",
            text = "11. 這則提醒內容對完成這個待辦事項需要多少時間的預估，符合我的情況。",
            type = QType.SINGLE_CHOICE,
            options = listOf(
                Option("more", "我覺得我需要更多時間"),
                Option("same", "與我預期差不多"),
                Option("less", "我覺得我可以用更少時間完成"),
                Option("na", "不適用／無法判斷"),
            ),
        ),
        Question(
            id = "q12",
            text = "12. 看完提醒內容後，您是否仍需要回去看原通知，才能決定下一步怎麼做？",
            type = QType.SINGLE_CHOICE,
            options = listOf(
                Option("yes", "需要"),
                Option("no", "不需要"),
                Option("unsure", "不確定"),
            ),
        ),
        Question(
            id = "q13",
            text = "13. 若需要或不確定，您覺得還需要回去看的原因是什麼？（可複選）",
            type = QType.MULTI_CHOICE,
            options = listOf(
                Option("a", "提醒內容資訊不夠，讓我無法直接決定下一步"),
                Option("b", "原通知部分資訊無法被提醒內容涵蓋（例如圖片、附件、對話脈絡）"),
                Option("c", "我擔心自己理解錯，想回去核對原文或細節"),
                Option("d", "我會回去看原通知，但主要是為了回覆/點連結/進行操作，不是為了補資訊"),
                Option("other", "其他（請簡述）", isOther = true),
            ),
        ),
    )

    fun introText(triggerType: String): String {
        return when (triggerType) {
            EsmTriggerTypes.A_USER_TRIGGERED_EXTRACTION ->
                "您剛剛已將這則通知交由 Noti 生成提醒內容。請根據「原通知」與「生成的提醒內容」回答以下問題。"
            EsmTriggerTypes.B_ENTERED_EDIT_PAGE ->
                "您剛剛已點擊並查看 Noti 自動生成的提醒內容。請根據「生成的提醒內容」與「對應的原通知」回答以下問題。"
            else ->
                "Noti 已自動為這則通知生成提醒內容。請根據「生成的提醒內容」與「對應的原通知」回答以下問題。"
        }
    }

    fun firstQuestionId(): String = "q1"

    /** Returns next question id; null means END. */
    fun nextQuestionId(currentId: String, answerJson: String): String? {
        // Minimal JSON parsing: answerJson is expected to include "value".
        // We'll keep this permissive.
        fun value(): String {
            return try {
                org.json.JSONObject(answerJson).optString("value", "")
            } catch (_: Exception) {
                ""
            }
        }

        fun likertValue(): Int {
            return try {
                org.json.JSONObject(answerJson).optInt("value", 0)
            } catch (_: Exception) {
                0
            }
        }

        return when (currentId) {
            "q1" -> if (value() == "yes") "q2_1" else "q2_2"
            "q2_2" -> null
            "q2_1" -> "q3"
            "q3" -> {
                val v = likertValue()
                if (v >= 4) "q5" else "q4"
            }
            "q4" -> "q5"
            "q5" -> {
                val v = likertValue()
                if (v >= 4) "q7" else "q6"
            }
            "q6" -> "q7"
            "q7" -> when (value()) {
                "yes" -> "q8"
                "no" -> "q11" // skip 8-10
                "unsure" -> "q10" // skip 8-9
                else -> "q8"
            }
            "q8" -> if (value() == "yes") "q9" else "q10"
            "q9" -> "q10"
            "q10" -> "q11"
            "q11" -> "q12"
            "q12" -> if (value() == "no") null else "q13"
            "q13" -> null
            else -> null
        }
    }

    fun questionById(id: String): Question? = questions.firstOrNull { it.id == id }
}

