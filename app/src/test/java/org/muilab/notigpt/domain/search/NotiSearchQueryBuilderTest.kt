package org.muilab.notigpt.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotiSearchQueryBuilderTest {

    @Test
    fun `build with empty input matches all and respects includeHistory`() {
        val qNoHistory = NotiSearchQueryBuilder.build(rawInput = "   ", includeHistory = false)
        assertTrue(qNoHistory.sql.contains("1 = 1"))
        assertTrue(qNoHistory.sql.contains("AND isVisible = 1"))
        assertEquals(emptyList<Any>(), qNoHistory.args)

        val qHistory = NotiSearchQueryBuilder.build(rawInput = "", includeHistory = true)
        assertTrue(qHistory.sql.contains("1 = 1"))
        assertTrue(!qHistory.sql.contains("isVisible = 1"))
        assertEquals(emptyList<Any>(), qHistory.args)
    }

    @Test
    fun `build parses quoted phrases into LIKE args`() {
        val built = NotiSearchQueryBuilder.build(rawInput = "\"baseball match\"", includeHistory = false)

        // One phrase => 1 condition with 4 LIKE placeholders
        assertTrue(built.sql.contains("extraText LIKE ?"))
        assertEquals(4, built.args.size)
        built.args.forEach { assertEquals("%baseball match%", it) }
    }

    @Test
    fun `build parses plus terms as AND`() {
        val built = NotiSearchQueryBuilder.build(rawInput = "urgent+tomorrow", includeHistory = true)

        // Two terms => 2 conditions => 8 args
        assertEquals(8, built.args.size)
        // verify ordering
        assertEquals("%urgent%", built.args[0])
        assertEquals("%tomorrow%", built.args[4])

        // Must join with AND
        val andCount = built.sql.split(" AND ").size - 1
        assertTrue(andCount >= 1)
    }

    @Test
    fun `build supports quotes plus remaining plus terms`() {
        val built = NotiSearchQueryBuilder.build(rawInput = "\"pay rent\"+today", includeHistory = false)

        // One phrase (4 args) + one term (4 args) => 8 args
        assertEquals(8, built.args.size)
        assertEquals("%pay rent%", built.args[0])
        assertEquals("%today%", built.args[4])
    }
}

