package com.moneyfen.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PriceTextTest {
    private val html = """<html><head><script>var x = 999.99;</script></head><body>
        <div class="fund"><span>سعر الوثيقة</span> <b>٢٣٫٤٥</b> جنيه</div>
        <td>NAV</td><td>1,234.5678</td></body></html>"""

    @Test fun anchorArabic() = assertEquals(23.45, PriceText.extract(html, "سعر الوثيقة"))
    @Test fun anchorEnglishWithThousands() = assertEquals(1234.5678, PriceText.extract(html, "nav"))
    @Test fun regex() = assertEquals(1234.5678, PriceText.extract(html, "NAV ([\\d,.]+)"))
    @Test fun firstNumber() {
        assertEquals(18.75717, PriceText.firstNumber("NAV  18.75717 EGP"))
        assertEquals(1234.5, PriceText.firstNumber("السعر ١,٢٣٤٫٥ جنيه"))
    }

    @Test fun splitDigits() {
        assertEquals(18.16, PriceText.firstNumber("NAV\n1\n8\n.\n1\n6\nEGP"))
        assertEquals(18.16, NavFinder.find("<div>NAV</div><div><span>1</span><span>8</span><span>.</span><span>1</span><span>6</span></div><div>EGP</div>", null))
    }

    @Test fun missing() { assertFailsWith<IllegalStateException> { PriceText.extract(html, "غير موجود") } }
}
