package org.muilab.notigpt.model.notifications

data class NotiOutcome(
    var embeddingString: String = "",
    var similarityScore: Double = 0.0,
    var explanation: String = "",
    var characteristicsJSONString: String = "",
    var summary: String = "",
    var sortScore: Double = 100.0,
    var category: String = ""
) {
    fun resetOutcomes() {
        summary = ""
        explanation = ""
        sortScore = 30.0
    }
}
