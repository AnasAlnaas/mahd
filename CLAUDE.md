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
- Reminders: the daily alarm is inexact (Android may deliver it up to 18 h late), so never re-set it on every app
  open — `ReminderScheduler.ensureScheduled()` only adds a missing alarm (BootReceiver re-sets it). When the app is
  opened and today's check has not run (`ReminderRules.isCheckDue`, `Prefs.lastCheckDate`), the app runs it itself.
  A tapped reminder opens its profile and tab (`OpenRequest`). Lock screens set to hide sensitive content show only
  "لديك تذكير من أطوار" (owner's choice: follow the phone's setting, no in-app switch).
- Notification permission is asked in context: a Home card (`RemindersPrompt`) explains the reminders and asks only
  when tapped (Android asks at most twice); never auto-prompt at startup.
- Danger signs: pregnancy (`pregnancy.json`) and children (`child_danger_signs.json`, DRAFT from WHO IMCI + NICE fever
  rule for under 3 months). A section with `maxAgeMonths` is shown first for a younger child. No "call ambulance"
  button until a doctor confirms the number (owner's decision).
- Growth: WHO standards are for term babies, so measurements before a premature baby's original due date
  (negative corrected age) are listed but not charted or classified.
- Backups: `BackupFile.decode(text, today)` validates the whole file before restore: structure (orphan/duplicate rows)
  and values (at least one profile, known kinds, child has a birth date / pregnancy a due date, dates 1900..today+1y,
  positive finite measurements). Everything the app can save must keep passing. The picker reads at most 5 MB, off the
  main thread. Backup file name: `atwar-backup-DATE.json`. Android Auto Backup is ON (database goes to the mother's
  own Google account); the privacy text says so. Declare it in Play's data-safety form.
- Photos (owner chose "option A"): each child has a "الصور" tab. Pictures are JPEGs in the app's private folder
  (`files/photos`, `data/PhotoStore`), shrunk to 1600 px and saved without EXIF; the DB (v3, table `photos`) says whose
  and when. Camera = the phone's camera app (`TakePicture`), gallery = Android's photo picker: no camera/photo
  permissions. Photos are NOT in Auto Backup (it stops above 25 MB) nor in the backup file; "save to gallery" copies them
  to Pictures/Atwar (storage permission only on Android ≤ 9). A restore keeps the photos of a child the backup
  describes the same way (`Profile.isSameChildAs`); delete profiles via `MahdApp.deleteProfile` (removes the files).
- Islamic advice (owner's request, 2026-10-08): `assets/content/islamic_advice.json` holds Qur'an verses and hadith
  from Sahih al-Bukhari / Sahih Muslim ONLY, each with its exact source (Bukhari numbers = Fath al-Bari, Muslim =
  Abd al-Baqi). Never add weak/disputed narrations, nor texts that clash with medical safety (tahnik with a chewed date,
  honey under one year). Items are tagged by stage (`for`, weeks/months, `sex`); `AdviceRules.forDay` rotates one per
  day per profile. Shown as a card high on Home (danger-signs card moved to the end: owner's choice) and as a
  notification on Tuesdays and Fridays (Settings switch, part of the reminders). Owner chose "sources only, no draft
  note" in the UI — but the texts (and the items marked `"verify": true`) must still be checked against printed
  sources by a qualified person before release. Ornate brackets ﴿ ﴾ are drawn in the system font (Readex lacks them).
- Screens collect database flows with `initial = null` and draw nothing until loaded (no "everything is late"
  flash); Home reads one `Repository.childRecords()`; each profile gets a fresh screen (`key(selected.id)`).
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

## Visual language ("Moon phases", Material 3 Expressive — read `DIRECTION.md` before any UI work)
- `DIRECTION.md` holds the palette, type, shapes, motion and the **banned list** (no emoji in UI chrome, no coloured
  card-edge bars, no stat-tile rows, no progress rings, no letter-spacing). Owner chose this direction (2026-10-08).
- Font: Readex Pro, one variable file `res/font/readex_pro.ttf` (OFL; licence in `assets/licenses/`). If it ever
  misbehaves, the owner's fallback is IBM Plex Sans Arabic.
- Signature: `MoonPhase` (components/Moon.kt) for every progress (vaccines, milestone areas, pregnancy, empty states);
  `TrimesterMoons` for the pregnancy (the launcher icon's three moons).
- Containers: `ListGroup` + `GroupRow`/`GroupItem` (Android 16 grouped list: 24dp outer, 6dp inner corners, 2dp gaps);
  `AppCard` for a single block; `FeaturedCard`/`WarningCard` for the few things that must stand out.
- Controls: `ExpressiveButton` (pill that squashes when pressed), `ConnectedChoice` (connected button group),
  `AppFilterChip`. Bottom bar = `ShortNavigationBar`; sub-screens use a collapsing `MediumTopAppBar`.
- Light/dark: Settings → المظهر offers تلقائي (follow the phone, default) / فاتح / داكن (`Prefs.themeMode`). MainActivity
  applies it to `MahdTheme(dark)`, the system-bar icons (`enableEdgeToEdge` with `SystemBarStyle.auto { dark }`) and the
  window background, so never call `isSystemInDarkTheme()` elsewhere — read the theme instead.
- Area colours: vaccines = rose (`primary`), growth = teal (`secondary`), milestones = gold (`tertiary`): used for moons
  and little else. Milestone domains use `domainAccent(id)`. Extra surface roles live in `LocalSurfaces`.
- material3 1.4 keeps `MaterialExpressiveTheme`/`MotionScheme` internal, so the expressive springs are copied in
  `AppMotion` (Theme.kt). Swap to the official API when material3 1.5 is stable (not before: no beta libraries).
- Tab icons are hand-drawn line icons (1.8 stroke, round caps): home = crescent, vaccines = syringe, growth = chart,
  milestones = building blocks, photos = frame, pregnancy = heart.
- Vaccines screen is a timeline: finished visits and all future visits except the next one fold into one quiet line.
- Growth chart labels its curves (+3 … −3, with a left-to-right mark so the sign stays in front in Arabic),
  tags the latest value, can zoom to the recent months, and shows a trend line (`GrowthMath.trend`).
- Milestone "share" draws a PNG with Android Canvas (`util/ShareCard.kt`); text goes through `StaticLayout` for correct Arabic.
- Launcher icon: three moons from crescent to full (phases of the moon = stages of growth).
- Gotcha: never edit Arabic files with perl one-liners (it double-encodes UTF-8); use the editor tool.
- Gotcha: Kotlin compiles in-process (`gradle.properties`) because the Kotlin daemon was unreachable on this PC.
