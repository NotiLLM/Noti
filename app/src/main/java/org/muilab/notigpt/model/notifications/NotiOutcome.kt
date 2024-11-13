package org.muilab.notigpt.model.notifications

data class NotiOutcome(
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
