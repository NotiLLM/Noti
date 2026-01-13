package org.muilab.notigpt.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotiSearchQueryBuilderTest {

    @Test
    fun `build with empty input matches all records`() {
        val q = NotiSearchQueryBuilder.build(rawInput = "   ")
        assertTrue(q.sql.contains("1 = 1"))
        // Search always scans all noti_record rows (no isVisible/isDismissed filter at query-builder level)
        assertTrue(!q.sql.contains("isVisible"))
        assertTrue(!q.sql.contains("isDismissed"))
        assertEquals(emptyList<Any>(), q.args)
    }

    @Test
    fun `build parses quoted phrases into LIKE args`() {
        val built = NotiSearchQueryBuilder.build(rawInput = "\"baseball match\"")

        // One phrase => 1 condition with 4 LIKE placeholders
        assertTrue(built.sql.contains("extraText LIKE ?"))
        assertEquals(4, built.args.size)
        built.args.forEach { assertEquals("%baseball match%", it) }
    }

    @Test
    fun `build parses plus terms as AND`() {
        val built = NotiSearchQueryBuilder.build(rawInput = "urgent+tomorrow")

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
        val built = NotiSearchQueryBuilder.build(rawInput = "\"pay rent\"+today")

        // One phrase (4 args) + one term (4 args) => 8 args
        assertEquals(8, built.args.size)
        assertEquals("%pay rent%", built.args[0])
        assertEquals("%today%", built.args[4])
    }
}
