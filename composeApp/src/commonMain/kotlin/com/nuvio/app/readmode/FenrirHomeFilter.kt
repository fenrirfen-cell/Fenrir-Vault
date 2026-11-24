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
 * - Spanish (and Portuguese) lists only show for profiles set to "Spanish only"; others leave them out.
 */
object FenrirHomeFilter {
    /** Profiles (by number) set to "Spanish only" in their profile settings. */
    private val _spanishProfiles = MutableStateFlow<Set<Int>>(emptySet())
    val spanishProfiles: StateFlow<Set<Int>> = _spanishProfiles.asStateFlow()
    private var persist: ((String) -> Unit)? = null

    fun isSpanishProfile(profileIndex: Int?): Boolean = profileIndex != null && profileIndex in _spanishProfiles.value

    fun setSpanishProfile(profileIndex: Int, on: Boolean) {
        _spanishProfiles.value = if (on) _spanishProfiles.value + profileIndex else _spanishProfiles.value - profileIndex
        persist?.invoke(_spanishProfiles.value.sorted().joinToString(","))
    }

    fun install(saved: String?, persist: (String) -> Unit) {
        this.persist = persist
        _spanishProfiles.value = saved.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    // Spanish and Portuguese words in list names ("Películas populares", "Tendências", "Ano", "Idioma"…)
    private val foreignWords = setOf(
        "películas", "pelicula", "película", "peliculas", "pelis", "español", "espanol", "latino", "castellano",
        "estrenos", "populares", "tendencias", "tendencia", "tendências", "tendência", "valoradas", "valorados",
        "novedades", "recomendadas", "recomendados", "doramas", "telenovelas", "emisión", "emision", "visto", "vistos",
        "clásicos", "clasicos", "infantil", "documentales", "temporada", "temporadas", "idioma", "idiomas", "año", "ano",
        "años", "anos", "género", "genero", "gênero", "géneros", "generos", "gêneros", "filmes", "séries", "lançamentos",
        "lancamentos", "destaques", "década", "décadas", "animación", "animação", "comedia", "comédia", "acción", "ação",
        "ficción", "ficção", "suspenso", "misterio", "mistério", "família", "familia", "crimen", "mejores", "melhores",
        "nuevos", "novos", "nuevas", "novas", "cine", "más", "mais", "em", "alta", "estreias", "dublado", "dublados",
        "legendado", "legendados", "doblaje", "doblada", "doblado", "subtitulada", "subtitulado",
    )
    private val foreignTags = Regex("""(\(es\)|\[es]|\(pt\)|\[pt]|\(br\)|\[br]|🇪🇸|🇲🇽|🇦🇷|🇧🇷|🇵🇹)""", RegexOption.IGNORE_CASE)

    /** A list in Spanish (or Portuguese), going by its name, or by its titles' language when the add-on says. */
    fun isSpanish(section: HomeCatalogSection): Boolean {
        val name = "${section.title} ${section.subtitle}"
        if (foreignTags.containsMatchIn("$name ${section.addonName}")) return true
        val words = name.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotBlank() }
        if (words.any { it in foreignWords && it != "em" && it != "alta" && it != "mais" } ||
            (words.contains("em") && words.contains("alta"))
        ) {
            return true
        }
        val langs = section.items.mapNotNull { it.language?.lowercase()?.takeIf { l -> l.isNotBlank() } }
        return langs.size >= 3 && langs.count { it.startsWith("es") || it.startsWith("spa") || it.startsWith("pt") || it.startsWith("por") } * 2 > langs.size
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
