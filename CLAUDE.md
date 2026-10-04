# Notes for Claude

Context for continuing this project in a new session or on another computer.

## Who and why
The owner is building an app for Libyan/Arab mothers who often can't use English-first apps
(BabyCenter, What to Expect, etc.). Arabic apps on the market cover only one stage; none use the
Libyan vaccine schedule or Libyan dialect. Plan: MVP first, add features over time.

## Decisions already made (don't re-litigate)
- Native Android only (Kotlin + Jetpack Compose). ~95% of Libyan phones are Android. Web later (v2).
- Fully offline. No INTERNET permission, no backend, no accounts, no analytics/ads SDKs.
- Minimal dependencies: only AndroidX core, activity-compose, Compose BOM (ui, foundation, material3).
  Database = built-in `SQLiteOpenHelper` (not Room). Reminders = `AlarmManager` (not WorkManager).
  JSON = built-in `org.json`. Navigation = tiny custom `Navigator` (no navigation library).
  Icons = hand-written vector drawables (no icon library). Charts = Compose `Canvas`.
- AGP 9.x with built-in Kotlin: do NOT apply `org.jetbrains.kotlin.android`. Only the Compose
  compiler plugin is applied. minSdk 26, target 36, compile 37.
- Arabic-only UI, forced RTL via `AppLocale.wrap()` in `MainActivity.attachBaseContext`.
  Numbers are always 0–9: pass numbers to string resources as `%1$s` text, never `%d`.
  Arabic plurals (zero/one/two/few/many/other) in `strings.xml`; watch grammatical case
  (e.g. use "باقي %s" / "مضى على موعده %s" so the nominative dual "يومان" stays correct).
- The mother is addressed in the feminine form. Text register: simple Modern Standard Arabic
  with light Libyan touches (قطوس، شنو، كورة، دلّاع، البازين). Libyan-dialect audio is planned for v1.1.
- Medical content is DATA in `app/src/main/assets/content/*.json` with a `"reviewed"` flag;
  the UI shows a draft notice while `reviewed` is false. Never present medical text as reviewed.
- Vaccine schedule (`vaccines_ly.json`) is a DRAFT: doses marked `"verify": true` are guesses.
  Confirmed from public sources: birth BCG + HepB + OPV; DTaP 2/4/6/18 m; rotavirus 2/4/6 m
  (not after 8 m); varicella + hepatitis A at 15 m (added 2023).
- Vaccine schedule: a dose can be given, marked "skipped / not applicable" (table `vaccine_skips`, DB v2), or
  expire automatically via `"maxAgeMonths"` in `vaccines_ly.json` (rotavirus: 8). Such doses are never "late"
  and get no reminders. Reminders are catch-up tolerant (`logic/ReminderRules.kt`).
- Growth: WHO standards are for term babies, so measurements before a premature baby's original due date
  (negative corrected age) are listed but not charted or classified.
- Backups: `BackupFile.decode` validates the whole file (orphan/duplicate rows) before restore. Android Auto Backup
  is ON (database goes to the mother's own Google account); the privacy text says so. Declare it in Play's data-safety form.
- Content files are covered by `ContentFilesTest`; run `gradlew testDebugUnitTest` after every content edit.
- App name: "أطوار" (Atwar), from the verse ﴿وَقَدْ خَلَقَكُمْ أَطْوَارًا﴾ (Nuh 71:14) — stages of growth. Decided by the owner.
  The package `ly.mahd.app`, the `Mahd` class/theme names and the backup marker `"app": "mahd"` are still the old working name:
  keep the backup marker forever (old backups), change `applicationId` before the first Play release (see build.gradle.kts).

## MVP scope (v1.0)
Onboarding + disclaimer; pregnancy and child profiles (several allowed); "Today" home;
pregnancy weeks 1–42 + danger signs; "my baby is born" conversion; vaccines with reminders;
growth (WHO charts, plain-language bands, corrected age for preterm); milestones 2 m–4 y;
backup save/share/restore as JSON; settings/about.

## Later (v1.1+)
Libyan-dialect audio, kick counter, contraction timer, appointment/medicine reminders, Hijri dates,
Ramadan & fasting guidance, starting solids with Libyan foods, photo diary, WhatsApp milestone
cards, printable doctor report, feeding/sleep log. v2 (optional internet): family sharing,
content updates, web version, clinic directory.

## Open questions for the owner
- Their coding experience (so far: Claude writes the code, owner runs/tests in Android Studio).
- Final app name; access to a Libyan paediatrician and obstetrician for content review.

## Build
`gradlew assembleDebug` and `gradlew testDebugUnitTest`. Needs JDK 17+ (Android Studio's bundled
JBR works). Keep the project outside OneDrive.
