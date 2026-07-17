package org.muilab.notigpt.domain.saveditem

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedItemActionButtonsTest {
    @Test
    fun `keeps grounded descriptive link text`() {
        assertEquals(
            "Renew membership",
            SavedItemActionButtons.linkDisplayText(
                "Renew membership",
                "https://example.com/account/renew?token=secret",
            ),
        )
    }

    @Test
    fun `uses host and semantic short path for generic label`() {
        assertEquals(
            "example.com/account/renew",
            SavedItemActionButtons.linkDisplayText(
                "Open link",
                "https://www.example.com/account/renew?token=secret#billing",
            ),
        )
    }

    @Test
    fun `uses host only for opaque short-link path`() {
        assertEquals(
            "maps.app.goo.gl",
            SavedItemActionButtons.fallbackLinkLabel("https://maps.app.goo.gl/AbCdEf123456"),
        )
    }

    @Test
    fun `uses host only for uuid path`() {
        assertEquals(
            "example.com",
            SavedItemActionButtons.fallbackLinkLabel(
                "https://example.com/123e4567-e89b-12d3-a456-426614174000?source=notification",
            ),
        )
    }
}
