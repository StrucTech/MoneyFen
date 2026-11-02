# 💰 MoneyFen

A personal Android app that reads your bank SMS (CIB, InstaPay, debit, credit) and
tracks your accounts, balances, net worth and savings plan. **100% free**, no AI, everything editable.

## Install on your phone

1. From your phone open: **https://github.com/StrucTech/MoneyFen/releases** → the latest build → download `MoneyFen-xx.apk`.
2. Open the file → allow "Install unknown apps". (Any new version installs over the old one and your data is kept.)
3. On first launch: pick a language → **Import all bank SMS from phone** → grant SMS permission.
   It imports your full history and creates the accounts automatically.

> ⚠️ Android 13+: if SMS permission is greyed out (Restricted setting), go to
> Settings → Apps → MoneyFen → ⋮ (top right) → **Allow restricted settings**, then go back and grant the permission.

## What the app does

| Feature | Details |
|---|---|
| Reading messages | Every new CIB SMS is recorded instantly with a notification. Old ones are imported from the inbox |
| Message types | InstaPay in/out, debit card purchase, ATM, deposit, salary, credit card purchase (EGP/USD), card payment, refunds, cashback, statements |
| Balance | Read from the message when it contains it (debit / credit available limit), otherwise calculated. **You can edit it at any time** |
| Credit card | Debt = limit − available. Statement and minimum payment shown on the home screen |
| Smart linking | Paying the card from your account = transfer between your accounts (not counted as spending). A refunded transaction cancels the original one |
| Wealth | Bank, credit card, cash, wallet, gold (grams × price), certificates/deposits (with return), money you lent, money you owe, other assets/debts |
| Categories | Ready-made categories + rules (if the merchant name contains X → category Y). All editable |
| Analytics | Donut by category, last 6 months income vs spending, top places you spend |
| Savings plan | Fixed % of income, goals with amount + date (shows the monthly amount needed), monthly budget per category with alerts at 80% and 100% |
| Other banks / wallets | Settings → SMS templates: add a regex for any message (e.g. Vodafone Cash) and test it on a real message |
| Languages | Arabic / English with a switch |
| Backup | Supabase (free) automatically every day + an export/import JSON file |

## Supabase setup (optional, free)

1. Create a free project at [supabase.com](https://supabase.com).
2. **SQL Editor** → paste the contents of [`supabase/schema.sql`](supabase/schema.sql) → Run.
3. **Authentication → Sign In / Providers → Email**: turn off "Confirm email" (easier) or confirm the email after signing up.
4. **Project Settings → API**: copy the `Project URL` and the `anon public` key.
5. In the app: Settings → Backup → paste them → Create account → Backup now.

Data is protected by Row Level Security: each user sees only their own backup.

## Development

```
core/   SMS parsing + balance calculation (pure Kotlin, tested)
app/    Android app (Jetpack Compose + Room)
```

- Tests: `./gradlew :core:test`
- To test the parser on a full SMS export: `MONEYFEN_SMS_TSV=/path/file.tsv ./gradlew :core:test`
  (each line: `date<TAB>sender<TAB>message`).
- Adding a new CIB message format: `core/src/main/kotlin/com/moneyfen/core/CibParser.kt`.

### Signing key
The APK is signed with `app/moneyfen.jks` (a personal key in the repo so updates install over each other).
To use a private key: add GitHub secrets `MONEYFEN_KEYSTORE_B64`, `MONEYFEN_KEYSTORE_PASSWORD`,
`MONEYFEN_KEY_ALIAS`, `MONEYFEN_KEY_PASSWORD` (note: changing the key means uninstalling the old version first).
