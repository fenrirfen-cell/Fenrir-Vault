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

    fun setSpanishProfile(profileIndex: Int, on: Boolean, recheckLists: Boolean) {
        val was = isSpanishProfile(profileIndex)
        setSpanishProfile(profileIndex, on)
        // switching a profile to or from Spanish picks its Home lists again
        if (recheckLists && was != on) {
            curated = curated - profileIndex
            persistCurated?.invoke(curated.sorted().joinToString(","))
        }
    }

    fun install(saved: String?, persist: (String) -> Unit) {
        this.persist = persist
        _spanishProfiles.value = saved.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    // ---------------------------------------------------------------- which Home lists start on

    /** Profiles whose Home lists were already set to Fenrir's picks once (then the user decides). */
    private var curated: Set<Int> = emptySet()
    private var persistCurated: ((String) -> Unit)? = null

    fun installCurated(saved: String?, persist: (String) -> Unit) {
        persistCurated = persist
        curated = saved.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    fun needsCuration(profileIndex: Int): Boolean = persistCurated != null && profileIndex !in curated

    fun markCurated(profileIndex: Int) {
        if (profileIndex in curated) return
        curated = curated + profileIndex
        persistCurated?.invoke(curated.sorted().joinToString(","))
    }

    private fun kindOf(title: String): String? {
        val t = title.lowercase()
        return when {
            "trending" in t -> "trending"
            "popular" in t -> "popular"
            "top rated" in t || "top-rated" in t || "best rated" in t -> "top"
            "featured" in t -> "featured"
            "new" in t.split(Regex("[^a-z]+")) || "latest" in t || "recent" in t || "in theaters" in t -> "new"
            else -> null
        }
    }

    /**
     * Lists that start switched on (everything else starts off; turn more on in Settings → Homescreen):
     * normal profiles get the main English lists (trending, popular, top rated, new, featured),
     * one of each per movies / series; Spanish-only profiles get every Spanish list.
     */
    fun startsOn(
        lists: List<Triple<String, String, String>>, // key, title, type
        spanishProfile: Boolean,
    ): Set<String> {
        val nameOnly = { title: String ->
            HomeCatalogSection(key = "", title = title, subtitle = "", addonName = "", target = com.nuvio.app.features.catalog.CatalogTarget.Addon("", "", ""), items = emptyList())
        }
        if (spanishProfile) return lists.filter { isSpanish(nameOnly(it.second)) }.map { it.first }.toSet()
        val picked = linkedMapOf<String, String>()
        lists.forEach { (key, title, type) ->
            if (isSpanish(nameOnly(title))) return@forEach
            val kind = kindOf(title) ?: return@forEach
            picked.getOrPut("$kind|$type") { key }
        }
        return picked.values.toSet()
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
