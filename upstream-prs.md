# Upstream PR plan: vehicle images, time since update, display precision, layout

This is a handoff from the cloud session that built fork PR aveshev/TPMS-advanced-NE#33, for the
local session that prepares the upstream (`VincentMasselis/TPMS-advanced`) PRs. Everything
below is in the fork's `develop` as of merge commit `01c4ef8`.

Before any of this goes upstream, follow CLAUDE.md "Remotes":
- Build each downport on a branch cut from `upstream/develop`, in a separate worktree.
- The user must install the exact commit on a physical device and sign it off before the PR is
  opened, or before pushing to a branch that already heads an upstream PR.
- **Database versions differ.** The fork is at database version 7 (per-vehicle calibration,
  `64e4d66`), and upstream is lower. An upstream-based build installed over fork data crashes
  with `Can't downgrade database`. Before installing, clear the app data, use another device, or
  back up and restore with `~/tpms-backups/tpms-data.sh`. Ask before overwriting a device's data.

Upstream `develop` was checked at `819773b` (2026-09-21). It already has all five vehicle kinds
(`Vehicle.Kind`), but only `schema_car_top_view.webp` and `schema_motorcycle_top_view.webp`.
Trailer, tadpole and delta show the `BackgroundImageAskHelp` "Help us" placeholder.

## Suggested PRs and order

| # | PR | Depends on |
|---|----|------------|
| 1 | Images for trailer, tadpole, delta, positioned in upstream's current layout | - |
| 2 | Time since last update | - (needs a settings toggle, see below) |
| 3 | Display precision: whole °C, one-decimal psi, bounded high pressures | - |
| 4 | Layout rework: tyres and readouts scale with the image | 1, and whatever of 2 and 3 merged |

