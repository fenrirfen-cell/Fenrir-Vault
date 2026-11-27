package com.nuvio.app.mugi

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** UI-only adapter to the existing playback history; IDs and callbacks must be real. */
data class ResumeUi(val id:String,val title:String,val subtitle:String,val remaining:String,val progress:Float)

@Composable
fun MugiResumeRail(art:MugiArt,mode:MugiMode,entries:List<ResumeUi>,
                   thumbnail:@Composable (ResumeUi)->Unit,onResume:(String)->Unit,onMenu:(String)->Unit,
                   title:String="Continue Watching") {
    if(entries.isEmpty()) return // honor existing empty-state behavior if needed
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Do not hard-code a device type. Wide screens show individual separated cards.
        val cardWidth = if(maxWidth<420.dp) maxWidth-24.dp else minOf(350.dp,maxWidth*.47f)
        Column {
            ParchmentLabel("▶ $title",art.parchment,Modifier.padding(start=14.dp,bottom=2.dp).width(210.dp).height(36.dp))
            LazyRow(contentPadding=PaddingValues(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                items(entries,key={it.id}) { item ->
                    RopeResumeCard(art,Modifier.width(cardWidth)) {
                        Box(Modifier.width(92.dp).height(72.dp).clip(RoundedCornerShape(8.dp))) { thumbnail(item) }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title,color=MugiPalette.Ivory,fontSize=14.sp,maxLines=1)
                            Text(item.subtitle,color=MugiPalette.Muted,fontSize=11.sp,maxLines=1)
                            SailingProgress(item.progress,mode.accent)
                            Text(item.remaining,color=MugiPalette.Muted,fontSize=10.sp,maxLines=1)
                        }
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            Text("▶",color=MugiPalette.Ivory,fontSize=20.sp,modifier=Modifier.size(44.dp).clickable(onClickLabel="Resume ${item.title}"){onResume(item.id)}.wrapContentSize())
                            Text("⋮",color=MugiPalette.Ivory,fontSize=18.sp,modifier=Modifier.size(44.dp).clickable(onClickLabel="Options for ${item.title}"){onMenu(item.id)}.wrapContentSize())
                        }
                    }
                }
            }
        }
    }
}

/** One selector only. Pass typed, mode-specific data from the existing VM to these slots. */
@Composable
fun MugiWatchHome(art:MugiArt,kind:WatchKind,onKindChange:(WatchKind)->Unit,
                  headerActions:@Composable RowScope.()->Unit,
                  hero:@Composable (WatchKind)->Unit,
                  resume:@Composable (WatchKind)->Unit,
                  topTen:@Composable (WatchKind)->Unit,
                  catalogs:LazyListScope.(WatchKind)->Unit) {
    LazyColumn(Modifier.fillMaxSize().background(MugiPalette.Navy),contentPadding=PaddingValues(bottom=96.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { MugiHeader(MugiMode.WATCH,art,actions=headerActions) }
        item { WatchKindSelector(kind,onKindChange,Modifier.padding(horizontal=16.dp)) }
        item { hero(kind) }
        item { resume(kind) }
        item { topTen(kind) }
        catalogs(kind)
        // Keep provider-specific rails, ordering settings, ratings and existing See All callbacks.
    }
}

/** Keep the original search/discover repository, requests and all existing provider/genre choices. */
@Composable
fun MugiWatchSearch(art:MugiArt,kind:WatchKind,onKindChange:(WatchKind)->Unit,query:String,onQueryChange:(String)->Unit,
                    headerActions:@Composable RowScope.()->Unit,
                    onBack:()->Unit,
                    dropdowns:@Composable (WatchKind)->Unit,
                    results:@Composable (WatchKind,String)->Unit) {
    Column(Modifier.fillMaxSize().background(MugiPalette.Navy).padding(bottom=86.dp)) {
        MugiHeader(MugiMode.WATCH,art,actions=headerActions)
        Row(Modifier.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("‹",color=MugiPalette.Ivory,fontSize=30.sp,modifier=Modifier.size(44.dp).clickable(onClick=onBack).wrapContentSize())
            Text("Search",color=MugiPalette.Ivory,fontSize=27.sp,fontFamily=FontFamily.Serif)
        }
        OutlinedTextField(value=query,onValueChange=onQueryChange,singleLine=true,
            modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),placeholder={Text(if(kind==WatchKind.MOVIES) "Search movies..." else "Search TV shows...")})
        ParchmentLabel("Discover",art.parchment,Modifier.padding(start=16.dp,top=16.dp).width(116.dp).height(34.dp))
        WatchKindSelector(kind,onKindChange,Modifier.padding(horizontal=16.dp,vertical=7.dp))
        dropdowns(kind) // Popular and All Genres; remove only redundant Movies/TV type dropdown.
        Box(Modifier.weight(1f).fillMaxWidth()) { results(kind,query) }
    }
}

