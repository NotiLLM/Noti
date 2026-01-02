package org.muilab.notigpt.domain.search

import androidx.sqlite.db.SimpleSQLiteQuery

/**
 * Builds the same raw SQL query previously assembled inside `NotiRepository.searchNotifications`.
 *
 * Why:
 * - The parsing rules (quotes + '+' AND-composition) are business logic and should be testable on the JVM.
 * - Keeping SQL generation in one place reduces regressions when tweaking search behavior.
 *
 * Contract:
 * - `rawInput` may contain quoted phrases: "baseball match"
 * - `+` combines terms using AND logic: urgent+tomorrow
 * - If `includeHistory` is false, we add `AND isVisible = 1`
 */
object NotiSearchQueryBuilder {

    data class BuiltQuery(
        val sql: String,
        val args: List<Any>,
    ) {
        fun toSQLiteQuery(): SimpleSQLiteQuery = SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    fun build(rawInput: String, includeHistory: Boolean): BuiltQuery {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<Any>()

        // 1) Parse exact phrases in quotes
        val quoteRegex = "\"([^\"]*)\"".toRegex()
        var remainingInput = rawInput

        quoteRegex.findAll(rawInput).forEach { match ->
            val phrase = match.groupValues[1]
            if (phrase.isNotBlank()) {
                conditions.add("(extraText LIKE ? OR extraBigText LIKE ? OR extraTitle LIKE ? OR person LIKE ?)")
                val like = "%$phrase%"
                repeat(4) { args.add(like) }
            }
        }

        // Remove quotes from input to process remaining keywords
        remainingInput = quoteRegex.replace(remainingInput, " ")

        // 2) Parse '+' combined keywords (AND logic)
        val terms = remainingInput
            .split("+")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        terms.forEach { term ->
            conditions.add("(extraText LIKE ? OR extraBigText LIKE ? OR extraTitle LIKE ? OR person LIKE ?)")
            val like = "%$term%"
            repeat(4) { args.add(like) }
        }

        val whereClause = if (conditions.isNotEmpty()) conditions.joinToString(" AND ") else "1 = 1"
        val visibilityClause = if (includeHistory) "" else " AND isVisible = 1"

        val sql = "SELECT * FROM noti_record WHERE $whereClause $visibilityClause ORDER BY whenTime DESC LIMIT 100"

        return BuiltQuery(sql = sql, args = args)
    }
}

