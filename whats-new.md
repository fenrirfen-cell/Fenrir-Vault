# What's new

Short notes for the next build, shown in the app after updating. Edit these before each build.

- Novels: lines that only hold invisible characters are treated as empty too, so paragraph gaps stay even
- Import a book: new Browse button opens a browser in the app; sign in or pass checks there, tap a book's download, and it's imported
- Import a book from a link and the in-app browser now ask sites like a phone browser does, so sites that open in Chrome but refused the app (403) should open here too
- Novels: no more big empty gaps between paragraphs or empty space at the top of a page; the gap is now just your paragraph spacing setting
- Import a book from a link: when the site asks for a human check, tap Open site, pass it, then tap Check link again; when a site simply doesn't let visitors open a folder, you're told that instead
- About: "Update channel" now switches beta builds on and off (same as Settings → Notifications → App updates → Get beta builds), and the version line shows your Fenrir build number
- Read side: the white bar that showed behind the bottom bar when you reload your library is gone; messages now show above it in the dark look
- Import a book from a link: plain http:// links are tried over https, and when a link still doesn't open you're told why
- Backup now includes your Stremio add-ons and Home rows too, so one Restore brings everything back. If add-ons or plugins ever disappear, the bigger list before it is kept as "fenrir-plugins-backup-previous.json" in Downloads › Fenrir Vault
- Beta builds: new builds come out as betas first. Turn on Settings → App updates → Get beta builds to get them; everyone else only gets them once they're released to all
- Links: CloudStream and Aniyomi now always look for links, even when none of your Stremio add-ons has this title, and right after the app opens (they wait for your plugins to finish loading instead of being skipped)
- Reload (the first chip in the player's Sources list and on the links screen) now really searches the plugin sites again; before, "nothing found" was kept for 15 minutes
- Player Sources: CloudStream and Aniyomi no longer show just a few old links after you picked one while they were still searching; a search that was stopped half-way runs again
- Plugin backup: your CloudStream plugins and Aniyomi extensions (repositories, what's installed, what you trusted) are saved by themselves to Downloads › Fenrir Vault, and the file stays there if you delete the app. After installing again: Settings → Sources → Plugin backup → Restore. "Save a copy" puts one anywhere you like
- Trust all: when several Aniyomi extensions are waiting to be trusted, trust them in one go from the top of the Installed list
- The built-in Fenrir Catalogs add-on is gone. Any copy left on this phone or your Nuvio account is removed by itself. If your add-ons went missing, add them back once and they stay
- Nuvio account sync fixed: the app waits for your account's add-ons before changing anything, and the "main lists only" first-run setup no longer switches off the Home rows you picked on your Nuvio account
- Add-ons the automatic check turns off (after 2 days of not answering) are now only turned off on this phone, not on your Nuvio account or your other devices
- Player: Back leaves straight away; the "Not the right one?" pop-up is gone (switch links with Sources in the player)
- Player Sources now lists your CloudStream and Aniyomi sites too, also when you started the episode from Continue watching or Home, and always for the show you're watching
- Opening a book is more 3D: the book swings round as it leaves the case, leans toward you as the hard cover swings open on its hinge with light and shadow, and the pages fan over before the reader opens
- Continue reading now always shows the book you read last (manga, webtoons and novels), taken straight from your reading history; before, some books never showed up or an old one stayed there
- New opening animation: the wolf's purple eyes open in the dark, the chained wolf, then the FENRIR VAULT logo (tap to skip)
- The player now uses Nuvio's classic layout by default (title at the top, one bar with Fill · Speed · Subs · Audio · Sources · Episodes). If you changed this switch yourself, your choice stays: Settings → Playback → Interface → Legacy layout
- Novels in page mode: every chapter now starts on a new page with its title at the top. The end of the previous chapter no longer shares a page with the next chapter's title, and turning back stops at the title
- Opening a book from Continue reading: the bottom bar and the top bar no longer stay on top of the book as it opens; the opening page uses your reader's colours, font and text size, and when you're part way into a chapter the words fade away so the reader opens where you stopped without a jump
