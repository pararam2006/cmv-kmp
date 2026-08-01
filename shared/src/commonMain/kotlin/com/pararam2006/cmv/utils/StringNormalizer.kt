package com.pararam2006.cmv.utils

object StringNormalizer {
    /**
     * Normalizes a track title or artist name by:
     * 1. Removing special characters (like ⓘ)
     * 2. Trimming whitespace
     * 3. Converting to lowercase
     * 4. Removing extra internal spaces
     */
    fun normalize(input: String?): String {
        if (input == null) return ""
        
        return input
            // Remove common special characters found in music notifications
            .replace("ⓘ", "")
            .replace("\u00A0", " ") // Replace non-breaking space
            // Keep only letters, digits, and basic punctuation, but really we want to be permissive
            // Let's just trim and lowercase for now, plus removing the specific 'ⓘ'
            .trim()
            .lowercase()
            .replace("\\s+".toRegex(), " ")
    }

    fun matches(stored: String, current: String): Boolean {
        val normStored = normalize(stored)
        val normCurrent = normalize(current)
        return normStored == normCurrent || normCurrent.contains(normStored) || normStored.contains(normCurrent)
    }
}
