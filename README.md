# Field

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.23208441.svg)](https://doi.org/10.5281/zenodo.23208441)

**Field** is an offline-first fitness testing and performance tracking app for coaches, PE teachers, and fitness professionals. It helps a coach run fitness testing events for their athlete groups, record results on the spot (including a built-in stopwatch for timed tests), and track performance against age/sex-based percentile norms over time.

## Features

- **Roster management** — organize athletes into groups (squads, classes, cycles)
- **Test library** — a catalog of fitness tests (strength, endurance, speed, agility, flexibility, balance) with defined units and validation ranges
- **Testing events** — create an event, pick tests and a group, and run through athletes with a live testing grid
- **Built-in stopwatch** — individual and group timing modes with trial support, for timed tests
- **Percentile-based reporting** — results are classified against norm references into three performance zones: green (Superior, ≥ 80th percentile), yellow (Healthy/Average, 40–79th), red (Needs Improvement, < 40th). Grey means no norm matched, which is the absence of data rather than a fourth zone.
- **Age-bracketed norms** — Childhood 5–12, Adolescence 13–19, Adults 20–40, Adults 41–62, Older Adults 63–115, following established developmental and fitness-assessment frameworks
- **Leaderboards & analytics** — event and all-time rankings, group trends, and remediation lists for athletes who need attention
- **Longitudinal athlete profiles** — individual dashboards with historical charts and progress over time
- **Fully offline** — all data lives in a local Room database; no network connection required for core functionality
- **Google Drive backup** — optional sign-in to back up and restore data via Google Drive

## Install

Field isn't on the Play Store. Install it straight from GitHub:

1. On your Android phone or tablet (Android 8.0 or newer), open the
   [latest release](https://github.com/vamshigunda-star/Field/releases/latest) and tap
   **Field-x.y.z.apk** to download it.
2. Open the downloaded file. If Android says installs from this source aren't allowed, tap
   **Settings**, turn on **Allow from this source**, and go back.
3. Tap **Install**. If Google Play Protect warns about an unrecognised app, tap
   **More details → Install anyway**. The warning appears because the app isn't from the Play
   Store.

**Updating:** install the newer APK the same way, over the old one. Your data is kept. Updates
aren't automatic, so check the releases page now and then. Back up to Google Drive (Settings → Data Backup &
Restore) before changing phones.

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose, Material 3 (including adaptive layouts for tablets)
- **Architecture:** Clean Architecture — `domain/` (pure Kotlin, no Android/Room dependencies) → `data/` (Room persistence, repositories) ← `ui/` (Compose screens, Hilt-injected ViewModels)
- **Persistence:** Room (SQLite)
- **DI:** Hilt
- **Async:** Kotlin Coroutines & Flow

See [DEVELOPMENT_CONTEXT.md](DEVELOPMENT_CONTEXT.md) for the full architecture reference.

## Getting started

### Requirements

- Android Studio (recent stable release)
- JDK 17
- Android SDK: minSdk 26, targetSdk 36, compileSdk 37
- **Python 3 on PATH.** The build regenerates the prepackaged database
  (`app/src/main/assets/database/alearning.db`, not committed) from the CSVs via
  `tools/build_prepackaged_db.py`; without Python the build fails at `preBuild`.

### Build

```bash
git clone https://github.com/vamshigunda-star/Field.git
cd Field
./gradlew assembleDebug          # Windows: gradlew assembleDebug (no ./)
```

Other useful commands:

```bash
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests (requires a device/emulator)
./gradlew lint                   # Lint checks
```

### Release build

Release builds are minified with R8. Debug builds are not, so anything that only breaks under
R8 (most importantly the Drive backup JSON, see `app/proguard-rules.pro`) is invisible in
debug. Always test a release APK before publishing.

Signing reads an **untracked** `keystore.properties` at the repo root:

```properties
storeFile=C:/path/outside/the/repo/field-upload.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without it, `assembleRelease` still succeeds but produces an unsigned APK. Never commit the
keystore or this file (both are in `.gitignore`). Keep the R8 `mapping.txt` from
`app/build/outputs/mapping/release/` for every published version, so crash stack traces can be
decoded.

On first launch, the app seeds its test catalog and norm reference data from CSV files bundled in `app/src/main/assets/`.

## Project status

This app was built as a focused, one-time contribution to sports education tooling. It isn't under active ongoing maintenance, but issues and pull requests are welcome from anyone who finds it useful.

## Norm data sources

The normative reference data in `app/src/main/assets/norms.csv` is drawn mostly from:

- American College of Sports Medicine. (2017). *ACSM's Guidelines for Exercise Testing and Prescription* (10th ed.). Wolters Kluwer / Lippincott Williams & Wilkins.
- Fukuda, D. H. (2019). *Assessments for Sport and Athletic Performance*. Human Kinetics.
- Kaminsky, L. A. (Ed.). (2010). *ACSM's Health-Related Physical Fitness Assessment Manual* (3rd ed.). Lippincott Williams & Wilkins.

The source standards use five classifications; Field consolidates them into three performance zones.

## How to cite

Field is archived on Zenodo. To cite it:

> Gunda, V. (2026). *Field: an offline-first fitness testing and performance tracking app for coaches* [Computer software]. Zenodo. https://doi.org/10.5281/zenodo.23208441

That DOI always resolves to the latest version; each release also gets its own version DOI on
Zenodo (1.0.0 is [10.5281/zenodo.23208442](https://doi.org/10.5281/zenodo.23208442)). Citation metadata is also in
[CITATION.cff](CITATION.cff), which GitHub's "Cite this repository" button reads.

## Privacy

Field collects nothing: all data stays on the device, and the optional Drive backup goes only to the user's own Drive app-data folder. See the [privacy policy](https://vamshigunda-star.github.io/Field/privacy).

## License

Field is licensed under the [MIT License](LICENSE).
