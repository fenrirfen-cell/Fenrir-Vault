package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import kotlin.reflect.KClass
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Fenrir Read: plugins add their own interceptors to these clients. If one of them throws
 * something other than an IOException (e.g. it needs a CloudStream app class Fenrir Read
 * doesn't have), OkHttp rethrows it on its own thread and the whole app crashes. This sits
 * outermost and turns those into a normal failed request, so only that site fails.
 */
private object PluginErrorGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = try {
        chain.proceed(chain.request())
    } catch (e: java.io.IOException) {
        throw e
    } catch (e: OutOfMemoryError) {
        throw e
    } catch (e: Throwable) {
        throw java.io.IOException("Plugin request failed: ${e.javaClass.simpleName}: ${e.message}", e)
    }
}

// Short name for requests client to make it nicer to use
private val jsonResponseParser = object : ResponseParser {
    override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
        return parseJson(text, kClass)
    }

    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
        return try {
            parse(text, kClass)
        } catch (_: Exception) {
            null
        }
    }

    override fun writeValueAsString(obj: Any): String {
        return obj.toJson()
    }
}

/** The default networking helper. This helper performs SSL checks.
 * If you need to make requests to websites with invalid SSL certificates use insecureApp instead. */
var app = Requests(responseParser = jsonResponseParser).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
    baseClient = baseClient.newBuilder().addInterceptor(PluginErrorGuard).build()
}

/** Same as the default app networking helper, but this instance ignores SSL certificates.
 * This should NEVER be used for sensitive networking operations such as logins. Only use this when required. */
@UnsafeSSL
var insecureApp = Requests(responseParser = jsonResponseParser).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
    baseClient = baseClient.newBuilder().addInterceptor(PluginErrorGuard).build()
}
