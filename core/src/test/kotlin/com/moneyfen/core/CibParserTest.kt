package com.moneyfen.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CibParserTest {
    private fun tx(s: String): ParsedSms {
        val r = CibParser.parse(s)
        assertIs<ParseResult.Tx>(r, "not parsed: $s")
        return r.tx
    }

    @Test fun instapayIn() {
        val t = tx("يرجى العلم انه تم تنفيذ تحويل لحظي بمبلغ 1,200.50 جم إلى حسابك المنتهي بـ ********1234 من JOHN DOE برقم مرجعي 166aea19 بتاريخ 19-09-2024 22:49 للمزيد، برجاء الاتصال بـ 19666")
        assertEquals(TxKind.TRANSFER_IN, t.kind); assertEquals(1200.50, t.amount); assertEquals("1234", t.accountHint); assertEquals("JOHN DOE", t.party)
    }

    @Test fun instapayOut() {
        val t = tx("يرجى العلم انه تم تنفيذ تحويل لحظي بمبلغ 300.00 جم من حسابك المنتهي بـ ********1234 برقم مرجعي 0948578909 بتاريخ 04-11-2024 18:06 للمزيد، برجاء الاتصال بـ 19666")
        assertEquals(TxKind.TRANSFER_OUT, t.kind); assertEquals(300.0, t.amount); assertEquals("1234", t.accountHint)
    }

    @Test fun instantPurchase() {
        val t = tx("يرجى العلم انه تم تنفيذ عملية شراء لحظي من حسابك المنتهي بـ ********1234 بمبلغ 128.58 جم من Mobile Recharge برقم مرجعي 0bee2bd5 بتاريخ 19-07-2025 20:37 الرصيد المتاح 1229.57 جم للمزيد، برجاء الاتصال بـ 19666.")
        assertEquals(TxKind.PURCHASE, t.kind); assertEquals(128.58, t.amount); assertEquals("Mobile Recharge", t.party); assertEquals(1229.57, t.balanceAfter)
    }

    @Test fun debitPurchase() {
        val t = tx("تم خصم مبلغ  EGP 128.58 من بطاقة الخصم المباشر المنتهية بـ **5678 عند WE Mobile Pre في 15/07/25 17:12 ، الرصيد المتاح 2818.15 EGP")
        assertEquals(TxKind.PURCHASE, t.kind); assertEquals("5678", t.accountHint); assertEquals("WE Mobile Pre", t.party); assertEquals(2818.15, t.balanceAfter)
    }

    @Test fun atm() {
        val t = tx("تم سحب مبلغ  EGP 6000.00  من بطاقة الخصم المباشر المنتهية بـ **5678 من          ATM في 28/11/24 20:01 ، الرصيد المتاح EGP 15274.88")
        assertEquals(TxKind.ATM_WITHDRAWAL, t.kind); assertEquals(6000.0, t.amount); assertEquals("ATM", t.party); assertEquals(15274.88, t.balanceAfter)
    }

    @Test fun deposit() {
        val t = tx("تم إيداع مبلغ 9800.00 EGP  في CIB ZAHRAN SMOUHA BNAZ في بطاقتك المنتهية بـ **5678 بتاريخ 18/01/26 23:06 ،الرصيد المتاح 9800.18 EGP")
        assertEquals(TxKind.DEPOSIT, t.kind); assertEquals(9800.0, t.amount); assertEquals(9800.18, t.balanceAfter)
    }

    @Test fun reversal() {
        val t = tx("تم اضافة المعاملة من PAYMOB DR El Khalil بقيمة 81.60 EGP إلى بطاقتك المنتهية بـ **5678 . الرصيد المتاح EGP 6153.93")
        assertEquals(TxKind.REFUND, t.kind); assertEquals(81.6, t.amount); assertEquals(6153.93, t.balanceAfter)
    }

    @Test fun salary() {
        val t = tx("عميلنا العزيز لقد تم تحويل مبلغ EGP11,421.64 على حسابكم لدينا من جهة العمل")
        assertEquals(TxKind.SALARY, t.kind); assertEquals(11421.64, t.amount)
    }

    @Test fun ccCharge() {
        val t = tx("Your credit card ending with#4321 was charged for EGP 344.99 at Talabat on 25/07/26  at 01:36. Card available limit is EGP  55180.62. For more details, please visit https://cib.eg/mb")
        assertEquals(TxKind.CC_CHARGE, t.kind); assertEquals("4321", t.accountHint); assertEquals("Talabat", t.party); assertEquals(55180.62, t.availableAfter)
    }

    @Test fun ccChargeUsd() {
        val t = tx("Your credit card #4321 was charged for USD 22.80 at ANTHROPIC CLAU on 27/08/26  at 18:57. Available limit is  45496.76 and the available international limit to use is EGP 153321")
        assertEquals("USD", t.currency); assertEquals(22.8, t.amount); assertEquals(45496.76, t.availableAfter)
    }

    @Test fun ccRefunds() {
        val a = tx("The transaction on your credit card#4321  from GEIDEA OAKBAY R  with EGP 1168.00 on 17/10/26  at 19:57 has been refunded. Please try again. Thank you")
        assertEquals(TxKind.CC_REFUND, a.kind); assertEquals(1168.0, a.amount)
        val b = tx("لقد تم رد USD18.82 على بطاقتكم الائتمانية المنتهية بـ# 4321 من ATOMS> . يرجى ملاحظة")
        assertEquals(TxKind.CC_REFUND, b.kind); assertEquals("USD", b.currency); assertEquals("ATOMS", b.party)
    }

    @Test fun ccPaymentAndCashback() {
        assertEquals(TxKind.CC_PAYMENT, tx("تم سداد مبلغ 44856.38 جم فى بطاقتكم الائتمانية المنتهية بـ 4321 بتاريخ 10-04-26").kind)
        assertEquals(1100.0, tx("لقد تم إضافة كاش باك بقيمة 1100.00 جم على بطاقتكم الائتمانية المنتهية بـ 4321").amount)
    }

    @Test fun transfers() {
        val a = tx("The external transfer you requested on online banking from account ending with ********1234 with amount EGP 495.00DR has been processed successfully on 13 APR 2025")
        assertEquals(TxKind.TRANSFER_OUT, a.kind); assertEquals(495.0, a.amount)
        val b = tx("Your account ending with ********1234 is debited with amount EGP 50,246.00DR On 30 SEP 2024 with transfer to another account.  For more information, please contact 19666.")
        assertEquals(50246.0, b.amount); assertEquals("1234", b.accountHint)
    }

    @Test fun infoAndIgnored() {
        val s = CibParser.parse("كشف حساب شهر ابريل 2026  لبطاقتك الائتمانية المنتهية بـ 4321 هو 44856.38 جم ، الحد الأدنى للسداد قبل  05/05 هو 2242.81 جم .يمكنك")
        assertIs<ParseResult.Info>(s); assertEquals(44856.38, s.info.amount); assertEquals("05/05", s.info.dueDate)
        val l = CibParser.parse("عميل CIB العزيز، جاري طباعة بطاقة MASTERCARD TITANIUM الائتمانية الجديدة الخاصة بك بحد ائتماني 59500 جم  وسيتم إرسالها")
        assertIs<ParseResult.Info>(l); assertEquals(59500.0, l.info.amount)
        assertEquals(ParseResult.Ignored, CibParser.parse("your OTP is 590079"))
        assertEquals(ParseResult.Ignored, CibParser.parse("تم رفض المعاملة على بطاقتك المنتهية بـ **5678 بقيمة 20.00 EGP لعدم إدخال الرقم السري."))
        assertEquals(ParseResult.Unknown, CibParser.parse("يتمنى لك CIB Prime عيد ميلاد سعيد!"))
    }

    @Test fun balanceCalc() {
        val txs = listOf(
            BalTx(1, -100.0, balanceAfter = 900.0),
            BalTx(2, -50.0),
            BalTx(3, +200.0),
        )
        assertEquals(1050.0, BalanceCalc.compute(txs, false, 0.0, null, null))
        assertEquals(700.0, BalanceCalc.compute(txs, false, 0.0, 500.0, 2))
        val cc = listOf(BalTx(1, -300.0, availableAfter = 59000.0), BalTx(2, 500.0))
        assertEquals(0.0, BalanceCalc.compute(cc, true, 59500.0, null, null))
    }

    @Test fun customPattern() {
        val p = CustomPattern("Vodafone", "تم استلام (?<amount>[\\d.,]+) جنيه من (?<party>\\d+).*?رصيدك (?<balance>[\\d.,]+)", TxKind.TRANSFER_IN)
        val r = CustomParser.parse("VodafoneCash", "تم استلام 50.00 جنيه من 01000000000 ; رصيدك 150.00 جنيه", listOf(p))!!
        assertEquals(50.0, r.amount); assertEquals(150.0, r.balanceAfter)
    }

    /** Runs over a real export when MONEYFEN_SMS_TSV is set (date \t sender \t body). Not committed data. */
    @Test fun realExport() {
        val path = System.getenv("MONEYFEN_SMS_TSV") ?: return
        val counts = mutableMapOf<String, Int>()
        val unknownMoney = mutableListOf<String>()
        File(path).readLines().forEach { line ->
            val body = line.split('\t').getOrNull(2) ?: return@forEach
            val r = CibParser.parse(body)
            val key = when (r) { is ParseResult.Tx -> r.tx.kind.name; is ParseResult.Info -> r.info.kind.name; else -> r.toString() }
            counts[key] = (counts[key] ?: 0) + 1
            if (r is ParseResult.Unknown && Regex("EGP|USD|جم|مبلغ|charged|debited").containsMatchIn(body)) unknownMoney += body.take(140)
        }
        println("COUNTS $counts")
        val bank = mutableListOf<BalTx>(); val cc = mutableListOf<BalTx>()
        File(path).readLines().forEachIndexed { i, line ->
            val parts = line.split('\t'); val r = CibParser.parse(parts.getOrNull(2) ?: "")
            if (r !is ParseResult.Tx) return@forEachIndexed
            val t = r.tx; val time = java.time.LocalDateTime.parse(parts[0].replace(' ', 'T')).toEpochSecond(java.time.ZoneOffset.UTC)
            val amt = if (t.currency == "USD") t.amount * 50 else t.amount
            val b = BalTx(time, amt * t.kind.direction, t.balanceAfter, t.availableAfter, i.toLong())
            if (t.kind.isCreditCardKind) cc += b else bank += b
        }
        println("BANK balance now = ${BalanceCalc.compute(bank, false, 0.0, null, null)}")
        println("CC balance now = ${BalanceCalc.compute(cc, true, 59500.0, null, null)}")
        unknownMoney.forEach { println("UNKNOWN $it") }
        assertTrue(counts.isNotEmpty())
    }
}
