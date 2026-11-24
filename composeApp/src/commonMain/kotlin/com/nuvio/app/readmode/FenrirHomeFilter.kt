package com.nuvio.app.readmode

import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeUiState
import com.nuvio.app.features.home.stableKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fenrir: Nuvio home lists, cleaned up.
 * - The same list from two add-ons (or an add-on added twice) shows once.
 * - Spanish lists only show in "Nuvio home · Español"; the normal Nuvio home leaves them out.
 */
object FenrirHomeFilter {
    private val _spanish = MutableStateFlow(false)
    val spanish: StateFlow<Boolean> = _spanish.asStateFlow()
    private var persist: ((Boolean) -> Unit)? = null

    fun setSpanish(on: Boolean) {
        _spanish.value = on
        persist?.invoke(on)
    }

    fun install(saved: Boolean, persist: (Boolean) -> Unit) {
        this.persist = persist
        _spanish.value = saved
    }

    private val spanishWords = Regex(
        """\b(pel[ií]culas?|pelis|series en|espa[ñn]ol|latino|castellano|estrenos|populares|tendencias?|mejor(es)? valorad[ao]s|novedades|recomendad[ao]s|doramas|telenovelas|animes? en|en emisi[oó]n|lo m[aá]s visto|cl[aá]sicos|infantil|documentales|temporada)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val spanishTags = Regex("""(\(es\)|\[es]|\bes-|🇪🇸|🇲🇽|🇦🇷|\blat\b|\besp\b)""", RegexOption.IGNORE_CASE)

    fun isSpanish(section: HomeCatalogSection): Boolean {
        val text = "${section.title} ${section.subtitle} ${section.addonName}"
        if (spanishWords.containsMatchIn(text) || spanishTags.containsMatchIn(text)) return true
        // the titles' own language, when the add-on says
        val langs = section.items.mapNotNull { it.language?.lowercase()?.takeIf { l -> l.isNotBlank() } }
        return langs.size >= 3 && langs.count { it.startsWith("es") || it.startsWith("spa") } * 2 > langs.size
    }

    private fun norm(t: String) = t.lowercase().replace(Regex("[^a-z0-9áéíóúñ]+"), " ").trim()

    fun apply(state: HomeUiState, spanishMode: Boolean): HomeUiState {
        val kept = mutableListOf<HomeCatalogSection>()
        for (section in state.sections) {
            if (isSpanish(section) != spanishMode) continue
            val keys = section.items.take(20).map { it.stableKey() }.toSet()
            val dupe = kept.any { other ->
                val otherKeys = other.items.take(20).map { it.stableKey() }.toSet()
                val overlap = if (keys.isEmpty() || otherKeys.isEmpty()) 0.0 else keys.intersect(otherKeys).size.toDouble() / minOf(keys.size, otherKeys.size)
                val sameName = norm(other.title) == norm(section.title) && other.target.contentType == section.target.contentType
                (sameName && (overlap >= 0.4 || keys.isEmpty())) || overlap >= 0.8
            }
            if (!dupe) kept += section
        }
        if (kept.size == state.sections.size) return state
        val keptKeys = kept.flatMapTo(HashSet()) { s -> s.items.map { it.stableKey() } }
        val hero = state.heroItems.filter { it.stableKey() in keptKeys }.ifEmpty { if (spanishMode) emptyList() else state.heroItems }
        return state.copy(sections = kept, heroItems = hero)
    }
}
