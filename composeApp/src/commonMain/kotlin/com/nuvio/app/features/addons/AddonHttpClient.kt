package com.nuvio.app.features.addons

internal suspend fun fetchAddonResponseText(
    url: String,
    forceRefresh: Boolean = false,
): String =
    // Fenrir: the built-in catalog add-on answers from inside the app
    if (com.nuvio.app.features.anidrama.FenrirCatalogs.handles(url)) {
        com.nuvio.app.features.anidrama.FenrirCatalogs.respond(url)
    } else if (forceRefresh) {
        httpGetTextWithHeaders(
            url = url,
            headers = mapOf("Cache-Control" to "no-cache"),
        )
    } else {
        httpGetText(url)
    }
