# Bloom

A private, beautiful menstrual cycle tracker for Android. **100% offline** — no account, no cloud, no analytics, no ads, and no `INTERNET` permission, so it is technically unable to send your data anywhere.

## Features
- **Tracking:** flow, symptoms, mood, discharge, intimacy, basal temperature and notes; everything autosaves.
- **Predictions with honest uncertainty:** the next period is a *window* (with a confidence tier), never a false-precision date. Fertile window and ovulation are estimates with their own range. Temperature logs (3-over-6 rule) confirm ovulation and personalise your luteal phase.
- **Calendar, insights, history:** phase-tinted month view, cycle-length chart and variability, symptom/mood patterns, temperature chart.
- **Widgets (Glance):** Small 2×2 ring, Medium 4×2 with 7-day strip, Large 4×4 with two-week grid. Updated on data change, at midnight (WorkManager), and on time/timezone/locale changes. A *Discreet* mode shows only a neutral dot.
- **Privacy:** AES-256-GCM encryption with a non-exportable Android Keystore key, optional PIN (PBKDF2, lockout) + biometrics, optional screenshot/recents blocking, OS backup and device transfer disabled.
- **Your data is yours:** complete JSON export, CSV export, password-encrypted backups (PBKDF2-600k + AES-GCM), and import/restore with preview, merge or replace. Uses the system file picker — no storage permissions.
- Light and dark themes, optional neutral reminders (the only permission is the optional notification permission).

## Build
```
./gradlew testDebugUnitTest assembleDebug     # APK in app/build/outputs/apk/debug
./gradlew assembleRelease                     # signs with BLOOM_STORE_* from ~/.gradle/gradle.properties, else debug key
```
Kotlin 2.1, Jetpack Compose, Glance 1.1, WorkManager; min SDK 26, target 36.

## Docs
- [`docs/RESEARCH.md`](docs/RESEARCH.md) — competitor, science and platform research.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — design decisions, algorithm, security model.

*Bloom's predictions are statistical estimates, not medical advice, and must not be used for contraception.*
