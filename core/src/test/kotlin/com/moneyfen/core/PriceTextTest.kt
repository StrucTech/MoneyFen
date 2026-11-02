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
    @Test fun missing() { assertFailsWith<IllegalStateException> { PriceText.extract(html, "غير موجود") } }
}
