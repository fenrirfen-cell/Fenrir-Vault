# Read mode (Tsundoku inside Nuvio)

The Android app combines Nuvio (Watch) and Tsundoku (Read). A Watch / Read switch at the top of
each home screen moves between them. Both stay open, and the app reopens on the side used last.
iOS and desktop builds are unchanged and don't show the switch.

## Getting the code

Tsundoku is a git submodule in `tsundoku/` (branch `nuvio-embed` of the Tsundoku fork):

```bash
git clone --recurse-submodules -b read-mode https://github.com/fenrirfen-cell/NuvioMobile1.0.0
# or, in an existing clone:
git submodule update --init tsundoku
```

## Building

```bash
./gradlew :androidApp:assembleFullDebug
```

Gradle includes `tsundoku/` as a separate build (see `settings.gradle.kts`) and links its
`:embedded` module, a library build of Tsundoku's app, into `androidApp`.

## Where things are

| What | Where |
| --- | --- |
| Application class (extends Tsundoku's `App`, shared image loader) | `androidApp/.../readmode/NuvioReadApplication.kt` |
| Switching, last-mode memory, launch redirect, back-to-exit | `androidApp/.../readmode/ReadModeController.kt` |
| Watch side of the switch | `composeApp/.../readmode/ReadModeSwitch.kt`, shown from `MainTabsDestination.kt` |
| Read side of the switch | `tsundoku/app/.../presentation/components/EmbeddedModeSwitch.kt` |
| Tsundoku library build | `tsundoku/embedded/build.gradle.kts` and its `AndroidManifest.xml` |

## Keep in sync when updating either app

- AGP version: `gradle/libs.versions.toml` (`agp`) must equal Tsundoku's `android-gradle`.
- Gradle wrapper: at least the version Tsundoku's wrapper uses.
- `tsundoku/embedded/build.gradle.kts`: version name/code, the dependency list (mirrors
  `tsundoku/app/build.gradle.kts`) and the quickjs-kt version (must match Nuvio's).
- `tsundoku/embedded/src/main/AndroidManifest.xml` is derived from Tsundoku's app manifest; the
  header comment lists the changes to re-apply when that manifest changes.
