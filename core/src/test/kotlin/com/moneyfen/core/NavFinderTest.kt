package com.moneyfen.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NavFinderTest {
    @Test fun nextDataJson() {
        val html = """<script id="__NEXT_DATA__">{"props":{"fund":{"symbol":"AZN","nav":18.75717,"date":"2026-11-02"}}}</script><div>loading</div>"""
        assertEquals(18.75717, NavFinder.find(html, null))
    }

    @Test fun arabicLabel() {
        val html = "<div><span>صافي قيمة الوثيقة</span><b>٢١٣٫١٥</b> جنيه</div><div>عدد الوثائق 1,000,000</div>"
        assertEquals(213.15, NavFinder.find(html, null))
    }

    @Test fun sanityPicksCloseValue() {
        val html = "<p>Price 5 EGP min</p><p>NAV 213.40</p>"
        assertEquals(213.40, NavFinder.find(html, 212.87))
    }

    @Test fun rejectsFarValues() {
        assertFailsWith<IllegalStateException> { NavFinder.find("<p>Price 5</p>", 212.87) }
    }
}
