# Direction — أطوار (Atwar)

## Thesis
Material 3 Expressive, with its personality taken from the app's own name: أطوار = stages, drawn as the phases of the
moon (the launcher icon). Progress is a moon filling from crescent to full; everything else stays calm, warm and
typographic, so a tired mother sees one clear number and one clear next step per screen.

## Who and where
Libyan mothers, Arabic only, on mid-range Android phones, often one-handed, often at night while feeding, sometimes with
a grandmother reading over the shoulder. Large type, high contrast, nothing that needs explaining.

## Palette (Theme.kt is the source of truth)
| Token | Light | Dark | Role |
|---|---|---|---|
| background | #F6F1EE | #161218 | Warm "moonlit paper" by day, night sky by night. Never pure white or black. |
| group row | #FFFDFB | #241F26 | Grouped list rows (`LocalSurfaces.groupRow`): lighter than the page, Android 16 style. |
| primary (rose) | #A23B5B | #FFB1C3 | Brand. Buttons, selected states, vaccine moons. The only loud colour. |
| secondary (teal) | #2F6F6A | #9BD3CC | Growth only: chart, growth moon/sparkline. |
| tertiary (gold) | #7C5800 | #F2BF48 | Milestones only. |
| error | #BA1A1A | #FFB4AB | Late doses and danger signs only. |
| moon shadow | #E6DCDC | #3E363E | Unlit part of every moon (`LocalSurfaces`). |
| inset | #F1EBE8 | #3A333B | Controls sitting on a group row (unselected answer buttons). |

Status colours (good / attention) stay in `StatusColors` and only ever describe a health state.

## Type
One family: **Readex Pro** (OFL, variable, `res/font/readex_pro.ttf`), chosen because its Arabic is drawn for screens,
its 0–9 digits are open and clear, and its modern-Kufi construction matches Expressive's geometric shapes.
- Hero numbers (age, week, "13 of 19"): display styles, SemiBold.
- Titles: SemiBold/Medium. Body: Regular, 17/28 and 15/24.
- **Letter-spacing is 0 everywhere** (spacing breaks Arabic joins). Arabic line-height ≈ 1.6× size.

## Space & form
- Page gutter 16dp. Space between groups 20–24dp; between rows inside a group 2dp.
- **Grouped lists** (`ListGroup`): outer corners 24dp, inner corners 6dp, 2dp gaps. This is the default container.
- Featured surface (danger signs, reminders prompt): 28dp, tinted container, no border.
- Buttons are full pills that **squash to 12dp corners when pressed** (`ExpressiveButton`).
- Choice rows are **connected button groups** (`ConnectedChoice`): outer pill ends, small inner corners; the selected
  segment becomes a full pill.
- No elevation shadows, no outlines on cards. Separation comes from tone, not lines.

## Motion
Material 3 Expressive's springs (slight overshoot), as `AppMotion` in Theme.kt. material3 1.4 keeps
`MaterialExpressiveTheme`/`MotionScheme` internal, so the values are copied; when 1.5 is stable, switch to the official API.
- Moons fill with the slow spatial spring when they appear or change (the signature moment).
- Pressed buttons squash; selected choice segments round out.
- Tabs crossfade. Nothing loops, nothing moves on its own — with one exception (owner's request, 2026-10-16): an
  exercise with two pose pictures shows them in one frame, taking turns (hold ~1.1 s, fade 0.45 s) like a tiny
  flip-book. It stays still when the phone's animations are off; a tap shows the next pose.

## Signature
**`MoonPhase`**: a disc whose lit part grows from the start (right in Arabic, also how a waxing moon looks) as a real
crescent → half → gibbous → full. Used for vaccines given, milestones seen per area, pregnancy weeks, the pregnancy
avatar, and empty states (a new moon = nothing yet).

## Banned in this project
- Emoji as icons, bullets, badges or decoration (in UI chrome). Content that *is* an emoji (fruit size) may stay inline.
- Coloured bars on the edge of cards. Outlined "warning" cards with red borders.
- A row of three identical stat tiles. Icons inside tinted circles/squares repeated down a list.
- Every block being the same card. Pink tint on every surface.
- `letterSpacing` on any text style. One-off font sizes outside the theme's type scale.
- Generic progress rings (use `MoonPhase`). Default Roboto/system font.
- Greeting fluff with emoji. Marketing Arabic (رحلتك نحو التميز، بكل سهولة ويسر …).
