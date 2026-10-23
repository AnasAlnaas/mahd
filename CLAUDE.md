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
- Feeding (owner's decision, 2026-10-10): the app promotes breastfeeding only. Never mention formula, bottles or
  "breast or bottle" as a choice in any content or string. A feeding problem is sent to the doctor (e.g. `s_enough_milk`:
  wet nappies and weight), never solved with formula in the app's own words.
- الأربعين (owner's request, 2026-10-11): "my baby is born" first asks طبيعية / عملية (`BirthType`, column
  `profiles.birth_type`, DB v4, in backups as optional "birthType"). For a child from the day of birth to day 43 (day of
  birth = day 1), Home shows a الأربعين card (a moon filling over 40 days) opening `PostpartumScreen`: what's normal per
  stage and per birth type (both when unknown; the type can be set there too), tips (mother) / how to help (father),
  the mother's emergency and same-day danger signs, the WHO postnatal check-ups (day 3, days 8–15, week 6) with one
  reminder each (keyed by birth date so twins share it), mood as information only (`moodMother` / `moodFather`: when low
  mood needs help) and Libyan الأربعين customs weighed against evidence. The app asks the mother NO questions in الأربعين
  (owner's decision, 2026-10-11: the PHQ-2 mood check was removed; don't add questionnaires back). Content:
  `postpartum.json` (DRAFT, reviewed false); shared text speaks to neither parent (PostpartumTest checks).
- Hospital bag (owner's request, 2026-10-11): `hospital_bag.json`, a general list for a Muslim woman (modest clothing,
  dhikr; no bottles). No country-specific items unless the owner approves them. Shown on pregnancy Home from week 28;
  ticks live in `Prefs.hospitalBag` (not in backups). Item ids are stored: never rename one.
- Rejected / postponed by the owner (2026-10-11): no feeding/nappy/sleep log (the owner does not want parents opening the
  app many times a day); no contraction timer; a mother & child health booklet with a doctor report kept for later.
- "ماذا آكل؟" (owner's request, 2026-10-13; it was postponed before): a DRAFT list of general and Libyan foods and drinks for
  pregnancy and breastfeeding is in `drafts/what_to_eat_draft.md`, sourced from NHS/FDA/CDC/WHO/NCCIH with an evidence column.
  The owner reviews and edits it first: do NOT add it to the app (no JSON, no screen) until they approve the list. Then make
  `what_to_eat.json` with `"reviewed": false`, stable ids, and feeding rules above (no bottles/formula, drop such source remarks).
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
  day per profile. WORDING IS VERIFIED, NOT TRUSTED (owner caught a wrong reflection, 2026-10-10): every verse must
  match api.quran.com letter for letter, and every hadith must be the narration's own words (not a paraphrase), with
  "واللفظ للبخاري/لمسلم" when the two differ. After ANY change to that file run
  `powershell -ExecutionPolicy Bypass -File tools\verify_islamic_texts.ps1` (must say "0 failed"). The script checks
  wording only: each reflection ("advice"/"adviceFather") must also be read against the verse's or hadith's real meaning
  and context (e.g. الكهف 46 contrasts children with الباقيات الصالحات; Muslim 996 is about servants, so it was dropped).
  Shown as a card high on Home (danger-signs card moved to the end: owner's choice) and as a
  notification on Tuesdays and Fridays (Settings switch, part of the reminders).
  Science facts (owner's request, 2026-10-10): `assets/content/science_facts.json` (kind `fact`, ids `s_*`, same stage
  fields) rotates with the verses/hadith on the Home card ("معلومة علمية"). Each fact names a health body (WHO, NHS, AAP,
  ACOG, CDC) or a specific peer-reviewed study; it is medical content, so the card shows the draft line while that file's
  `reviewed` is false. Notifications stay verse/hadith only (the switch promises that). Removed in the same review:
  the aqiqah hadith (Bukhari 5472 is mu'allaq, not a connected narration).
- Context-aware advice and reminders (owner's request, 2026-10-10): `logic/LibyaCalendar` (offline) gives Libya's season
  (winter Dec–Feb, spring Mar–May with the ghibli, summer Jun–Sep, autumn Oct–Nov), Islamic occasions from
  `java.time.chrono.HijrahDate` (Umm al-Qura; Libya sights the moon, so Settings → التاريخ الهجري shifts it −1/0/+1 day:
  `Prefs.hijriOffset` → `LibyaCalendar.hijriOffsetDays`, applied to Home, reminders and greetings alike) and
  Libyan public holidays. Content items may carry `seasons`, `occasions` (ramadan, ramadan_last_ten, dhul_hijjah_ten,
  arafah, eid_fitr, eid_adha, celebration = Eids/Mawlid/17 Feb) and `situations` (siblings, expecting, twins, preterm,
  from all the profiles on the phone: `Situation.of`); such an item shows only when one applies. `AdviceRules.forDay`:
  the Eids, Arafah, celebration days and the first days of Ramadan / last ten / Dhul Hijjah show their own items first;
  otherwise contextual items come up on spaced days (none repeats within 14 days); the card label says why
  ("معلومة علمية · في الصيف"). Advice notifications also go out on 1 Ramadan and the first day of each Eid with a
  greeting title. Vaccine reminders add a line when the due day is a public holiday (centre may be closed) or in Ramadan. Owner chose "sources only, no draft
  note" in the UI — the reflections should still be read by a qualified person before release. Ornate brackets ﴿ ﴾ are drawn in the system font (Readex lacks them).
- Mother or father (owner's request, 2026-10-09): asked on first open (`ParentChoiceScreen`, before onboarding; also once
  for existing installs), changeable in Settings (`Prefs.parent`, `LocalParent`). Father = blue palette (`FatherLight/Dark`
  in Theme.kt; teal/gold/health colours unchanged) and masculine text. Text swap without touching screens: every string
  with a father version lives in `res/values/strings_father.xml` as `<name>_m`, listed in `util/FatherStrings.kt`;
  `AppLocale.wrap` returns resources that swap them (Android's grammatical-gender API needs Android 14). Changing the role
  recreates the activity. `FatherTest` checks each `_m` has its mother string, the same placeholders, and a map entry —
  add both when you add a gendered string. Father content (DRAFT): `pregnancy.json` `fatherStages` (wife / how to support
  her, weeks 1–42, shown instead of "your body"/tip and on Home), `milestones.json` `tipsFather` per age, and
  `islamic_advice.json` `parent` (mother|father|both) + `adviceFather`; 15 father-only texts. Shared content (week "baby"
  text, skill items, warnings) is written to neither parent. One phone = one parent; they share data via backup files.
- Skills tab (owner's design, 2026-10-09): only skills of the ages the child has REACHED are shown (corrected age for a
  preterm baby); older ages stay hidden. All reached lists show as one list (`logic/MilestoneFocus`): "راقبيها الآن"
  (newest age first) and "أتقنها" (answered yes) at the end. Answers: نعم / ليس بعد / يحاول — `MilestoneAnswer.PROGRESS`
  keeps code 2 of the old "not sure" so old saves and backups still load. "Yes" plays a small firework (`SparkBurst`) and
  holds the skill in place ~1 s before it glides down (`animateItem`); the page keeps its scroll position (a lazy list
  would otherwise follow the moved key). A 1-month list (AAP, not CDC) is first; before 1 month the tab and Home show a
  countdown moon instead of skills.
- Owner's edits (2026-10-15): Home shows the child's age in days on a smaller line under the age (from 1 month on).
  Growth is no longer a tab of its own: skills and growth share one tab (`SkillsAndGrowth`, a ConnectedChoice
  المهارات / النمو; `Routes.GROWTH` opens it on its growth part). The bottom bar names that tab "النمو" with the growth
  chart icon (owner, 2026-10-16). Adding a child older than the first list
  saves "yes" for every skill of the earlier ages (`MilestoneFocus.presumedDone`, `Repository.addChild`), so only the
  newest list is empty; a note under "أتقنها" says any answer can be changed.
- Mother's exercises (owner's request, 2026-10-15): `exercises.json` (DRAFT, reviewed false) holds the owner's 17
  exercises (her guide, after the Pregnancy and Postpartum TV channel) in stages: الثلث الأول/الثاني/الثالث (weeks 1–13,
  14–27, 28–42; the current trimester is shown first) and بعد الولادة; safety notes follow ACOG 2020, WHO 2020, NHS and
  Goom 2019. `ExercisesScreen`: في الحمل / بعد الولادة switch, natural/caesarean advice from `birthType` (an exercise's
  `caesarean` note shows unless the birth was natural), stop signs, a father part. Shown on pregnancy Home and on child
  Home for the first 6 months (`EXERCISES_UNTIL_DAY`). Shared text speaks to neither parent (ExercisesTest).
  Pictures (AI-drawn by the owner from `drafts/exercise_pose_prompts.md`): each exercise lists its `images` (one or two
  poses, `<id>`, `<id>_1`/`_2`, plus the shared optional `pelvic_floor_diagram`); files are
  `assets/exercises/<name>.webp`, made by `tools/prepare_exercise_pictures.ps1` (ffmpeg: ≤1000 px WebP, from the
  git-ignored `drafts/exercise_pictures/`). A missing picture is simply not shown; pictures sit on a fixed light panel in
  both themes (white, matching the pictures' own white background). An exercise's own poses take turns in one frame
  (`PoseFrame`: crossfade loop, the owner's exception to "nothing moves on its own"; still when animations are off, tap
  for the next pose); the shared diagram sits beside them. All 27 pictures are in (Gemini, 2026-10-16).
  Daily program (owner's PDF "برنامج التمارين اليومي للحامل", 2026-10-23): a stage may carry a `routine` (ordered
  exercise ids + that day's dose; ids may come from either part, e.g. Kegel = `post_pelvic_floor`). Only `t3` has one
  (weeks 28–42): the PDF's 9 minus ankle circles and walking — the owner said NO new exercises. `ExercisesScreen` shows
  it first for the MOTHER only (she ticks her own exercises): a moon for the day, the pregnancy week as 7 day moons
  (an earlier day can be opened to tick a forgotten exercise, `logic/ExerciseRoutine`), one tick per exercise per day
  (Kegel too, not 3: owner doesn't want many opens a day). IN ORDER (owner, 2026-10-23): only the current exercise
  (`ExerciseRoutine.current`, the first not ticked) can be ticked; it opens with its pictures (poses take turns) and
  steps, which shrink away when ticked while the next one's rise in; later ones are locked; tapping the one just before
  un-ticks it (one step back). A routine step may carry its own `images` (replacing the exercise's in the program only):
  Kegel shows just `pelvic_floor_diagram`, because `post_pelvic_floor`'s picture has a cot; the owner will draw
  `post_pelvic_floor_program` (prompt in drafts/exercise_pose_prompts.md). No reminder, no printable tracker (later). Ticks: table
  `exercise_log` (DB v5), in backups as optional "exerciseLog". Pregnancy Home's exercises row shows today's count. Fill `"pictureCredit"` if the drawing tool's licence asks for a credit. Never rename ids or picture names.
- Screens collect database flows with `initial = null` and draw nothing until loaded (no "everything is late"
  flash); Home reads one `Repository.childRecords()`; each profile gets a fresh screen (`key(selected.id)`).
- Content files are covered by `ContentFilesTest`; run `gradlew testDebugUnitTest` after every content edit.
- App name: "أطوار" (Atwar), from the verse ﴿وَقَدْ خَلَقَكُمْ أَطْوَارًا﴾ (Nuh 71:14) — stages of growth. Decided by the owner.
  The source namespace `ly.mahd.app`, the `Mahd` class/theme names and the backup marker `"app": "mahd"` are still the old working name:
  keep the backup marker forever (old backups). `applicationId` is `ly.atwar.app` (owner's choice, 2026-10-12): permanent, never change it.
- Distribution (owner, 2026-10-12): first store is Aptoide (signed APK only, no AAB; Play comes later). Release signing: `keystore.properties`
  (git-ignored) points to `D:\code\mahd-keys\atwar-release.jks`; `gradlew assembleRelease` gives a signed `app-release.apk`. The key must be
  backed up by the owner and used for every update (a different key cannot update an installed app).

## MVP scope (v1.0)
Onboarding + disclaimer; pregnancy and child profiles (several allowed); "Today" home;
pregnancy weeks 1–42 + danger signs; "my baby is born" conversion; vaccines with reminders;
growth (WHO charts, plain-language bands, corrected age for preterm); milestones 1 m–4 y;
backup save/share/restore as JSON; settings/about.

## Later (v1.1+)
Libyan-dialect audio, kick counter, appointment/medicine reminders,
starting solids with Libyan foods / "can I eat it?" (postponed), photo diary, WhatsApp milestone
cards, mother & child health booklet with a printable doctor report (later). v2 (optional internet): family sharing,
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
- Motion (owner asked for a smoother, more animated app, 2026-10-22; rules in DIRECTION.md "Motion"): helpers live in
  `components/Motion.kt` (openScreen/closeScreen, fadeThrough, sharedAxisX, fadeAndResize, unfold/fold, RollingText,
  rememberPressMorph, pressScale, staggerIn, FadeInImage). `MainNav` drives screens with a `SeekableTransitionState` +
  `PredictiveBackHandler` (manifest: `enableOnBackInvokedCallback`); all tabs are one screen (`TABS_KEY`), so a `Screen`
  equals another by key only. Screens stay composed while they slide out or peek during a back swipe, so anything that
  navigates by itself must run only for the screen shown (`active` / `redirect` in MahdRoot). A `SaveableStateHolder`
  keeps open screens' state (the tabs' scroll) and forgets closed ones. Content that leaves through an animation must
  render from the state it was given (`AnimatedContent { shown -> }`), never from the new value. The onboarding forms
  pass `onSaved = {}`: MahdRoot crossfades to the main screens by itself.
- Tab icons are hand-drawn line icons (1.8 stroke, round caps): home = crescent, vaccines = syringe, growth = chart
  (the النمو tab), photos = frame, pregnancy = heart; `ic_milestones` (building blocks) is no longer on the bar.
- Vaccines screen is a timeline: finished visits and all future visits except the next one fold into one quiet line.
- Growth chart labels its curves (+3 … −3, with a left-to-right mark so the sign stays in front in Arabic),
  tags the latest value, can zoom to the recent months, and shows a trend line (`GrowthMath.trend`).
- Milestone "share" draws a PNG with Android Canvas (`util/ShareCard.kt`); text goes through `StaticLayout` for correct Arabic.
- Launcher icon: three moons from crescent to full (phases of the moon = stages of growth).
- Gotcha: never edit Arabic files with perl one-liners (it double-encodes UTF-8); use the editor tool.
- Gotcha: Kotlin compiles in-process (`gradle.properties`) because the Kotlin daemon was unreachable on this PC.
