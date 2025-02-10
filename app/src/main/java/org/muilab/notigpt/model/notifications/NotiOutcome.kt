package org.muilab.notigpt.model.notifications

data class NotiOutcome(
    var embeddingString: String = "",
    var similarityScore: Double = 0.0,
    var score: Double = 100.0,
    var explanation: String = "",
    var summary: String = ""
) {
    fun resetOutcomes() {
        summary = ""
        explanation = ""
        score = 30.0
    }
}
