package com.nuvio.app.readmode

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FenrirFoundLinksTest {
    @Test
    fun reloadReplacesCachedLinks() = runTest {
        val host = FakeHost()
        val query = FenrirStreamQuery("Cache regression test", isMovie = true, season = null, episode = null)

        suspend fun find(force: Boolean = false): List<String> {
            val urls = mutableListOf<String>()
            FenrirFoundLinks.find(host, query, forceRefresh = force) { _, streams ->
                urls.addAll(streams.map { it.url })
            }
            return urls
        }

        assertEquals(listOf("https://example.com/1"), find(force = true))
        assertEquals(listOf("https://example.com/1"), find())
        assertEquals(1, host.calls)
        assertEquals(listOf("https://example.com/2"), find(force = true))
        assertEquals(listOf("https://example.com/2"), find())
        assertEquals(2, host.calls)
    }

    private class FakeHost : FenrirPluginHost {
        override val id = "test-cache-reload"
        override val title = "Test"
        override val subtitle = "Test"
        override val state = MutableStateFlow(FenrirPluginState())
        override val hasSources = true
        var calls = 0
        override fun refresh() = Unit
        override fun addRepo(url: String) = Unit
        override fun removeRepo(url: String) = Unit
        override fun install(id: String) = Unit
        override fun uninstall(id: String) = Unit
        override fun trust(id: String) = Unit
        override fun setShowNsfw(show: Boolean) = Unit
        override fun clearMessage() = Unit
        override suspend fun findStreams(query: FenrirStreamQuery, onFound: suspend (String, List<FenrirPluginStream>) -> Unit) {
            calls++
            onFound("test", listOf(FenrirPluginStream("test", "Test", "", "https://example.com/$calls")))
        }
        override suspend fun search(query: String) = emptyList<FenrirPluginHit>()
        override suspend fun episodes(hit: FenrirPluginHit) = emptyList<FenrirPluginEpisode>()
        override suspend fun streams(episode: FenrirPluginEpisode) = emptyList<FenrirPluginStream>()
    }
}
