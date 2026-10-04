# أطوار · Atwar

An Arabic-first, offline Android app that helps Libyan and Arab mothers follow their child
from pregnancy to age 4: pregnancy week by week, the Libyan vaccine schedule with reminders,
WHO growth charts, and developmental milestones.

> **Status:** MVP in progress (version 0.1.0). The medical content is a **draft** and must be
> reviewed by a paediatrician and an obstetrician before any public release.

## Principles

- **Works fully offline.** The app does not even request the INTERNET permission.
- **Native Android, few dependencies.** Kotlin + Jetpack Compose. Only official AndroidX
  libraries; the database is Android's built-in SQLite and reminders use the built-in AlarmManager.
- **Content is data, not code.** All medical text lives in `app/src/main/assets/content/`.
- **Arabic, right-to-left, always** — even on phones set to another language.

## Open the project on a new computer

1. Install **Android Studio** (latest stable) from <https://developer.android.com/studio>.
2. Clone this repository **outside OneDrive** (OneDrive syncing build folders causes errors):
   ```
   git clone https://github.com/<your-account>/mahd.git C:\dev\mahd
   ```
3. In Android Studio: **File → Open** → choose the `mahd` folder. Let Gradle sync finish
   (the first sync downloads a lot; it can take 10–20 minutes).
4. Plug in an Android phone with **USB debugging** on (or create an emulator in Device Manager),
   then press **Run ▶**.

Android Studio creates `local.properties` (the path to your Android SDK) automatically;
it is not stored in Git.

## Project map

```
app/src/main/
├── assets/content/          ← medical content (review these with doctors)
│   ├── pregnancy.json       weeks 1–42 + danger signs
│   ├── milestones.json      2 months → 4 years (adapted from CDC 2022)
│   ├── vaccines_ly.json     Libyan schedule — DRAFT, verify with NCDC
│   └── growth_who.csv       WHO growth standards, 0–60 months
├── java/ly/mahd/app/
│   ├── data/                SQLite database, repository, backup file, settings
│   ├── content/             reads the content files; WHO growth maths
│   ├── logic/               pregnancy dating, corrected age, growth bands, vaccine planner
│   ├── reminders/           daily check → vaccine and "new week" notifications
│   ├── ui/                  Compose screens, navigation, theme
│   └── util/                Arabic dates/plurals, locale
└── res/                     strings (Arabic), icons, theme
app/src/test/                unit tests for the logic (run: gradlew testDebugUnitTest)
```

## Before a public release

- [ ] Paediatrician reviews `milestones.json` and `vaccines_ly.json`
- [ ] Obstetrician reviews `pregnancy.json` (weeks + danger signs)
- [ ] Confirm the full vaccine schedule with the National Centre for Disease Control (NCDC)
- [ ] Set `"reviewed": true` in each file once signed off (removes the draft notices)
- [ ] Final app name, icon and `applicationId`
- [ ] Play data-safety form: mention Android Auto Backup (database is backed up to the user's own Google account)
- [ ] Privacy policy page, Play Console account, 14-day closed test with 12+ testers
