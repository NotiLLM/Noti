package org.muilab.notigpt.domain.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotiActionTypeTest {

    @Test
    fun `fromWireValue maps known values`() {
        assertEquals(NotiActionType.DismissSwipe, NotiActionType.fromWireValue("dismiss_swipe"))
        assertEquals(NotiActionType.Pin, NotiActionType.fromWireValue("pin"))
        assertEquals(NotiActionType.Unpin, NotiActionType.fromWireValue("unpin"))
        assertEquals(NotiActionType.AccessClickDismiss, NotiActionType.fromWireValue("access_click_dismiss"))
        assertEquals(NotiActionType.AccessClickSearch, NotiActionType.fromWireValue("access_click_search"))
        assertEquals(NotiActionType.MakeTodo, NotiActionType.fromWireValue("make_task"))
        assertEquals(NotiActionType.MakeTodo, NotiActionType.fromWireValue("make_todo"))
    }

    @Test
    fun `fromWireValue returns null for unknown values`() {
        assertNull(NotiActionType.fromWireValue("unknown_action"))
    }
}
