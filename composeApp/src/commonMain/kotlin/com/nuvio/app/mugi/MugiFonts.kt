package com.nuvio.app.mugi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.cinzel
import nuvio.composeapp.generated.resources.cinzel_bold
import nuvio.composeapp.generated.resources.playfair_display
import nuvio.composeapp.generated.resources.playfair_display_bold
import nuvio.composeapp.generated.resources.playfair_display_italic
import nuvio.composeapp.generated.resources.playfair_display_italic_bold
import org.jetbrains.compose.resources.Font

/** Cinzel: major headings and achievement titles. */
val MugiCinzel: FontFamily
    @Composable get() {
        val regular = Font(Res.font.cinzel, FontWeight.Normal)
        val bold = Font(Res.font.cinzel_bold, FontWeight.Bold)
        return remember(regular, bold) { FontFamily(regular, bold) }
    }

/** Playfair Display: section headings, book titles and labels (italic for dates and subtitles). */
val MugiPlayfair: FontFamily
    @Composable get() {
        val regular = Font(Res.font.playfair_display, FontWeight.Normal)
        val bold = Font(Res.font.playfair_display_bold, FontWeight.Bold)
        val italic = Font(Res.font.playfair_display_italic, FontWeight.Normal, FontStyle.Italic)
        val boldItalic = Font(Res.font.playfair_display_italic_bold, FontWeight.Bold, FontStyle.Italic)
        return remember(regular, bold, italic, boldItalic) { FontFamily(regular, bold, italic, boldItalic) }
    }
