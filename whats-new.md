# What's new

Short notes for the next build, shown in the app after updating. Edit these before each build.

- Home: the same list from two add-ons now shows once
- Fixed: the app closing over and over after the WatchParty CloudStream plugin was installed or tested; plugins that hook into the app's screens can't crash it anymore
- When a book is gone from its site (page not found / can't reach the site), a clear message replaces the error code; the app looks for the same book on your other sources and offers Switch (your progress moves with it), Search other sources, or Open in WebView
- Plugin check: test CloudStream plugins before installing (Available → Test all); each shows Works / Slow / Down, with filters
- Opening a book from the shelf: no second cover while it flies out, and no flash of the library before the reader
- Not working list: the same source in many languages shows once with a ×N count; sources that are only empty (like some MangaDex languages) aren't turned off anymore, and the ones that were are back on
- Mass import: when a link doesn't open straight away, the book is looked up on the site instead; sites that changed their ending (.com → .net) still match
- Novels in page mode: tap the right side for the next page and the left side to go back, with the book flip (the middle still opens the menu)
- FENRIR WATCH ▾ always opens now; with no plugins it still has For you
- For you moved from the bottom bar into the FENRIR WATCH ▾ menu (on Watch and Ani-drama); the bottom bar is Home, Assistant, My list, Settings
- Fixed: menus and buttons with drop-downs work again (the pop-up block in r160 caught the app's own menus)
- Ani-drama: FENRIR WATCH ▾ opens the Home source picker again
- No more pop-ups from sources: when a CloudStream plugin or Aniyomi / Tsundoku extension tries to open its own window (ads, donation boxes, notices), it's blocked and a short "Blocked a pop-up from a source" note shows instead
- CloudStream / Aniyomi: links already found on the show page (Found in) show instantly when you tap the site or open the links or the player's Sources; a search still running is joined instead of started again
- Reader page flip (manga and novels): turns like a real book both ways. Tapping gives a smooth full turn; going back, the previous page lifts off the spine and lays down; swiping back keeps the fold under your finger. Soft shadow under the curl and the print shows faintly through the back of the page
- Show pages open instantly with what the poster already knows (title, pictures, description, year, rating); episodes, cast and the rest fill in. Play turns on when the full details are in
- A slow add-on no longer holds a show page up: once any add-on has the title, the ones above it get 1.5 more seconds
- Watch Party, shared links: when the host stops sharing, leaves or ends the party, anyone watching the host's link is stopped and asked to pick one of their own. A shared link only plays in Fenrir's own player, can't be copied or opened elsewhere, and isn't saved for later
- Watch Party with plugin links: when the party plays a CloudStream / Aniyomi site, everyone's app looks on that site too and marks its links as the party source (same site, same episode)
- Settings: the wolf howl is gone; app update notifications moved into Settings → Notifications
- Manage sources → Stremio: each add-on has Configure (opens its own setup page) and Refresh (reloads it after you change it)
- Posters no longer show their rating twice
- Home starts with just the main lists (trending, popular, top rated, new; one each for movies and series). Turn on any others in Settings → Homescreen

- New Home look: a big featured banner from the top of the screen, a play button on Continue Watching cards, a Top 10 row, ratings and SUB/DUB on posters, cleaner row headers with where each list comes from
- Faster show pages: all your add-ons are asked at once and the page opens as soon as one answers; extra details fill in after
- More like this and sequels show again (TMDB details are on by default), and load alongside ratings with a time limit
- Novel reviews: the first review site that answers shows right away

- New: "Links from" on show and movie pages: All, Nuvio add-ons or Plugins (CloudStream / Aniyomi). Remembered for each title
- "Found in your sources" also lists your plugin sites (purple); tap one to play straight from that site
- Player: the Sources list now includes your CloudStream and Aniyomi sites, so you can switch to a plugin link while watching

- Fixed: a show page that couldn't load kept spinning forever; it now stops after 30 seconds with Retry
- Fixed: Search on the Ani-drama page opens the search screen (it did nothing when a CloudStream site was picked for Home)
- Fixed: tapping FENRIR WATCH ▾ opens the Home / source picker again
- New Regular library look: a big Continue Reading card over the book's own cover, category tabs with book counts, and covers showing type, unread chapters and reading progress
- Read side (Regular look): Search sits next to Read; everything else is under FENRIR READ ▾
- Opening Ani-drama from the Read side no longer waits on the loading screen
- Safety: add-on links from websites now ask before installing, and CloudStream plugins only download over secure (https) connections

- Ani-drama: pull down to refresh; it also refreshes by itself when you come back after 30 minutes, so airing lists and new episodes stay current
- Ani-drama: the search button no longer covers FENRIR WATCH

- Fixed: the Luxury Collection shelf was empty (the background was drawn over the books)
- Watch: tapping a title on a CloudStream or Aniyomi site's home page now opens that site's own episodes and links, instead of jumping to the Nuvio show page and its addon links

- New: choose how your library looks. Tap FENRIR READ ▾ → Library look:
  - Bookshelf: the wooden bookcase with ornate tomes
  - Luxury Collection: a black-and-gold glass display cabinet with collector's-edition books on stands, gold nameplates, and side towers on wide screens
  - Regular: a plain dark grid
- Watch: CloudStream and Aniyomi plugins now show up even when you start a show from Continue watching, and each site gets its own section with its own links
- Luxury adapts to the screen: folded it fills the width, unfolded it adds the decorative towers
