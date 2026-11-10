# Architecture & decisions

## Layers
`domain/` pure Kotlin (models, `CycleEngine`, `Narrative`) → `data/` (`EncryptedStore`, `BloomRepository`, `Portability`) → `ui/` (Compose, one `AppViewModel`) and `widget/` (Glance, reads the repository snapshot). `security/` holds crypto + PIN. The engine takes `today` as a parameter and has no Android dependencies, so all of it is unit-tested on the JVM.

## Storage
One JSON document, AES-256-GCM, key in Android Keystore (non-exportable, not auth-bound so widgets/reminders work while the app is locked — the app lock is a screen gate). Atomic temp-file + rename with a `.bak` generation. Chosen over SQLCipher/Room: data is a few KB/year, no native crypto library to trust, and Jetpack Security's EncryptedSharedPreferences is deprecated. If the keystore key is lost the app says so instead of silently wiping.

## Prediction algorithm
1. Period = run of flow ≥ Light days (gaps ≤ 1 day tolerated); spotting only extends a period.
2. Cycle length = gap between period starts; keep 15–60 d; with ≥5 samples reject > 3×MAD outliers (missed logging).
3. Predicted length = recency-weighted mean (decay 0.8, last 6) shrunk toward the user's default while data is thin.
4. Window half-width = ⌈1.28·SD·√k⌉ (≈80% interval, k = cycles ahead), clamped 1–10; SD floors at 3.5 d with <2 cycles.
5. Ovulation = next start − luteal (13 d default; population mean ≈ 12.4); its window adds ±2 d of luteal variability. Fertile window = ovulation −5…+1.
6. BBT 3-over-6 rule confirms ovulation; ≥2 confirmed cycles personalise luteal length.
7. Confidence: High ≥6 cycles & SD ≤2; Medium ≥3 & SD ≤4; else Low. Overdue periods show "N days late" and the cycle-day counter keeps running.

## Security model
No `INTERNET` permission; `allowBackup=false` plus `dataExtractionRules` excluding everything; optional `FLAG_SECURE`; PIN = PBKDF2-HMAC-SHA256 210k + salt, escalating lockout (the stored hash lives inside the encrypted file); exports never include PIN material; CSV defuses formula injection; imports are validated and size-capped; notifications are neutral and `VISIBILITY_SECRET`.

## Widgets
Glance `SizeMode.Responsive`; ring/strip drawn to small bitmaps. Refresh: repository `onChanged`, self-rescheduling midnight WorkManager job, and TIME_SET/TIMEZONE/LOCALE/BOOT/package-replaced receiver (ACTION_DATE_CHANGED isn't deliverable to manifest receivers on API 26+).
