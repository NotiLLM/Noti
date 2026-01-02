package org.muilab.notigpt.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationFilterTest {

    @Test
    fun `ignoreKeyRules matches historical connectivity rules`() {
        assertEquals(
            NotificationFilter.IgnoreReason.CONNECTIVITY_NOTIFICATION,
            NotificationFilter.NotificationKeyRules.ignoreReasonForKey("abc ConnectivityNotification def")
        )
        assertEquals(
            NotificationFilter.IgnoreReason.ANDROID_WIFI,
            NotificationFilter.NotificationKeyRules.ignoreReasonForKey("com.android.wifi:foo")
        )
        assertEquals(
            NotificationFilter.IgnoreReason.ANDROID_NETWORKSTACK,
            NotificationFilter.NotificationKeyRules.ignoreReasonForKey("com.google.android.networkstack:bar")
        )

        assertNull(NotificationFilter.NotificationKeyRules.ignoreReasonForKey("normal:key"))
    }
}
