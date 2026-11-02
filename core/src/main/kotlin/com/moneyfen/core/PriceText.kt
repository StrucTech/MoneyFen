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
