# أطوار (Atwar) — hand-over notes

Written 2026-10-04 to continue this project in a new chat or on another computer; updated the same day after the
second review round. Read this first, then `CLAUDE.md` (decisions already made — don't re-litigate) and `README.md`.

## 1. What the project is
- **أطوار / Atwar** (was "مهد / Mahd"): an **offline, Arabic-first, native Android app** (Kotlin + Jetpack Compose) for
  Libyan/Arab mothers, from pregnancy to age 4: pregnancy week by week, vaccine schedule with reminders,
  WHO growth charts, developmental milestones, danger signs, JSON backup/restore.
- The name comes from the verse ﴿وَقَدْ خَلَقَكُمْ أَطْوَارًا﴾ (Nuh 71:14) = stages. Chosen by the owner.
- Package/`applicationId` is still `ly.mahd.app` (old working name). **Change it once, before the first Play release.**
  Keep the backup marker `"app": "mahd"` forever (old backup files depend on it).
- Repo: `https://github.com/AnasAlnaas/mahd` (private). Local clone: `D:\code\mahd` (outside OneDrive, on purpose).
- `main` is pushed and up to date; it includes the second review round (branch `fix/review-round-2`, fast-forwarded).
  All local feature branches are merged and can be deleted (see §7).
- Owner: not a programmer ("Claude writes the code, owner runs/tests"). Wants Claude to **drive** (build, run, test,
  screenshot) and to ask before outward-facing actions (push, merge, install on the real phone).

## 2. State of the app (all verified on an emulator, 41 unit tests pass, release build OK)
- Onboarding: welcome + disclaimer → choose *pregnant / I have a child / I have a backup file* (restore on first launch).
  The choice screen scrolls on small phones / large fonts.
- Profiles: several pregnancies/children; "my baby is born" turns a pregnancy into a child; an overdue pregnancy can
  still be renamed.
- Home: greeting hero (boy/girl badge), 3 "today" tiles, **danger-signs card** (pregnancy and child), next-vaccine card
  with progress ring, milestones and growth cards, accent bars, fade between tabs. A **"turn on reminders" card**
  explains the reminders and asks for the notification permission only when tapped (or opens the phone's settings
  if Android was already asked); "not now" hides it.
- Screens draw nothing until their data is loaded (no "everything is late" flash); Home reads one
  `Repository.childRecords()`; each profile gets a fresh screen.
- Vaccines: **timeline** (green done / red late / filled due / grey skipped / hollow later; readable in dark mode),
  folding, progress ring, per-dose date picker, "mark all given", "skip remaining", "record everything up to this visit",
  rotavirus auto-expires after 8 months.
- Reminders: one inexact daily alarm (~10:00) that is **only set when missing** (re-setting it on every app open used to
  push a late check to the next day forever); the app runs the day's check itself when opened if it has not run yet;
  2-day catch-up; tapping a reminder opens **that profile on the right tab**; lock screens set to hide sensitive content
  show only "لديك تذكير من أطوار".
- Growth: WHO LMS charts, labelled curves, tag on latest value, "recent months" zoom (now zooms vertically too),
  trend line, no chart/band before a premature baby's original due date.
- Milestones: per-age checklists (CDC 2022 adaptation), 4 domain rings, share-achievements PNG.
- Pregnancy: week guide 1–42, danger signs, trimester bar, "has your baby arrived?" prompt after the due date.
- **Child danger signs** (new, DRAFT): "at any age" + "under 3 months" lists from WHO IMCI; a baby under 3 months sees
  that list first.
- Settings: reminders switch (+ live "notifications are off" warning), backup save/share/restore, privacy note on the
  backup file, About. Backups are **validated by value** (at least one profile, dates 1900..today+1y, a child needs a birth
  date, a pregnancy a due date, sane measurements), read off the main thread, max 5 MB. File name `atwar-backup-DATE.json`.
- Sharing (backup, achievements card) shows a message instead of crashing when it fails.
- Medical content is **DRAFT** (`reviewed: false` in `app/src/main/assets/content/*.json`). Never present it as reviewed.

## 3. Environment on this laptop (Windows 11)
- Android Studio: `C:\Program Files\Android\Android Studio` (bundled JDK 25 in `jbr`). SDK: `%LOCALAPPDATA%\Android\Sdk`
  (platform `android-37.0`, build-tools with `apksigner`/`zipalign`). `local.properties` exists and is git-ignored.
- Emulator AVD `Medium_Phone_API_37.0` works (needs Windows Hypervisor Platform — already enabled).
- A real phone (Redmi, model 2201117TG, adb serial `3eebe64d`) is often plugged in over USB.
  **Always target the emulator explicitly** (`adb -s emulator-5554 …` or `ANDROID_SERIAL=emulator-5554`) so the phone is
  untouched. `gradlew installDebug` installs to every connected device otherwise.
- No python / node / sqlite3 on the machine (none on the emulator either). Git Bash + PowerShell + perl exist.
  **Never edit Arabic files with perl one-liners** (double-encodes UTF-8 — it corrupted `strings.xml` once; use the editor tool).
- Kotlin compiles in-process (`gradle.properties`) because the Kotlin daemon was unreachable; debug builds take ~10–30 s,
  `assembleRelease` (R8) ~3 min.
- Git: the saved GitHub login (Git Credential Manager) works for push; `gh` is not installed.
- `adb.exe` cannot read Windows paths longer than 260 characters ("cannot stat"): push files from a short path or name.

## 4. Commands
PowerShell, from `D:\code\mahd`:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest            # 41 tests
$env:ANDROID_SERIAL = "emulator-5554"      # keep the phone out of it
.\gradlew.bat installDebug
```
Start the emulator: `%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -avd Medium_Phone_API_37.0` (boots in ~2 min).

**Test APK for the owner's phone** (optimised release build, signed with this laptop's debug key so it can update a
debug install; not for Play — Play needs a real upload key):
```powershell
.\gradlew.bat assembleRelease
$bt = (Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" | Sort-Object Name | Select-Object -Last 1).FullName
& "$bt\zipalign.exe" -f -p 4 app\build\outputs\apk\release\app-release-unsigned.apk app\build\aligned.apk
& "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --out atwar-test.apk app\build\aligned.apk
```
(`*.apk` is git-ignored.) The phone needs "install unknown apps" allowed for the app used to open the file.

Driving the emulator with adb (Git Bash; set `MSYS_NO_PATHCONV=1` or `/sdcard/...` paths get mangled):
- screenshot: `adb -s emulator-5554 exec-out screencap -p > shot.png` (1080x2400)
- taps: `adb -s emulator-5554 shell input tap X Y`; swipe: `input swipe x1 y1 x2 y2 ms`
- find buttons: `uiautomator dump /sdcard/ui.xml`, then read `text` / `content-desc` / `bounds`.
- dark mode: `adb shell cmd uimode night yes|no`; reset app: `adb shell pm clear ly.mahd.app`.
- small phone + big font: `adb shell wm size 720x1280`, `wm density 320`, `settings put system font_scale 2.0`
  (undo: `wm size reset`, `wm density reset`, `font_scale 1.0`).
- notification permission: `pm grant|revoke ly.mahd.app android.permission.POST_NOTIFICATIONS`; to make Android ask
  again: `pm clear-permission-flags ly.mahd.app android.permission.POST_NOTIFICATIONS user-set user-fixed`.
- posted notifications: `dumpsys notification --noredact`; the alarm: `dumpsys alarm | grep -A4 ly.mahd.app`.
- app prefs: `adb shell run-as ly.mahd.app cat shared_prefs/mahd_prefs.xml` (debug builds only).
- read the generated share image: `adb exec-out run-as ly.mahd.app cat cache/share/atwar-achievements.png`.

Seeding test data (no sqlite): push a backup JSON to `/sdcard/Download/` and use **restore** (works on first launch too).
Backup format (version 1): `{"app":"mahd","format":1,"profiles":[{id,kind:"child"|"pregnancy",name,sex:"boy"|"girl",
birthDate,dueDate,weeksAtBirth}],"growth":[...],"doses":[{profileId,doseId,givenDate}],"milestones":[],"skippedDoses":[]}`.
`decode` rejects orphan/duplicate rows and nonsense values. On the emulator's Downloads: `mahd-test.json` (first round:
a 10-month-old, a 30-weeker, an overdue pregnancy) and `atwar-test.json` (a 16-month-old with a year of growth data, a
2-month-old whose vaccines are due in 3 days, a pregnancy 45 days past due, a current pregnancy). Restoring clears the
"reminder already sent" marks, so a restore + reopening the app re-sends due reminders.

## 5. Code map (`app/src/main/java/ly/mahd/app/`)
- `logic/` — `VaccinePlanner` (plan/summary/skips/expiry), `ChildAge` (corrected age, `isBeforeCorrectedTerm`),
  `GrowthMath` (bands, percentile, `trend`), `PregnancyMath`, `ReminderRules` (thresholds, catch-up, `isCheckDue`).
- `data/` — SQLite (`MahdDatabase` v2), `Repository` (Flow + version bump, `childRecords`), `BackupFile` (validated
  encode/decode), `Prefs` (incl. `lastCheckDate`, `reminderPromptDismissed`), `Models` (incl. `ChildRecords`).
- `reminders/` — `ReminderScheduler` (`ensureScheduled` / `schedule`), `ReminderReceiver` (`runChecks`, `runIfDue`,
  `BootReceiver`), `Notifications` (channel, post with profile + tab, lock-screen version, `openSettings`).
- `content/` — JSON/CSV readers; `ContentRepository` takes an opener lambda so tests can read the real files.
  Files: `pregnancy.json`, `milestones.json`, `vaccines_ly.json`, `child_danger_signs.json`, `growth_who.csv`.
- `ui/` — `MahdRoot` (onboarding vs main, tabs, `OpenRequest` from a tapped reminder), `Navigator` (+ `Routes`).
  `ui/screens/` — Home, Vaccines (timeline), Growth + `GrowthChart` (Canvas), Milestones, Pregnancy, `DangerSignsScreen`
  (pregnancy + child), Settings, Onboarding, `RemindersPrompt`, `Restore.kt` (picker + `applyRestore` + `runOrToast`).
  `ui/components/` — `AppCard(accent)`, `ProgressRing`, `TrimesterBar`, `EmojiBadge`, `LocalToday`, `LocalResumeCount`,
  `rememberCanPostNotifications`, date picker.
- `util/` — `Format` (Arabic dates/plurals, digits always 0–9), `AppLocale` (forced ar-LY RTL), `ShareCard`.
- Tests: `app/src/test/java/ly/mahd/app/LogicTest.kt` and `ReviewFixesTest.kt` (includes `ContentFilesTest`, which parses the
  real content files — run it after every content edit).

## 6. Decisions to keep (full list in CLAUDE.md)
Native Android only; fully offline (no INTERNET permission, no SDKs); minimal dependencies (AndroidX + Compose + test-only
`org.json`); Arabic only, RTL, feminine address, simple MSA with light Libyan touches; numbers passed as `%1$s` text;
medical text is data with a `reviewed` flag; Android Auto Backup stays ON (privacy text says so — mention it in Play's
data-safety form). Owner's decisions in the second round: **no "call ambulance" button until a doctor confirms the
number**; lock-screen privacy **follows the phone's setting** (no in-app switch); child danger signs **drafted from WHO**
and kept as a draft until a paediatrician signs off; never auto-prompt for notifications.

## 7. Open items / next steps (suggested order)
1. **Owner tries the test APK on the Redmi** (`atwar-test.apk`): reminders the next morning (~10:00, can be hours late),
   tapping a reminder, sharing to WhatsApp, the new Home cards, restore of a WhatsApp-saved backup.
2. **Doctor review** of `pregnancy.json`, `milestones.json`, `vaccines_ly.json` and the new `child_danger_signs.json`
   (check every number: 38 °C under 3 months, 35.5 °C, 60 breaths a minute, 8 hours without urine); confirm the 10 doses
   with `"verify": true` against the NCDC schedule (hexavalent vs pentavalent+IPV, pneumococcal schedule, MMR ages, a
   9-month measles dose?, school-entry dose). Obstetrician to consider extra danger signs (one-sided leg swelling, severe
   palm/sole itching, trauma).
3. Emergency number for a "call ambulance" button on both danger-signs pages, once a doctor confirms it
   (`Intent.ACTION_DIAL` needs no permission).
4. Remaining content gaps: newborn safety (safe sleep, no honey < 12 months), feeding, postpartum.
5. "My baby is born" defaults the birth date to today; for a pregnancy long past its due date that gives an absurd
   "weeks at birth" (e.g. 46) unless she picks the real date — consider defaulting to the due date or asking.
6. Ask a Libyan reader: "تطعيمة" vs "التطعيم".
7. Edit a growth measurement (today: delete and re-add only).
8. Final Latin name for Play (tentatively "Atwar"), `applicationId`, privacy-policy page, Play Console account, upload key,
   closed test (14 days, 12+ testers); bump `versionCode` for every build shared with testers.
9. Roadmap (v1.1+): Libyan-dialect audio, kick counter, contraction timer, appointment reminders, Hijri dates, Ramadan
   guidance, starting solids, photo diary, printable doctor report, feeding/sleep log.
10. Housekeeping: delete the merged local branches
    (`git branch -d fix/review-findings feature/atwar-timeline-restore feature/visual-polish fix/review-round-2`).

## 8. History
First chat: found the repo (Mahd) → reviewed code + content → fixed skipped/expired vaccines, preterm growth, restore
safety, reminder catch-up, privacy text, content tests → installed Android Studio, enabled emulator acceleration, tested
flows on the emulator → added first-launch restore, renamed to أطوار, vaccine timeline + progress ring → visual polish
(Home tiles, chart labels, milestone rings, share card, pregnancy bar, new icon) → merged and pushed `main`.
Commits: `44923d3` review fixes · `a3b86dd` polish after emulator testing · `ad18201` rename + restore + timeline + ring ·
`cd4d0d3` visual polish.

Second chat (same day): full review (bugs, security, code quality, UX) → 17 commits on `fix/review-round-2`, one per
item, each checked on the emulator: reminder alarm no longer reset on app open + check on app open; chart vertical zoom;
scrollable first-launch screen; reminder tap opens its profile/tab; sharing without crashes; live notification warning;
no empty-data flash + Home reads once; overdue pregnancy can be saved; backup validation by value; backup privacy note;
lock-screen version; tidy-up; in-context notification permission; dark-mode timeline marks; child danger signs; notes in
CLAUDE.md and this file → fast-forwarded `main` and pushed → built `atwar-test.apk` for the owner's phone.
