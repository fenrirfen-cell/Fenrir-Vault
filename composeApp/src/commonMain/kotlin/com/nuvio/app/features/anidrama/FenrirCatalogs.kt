package com.nuvio.app.features.anidrama

/**
 * Fenrir: the built-in "Fenrir Catalogs" add-on was removed. Builds from 25 Nov 2026 installed it
 * by themselves and some sent it to the Nuvio account. These names are only kept so any leftover
 * copy (in this phone's add-on list, on the account, or in the Home row settings) is recognised
 * and taken out, never added back.
 */
object FenrirCatalogs {
    const val ADDON_ID = "fenrir.catalogs"
    const val MANIFEST_URL = "https://catalogs.fenrir.invalid/manifest.json"

    /** Home row settings of the old add-on ("fenrir.catalogs:type:id"). */
    fun isHomeKey(key: String): Boolean = key.startsWith("$ADDON_ID:")
}
