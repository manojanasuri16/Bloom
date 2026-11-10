# Bloom Research Brief

Compiled 2026-11-10. Sources are cited inline. **[UNVERIFIED]** marks claims I could not confirm against a primary or official source. Bloom is offline-only, so nothing here assumes a server.

---

## 1. Competitor teardown

| App | Key features | UX strengths / weaknesses | Widgets | Privacy record | Business model |
|---|---|---|---|---|---|
| **Flo** (Flo Health) | Period/ovulation prediction, symptom logging, pregnancy mode, content feed, "Health Assistant" chatbot, partner mode | Polished and full-featured. Complaints about features moving behind the paywall in v5 ([Flo dev replies on Play, via android-stats](https://android-stats.com/d/org.iggymedia.periodtracker)) | **[UNVERIFIED]** no Android widget documentation found | **FTC 2021**: shared health data (e.g. pregnancy intent) with Facebook/Google analytics despite promising privacy. Order requires affirmative consent and an independent review ([FTC](https://www.ftc.gov/node/73903), [Becker's](https://www.beckershospitalreview.com/consumerism/fertility-app-company-accused-of-sharing-private-data-to-sell-ads-settles-with-ftc.html)). **Frasco v. Flo (Aug 2025)**: jury found Meta violated California's privacy law (CIPA) via the SDK in Flo. Google ($48M) and Flo ($8M) settled ([Lawdragon](https://www.lawdragon.com/news-features/2025-08-25-big-tech-on-trial-jury-finds-meta-liable-for-misusing-women-health-data), [Labaton](https://www.labaton.com/cases/frasco-v-flo-health-inc)). Opt-in "Anonymous Mode" added Sep 2022 after Dobbs ([Engadget](https://www.engadget.com/flo-anonymous-mode-is-now-available-on-ios-130039242.html)) | Freemium, Premium subscription (about $10/month in the US per one secondary source) |
| **Clue** (BioWink, Berlin) | Science-led tracking, many tracking categories, Clue Plus modes (Conceive, Pregnancy, Perimenopause), wearables | Neutral, inclusive tone. Data and predictions are tied to an account | iOS listing advertises a cycle/next-period widget ([App Store](https://apps.apple.com/za/app/id657189652?l=en-gb)). Android **[UNVERIFIED]** | Under GDPR, data stored in the EU, says it will not comply with US subpoenas for user data ([Clue support](https://support.helloclue.com/hc/en-us/articles/29048867548061-How-Clue-handles-data-privacy), [Clue on Roe](https://helloclue.com/articles/abortion/clue-s-response-to-roe-vs-wade)). Mozilla flagged minimal ad-network sharing (no health data) ([Mozilla PNI](https://foundation.mozilla.org/privacynotincluded/clue-period-cycle-tracker/)) | Freemium + Clue Plus (price varies by region) ([Clue](https://support.helloclue.com/hc/en-us/articles/115005215266-How-much-does-Clue-Plus-cost)) |
| **Stardust** | Period/pregnancy tracking with astrology, moon phases, "cycle horoscope", friend sharing and compatibility ([Bustle](https://www.bustle.com/wellness/stardust-app-review-period-tracking-astrology)) | Playful and social, which drives virality. Social features add exposure | **[UNVERIFIED]** | **June 2022**: its policy said it would cooperate with police "whether or not legally required". Removed after Motherboard asked about it ([Vice](https://www.vice.com/en/article/the-1-period-tracker-on-the-app-store-will-hand-over-data-without-a-warrant/), [Gizmodo](https://gizmodo.com/stardust-roe-v-wade-encrypted-period-tracking-app-abort-1849113572)). TechCrunch: its "E2E" design sent a key back to the server. Mozilla: trackers contacted before any data is entered ([Mozilla](https://www.mozillafoundation.org/en/nothing-personal/stardust-privacy-review/)) | Free + optional subscriptions |
| **Natural Cycles** | BBT + LH algorithm. FDA De Novo (Aug 2018) as a contraceptive: 93% effective with typical use, 98% with perfect use. Oura (2022) and Apple Watch temperature inputs ([PR Newswire](https://prnewswire.com/news-releases/us-food-and-drug-administration-fda-clears-natural-cycles-as-the-first-digital-method-of-birth-control-in-the-united-states-300695804.html), [MDD](https://www.medicaldesigndevelopment.com/topics/devices/news/22873518/natural-cycles-birth-control-app-cleared-for-integration-with-apple-watch)) | Clear red/green day UX. Needs daily measuring and a paid subscription | **[UNVERIFIED]** | Says it does not sell data. Cloud account required. A 2026 algorithm re-clearance cites the 2018 efficacy figure without new published data ([Unite.AI](https://www.unite.ai/?p=441559), secondary source) | Subscription only |
| **Euki** (Euki Inc., spun out of Women Help Women in 2024) | Period, sexual and reproductive health logging, abortion/contraception info | PIN, decoy screen with fake data, scheduled auto-delete. Protections are **opt-in** | **[UNVERIFIED]** (likely none) | No account, data stays on device per Mozilla's testing ([Mozilla](https://www.mozillafoundation.org/en/nothing-personal/euki-privacy-review/)). No backup, so a lost phone means lost data | Nonprofit, free |
| **drip** (Bloody Health, GPL-3.0) | Bleeding, BBT, mucus/cervix (sympto-thermal), mood, pain. Predictions after **3 complete cycles**, using period start days only. Cycles >99 days skipped. React Native ([GitLab](https://gitlab.com/bloodyhealth/drip), [FAQ](https://dripapp.org/faq.html)) | Transparent: shows uncertainty as dashed circles (SD <1.5 gives ±1 day, otherwise ±2). Plain UI | None mentioned in the FAQ | Data stays on device. Developers say there is no server ([Mastodon](https://mastodon.social/@dripapp/111415456348255372)). Password protection and CSV import/export ([F-Droid](https://f-droid.org/ru/packages/com.drip/index.html)) | Donations, free |
| **Period Tracker by GP Apps** | Month calendar, ovulation/fertile days, moods, symptoms, weight, temperature, notes, discreet icon, passcode/Touch ID ([App Store](https://apps.apple.com/bg/app/id330376830)) | Simple and long-established. Dated UI | **[UNVERIFIED]** | Policy details **[UNVERIFIED]**. Do not confuse it with "Period Calendar" (ABISHKKING), which Mozilla found sending data to Google ad SDKs ([Mozilla](https://www.mozillafoundation.org/en/nothing-personal/period-calendar-privacy-review/)) | Free with ads + paid upgrade **[UNVERIFIED]** |

**Patterns:** (a) Every privacy incident above came from **third-party SDKs or server-side data**, not on-device storage. (b) Privacy-first apps (Euki, drip) trade away polish, widgets and backup. (c) No app explains its prediction uncertainty well except drip.

### Where Bloom can be better
1. **Provably offline.** Ship with **no `INTERNET` permission** and no third-party SDKs. State this in the Play Data Safety form and an in-app "What leaves this phone: nothing" screen. That makes the Flo and Stardust failure modes impossible.
2. **Honest predictions.** Show a range plus a confidence tier ("Likely Mar 3 to 6, medium confidence"), not one day. Explain why, e.g. "your last 6 cycles varied by 5 days".
3. **Privacy that is on by default** (Euki's protections are opt-in). Turn on app lock, `FLAG_SECURE` and a discreet widget during onboarding.
4. **Widgets with a discreet mode.** None of the privacy-first apps offer widgets. Provide a neutral widget ("Day 12 · 4 days") with no words like "period" or "fertile".
5. **Owner-controlled encrypted backup.** Fix Euki's "lost phone = lost data" with a password-encrypted file the user saves anywhere through the Storage Access Framework (SAF).
6. **One-tap import from Clue, Flo and drip exports**, so switching costs almost nothing.
7. **Fast logging** in at most 2 taps from the widget or home screen, plus quick-log chips.
8. **Clinically framed insights.** Flag cycles outside FIGO's normal range (24–38 days, variation >7–9 days, bleeding >8 days) with "consider talking to a clinician", without diagnosing.
9. **Never market fertility days as contraception** (Bloom is not a cleared device like Natural Cycles).

---

## 2. Cycle prediction science

### Key evidence
| Finding | Source |
|---|---|
| 612,613 ovulatory cycles, 124,648 Natural Cycles users. Mean cycle **29.3 d**. Follicular phase **16.9 d** (95% CI 10–30). Luteal phase **12.4 d** (95% CI 7–17). Only about 13% of cycles are 28 d. Cycle length falls 0.18 d/yr between ages 25 and 45. Variation is 14% higher at BMI >35. Ovulation is often not on day 14 | Bull et al. 2019, *npj Digit Med* 2:83 ([PubMed](https://pubmed.ncbi.nlm.nih.gov/31482137/), [ScienceDaily](https://www.sciencedaily.com/releases/2019/08/190826191924.htm)) |
| Luteal phase in 28-day cycles ranged from 7 to 19 d | Bull 2019 (via [PMC6710244](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6710244/) summary) |
| 378k Clue users, 4.9M cycles. Within-person variability (mean absolute difference between consecutive cycle lengths) separates users, and stays stable over time | Li et al. 2020, *npj Digit Med* ([PMC7250828](https://pmc.ncbi.nlm.nih.gov/articles/PMC7250828)). A 2021 Li et al. follow-up is **[UNVERIFIED]** |
| 141 women, 1,060 cycles. Mean 28.9 d (SD 3.4). 95% of cycles fell between 22 and 36 d. **42.5% of women varied >7 d**. The follicular phase drives most of the variability | Fehring et al. 2006, *JOGNN* 35:376 ([PubMed](https://pubmed.ncbi.nlm.nih.gov/16700687/)). Their exact luteal SD is **[UNVERIFIED]** |
| Luteal phase is less variable than follicular but still not predictable in length | Henry et al. 2024, *Hum Reprod* ([News-Medical](https://www.news-medical.net/news/20241007/Study-reveals-diverse-lengths-of-luteal-phases-in-healthy-women.aspx)) |
| Ovulation tests, 75,981 cycles: **10-day spread** of ovulation day in 28-day cycles, mode day 15 | Soumpasis et al. 2020, *Hum Reprod Open* ([UCL](https://discovery.ucl.ac.uk/id/eprint/10096740/)). Industry-funded |
| Fertile window = **6 days ending on ovulation day**. Probability of conception rises from 0.10 at 5 days before ovulation to 0.33 on ovulation day | Wilcox et al. 1995, *NEJM* 333:1517 ([PDF](https://www.talmudology.com/s/TIMING-OF-SEXUAL-INTERCOURSE-IN-RELATION-TO-OVULATION-NEJM-1995.pdf)) |
| FIGO normal ranges: 24–38 d. Regular means shortest-to-longest spread ≤7 d (ages 26–41) or ≤9 d (ages 18–25 and 42–45). Bleeding ≤8 d | Munro et al. 2018, *IJGO* (via [MSD](https://www.msdmanuals.com/professional/multimedia/table/normal-menstrual-parameters)) |

### Implications
- **Calendar-method limits:** Ovulation day varies by about 10 days even in "28-day" cycles. Counting back 14 days is wrong on average (the mean luteal phase is 12.4 d) and wrong for any one person. Calendar-only fertility estimates miss often: in one analysis only 54.9% of women's own ovulation estimates fell inside their fertile window ([Clearblue HCP PDF](https://uk.clearblue.com/sites/default/files/HCP_Publications/Articles-FertilityAndOvulation/HCP-0078v1HCP_Communications-AccuracyOfPercept.pdf)). **Bloom's calendar-only fertile window is informational only.**
- **Fertile window shown:** ovulation −5 … ovulation +1. The +1 day is a conventional buffer for egg survival and estimation error. Wilcox's data end the window at ovulation day.
- **BBT "3-over-6" rule:** Ovulation is confirmed when **3 consecutive temperatures are above the highest of the previous 6**. The coverline is drawn at that highest low temperature. The common Sensiplan/Daysy variant needs the 3rd reading ≥0.2 °C above the coverline, and waits for a 4th reading otherwise ([Daysy](https://usa.daysy.me/learn-more/learn-your-cycle/nfp-rules), [Ovy](https://ovyapp.com/en/blogs/news/die-regeln-der-symptothermalen-methode-auf-einen-blick)). Thresholds differ between schools, and a primary source for 0.2 °C is **[UNVERIFIED]**. BBT confirms ovulation only **after it has happened**. Use it to (a) mark the luteal phase start and (b) learn the user's own luteal length. Never use it for prospective "safe days".

### Recommended Bloom algorithm (v1)
1. **Inputs:** period start dates. Cycle length `L_i = start_{i+1} − start_i`. The current open cycle is never used.
2. **Sanity filter:** drop cycles with `L < 10` or `L > 90` from the statistics (Bull's exclusion criteria). Also let users mark a cycle "atypical" (illness, postpartum, coming off hormonal birth control) to exclude it.
3. **Outliers:** keep the last **N = 12** valid cycles. Compute `med` and `MAD` (scaled ×1.4826, floor 1 d). Exclude from the estimate any cycle with `|L − med| > 3·MAD`, but still show it in history with an "unusual cycle" badge.
4. **Point estimate:** weighted median with recency weights `w_k = 0.8^k`, where k=0 is the most recent cycle. Robust and drifts with age. The weighted mean is an acceptable alternative once n ≥ 6.
5. **Spread:** `s` = weighted SD of the retained cycles. Floor at 1.0 d. When n < 6, apply a small-sample shrink `s' = sqrt((n·s² + 3·3.0²)/(n+3))`, which blends toward a population prior of about 3 d (Fehring SD 3.4).
6. **Next-period window:** `lastStart + est ± ceil(1.0·s')` as the "likely" range (about 68%). Optionally show a lighter `±ceil(2·s')` "possible" band.
7. **Ovulation estimate:** `nextStart − Lut`, where `Lut` = the user's mean BBT-confirmed luteal length if there are ≥2 confirmations, otherwise **13 d** (between Bull's 12.4 and the classic 14). Uncertainty is `sqrt(s'² + 2²)`, using a luteal SD of about 2 (Bull: 12.4 ± 2.4).
8. **Fertile window:** `[ovu − 5 − ⌈σ⌉, ovu + 1 + ⌈σ⌉]`, drawn with soft edges. If BBT confirms ovulation this cycle, close the window 3 days after the temperature rise.
9. **Confidence tiers:**

| Tier | Condition | UI |
|---|---|---|
| None | 0 complete cycles | "Log your next period to start predictions". Optionally use the typical length the user enters, labelled "estimate" |
| Low | 1–2 cycles, or s' > 4 | Wide band, "early estimate" |
| Medium | 3–5 cycles and s' ≤ 4 (drip also waits for 3 cycles) | Normal band |
| High | ≥6 cycles and s' ≤ 2 | Tight band |

10. **Late handling:** if today > predicted end and no period has been logged, say "Your period may be late". Never imply pregnancy.
11. **Health flags (informational):** median cycle <24 or >38 d, shortest-to-longest spread >9 d, bleeding >8 d, or no period for 90 d. Each shows "consider talking to a healthcare provider".
12. **Minimum data:** 1 cycle for a crude estimate, **3 for a meaningful window**, 6+ for stable SD estimates.

### Disclaimer wording (draft)
> "Bloom estimates dates from what you log. Predictions are approximate and cycles naturally vary. **Bloom is not a medical device and must not be used as contraception or to diagnose any condition.** Fertile-window estimates cannot tell you when you are safe from pregnancy. If you have concerns about your cycle, talk to a healthcare professional."

Show it in onboarding (with an acknowledgement), on the fertile-window info sheet, and in About.

---

## 3. Android engineering (2026)

### Storage encryption
| Option | Status | Verdict |
|---|---|---|
| `EncryptedSharedPreferences` / `EncryptedFile` (`androidx.security:security-crypto`) | **Deprecated in 1.1.0**, no further releases planned ([API ref](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences), [Include Security, Aug 2026](https://blog.includesecurity.com/2026/08/encryptedsharedpreferences-is-dead-heres-what-you-should-use-instead/)) | **Do not use** |
| Jetpack DataStore + **Tink** AEAD (`datastore-tink`, `tink-android`), keyset wrapped by an Android Keystore master key | The current recommended replacement ([Include Security](https://blog.includesecurity.com/2026/08/encryptedsharedpreferences-is-dead-heres-what-you-should-use-instead/), [ProAndroidDev](https://proandroiddev.com/finally-the-official-way-of-secure-datastore-is-here-ae878f5eb457)). In KINTO's benchmark Tink was faster than raw Keystore calls ([KINTO](https://blog.kinto-technologies.com/posts/2025-06-16-encrypted-shared-preferences-migration-en/)) | **Settings and small data** |
| Room + **SQLCipher** (`net.zetetic:sqlcipher-android`, `SupportOpenHelperFactory`). The old `android-database-sqlcipher` is deprecated ([GitHub](https://github.com/sqlcipher/sqlcipher-android), [Zetetic](https://www.zetetic.net/sqlcipher/sqlcipher-for-android-migration)) | Maintained, adds native libraries (about several MB). Latest version **[UNVERIFIED]** (4.9.0 seen) | **Main log DB** if Bloom needs queries and migrations |
| Raw Keystore `AES/GCM/NoPadding` file | Works. Keystore supports only 12-byte IVs, and IV handling is on you ([AOSP CTS](https://android.googlesource.com/platform/cts/+/a51a408)) | Use only to wrap one data-encryption key (DEK), not for bulk data |

**Recommendation:** Room + SQLCipher. The DB passphrase is a random 32-byte DEK, wrapped by a non-exportable Keystore AES-256-GCM key (StrongBox if available, falling back gracefully). Store the wrapped DEK in `noBackupFilesDir`. **Do not** set `setUserAuthenticationRequired` on that key, because the widget and WorkManager must read data while the app is "locked". App lock is a UI gate on top of full-disk and file-based encryption. Simpler alternative: since the data volume is tiny (a few thousand rows), keep a single Tink-AEAD-encrypted Proto DataStore.

### App lock
- Use `androidx.biometric` `BiometricPrompt` with `BIOMETRIC_STRONG`. If a `CryptoObject` is used, the authenticators must be `BIOMETRIC_STRONG` only ([BiometricPromptData ref](https://developer.android.com/reference/androidx/credentials/provider/BiometricPromptData)). `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` is unsupported on API ≤29, and `setNegativeButtonText` cannot be combined with `DEVICE_CREDENTIAL` ([OWASP MASTG](https://mas.owasp.org/MASTG/best-practices/MASTG-BEST-0031/)).
- **App PIN as fallback** (independent of the device PIN). A 4–6 digit PIN has only 10⁴–10⁶ possibilities, so any KDF on its own is brute-forceable offline. Use **PBKDF2-HMAC-SHA256, 16-byte random salt, ≥600,000 iterations** (OWASP's figure; tune to about 300 ms on a low-end device) ([OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet)). **Additionally** key it with a Keystore HMAC key (`HMAC(KeystoreKey, PBKDF2(pin))`) so the hash is useless off-device. Add exponential lockout (30 s → 1 h) and an optional wipe after N failures, Euki-style.
- Lock when backgrounded for more than X seconds, configurable with immediate as the default (`ProcessLifecycleOwner`).
- `window.addFlags(FLAG_SECURE)` blocks screenshots and display on non-secure displays ([API ref](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE)). It also blanks the Recents thumbnail in practice; the reference page does not state that explicitly. On API 33+, `setRecentsScreenshotEnabled(false)` adds extra protection. Make both user-toggleable, default on.
- Discreet launcher icon and name via `<activity-alias>` toggling (GP Apps has a discreet icon). An optional decoy PIN is a v2 idea (Euki).

### Manifest hardening
- **No `<uses-permission android:name="android.permission.INTERNET"/>`.** Also check merged manifests, because libraries such as WorkManager do not add it while some analytics and crash libraries do. Add a CI check on `build/intermediates/merged_manifest`.
- `android:allowBackup="false"`, plus `android:dataExtractionRules` that excludes all domains from both `<cloud-backup>` and `<device-transfer>`, plus `fullBackupContent` for API ≤30. On Android 12+, some OEMs ignore `allowBackup=false` for device-to-device transfer, so the explicit `<device-transfer>` excludes matter ([Auto Backup docs](https://developer.android.com/guide/topics/data/autobackup)). Android 16 QPR2 adds `<cross-platform-transfer platform="ios">`; exclude that too (same doc). Even if a file leaked, the Keystore-wrapped DEK does not transfer, so the data could not be decrypted.
- No exported components except the widget receiver and launcher. `android:usesCleartextTraffic` is irrelevant without internet access.

### Export / import without permissions
- SAF: `ActivityResultContracts.CreateDocument("application/octet-stream")` for export and `OpenDocument()` for import. No storage permission is needed, and the user picks the destination (local storage, USB, or a cloud app of their choice, which is their own decision).

### Backup file format (`.bloom`)
```
magic "BLOOMBK1" (8B) | version u8 | kdf_id u8 (1=PBKDF2-SHA256, 2=Argon2id)
kdf params (iter u32 | or m_KiB u32, t u32, p u8) | salt 16B | nonce 12B
ciphertext = AES-256-GCM(key, nonce, gzip(JSON), AAD = all header bytes) | tag 16B
```
- KDF default: **PBKDF2-HMAC-SHA256 with 600k iterations** (built into the JDK, no native code). Argon2id (**m=19 MiB, t=2, p=1** is OWASP's minimum) is preferable for passwords but needs a library, e.g. Bouncy Castle `Argon2BytesGenerator` **[UNVERIFIED on Android performance]**. Storing params in the header lets Bloom raise them later.
- Enforce a passphrase of ≥10 characters or 4 diceware words with a strength meter. Plaintext CSV/JSON export should require an explicit warning confirmation.

---

## 4. Widgets (Jetpack Glance)

- **Versions:** Glance **1.2.0 stable (26 Aug 2026)** and 1.3.0-alpha02 (1 Jul 2026) ([release notes](https://developer.android.com/jetpack/androidx/releases/glance)). 1.2 changes: minSdk 23, generated previews (`providePreview` + `setWidgetPreview`), `MultiProcessGlanceAppWidget`, image alpha. 1.3 alpha adds Remote Compose snap scrolling (Android 17+, compileSdk 37). **Use 1.2.0.**
- **Limitations:** Glance has its own composables (no `androidx.compose.ui`) that map to RemoteViews: `Box`→`RelativeLayout`, `Column`/`Row`→`LinearLayout`, `LazyColumn`→`ListView`. There are **no custom fonts** (system families only), no Canvas or custom drawing, so draw rings as pre-rendered drawables or bitmaps. Interaction is limited to clicks, compound buttons and actions ([Glance build UI](https://developer.android.com/develop/ui/compose/glance/build-ui)).
- **Sizes:** use `SizeMode.Responsive(setOf(...))`. The system picks the best fit, falling back to the smallest. Suggested sizes: `DpSize(57,57)` 1×1 ("D12" dot), `110×110` 2×2 (day + next-period countdown), `250×110` 4×1 or 4×2 (week strip), `250×250` (mini calendar). Pre-Android 12 behaves like Exact. Set `targetCellWidth/Height`, `minResizeWidth` and `maxResizeWidth` in the provider XML.
- **Update mechanisms (official):**
  - `updatePeriodMillis` has a **minimum of 30 min**, and 0 disables it. Set it to 0 and use **WorkManager** for configurable schedules ([advanced widget docs](https://developer.android.com/develop/ui/views/appwidgets/advanced)). Glance `provideGlance` already runs inside a WorkManager session.
  - Call **`GlanceAppWidget().updateAll(context)`** after every data change in the app, on app open, and from the daily worker.
  - **Day rollover:** `ACTION_DATE_CHANGED` is **not** on the implicit-broadcast exemption list, so a manifest receiver will not get it on API 26+ ([exceptions list](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)). Instead, enqueue a unique `OneTimeWorkRequest` with `initialDelay` set to the next local midnight plus a few minutes, which re-enqueues itself. Also listen for `TIME_SET`, `TIMEZONE_CHANGED`, `LOCALE_CHANGED` and `BOOT_COMPLETED` (all exempt) to re-render and reschedule. **No exact alarms are needed.** A few minutes' delay is acceptable for day-granular content.
  - Standby buckets still throttle work ([docs](https://developer.android.com/develop/ui/views/appwidgets/advanced)). Mitigate by recomputing "today" at render time from stored dates, never storing "day N" itself.
- **Dark mode:** `GlanceTheme` with `ColorProviders(light, dark)` or `ColorProvider(day=, night=)`. Use `GlanceTheme.colors.widgetBackground`. Material You dynamic color is available on Android 12+ via the default Glance theme. Prefer resource IDs so the system resolves day/night ([build UI](https://developer.android.com/develop/ui/compose/glance/build-ui)).
- **Discreet widget:** when "Discreet" is on (default when app lock is enabled), show only a neutral glyph plus "Day 12" or "4 days", with no "period", "fertile" or "ovulation" text, and neutral colors (no red/pink coding). Tapping it opens the app behind the lock. Widget content is rendered by the launcher and visible to anyone holding the unlocked phone, and possibly on lock-screen or hub widget surfaces **[UNVERIFIED per launcher]**. The discreet mode addresses exactly this. Exclude widget state from backup too.
- **Bitmap memory:** the RemoteViews bitmap cap is **6 × screenWidth × screenHeight bytes** (1.5 screens at ARGB_8888). Exceeding it throws `IllegalArgumentException` ([AOSP commit mirror](https://gitlab.e.foundation/e/os/android_frameworks_base/-/commit/311c79c3e93589c6fc720fe6c58ed522af591376)). There is also a ~1 MB binder transaction limit. Android 17 warns that `partiallyUpdateAppWidget` with bitmaps keeps old allocations ([advanced docs](https://developer.android.com/develop/ui/views/appwidgets/advanced)). Rules: prefer vector drawables and `ImageProvider(resId)`, size any bitmap to the widget's dp × density, and never partial-update images.

---

## 5. Data portability

| App | Export | Format / fields |
|---|---|---|
| Clue | In-app "Download my data", link emailed, expires in 72 h | **JSON** with tracked data and settings ([Clue](https://support.helloclue.com/hc/en-us/articles/17320910724125-How-do-I-get-a-copy-of-my-Clue-data)). Field names **[UNVERIFIED]**. Build the importer against real sample files |
| Flo | Request via in-app Help → Contact us (or Menu → My Data) | **JSON** of raw values ([Flo help](https://help.flo.health/hc/en-us/articles/360054973811-How-do-I-export-my-data)). Schema **[UNVERIFIED]**. Anonymous Mode users must register first |
| drip | Settings → Data → Export/Import, CSV | Date `YYYY-MM-DD`. `bleeding.value` 0=spotting, 1=light, 2=medium, 3=heavy. `bleeding.exclude` true/false, which must not be empty. Dotted `symptom.field` headers for temperature, mucus, cervix, mood and so on ([FAQ](https://dripapp.org/faq.html)). The full header list **[UNVERIFIED]** can be read from the drip source on GitLab |

### Bloom JSON (canonical export, inside the encrypted backup)
```json
{
  "format": "bloom-export",
  "schemaVersion": 1,
  "appVersion": "1.0.0",
  "exportedAt": "2026-11-10T09:00:00Z",
  "settings": { "typicalCycleDays": 28, "tempUnit": "C", "lutealOverride": null },
  "days": [
    { "date": "2026-10-01", "flow": "medium", "spotting": false,
      "bbt": { "value": 36.45, "time": "06:30", "excluded": false },
      "mucus": "creamy", "cervix": null, "lhTest": "negative",
      "symptoms": ["cramps", "headache"], "mood": ["calm"], "sex": "protected",
      "note": "free text", "cycleExcluded": false }
  ]
}
```
- **Versioning:** use an integer `schemaVersion` that only goes up. Importers accept every version ≤ current and migrate step by step (`v1→v2→…`) in pure functions with unit tests. Unknown fields are kept in an `extra` map rather than dropped. Enum values are stable lowercase strings, never ordinals. Dates are ISO-8601 local dates (days carry no timezone), and only `exportedAt` uses UTC. Derived data (predictions, cycle stats) is **never exported** and is recomputed on import. Publish a JSON Schema file per version in `docs/schema/`.
- **Import:** detect the source by sniffing headers and keys (Bloom, drip CSV, Clue JSON, Flo JSON). Show a dry-run preview ("412 days, 14 cycles, 3 conflicts") and let the user choose merge or replace.

### Bloom CSV (human-readable, plaintext, with a warning)
`date,flow,spotting,bbt_c,bbt_time,bbt_excluded,mucus,cervix,lh_test,symptoms,mood,sex,note,cycle_excluded`
- One row per logged day. Multi-value fields are `;`-separated. Temperatures are always in °C, with conversion handled in the UI. UTF-8 with a header row, RFC 4180 quoting. Put the version in the filename (`bloom-v1-2026-11-10.csv`), not in a comment line, because many spreadsheet tools choke on comment lines.

---

## Decisions summary

| Area | Decision |
|---|---|
| Network | No `INTERNET` permission, no third-party SDKs, CI check on the merged manifest |
| Storage | Room + SQLCipher (`sqlcipher-android`). Random DEK wrapped by a Keystore AES-GCM key in `noBackupFilesDir`. Settings in Tink-encrypted DataStore. **Do not use** security-crypto |
| App lock | BiometricPrompt (`BIOMETRIC_STRONG`) + app PIN (PBKDF2-SHA256 ≥600k + Keystore HMAC pepper, throttling). On by default |
| Screen privacy | `FLAG_SECURE` + `setRecentsScreenshotEnabled(false)` by default, discreet icon alias |
| Backup rules | `allowBackup=false`. `dataExtractionRules` excludes everything (cloud, device-transfer, cross-platform) |
| Backup/export | SAF only. `.bloom` = header + PBKDF2 (Argon2id later) + AES-256-GCM over gzip JSON with the header as AAD. Plaintext CSV/JSON behind a warning |
| Prediction | Recency-weighted median of the last ≤12 cycles, MAD outlier rejection, shrunk SD window, 4 confidence tiers, luteal 13 d default (personalised with BBT 3-over-6) |
| Fertile window | ovulation −5 … +1, widened by uncertainty. Informational only, never contraception |
| Disclaimers | "Not a medical device / not contraception" at onboarding, on the fertile sheet and in About. FIGO-based "talk to a clinician" flags |
| Widgets | Glance 1.2.0, `SizeMode.Responsive` (1×1, 2×2, 4×1, 4×2), `updatePeriodMillis=0` + midnight WorkManager chain + `updateAll` on change + exempt time broadcasts. Vector assets, discreet mode default when locked |
| Portability | Versioned `bloom-export` JSON (integer `schemaVersion`, stepwise migrations), flat CSV v1, importers for drip CSV and Clue/Flo JSON (schemas to verify from samples) |
