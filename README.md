<div align="center">

  <img src="assets/readme/fenrir-icon.png" alt="MugiVerse" width="120" />

  <h1>MugiVerse</h1>

  <p>
    One Android app to <b>watch</b> and to <b>read</b>.
    <br />
    Shows, movies and anime on one side. Light novels and manga on the other. One tap to switch between them.
  </p>

  [Download the latest APK](https://github.com/fenrirfen-cell/Mugiverse/releases/latest) · [All releases](https://github.com/fenrirfen-cell/Mugiverse/releases) · [Report a problem](https://github.com/fenrirfen-cell/Mugiverse/issues)

</div>

## What it is

MugiVerse combines two open-source apps into one:

- **Watch**, built on [Nuvio Mobile](https://github.com/NuvioMedia/NuvioMobile): a library with artwork, ratings, subtitles and your place saved.
- **Read**, built on [Tsundoku](https://github.com/tsundoku-otaku/tsundoku): a reader for light novels and manga with a library, updates and history.

The **Watch / Read** switch at the top moves between the two. The app reopens on the side you used last.

The app doesn't come with any content. You add your own sources, such as Stremio add-ons, CloudStream and Aniyomi plugins, or Tsundoku extensions.

## Features

**The app**
- One **Sources** hub for both sides: add, remove and pin sources, and paste repo links.
- New repos are tested before you add them, and each extension is checked on its own. Broken ones get a health dot and **Fix**, **Uninstall** or **Turn on**.
- **Update all** updates extensions and plugins one at a time, so nothing freezes.
- Updates are built in: a red dot on the **Watch / Read** switch, with **Update** at the top of its menu.
- An opening animation of the wolf breaking its chains and opening the vault, with an optional howl. The howl stays quiet on silent or vibrate and can be turned off in **Settings → Look**.
- Glass bottom island and a top bar that slides away with your finger as you scroll.

**Watch**
- Home rows from your Stremio, CloudStream and Aniyomi sources. You can hide any row and show it again later.
- **✨ For you**: suggestions based on everything you watched, plus your list. Swipe it down to close.
- Ratings on posters. Tap one to see why it scored that way, with the most-liked and lowest-rated reviews and a spoiler switch.
- **More like this** on every title, using TMDB, Trakt or Simkl.

**Read: library and discovery**
- A library with categories you swipe between, a **Continue reading** card and a warning chip when a source your books use stops working.
- Ratings from AniList, MyAnimeList and Anime-Planet on manga covers.
- **✨ For you** with **Because you read …** rows, and **Readers also liked** on every book page.
- **Search everything** from the library across **Manga**, **Novels** or **Both**, in your pin order.
- **Pin order** you can drag to rearrange.

**Read: book page**
- A reviews card under the description. Novels use **Webnovel's** reviews, with stars per reader, score parts (writing, updates, story, characters), a star filter and a spoiler switch. AniList, Novel Updates, Royal Road and MyAnimeList show as extra sites. Manga use AniList, MyAnimeList and Anime-Planet.
- Tabs that stay at the top: **Chapters · Pages · Reviews**.
- **Pages**: see every page of a chapter, hold one to look closer, tap to read from that page, and bookmark pages.
- Novel chapters show their title, opening lines, reading time and word count, and you can hold one to preview it.
- A small bottom bar, **🔖 · Continue · ⋯**:
  - **🔖** shows your bookmarks, including an automatic **Where you stopped**.
  - **⋯** shows where you stopped, the newest chapter, reading incognito, starting over and downloading the next 5.
- If you go back to an earlier chapter, **Continue** asks whether to pick up there or at the furthest chapter you've read.

**Read: reading**
- Manga can turn pages like a book (page curl).
- The time, battery and chapter show while you read manga, and novels have a status bar with the time, battery and progress.
- A novel chapter counts as read at 95%, and **Continue** follows your reading history. A chip tells you if you skipped chapters.

**Read: your own books**
- **Import a book** from a link, a web page or your phone. On a web page it finds every book it can, you can search the list, and you can download one or many. Each download is limited to about 1 GB.
- A built-in converter turns PDF, CBZ, CBR, 7z, MOBI and AZW3 into EPUB. Locked (DRM) books and app installers are blocked.
- Link safety checks warn about http links, bare number addresses, look-alike names and copycat domains.
- **My Books** lists your imports by series, author or genre.
- **Catalogs** (OPDS), with Project Gutenberg and Standard Ebooks built in, and you can add your own.
- **Share on Wi-Fi**: other devices at home can download your imported books. It's protected by a PIN and off by default.
- **Storage & backup** shows the space used, cleans up downloads of finished books and links to backups.

## Install

1. Download the newest `.apk` from [Releases](https://github.com/fenrirfen-cell/Mugiverse/releases/latest).
2. Open it on your phone and allow installs from your browser or file manager if Android asks.
3. After that, the app updates itself. When a new version is out, a red dot shows on the **Watch / Read** switch. Tap it, then **Update**.

Android only. iOS and desktop builds of Nuvio are not part of this project.

### Optional plugin repo

To add the J-Novel Club plugin (light novels only), paste this link into **Read → Browse → Extensions → +**:

```
https://raw.githubusercontent.com/fenrirfen-cell/Mugiverse/read-mode/plugins/plugins.min.json
```

## Build from source

You need Android Studio, the Android SDK and JDK 17 or newer.

```bash
git clone --recurse-submodules -b read-mode https://github.com/fenrirfen-cell/Mugiverse
cd Mugiverse
./gradlew :androidApp:assembleFullDebug
```

The Read side lives in the `tsundoku/` submodule ([fenrirfen-cell/tsundoku1.0.0](https://github.com/fenrirfen-cell/tsundoku1.0.0), branch `nuvio-embed`). [docs/READ_MODE.md](docs/READ_MODE.md) explains how the two apps fit together.

### Releases

The **Fenrir build** GitHub Action builds a signed APK and publishes it as a release. Once a day it checks for a new official Nuvio or Tsundoku release. When there is one, it merges it in and publishes a new build. If a merge clashes, it opens an issue instead. You can also start it by hand from the Actions tab.

## Credits and licenses

MugiVerse stands on the work of two projects. Thank you to their authors:

- **Nuvio Mobile** by NuvioMedia, licensed under the [GNU GPL v3.0](./LICENSE).
- **Tsundoku** by tsundoku-otaku, licensed under the [Apache License 2.0](https://github.com/fenrirfen-cell/tsundoku1.0.0/blob/nuvio-embed/LICENSE). It is itself based on Mihon and Tachiyomi.

Because it includes Nuvio, MugiVerse as a whole is released under the **GNU GPL v3.0**. MugiVerse is not affiliated with or endorsed by Nuvio, Tsundoku or any source or plugin author.

## Disclaimer

MugiVerse doesn't host, store or share any media. It only shows what the sources you add provide. You're responsible for making sure the sources you use are legal where you live.
