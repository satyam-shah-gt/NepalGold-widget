package com.goldrate.widget.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `formats whole amounts without decimals`() {
        assertEquals("243,055", Format.amount(243055.0, AppLang.EN))
        assertEquals("0", Format.amount(0.0, AppLang.EN))
    }

    @Test
    fun `keeps two decimals for fractional amounts`() {
        assertEquals("3,369.50", Format.amount(3369.5, AppLang.EN))
    }

    @Test
    fun `groups english amounts in threes`() {
        assertEquals("1,234,567,890", Format.amount(1234567890.0, AppLang.EN))
        assertEquals("12,345,678", Format.amount(12345678.0, AppLang.EN))
    }

    @Test
    fun `uses devanagari digits and south asian grouping in nepali`() {
        assertEquals("२,४३,०५५", Format.amount(243055.0, AppLang.NE))
        assertEquals("३,३६९.५०", Format.amount(3369.5, AppLang.NE))
        assertEquals("१,२३,४५,६७,८९०", Format.amount(1234567890.0, AppLang.NE))
    }

    @Test
    fun `keeps the sign of a change`() {
        assertEquals("+255", Format.signed(255.0, AppLang.EN))
        assertEquals("-42.50", Format.signed(-42.5, AppLang.EN))
        assertEquals("0", Format.signed(0.0, AppLang.EN))
    }

    @Test
    fun `renders an em dash for unusable values`() {
        assertEquals(Format.EM_DASH, Format.amount(Double.NaN, AppLang.EN))
        assertEquals(Format.EM_DASH, Format.signed(Double.NaN, AppLang.EN))
    }

    @Test
    fun `converts between ascii and devanagari digits`() {
        assertEquals("१०", Format.toDevanagari("10"))
        assertEquals("10", Format.toLatinDigits("१०"))
    }

    @Test
    fun `keeps the percent sign in both languages`() {
        assertEquals("+0.11%", Format.signedPercent(320.0, 283200.0, AppLang.EN))
        assertEquals("-1.25%", Format.signedPercent(-320.0, 25600.0, AppLang.EN))
        // The Nepali form used to drop the percent sign entirely.
        assertEquals("+०.११%", Format.signedPercent(320.0, 283200.0, AppLang.NE))
        assertEquals("-१.२५%", Format.signedPercent(-320.0, 25600.0, AppLang.NE))
    }

    @Test
    fun `renders chart axis labels in the language's digits`() {
        assertEquals("286k", Format.axisLabel(286000.0, AppLang.EN))
        assertEquals("39.4k", Format.axisLabel(39400.0, AppLang.EN))
        assertEquals("3,936", Format.axisLabel(3936.0, AppLang.EN))

        assertEquals("२८६k", Format.axisLabel(286000.0, AppLang.NE))
        assertEquals("३९.४k", Format.axisLabel(39400.0, AppLang.NE))
        assertEquals("३,९३६", Format.axisLabel(3936.0, AppLang.NE))
    }

    @Test
    fun `rounds axis labels to whole numbers`() {
        assertEquals("286k", Format.axisLabel(286400.0, AppLang.EN))
        assertEquals("3,936", Format.axisLabel(3936.4, AppLang.EN))
    }
}

class RateLabelsTest {

    @Test
    fun `translates the endpoint labels into english`() {
        assertEquals(
            "Printed Gold (10 gram)",
            RateLabels.display("छापावाल सुन (१० ग्राम)", AppLang.EN),
        )
        assertEquals(
            "Printed Gold (1 tola)",
            RateLabels.display("छापावाल सुन (१ तोला)", AppLang.EN),
        )
        assertEquals(
            "Real Silver (10 gram)",
            RateLabels.display("असली चाँदी दर (१० ग्राम)", AppLang.EN),
        )
        assertEquals(
            "Real Silver (1 tola)",
            RateLabels.display("असली चाँदी दर (१ तोला)", AppLang.EN),
        )
    }

    @Test
    fun `keeps the endpoint label in nepali mode`() {
        val raw = "छापावाल सुन (१० ग्राम)"
        assertEquals(raw, RateLabels.display(raw, AppLang.NE))
    }

    @Test
    fun `falls back to the raw label when it cannot be interpreted`() {
        val raw = "कुनै अज्ञात दर"
        assertEquals(raw, RateLabels.display(raw, AppLang.EN))
    }
}
