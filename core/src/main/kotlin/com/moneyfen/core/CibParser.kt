package com.moneyfen.core

/**
 * Parser for CIB Egypt SMS messages (Arabic + English templates).
 * Every template was taken from real messages; see CibParserTest.
 */
object CibParser {
    private const val AMT = "([\\d,]+(?:\\.\\d+)?)"
    private const val ACC = "\\*{0,20}(\\d{3,6})"

    private class Template(val regex: Regex, val build: (MatchResult) -> ParseResult)

    private fun r(p: String) = Regex(p, setOf(RegexOption.IGNORE_CASE))
    private fun MatchResult.g(i: Int) = groupValues[i].trim()
    private fun MatchResult.a(i: Int) = groupValues[i].toAmount() ?: 0.0
    private fun tx(t: ParsedSms) = ParseResult.Tx(t)

    private val templates = listOf(
        // InstaPay in: يرجى العلم انه تم تنفيذ تحويل لحظي بمبلغ 1.00 جم إلى حسابك المنتهي بـ ********1234 من NAME برقم مرجعي aaaa1111 بتاريخ ...
        Template(r("تحويل لحظي بمبلغ $AMT جم إلى حسابك المنتهي بـ ?$ACC من (.+?) برقم مرجعي (\\S+)")) {
            tx(ParsedSms(TxKind.TRANSFER_IN, it.a(1), accountHint = it.g(2), party = it.g(3), reference = it.g(4)))
        },
        // InstaPay out: تحويل لحظي بمبلغ 300.00 جم من حسابك المنتهي بـ ********1234 برقم مرجعي 0000000000
        Template(r("تحويل لحظي بمبلغ $AMT جم من حسابك المنتهي بـ ?$ACC(?: إلى (.+?))? برقم مرجعي (\\S+)")) {
            tx(ParsedSms(TxKind.TRANSFER_OUT, it.a(1), accountHint = it.g(2), party = it.g(3).ifBlank { null }, reference = it.g(4)))
        },
        // Instant purchase: عملية شراء لحظي من حسابك المنتهي بـ ********1234 بمبلغ 128.58 جم من Mobile Recharge برقم مرجعي x بتاريخ .. الرصيد المتاح 1229.57 جم
        Template(r("عملية شراء لحظي من حسابك المنتهي بـ ?$ACC بمبلغ $AMT جم من (.+?) برقم مرجعي (\\S+).*?الرصيد المتاح $AMT")) {
            tx(ParsedSms(TxKind.PURCHASE, it.a(2), accountHint = it.g(1), party = it.g(3), reference = it.g(4), balanceAfter = it.a(5)))
        },
        // Debit purchase: تم خصم مبلغ EGP 128.58 من بطاقة الخصم المباشر المنتهية بـ **5678 عند WE Mobile Pre في 15/07/25 17:12 ، الرصيد المتاح 2818.15 EGP
        Template(r("تم خصم مبلغ (?:EGP )?$AMT(?: EGP)? من بطاقة الخصم المباشر المنتهية بـ ?$ACC عند (.+?) في .*?الرصيد المتاح (?:EGP )?$AMT")) {
            tx(ParsedSms(TxKind.PURCHASE, it.a(1), accountHint = it.g(2), party = it.g(3), balanceAfter = it.a(4)))
        },
        // ATM: تم سحب مبلغ EGP 6000.00 من بطاقة الخصم المباشر المنتهية بـ **5678 من CIB ATM في 28/11/24 20:01 ، الرصيد المتاح EGP 15274.88
        Template(r("تم سحب مبلغ (?:EGP )?$AMT(?: EGP)? من بطاقة الخصم المباشر المنتهية بـ ?$ACC من (.+?) في .*?الرصيد المتاح (?:EGP )?$AMT")) {
            tx(ParsedSms(TxKind.ATM_WITHDRAWAL, it.a(1), accountHint = it.g(2), party = it.g(3), balanceAfter = it.a(4)))
        },
        // Cash deposit: تم إيداع مبلغ 9800.00 EGP في CIB BRANCH في بطاقتك المنتهية بـ **5678 بتاريخ .. ،الرصيد المتاح 9800.18 EGP
        Template(r("تم إيداع مبلغ (?:EGP )?$AMT(?: EGP)? في (.+?) في بطاقتك المنتهية بـ ?$ACC.*?الرصيد المتاح (?:EGP )?$AMT")) {
            tx(ParsedSms(TxKind.DEPOSIT, it.a(1), accountHint = it.g(3), party = it.g(2), balanceAfter = it.a(4)))
        },
        // Debit reversal: تم اضافة المعاملة من PAYMOB DR El Khalil بقيمة 81.60 EGP إلى بطاقتك المنتهية بـ **5678 . الرصيد المتاح EGP 6153.93
        Template(r("تم [اإ]ضافة المعاملة من (.+?) بقيمة $AMT (?:EGP )?إلى بطاقتك المنتهية بـ ?$ACC.*?الرصيد المتاح (?:EGP )?$AMT")) {
            tx(ParsedSms(TxKind.REFUND, it.a(2), accountHint = it.g(3), party = it.g(1), balanceAfter = it.a(4)))
        },
        // Salary: عميلنا العزيز لقد تم تحويل مبلغ EGP11,421.64 على حسابكم لدينا من جهة العمل
        Template(r("تم تحويل مبلغ (?:EGP ?)?$AMT(?: EGP)? على حسابكم لدينا من جهة العمل")) {
            tx(ParsedSms(TxKind.SALARY, it.a(1), party = "Salary"))
        },
        // Credit card charge (EGP or foreign):
        // Your credit card ending with#4321 was charged for EGP 344.99 at Talabat on 25/07/26 at 01:36. Card available limit is EGP 55180.62.
        // Your credit card #4321 was charged for USD 22.80 at ANTHROPIC CLAU on 27/08/26 at 18:57. Available limit is 45496.76 and ...
        Template(r("credit card (?:ending with ?)?# ?(\\d{3,6}) was charged for ([A-Z]{3}) ?$AMT at (.+?) on [\\d/]+ at [\\d:]+\\.? (?:Card )?available limit is (?:EGP )?$AMT")) {
            tx(ParsedSms(TxKind.CC_CHARGE, it.a(3), currency = it.g(2).uppercase(), accountHint = it.g(1), party = it.g(4), availableAfter = it.a(5)))
        },
        // Credit card charge without limit (fallback)
        Template(r("credit card (?:ending with ?)?# ?(\\d{3,6}) was charged for ([A-Z]{3}) ?$AMT at (.+?) on ")) {
            tx(ParsedSms(TxKind.CC_CHARGE, it.a(3), currency = it.g(2).uppercase(), accountHint = it.g(1), party = it.g(4)))
        },
        // Credit card reversal: The transaction on your credit card#4321 from GEIDEA OAKBAY R with EGP 1168.00 on .. has been refunded
        Template(r("transaction on your credit card ?# ?(\\d{3,6}) from (.+?) with ([A-Z]{3}) ?$AMT .*?has been refunded")) {
            tx(ParsedSms(TxKind.CC_REFUND, it.a(4), currency = it.g(3).uppercase(), accountHint = it.g(1), party = it.g(2)))
        },
        // Credit card refund: لقد تم رد USD18.82 على بطاقتكم الائتمانية المنتهية بـ# 4321 من ATOMS>
        Template(r("تم رد ([A-Z]{3}) ?$AMT على بطاقتكم الائتمانية المنتهية بـ ?# ?(\\d{3,6}) من ([^.>]+)")) {
            tx(ParsedSms(TxKind.CC_REFUND, it.a(2), currency = it.g(1).uppercase(), accountHint = it.g(3), party = it.g(4)))
        },
        // Credit card payment: تم سداد مبلغ 44856.38 جم فى بطاقتكم الائتمانية المنتهية بـ 4321
        Template(r("تم سداد مبلغ $AMT جم ف[ىي] بطاقتكم الائتمانية المنتهية بـ ?#? ?(\\d{3,6})")) {
            tx(ParsedSms(TxKind.CC_PAYMENT, it.a(1), accountHint = it.g(2), party = "Card payment"))
        },
        // Cashback: لقد تم إضافة كاش باك بقيمة 1100.00 جم على بطاقتكم الائتمانية المنتهية بـ 4321
        Template(r("تم [اإ]ضافة كاش باك بقيمة $AMT جم على بطاقتكم الائتمانية المنتهية بـ ?#? ?(\\d{3,6})")) {
            tx(ParsedSms(TxKind.CASHBACK, it.a(1), accountHint = it.g(2), party = "Cashback"))
        },
        // External transfer: The external transfer you requested ... from account ending with ********1234 with amount EGP 495.00DR has been processed
        Template(r("external transfer .*?account ending with ?$ACC with amount (?:EGP )?$AMT ?DR")) {
            tx(ParsedSms(TxKind.TRANSFER_OUT, it.a(2), accountHint = it.g(1), party = "External transfer"))
        },
        // Account debit: Your account ending with ********1234 is debited with amount EGP 50,246.00DR On 30 SEP 2024 with transfer to another account.
        Template(r("account ending with ?$ACC is debited with amount (?:EGP )?$AMT ?DR.*?with (.+?)\\.")) {
            tx(ParsedSms(TxKind.TRANSFER_OUT, it.a(2), accountHint = it.g(1), party = it.g(3).replaceFirstChar { c -> c.uppercase() }))
        },
        // Account credit (English, defensive)
        Template(r("account ending with ?$ACC is credited with amount (?:EGP )?$AMT ?CR(?:.*?with (.+?)\\.)?")) {
            tx(ParsedSms(TxKind.TRANSFER_IN, it.a(2), accountHint = it.g(1), party = it.g(3).ifBlank { null }))
        },
        // Statement: كشف حساب شهر ابريل 2026 لبطاقتك الائتمانية المنتهية بـ 4321 هو 44856.38 جم ، الحد الأدنى للسداد قبل 05/05 هو 2242.81 جم
        Template(r("كشف حساب شهر .+? لبطاقتك الائتمانية المنتهية بـ ?(\\d{3,6}) هو $AMT جم ، الحد الأدنى للسداد قبل ([\\d/]+) هو $AMT")) {
            ParseResult.Info(ParsedInfo(InfoKind.CC_STATEMENT, it.g(1), it.a(2), minDue = it.a(4), dueDate = it.g(3)))
        },
        // New credit limit: بطاقة ... الائتمانية الجديدة الخاصة بك بحد ائتماني 59500 جم
        Template(r("الائتمانية .*?بحد ائتماني $AMT")) {
            ParseResult.Info(ParsedInfo(InfoKind.CREDIT_LIMIT, null, it.a(1)))
        },
    )

    private val ignored = listOf(
        r("تم رفض المعاملة"), r("OTP"), r("الرقم السر"), r("invalid IPN PIN"), r("token"),
        r("تم استلام الطلب لرد مبلغ"),
    )

    fun parse(body: String): ParseResult {
        val text = body.normalizeSpaces()
        for (t in templates) {
            val m = t.regex.find(text) ?: continue
            val res = t.build(m)
            if (res is ParseResult.Tx && res.tx.amount <= 0.0) continue
            return res
        }
        if (ignored.any { it.containsMatchIn(text) }) return ParseResult.Ignored
        return ParseResult.Unknown
    }
}