enum class AniCategory(val label:String){ANIME("Anime"),K_DRAMA("K-Drama"),C_DRAMA("C-Drama"),J_DRAMA("J-Drama")}

@Composable
fun AniCategories(art:MugiArt,value:AniCategory,onChange:(AniCategory)->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        AniCategory.values().forEach { category ->
            Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(7.dp)).clickable { onChange(category) },contentAlignment=Alignment.Center) {
                if(category==value){Box(Modifier.matchParentSize().background(Color(0xFF552081)).border(1.dp,MugiPalette.Gold,RoundedCornerShape(7.dp)))
                    Text(category.label,color=MugiPalette.Ivory,fontSize=12.sp,maxLines=1,fontFamily=FontFamily.Serif)
                }else ParchmentLabel(category.label,art.parchment,Modifier.matchParentSize(),12)
            }
        }
    }
}

/** Existing filter component must retain Airing, Trending, Popular, Top 100, Seasons,
 * Genres, Upcoming, Calendar, Dub/Sub/Both and original behavior for each category. */
@Composable
fun MugiAniDramaHome(art:MugiArt,category:AniCategory,onCategoryChange:(AniCategory)->Unit,
                     headerActions:@Composable RowScope.()->Unit,existingFilters:@Composable (AniCategory)->Unit,
                     existingCatalogs:@Composable (AniCategory)->Unit) {
    Column(Modifier.fillMaxSize().background(MugiPalette.PurpleNavy).padding(bottom=86.dp)) {
        MugiHeader(MugiMode.ANI_DRAMA,art,actions=headerActions)
        AniCategories(art,category,onCategoryChange)
        existingFilters(category)
        Box(Modifier.fillMaxWidth().weight(1f)) { existingCatalogs(category) }
    }
}

/** Item slots should reuse the app's existing 3D book tile and its tap/open animation. */
@Composable
fun <T> MugiReadLibrary(art:MugiArt,books:List<T>,headerActions:@Composable RowScope.()->Unit,
                        resumeContent:@Composable RowScope.()->Unit,
                        existingCategorySelector:@Composable ()->Unit,
                        bookTile:@Composable (T)->Unit,addBookTile:@Composable ()->Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MugiPalette.Wood)) {
        val columns=(maxWidth/94.dp).toInt().coerceIn(2,7)
        val rows=books.chunked(columns)
        LazyColumn(contentPadding=PaddingValues(bottom=96.dp)) {
            item {
                Box(Modifier.fillMaxWidth()) {
                    Image(art.libraryCabin,null,Modifier.matchParentSize(),contentScale=ContentScale.Crop)
                    Column {
                        MugiHeader(MugiMode.READ,art,actions=headerActions)
                        CaptainReadingPanel(art,Modifier.padding(12.dp),content=resumeContent)
                    }
                }
            }
            item { existingCategorySelector() }
            items(rows.size) { index ->
                val group=rows[index]
                CaptainShelfRow(art) {
                    group.forEach{book -> Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.BottomCenter){bookTile(book)}}
                    repeat(columns-group.size){Spacer(Modifier.weight(1f))}
                }
            }
            item { CaptainShelfRow(art) { Box(Modifier.weight(1f),contentAlignment=Alignment.BottomCenter){addBookTile()};repeat(columns-1){Spacer(Modifier.weight(1f))} } }
        }
    }
}
