# Field

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
- JDK 11
- Android SDK: minSdk 24, targetSdk 34, compileSdk 36

### Build

```bash
git clone https://github.com/vamshigunda-star/Alearning.git
cd Alearning
./gradlew assembleDebug          # Windows: gradlew assembleDebug (no ./)
```

Other useful commands:

```bash
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests (requires a device/emulator)
./gradlew lint                   # Lint checks
```

On first launch, the app seeds its test catalog and norm reference data from CSV files bundled in `app/src/main/assets/`.

## Project status

This app was built as a focused, one-time contribution to sports education tooling. It isn't under active ongoing maintenance, but issues and pull requests are welcome from anyone who finds it useful.

## License

Field is licensed under the [MIT License](LICENSE).
