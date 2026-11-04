package com.moneyfen.app.data

/** Default categories and auto-categorisation rules. Everything is editable in the app. */
object Seed {
    private val E = CategoryType.EXPENSE.name
    private val I = CategoryType.INCOME.name
    private val T = CategoryType.TRANSFER.name

    val categories = listOf(
        CategoryEntity(key = "food", nameEn = "Food & dining", nameAr = "أكل ومطاعم", emoji = "🍔", type = E, sortOrder = 1),
        CategoryEntity(key = "groceries", nameEn = "Groceries", nameAr = "سوبرماركت", emoji = "🛒", type = E, sortOrder = 2),
        CategoryEntity(key = "transport", nameEn = "Fuel & transport", nameAr = "بنزين ومواصلات", emoji = "⛽", type = E, sortOrder = 3),
        CategoryEntity(key = "bills", nameEn = "Bills & recharge", nameAr = "فواتير وشحن", emoji = "📱", type = E, sortOrder = 4),
        CategoryEntity(key = "shopping", nameEn = "Shopping", nameAr = "شوبينج", emoji = "🛍️", type = E, sortOrder = 5),
        CategoryEntity(key = "health", nameEn = "Health", nameAr = "صحة وأدوية", emoji = "💊", type = E, sortOrder = 6),
        CategoryEntity(key = "fun", nameEn = "Entertainment", nameAr = "خروجات وترفيه", emoji = "🎬", type = E, sortOrder = 7),
        CategoryEntity(key = "subs", nameEn = "Subscriptions", nameAr = "اشتراكات", emoji = "💻", type = E, sortOrder = 8),
        CategoryEntity(key = "sport", nameEn = "Sport & care", nameAr = "رياضة وعناية", emoji = "🏋️", type = E, sortOrder = 9),
        CategoryEntity(key = "cash", nameEn = "Cash withdrawal", nameAr = "سحب كاش", emoji = "🏧", type = E, sortOrder = 10),
        CategoryEntity(key = "sent", nameEn = "Sent to people", nameAr = "تحويلات لناس", emoji = "📤", type = E, sortOrder = 11),
        CategoryEntity(key = "fees", nameEn = "Bank fees", nameAr = "رسوم بنكية", emoji = "💸", type = E, sortOrder = 11),
        CategoryEntity(key = "other", nameEn = "Other", nameAr = "أخرى", emoji = "❓", type = E, sortOrder = 12),
        CategoryEntity(key = "salary", nameEn = "Salary", nameAr = "مرتب", emoji = "💼", type = I, sortOrder = 20),
        CategoryEntity(key = "freelance", nameEn = "Freelance", nameAr = "فريلانس", emoji = "💻", type = I, sortOrder = 21),
        CategoryEntity(key = "received", nameEn = "Received (not income)", nameAr = "تحويلات واردة (مش دخل)", emoji = "📥", type = T, sortOrder = 31),
        CategoryEntity(key = "refund", nameEn = "Refunds & cashback", nameAr = "مرتجعات وكاش باك", emoji = "↩️", type = E, sortOrder = 13),
        CategoryEntity(key = "deposit", nameEn = "Cash deposit", nameAr = "إيداع كاش", emoji = "💰", type = T, sortOrder = 32),
        CategoryEntity(key = "other_income", nameEn = "Other income", nameAr = "دخل آخر", emoji = "➕", type = I, sortOrder = 24),
        CategoryEntity(key = "internal", nameEn = "Between my accounts", nameAr = "بين حساباتي", emoji = "🔁", type = T, sortOrder = 30),
    )

    /** pattern -> category key */
    val rules = listOf(
        "mobile recharge|we mobile|we-|vodafone|orange|etisalat|home internet|electric|water|gas|fawry bill|waffarha|utilit" to "bills",
        "talabat|elmenus|mcdonald|kfc|pizza|kabab|kebab|sushi|cafe|café|costa|starbucks|restau|potasta|koshary|cilantro|tarboush|manna|patisser|konafa|waffle|burger|buffalo|dar el bon|beano|smash|food|chicken|ghataty|tete a tete" to "food",
        "demel|sarai market|kazyon|carrefour|spinneys|metro|gomla|seoudi|hyper|market|baraka|co op|coop|kheir zaman|oscar|zahran|ozone|o zone|rayaheen|bim |circle k" to "groceries",
        "mobil|petrol|q8|chillout|watanya|wataniya|total|emarat|exxon|uber|careem|indrive|didi|swvl|cpc" to "transport",
        "lc waikiki|defacto|zara|h&m|amazon|noon|jumia|shein|essentials|elaraby|el araby|shop|store|casual|glitter|jewel|tie house|toys|computers|phones|e acc" to "shopping",
        "pharma|pharmacy|صيدلية|saidaly|clinic|hospital|dr |lab|ezaby|elezaby|seif" to "health",
        "vox|cinema|aqua park|kids castle|carting|bowling|park|club|sporting" to "fun",
        "anthropic|openai|netflix|spotify|google|apple.com|youtube|shahid|watch it|osn|atoms|chatgpt|microsoft|github" to "subs",
        "gym|muscle|fit and fix|salon|barber|khairo" to "sport",
    )
}