The user first proposed three PRs, with the precision change (fork PR #5) folded into the layout
PR. The cloud session recommended splitting it out as PR 3. It changes what every screen shows
(tyre readouts, notification, unlocated-sensor list), stands alone, and is easy to accept or
reject by itself. The user had not decided when this was written.

PR 4 should be opened last, or as a draft stating its dependencies. Fork PRs can't be stacked
upstream, so until 1-3 merge, PR 4 would show their changes too. Its widest-readout samples must
match what actually merged (see PR 4).

---

## PR 1: Images

### What goes in
- The three final images, byte-identical to the fork's `develop`:
  - `feature/main/src/main/res/drawable-nodpi/schema_single_axle_trailer_top_view.webp`
  - `feature/main/src/main/res/drawable-nodpi/schema_tadpole_three_wheeler_top_view.webp`
  - `feature/main/src/main/res/drawable-nodpi/schema_delta_three_wheeler_top_view.webp`

  Take them with `git checkout 01c4ef8 -- <paths>`. Don't cherry-pick the image commits: there
  are several intermediate versions, including two discarded tadpoles (`fcf8665`, `36bddac`).
- `Vehicle.kt`: an `Image` for each of the three kinds, following upstream's car and motorcycle
  pattern:
  - `aspectRatio(208f / 462f)` centred;
  - `height = Dimension.percent(...)`;
  - a `tyreBox` whose corners/edges anchor the fixed `30.dp x 100.dp` tyres.
- Remove the `BackgroundImageAskHelp` calls, and the composable itself. It becomes dead code and
  Detekt will likely flag it. Say so in the PR text: it's the maintainer's own call for community
  images.
- No other code changes.

### Tyre positions in upstream's scheme
Upstream tyres are fixed dp boxes. The image is a percentage of the screen height, so tyres only
line up with the drawn wheels on typical phone proportions, the same as the car and motorcycle
today. Tune the `tyreBox` per kind on the device.

Useful references:
- **The first fork version used this scheme.** Commits `72ef265` (images added), `6554783`/`412e3a7`
  (delta fenders), `ebcd734` and `09f3ea8` (tyre alignment) positioned tyres with upstream-style
  boxes, before `0d005c5` switched to image-relative placement. Their `Vehicle.kt` diffs are the
  closest starting point, but they used earlier image versions. The tadpole in particular was
  replaced later, so recheck its numbers.
- **Where the drawn wheels are**, from the final fork layout (fractions of the 625x1386 image;
  "span" = horizontal distance between the two tyre centres as a fraction of the image width):

  | Kind | Wheels |
  |------|--------|
  | Trailer | track span `.86`, axle at y `.686` |
  | Tadpole | front track span `.68` at y `.1005`; rear wheel at y `.845`, centred |
  | Delta | front wheel at y `.0865`, centred; rear track span `.713` at y `.835` |
  | Car (for comparison) | track span `.74`, axles at y `.217` / `.783` |
  | Motorcycle (for comparison) | front y `.0865`, rear y `.805` |

- **Tadpole rear readout:** the image has a one-sided exhaust on the right, reaching about 75% of
  the image width. Place the rear readout past it, not next to the rear tyre, or it overlaps.
- **Delta:** the rear wheels are drawn fully covered by tapered fenders, so the small fixed tyre
  boxes sit inside the fenders instead of showing a mismatched tyre.

### Image facts (for the PR text)
- 625x1386, lossless webp, black RGB with alpha, so only alpha matters: the app tints with
  `ColorFilter.tint(onBackground)`. Same size, aspect and format as the car and motorcycle.
- Lines are about 1.4 px, uniform width, anti-aliased, measured against the motorcycle's 1.40.
- No metadata: the webp info keys are only `background`/`loop`. There is no EXIF, XMP or C2PA,
  and no Google/Gemini strings.
- Upstream's issue template `vehicle-background-image-proposal.md` asks for "schematic top view,
  not too much details, transparency, ~1.5 px lines, more than 500x1000". The images meet all of
  it.

### Licensing notes
- The project is Apache-2.0, upstream and fork. A contribution only needs the contributor to have
  the right to submit it.
- **Origin:** the user generated the images with Gemini (nano-banana) and edited them by hand in
  GIMP. The cloud session then processed them by script:
  - re-stroked to a uniform line width;
  - replaced the delta's rear wheels with tapered fenders;
  - made the tadpole symmetric and redrew its tail tip, restoring the original exhaust
    afterwards.
- **Gemini terms:** Google doesn't claim ownership of generated output, so the user can use and
  contribute it.
- **Copyright:** purely AI-generated parts probably aren't copyrightable (US). That doesn't block
  contributing them; it only means nobody could stop others copying those parts. The user's hand
  edits are theirs to license under Apache-2.0.
- **Brands:** no logos, badges or text. The tadpole is scooter-style, closest to a Yamaha Tricity
  or Piaggio MP3, without trade dress. Low risk.
- **Not verified:** upstream's own policy on AI-generated contributions. Check upstream
  `CONTRIBUTING`, the README and recent issues/PRs before opening. Either way, disclose the origin
  in the PR text.

### Related upstream issues
All were opened through the "vehicle background image proposal" template, with no image attached,
no maintainer reply and no linked PR (checked 2026-09-26):
- #459 and #458 "Can am spyder F3" (open, 2026-09-01), and #447 (closed copy of the same)
- #395 "Can am" (open)
- #397 "Piaggio Mp3 2011" (open)

These are tadpole three-wheelers. Nothing asks for a trailer or delta image. #4
"Trailer/Caravan" (closed) added the trailer kind itself.

In the fork PR these were written as code (`VincentMasselis/TPMS-advanced#459`) to avoid
cross-reference events. In an upstream PR, plain `#459` links are fine and useful. Use "Related
to", not "Closes", and let the maintainer close them.

### Draft PR text

> **Add background images for the single-axle trailer and both three-wheelers**
>
> The trailer, tadpole (two wheels at the front) and delta (two at the rear) three-wheelers
> showed the "This screen needs a background image" placeholder. This adds a schematic top view
> for each, in the same style as the car and motorcycle images, and positions the tyre boxes on
> the drawn wheels.
>
> - 625x1386 transparent lossless webp, ~1.4 px lines, tinted like the existing images, no
>   metadata; follows the vehicle background image proposal template.
> - The tadpole is scooter-style (Tricity/MP3-like) and should cover the Can-Am Spyder and MP3
>   requests. The delta's rear wheels sit under fenders so the tyre boxes don't clash with a
>   drawn tyre.
> - Removes `BackgroundImageAskHelp`, which no vehicle kind uses any more. Happy to keep it if
>   you'd like it around for future kinds.
>
> **Where the images come from:** I generated them with Gemini, edited them by hand in GIMP, then
> cleaned up the line work by script. There are no logos or brand details. I'm contributing them
> under the project's Apache-2.0 licence.
>
> Related to #459, #458, #397, #395.
>
> Tested on a Galaxy S9/S9+ (portrait and landscape): <screenshots>

---

## PR 2: Time since last update

### What goes in
| Fork commit | What |
|---|---|
| `d48d7b3` | The feature: tiered label under each readout (`<1 min`, `N min` to 119, `N hours` to 47, `N days`), ticking on its own at each tier boundary and reset by a new packet. Adds a "Show time since update" toggle, on by default. (Fork PR #8.) |
| `203e12d` | Re-recorded `TyreStatTest` goldens (#8) |
| `c967475` | Fixes `VehicleTemplateTest` for the new `State` args (#8) |
| `dca3f41` | "hour" → "hours" (fork PR #9) |
| part of `ab98a19` | Only the `TyreStat.kt` hunk: cap at `"99+ days"` (`MAX_DAYS = 99`). The `Vehicle.kt` hunk (calibration `*`) is fork-only. |

### Catch: the settings toggle has nowhere to go upstream
`d48d7b3` adds the toggle to `TyreDisplaySettings` and `AppPreferences.showTimeSinceUpdate`.
`TyreDisplaySettings`, the "App settings" screen and the `showTimestamp`/`showSensorId` toggles
all came from the fork-only commit `7e757d4`, "Add global App settings with tyre display
toggles". Upstream has none of it: its `AppPreferences` only holds version info, and there's
only `VehicleSettings.kt`.

Options:
1. **Minimal:** always show the label, with no toggle.
2. **Small:** a single `AppPreferences` flag with a checkbox somewhere existing.
3. **Full:** bring the relevant part of `7e757d4` (App settings screen with a Display section),
   without the timestamp toggle (`e44b626` later removed it) and, unless wanted, without sensor
   ID. This is the biggest change.

Ask the user. Don't port the fork's later settings redesign (`12039e8`).

Also related: `3f1e684` (per-line alert colour/blink) touched the time-since line's colour. It's a
separate behaviour change; leave it out unless the user wants it.

### Draft PR text

> **Show the time since each tyre's last sensor update**
>
> Adds a line under each tyre's pressure and temperature: `<1 min`, then minutes up to 119, hours
> up to 47, then days, capped at `99+ days`. It updates by itself at each step, and a new packet
> resets it. This makes a stale reading (sensor asleep, out of range, battery dying) obvious
> without comparing timestamps. <toggle: describe the chosen option>

---

## PR 3: Display precision

### What goes in
| Fork commit | What |
|---|---|
| `9db19d1` (fork PR #5) | °C without a decimal (sensors report whole degrees); psi with one decimal, since 1 kPa steps don't land on whole psi; kPa unchanged. Re-recorded goldens. |
| `78b06c1` | Keeps high pressures as wide as normal ones: one decimal less from 10 bar (`10.3 bar`) and 100 psi (`150 psi`); `PressureTest` covers the thresholds. |

`78b06c1` only makes sense after `9db19d1`: upstream psi is whole already (`%.0f psi`).

This affects `Pressure.string`/`Temperature.string` everywhere: tyre readouts, the background
notification (`ServiceNotifier`) and the unlocated-sensor list. Android Auto's compact format is
unchanged.

### Draft PR text

> **Tidy pressure and temperature precision**
>
> - °C drops the always-zero decimal (`30°C`, not `30.0°C`): the sensors report whole degrees.
> - psi gains one decimal (`32.6 psi`): whole psi (~6.9 kPa) is coarser than the kPa and bar
>   displays and hides real changes.
> - At 10 bar and above, and 100 psi and above, one decimal is dropped (`10.3 bar`, `150 psi`), so
>   high-pressure tyres (bicycles, RVs) keep the same label width.

---

## PR 4: Layout rework

### What goes in
The `Vehicle.kt` part of fork PR #33: `0d005c5` onwards, excluding the image-only commits.
Porting the final `Vehicle.kt` from `01c4ef8` and adapting it is probably easier than cherry-
picking 20 commits.
- **Tyres follow the image:** they're placed at image fractions (`imageGuideline`, `ImageSpan`)
  and sized at `TYRE_HEIGHT = .165f` of the image height, 15:40.
- **Image height is computed** (fraction of the container):
  - fitted to the width left after the widest readout (`TextMeasurer`), an 8dp gap per readout
    side in use, and a 4dp margin each side;
  - capped at `MAX_IMAGE_AREA = .5` of the screen area;
  - clamped to `.45..0.9`.
- **Readouts:**
  - vertically centred on their tyre (`verticallyCenteredOn`), but kept within the image's top
    and bottom;
  - horizontally next to the outline;
  - the side each readout goes on comes from `Kind.locations` (Axle → right).
  - Image and readouts are centred together (this shifts the motorcycle left).
- **Widest-readout samples** (`PressureUnit.widestReadout`, `WIDEST_DETAILS`) must match upstream
  after PRs 2-3:
  - Drop the calibration `*` (fork-only): `1034 kpa`, `8.88 bar`, `88.8 psi` (whole psi → e.g.
    `150 psi`, if PR 3 wasn't taken).
  - Keep `88 hours`/`99+ days` only if PR 2 merged.
  - Temperature `188°C` is only right with whole °C (PR 3); otherwise use `188.8°C`.
- **Unit lookup:** the fork reads the pressure unit through `VehicleSettingsViewModel`
  (`component.viewModel(component.key()) { it.VehicleSettingsViewModel() }.pressureUnit`).
  Upstream has the same `pressureUnit` and `VehicleSettingsViewModel()` factory (checked at
  `819773b`).

### Draft PR text

> **Scale tyres and readouts with the vehicle image**
>
> Tyre boxes were fixed dp while the image was a percentage of the screen height, so they drifted
> off the drawn wheels on other screen shapes, badly in landscape. Now:
> - tyres are placed and sized as fractions of the image, so they stay on the wheels at any size
>   and orientation;
> - the image is as big as possible while leaving room for the widest possible readout (measured,
>   so it adapts to font scale), up to half the screen area;
> - readouts are centred on their tyre and kept within the image height;
> - the image and readouts are centred together (the motorcycle's readouts are all on one side).
>
> Screenshots: <phone portrait, phone landscape, per kind>

---

## Image scripts

`upstream-notes/image-scripts/` holds the cloud session's image tooling, kept because the
session's scratchpad doesn't survive:
- `restroke.py`: `restroke_smooth(alpha4x, width)` centerline re-stroke (skeletonize at 4x, prune
  spurs, smooth, redraw at a uniform width, downsample). Used for the trailer (width 1.35, from
  the user's `1.webp`) and the tadpole (1.27). The trailer's one-off driver wasn't saved.
- `fenders.py`: the delta's tapered rear fenders over the user's `3.webp`.
- `t7_convert_exh.py`: the final tadpole from the user's `7.webp`:
  - mirrored about x=230.5, with the original exhaust region kept;
  - re-stroked;
  - tail tip redrawn;
  - upper exhaust mounting hole redrawn.

  It reproduces the committed file byte for byte:
  `python3 t7_convert_exh.py <outdir> 1.27`.
- `mkscratch.py`: builds a scratch Paparazzi copy of `Vehicle.kt` (fake tyres/readouts) to render
  layouts. Never commit its output.

The source images (`1.webp`, `3.webp`, `7.webp`) are the user's, not in the repo, and the
scripts have absolute paths to them. Python deps: Pillow, numpy, scipy, scikit-image, cairosvg.
