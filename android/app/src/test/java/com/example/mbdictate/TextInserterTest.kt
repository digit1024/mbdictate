package com.example.mbdictate

import com.example.mbdictate.insert.TextInserter
import org.junit.Assert.assertEquals
import org.junit.Test

class TextInserterTest {
    @Test
    fun placeholderTextIsIgnored() {
        assertEquals(
            "",
            TextInserter.effectiveExistingText(
                text = "Wyszukaj aplikacje",
                hint = "Wyszukaj aplikacje",
                showingHint = true,
            ),
        )
    }

    @Test
    fun hintMatchIsIgnoredWithoutFlag() {
        assertEquals(
            "",
            TextInserter.effectiveExistingText(
                text = "Search apps",
                hint = "Search apps",
                showingHint = false,
            ),
        )
    }

    @Test
    fun realContentIsKept() {
        assertEquals(
            "hello ",
            TextInserter.effectiveExistingText(
                text = "hello ",
                hint = "Search apps",
                showingHint = false,
            ),
        )
        assertEquals(
            "",
            TextInserter.effectiveExistingText(
                text = null,
                hint = "Search apps",
                showingHint = true,
            ),
        )
    }
}