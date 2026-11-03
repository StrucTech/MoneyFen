package com.moneyfen.core

/** Pulls a price out of a web page's HTML. */
object PriceText {
    fun extract(html: String, anchor: String?): Double {
        val text = html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace(Regex("\\s+"), " ")
            .map { ch -> if (ch in '٠'..'٩') '0' + (ch - '٠') else if (ch == '٫') '.' else ch }.joinToString("")
        val num = Regex("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?")
        val a = anchor?.trim().orEmpty()
        val value: String = when {
            a.contains("(") -> Regex(a, RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)
                ?: Regex(a, RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1)
                ?: error("Regex didn't match / الريجيكس مش لاقي حاجة")
            a.isNotEmpty() -> {
                val i = text.indexOf(a, ignoreCase = true)
                if (i < 0) error("\"$a\" not found on the page (the page may load the price with JavaScript) / النص مش موجود في الصفحة")
                num.find(text, i + a.length)?.value ?: error("No number after \"$a\"")
            }
            else -> error("Write the text that comes before the price / اكتب النص اللي قبل السعر")
        }
        return value.replace(",", "").toDoubleOrNull() ?: error("Bad number: $value")
    }
}

/** Finds a fund NAV / price in a page without knowing the exact layout. */
object NavFinder {
    private const val NUM = "(\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)"
    private val jsonKeys = listOf("nav", "navValue", "lastNav", "last_nav", "navPrice", "certificatePrice", "lastPrice", "last_price", "price", "close")
    private val labels = listOf(
        "صافي قيمة الوثيقة", "سعر الوثيقة", "قيمة الوثيقة", "آخر سعر", "سعر الشهادة",
        "Net Asset Value", "NAV", "Certificate Price", "Last Price", "Price", "السعر",
    )

    private fun toNum(s: String) = s.replace(",", "").toDoubleOrNull()

    /** All plausible candidates, best first. */
    fun candidates(html: String): List<Double> {
        val out = mutableListOf<Double>()
        for (k in jsonKeys) {
            Regex("\"$k\"\\s*:\\s*\"?$NUM", RegexOption.IGNORE_CASE).findAll(html).forEach { m -> toNum(m.groupValues[1])?.let(out::add) }
        }
        val text = html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace(Regex("\\s+"), " ")
            .map { ch -> if (ch in '٠'..'٩') '0' + (ch - '٠') else if (ch == '٫') '.' else ch }.joinToString("")
        for (l in labels) {
            var i = text.indexOf(l, ignoreCase = true)
            while (i >= 0) {
                val after = text.substring(i + l.length, minOf(text.length, i + l.length + 40))
                Regex(NUM).find(after)?.let { m -> toNum(m.value)?.let(out::add) }
                i = text.indexOf(l, i + l.length, ignoreCase = true)
            }
        }
        return out.filter { it > 0.0001 }.distinct()
    }

    /**
     * Best NAV. With a previous price, only accepts a value within ±[maxMovePct]% of it
     * (funds don't jump), so a wrong number on the page is never saved.
     */
    fun find(html: String, last: Double?, maxMovePct: Double = 25.0): Double {
        val c = candidates(html)
        if (c.isEmpty()) error("No price found on the page / ملقتش سعر في الصفحة")
        if (last == null || last <= 0) return c.first()
        return c.firstOrNull { kotlin.math.abs(it - last) / last * 100 <= maxMovePct }
            ?: error("Found ${c.take(3)} but none is close to the last price $last / الأرقام بعيدة عن آخر سعر")
    }
}
