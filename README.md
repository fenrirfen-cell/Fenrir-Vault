<div align="center">

  <img src="assets/readme/fenrir-icon.png" alt="Fenrir Read" width="120" />

  <h1>Fenrir Read</h1>

  <p>
    One Android app to <b>watch</b> and to <b>read</b>.
    <br />
    Shows, movies and anime on one side. Light novels and manga on the other. One tap to switch between them.
  </p>

  [Download the latest APK](https://github.com/fenrirfen-cell/Fenrir_Read/releases/latest) · [All releases](https://github.com/fenrirfen-cell/Fenrir_Read/releases) · [Report a problem](https://github.com/fenrirfen-cell/Fenrir_Read/issues)

</div>

## What it is

Fenrir Read combines two open-source apps into one:

- **Watch**, built on [Nuvio Mobile](https://github.com/NuvioMedia/NuvioMobile): a library with artwork, ratings, subtitles and your place saved.
- **Read**, built on [Tsundoku](https://github.com/tsundoku-otaku/tsundoku): a reader for light novels and manga with a library, updates and history.

The **FENRIR READ ▾** switch at the top moves between the two. The app reopens on the side you used last.

The app doesn't come with any content. You add your own sources, such as Stremio add-ons, CloudStream and Aniyomi plugins, or Tsundoku extensions.

## Features

**Both sides**
- One **Sources** hub to add, remove and pin sources, with a box where you paste repo links.
- **Check sources** finds broken sources and turns them off. You can undo it.
- If a plugin is known to be broken, you get a warning before installing it.
- A **✨ For you** tab with suggestions based on what you watch or read.
- Glass bottom island and bars that hide while you scroll.
- Built-in updates: pull down to refresh and a small pill shows when a new version is ready.

**Watch**
- Home rows from your Stremio, CloudStream and Aniyomi sources. You can hide any row and show it again later.
- **More like this** on every title, using TMDB, Trakt or Simkl.

**Read**
- A library with categories you swipe between and a **Continue reading** card.
- **Updates** has a switch for New chapters and Calendar.
- **History** grouped by day, with reading stats.
- **Browse** has Sources, Extensions and Migrate, with a Novel / Manga toggle.
- **You may like** on every book page.

## Install

1. Download the newest `.apk` from [Releases](https://github.com/fenrirfen-cell/Fenrir_Read/releases/latest).
2. Open it on your phone and allow installs from your browser or file manager if Android asks.
3. After that, the app updates itself. When a new version is out, tap **Update** on the pill.

Android only. iOS and desktop builds of Nuvio are not part of this project.

### Optional plugin repo

To add the J-Novel Club plugin (light novels only), paste this link into **Read → Browse → Extensions → +**:

```
https://raw.githubusercontent.com/fenrirfen-cell/Fenrir_Read/read-mode/plugins/plugins.min.json
```

## Build from source

You need Android Studio, the Android SDK and JDK 17 or newer.

```bash
git clone --recurse-submodules -b read-mode https://github.com/fenrirfen-cell/Fenrir_Read
cd Fenrir_Read
./gradlew :androidApp:assembleFullDebug
```

The Read side lives in the `tsundoku/` submodule ([fenrirfen-cell/tsundoku1.0.0](https://github.com/fenrirfen-cell/tsundoku1.0.0), branch `nuvio-embed`). [docs/READ_MODE.md](docs/READ_MODE.md) explains how the two apps fit together.

### Releases

The **Fenrir Read build** GitHub Action builds a signed APK and publishes it as a release. Once a day it checks for a new official Nuvio or Tsundoku release. When there is one, it merges it in and publishes a new build. If a merge clashes, it opens an issue instead. You can also start it by hand from the Actions tab.

## Credits and licenses

Fenrir Read stands on the work of two projects. Thank you to their authors:

- **Nuvio Mobile** by NuvioMedia, licensed under the [GNU GPL v3.0](./LICENSE).
- **Tsundoku** by tsundoku-otaku, licensed under the [Apache License 2.0](https://github.com/fenrirfen-cell/tsundoku1.0.0/blob/nuvio-embed/LICENSE). It is itself based on Mihon and Tachiyomi.

Because it includes Nuvio, Fenrir Read as a whole is released under the **GNU GPL v3.0**. Fenrir Read is not affiliated with or endorsed by Nuvio, Tsundoku or any source or plugin author.

## Disclaimer

Fenrir Read doesn't host, store or share any media. It only shows what the sources you add provide. You're responsible for making sure the sources you use are legal where you live.
