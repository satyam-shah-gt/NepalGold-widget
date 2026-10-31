package com.goldrate.widget.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The transparency levels of the widget's surface.
 *
 * The level is stored as a code, so a code that is not known - an older install, a typo - has to
 * fall back to the level the widget ships with instead of failing.
 */
class TransparencyTest {

    @Test
    fun `maps the stored codes`() {
        assertEquals(Transparency.SUBTLE, Transparency.from("subtle"))
        assertEquals(Transparency.BALANCED, Transparency.from("balanced"))
        assertEquals(Transparency.SOLID, Transparency.from("solid"))
    }

    @Test
    fun `falls back to the default level`() {
        assertEquals(Transparency.BALANCED, Transparency.from(null))
        assertEquals(Transparency.BALANCED, Transparency.from(""))
        assertEquals(Transparency.BALANCED, Transparency.from("something-else"))
    }

    @Test
    fun `ships with the balanced level`() {
        assertEquals(Transparency.BALANCED, Transparency.DEFAULT)
    }

    @Test
    fun `every level has its own code`() {
        assertEquals(
            Transparency.entries.size,
            Transparency.entries.map { it.code }.toSet().size,
        )
    }
}
