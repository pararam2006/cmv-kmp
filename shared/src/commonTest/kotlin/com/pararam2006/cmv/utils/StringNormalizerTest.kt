package com.pararam2006.cmv.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringNormalizerTest {
    @Test
    fun normalizesNotificationText() {
        assertEquals("track title", StringNormalizer.normalize("  TRACK\u00A0  ⓘTitle  "))
    }

    @Test
    fun matchesNormalizedPartialTitles() {
        assertTrue(StringNormalizer.matches("Track Title", "Track Title (Remastered)"))
    }
}
