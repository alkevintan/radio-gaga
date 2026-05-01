# AGENTS.md

Operational guide for AI agents working on this repo.

## Project

Radio Gaga — Android internet radio player. Sideload-only (no Play Store). OTA updates ship via GitHub Releases on `alkevintan/radio-gaga`.

- **minSdk:** 21 (Lollipop) · **targetSdk/compileSdk:** 34
- **Lang:** Kotlin · **Build:** Gradle (Groovy DSL) · **JVM:** 17
- **Stack:** ExoPlayer, Room, OkHttp, Material Components, Coroutines, Picasso, ZXing
- **Package:** `com.radio.player`

## Build & run

```bash
# JAVA_HOME for shell sessions on this machine
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew :app:assembleDebug   # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug    # install onto connected device/emulator
```

There is **no release build type signing** configured. Release APKs ship as debug-signed (see "Releases" below). Do not enable `signingConfigs.release` without first coordinating keystore handover — current OTA continuity depends on the developer's `~/.android/debug.keystore`.

## Source layout

```
app/src/main/
├── AndroidManifest.xml
├── assets/cacerts.pem                    # Mozilla CA bundle for legacy TLS
├── java/com/radio/player/
│   ├── MainActivity.kt                   # station list, playback bar, auto-update check
│   ├── RadioApplication.kt
│   ├── data/                             # Room entities, DAOs, repos
│   ├── service/
│   │   ├── RadioPlaybackService.kt       # ExoPlayer foreground service
│   │   └── AlarmReceiver.kt              # scheduled-wake alarms
│   ├── ui/                               # activities, dialogs, adapters
│   │   ├── SettingsActivity.kt
│   │   ├── UpdateDialog.kt               # OTA prompt
│   │   └── …
│   ├── util/
│   │   ├── HttpClientFactory.kt          # OkHttp w/ legacy CA trust on pre-N
│   │   ├── SettingsManager.kt            # SharedPreferences accessors
│   │   ├── UpdateChecker.kt              # GitHub Releases poller
│   │   └── UpdateInstaller.kt            # DownloadManager + FileProvider install
│   └── viewmodel/
└── res/
    ├── layout/, values/, xml/file_paths.xml
```

## OTA update flow

1. App polls `https://api.github.com/repos/alkevintan/radio-gaga/releases/latest` (auto on launch, throttled to 24h; or manual via Settings).
2. Tag must look like `vX.Y.Z`. `tag_name` minus leading `v` becomes `versionName` for comparison against installed `PackageInfo.versionName`.
3. First `.apk` asset on the release is downloaded via `DownloadManager` to app-private external storage (`Download/updates/RadioGaga-update.apk`).
4. APK opened via `FileProvider` (authority `${applicationId}.updates`, paths in `res/xml/file_paths.xml`) with `ACTION_VIEW` → system installer.
5. Drafts and prereleases are skipped by `UpdateChecker.fetchLatest`.

**Signature continuity is critical.** Android refuses upgrades when keystore differs. All releases must be built on the same machine that produced v1.0.0 (the developer's `~/.android/debug.keystore`).

The owner/repo is read from `res/values/strings.xml` (`update_github_owner`, `update_github_repo`). Empty values disable update checks gracefully.

## Releases

Use the helper script — do not run the steps by hand:

```bash
scripts/make-release.sh 1.0.1
scripts/make-release.sh 1.0.1 --notes "Fixed crash on alarm trigger"
scripts/make-release.sh 1.0.1 --notes-file CHANGES.md
scripts/make-release.sh 1.0.1 --prerelease       # tagged but skipped by UpdateChecker
```

What it does:
1. Validates: on `main`, clean tree, in sync with origin, tag/release don't exist, semver format
2. Bumps `versionName` to the given value, increments `versionCode` by 1 in `app/build.gradle`
3. `./gradlew :app:assembleDebug`
4. Commits the bump as `chore: release vX.Y.Z`, creates annotated tag, pushes both
5. `gh release create` with the renamed APK (`RadioGaga-X.Y.Z.apk`) attached
6. If `--notes` not given, generates from `git log <prev-tag>..HEAD`

Aborts on any precondition failure — safe to re-run after fixing.

## Conventions

- Conventional commits: `feat:`, `fix:`, `chore:`, `refactor:`, `docs:`. Scope optional (`fix(ui): …`).
- Themes are enum-driven via `SettingsManager.Theme`. Adding one means: enum entry, `themes.xml` style, `themes_splash.xml` style, branch in `MainActivity.applyTheme()` and `SettingsActivity`.
- Persisted user settings go through `SettingsManager` only — no scattered `getSharedPreferences` calls.
- Network calls must use `HttpClientFactory.get(context)` so legacy-Android CA trust is consistent.
- New activities go under `ui/`, new background work under `service/`, helpers under `util/`.

## Gotchas

- `RadioPlaybackService` uses the deprecated standalone `com.google.android.exoplayer:exoplayer:*` artifact (see `build.gradle`). Many Kotlin warnings are baseline noise — filter for new ones.
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` is required on Android 14+ for the playback service to start; manifest is already set.
- ProGuard/R8 is configured for release (`minifyEnabled true`) but release builds are not produced today. If you turn release builds on, audit `proguard-rules.pro` for Gson/OkHttp/ExoPlayer keep rules first.
- Debug APK is ~11 MB; not minified. Acceptable for sideload distribution.
