package com.nuvio.app

import android.app.Application
import android.os.Build
import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.nuvio.app.core.network.NetworkErrorInterceptor
import com.nuvio.app.core.poster.CustomPosterFallbackInterceptor
import com.nuvio.app.features.servers.plex.PlexArtworkInterceptor
import com.nuvio.app.features.servers.silo.SiloArtworkInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Custom Application class that implements [SingletonImageLoader.Factory] to guarantee
 * the Coil ImageLoader (with [CustomPosterFallbackInterceptor]) is created before any
 * image request. Without this, Coil's auto-init ContentProvider creates the singleton
 * before MainActivity.onCreate, and interceptors registered later are ignored.
 *
 * Builds with Read mode use a subclass of Tsundoku's Application instead (see androidApp's
 * `NuvioReadApplication`), which adds the same components through [NuvioImageLoaderComponents].
 */
class NuvioApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .components {
                NuvioImageLoaderComponents.addInterceptors(this)
                NuvioImageLoaderComponents.addDecoders(this)
                NuvioImageLoaderComponents.addNetworkFetcher(this)
            }
            .build()
    }
}

/** Nuvio's Coil components, shared by [NuvioApplication] and the Read mode host application. */
object NuvioImageLoaderComponents {

    /** Artwork fallbacks and Plex / Silo artwork links. Each only acts on Nuvio's own requests. */
    fun addInterceptors(registry: ComponentRegistry.Builder) {
        registry.add(CustomPosterFallbackInterceptor())
        registry.add(PlexArtworkInterceptor())
        registry.add(SiloArtworkInterceptor())
    }

    fun addDecoders(registry: ComponentRegistry.Builder) {
        registry.add(SvgDecoder.Factory())
        if (Build.VERSION.SDK_INT >= 28) {
            registry.add(AnimatedImageDecoder.Factory())
        } else {
            registry.add(GifDecoder.Factory())
        }
    }

    fun addNetworkFetcher(registry: ComponentRegistry.Builder) {
        registry.add(
            KtorNetworkFetcherFactory(
                httpClient = {
                    HttpClient(OkHttp) {
                        engine {
                            addInterceptor(NetworkErrorInterceptor())
                        }
                    }
                },
                cacheStrategy = { CacheControlCacheStrategy() },
            ),
        )
    }
}
