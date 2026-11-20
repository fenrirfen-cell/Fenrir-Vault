package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.mapper

/**
 * CloudStream's key/value settings store (normally part of the CloudStream app), which plugins
 * use to remember things like their Cloudflare user agent or login. Same names and storage
 * format as recloudstream/cloudstream's DataStore (GPL-3.0).
 */
object DataStore {
    const val PREFERENCES_NAME = "rebuild_preference"

    @JvmStatic
    fun Context.getSharedPrefs(): SharedPreferences =
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @JvmStatic
    fun getFolderName(folder: String, path: String): String = "$folder/$path"

    @JvmStatic
    fun Context.getKeys(folder: String): List<String> {
        val fixedFolder = folder.trimEnd('/') + "/"
        return getSharedPrefs().all.keys.filter { it.startsWith(fixedFolder) }
    }

    @JvmStatic
    fun Context.removeKey(folder: String, path: String) {
        removeKey(getFolderName(folder, path))
    }

    @JvmStatic
    fun Context.containsKey(folder: String, path: String): Boolean = containsKey(getFolderName(folder, path))

    @JvmStatic
    fun Context.containsKey(path: String): Boolean = getSharedPrefs().contains(path)

    @JvmStatic
    fun Context.removeKey(path: String) {
        runCatching {
            val prefs = getSharedPrefs()
            if (prefs.contains(path)) prefs.edit().remove(path).apply()
        }
    }

    @JvmStatic
    fun Context.removeKeys(folder: String): Int {
        val keys = getKeys("$folder/")
        return runCatching {
            getSharedPrefs().edit().apply { keys.forEach { remove(it) } }.apply()
            keys.size
        }.getOrDefault(0)
    }

    @JvmStatic
    fun <T> Context.setKey(path: String, value: T) {
        runCatching { getSharedPrefs().edit().putString(path, mapper.writeValueAsString(value)).apply() }
    }

    @JvmStatic
    fun <T> Context.getKey(path: String, valueType: Class<T>): T? = runCatching {
        val json = getSharedPrefs().getString(path, null) ?: return null
        json.toKotlinObject(valueType)
    }.getOrNull()

    @JvmStatic
    fun <T> Context.setKey(folder: String, path: String, value: T) {
        setKey(getFolderName(folder, path), value)
    }

    @JvmStatic
    fun <T> String.toKotlinObject(valueType: Class<T>): T = mapper.readValue(this, valueType)

    inline fun <reified T : Any> String.toKotlinObject(): T = mapper.readValue(this, T::class.java)

    inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? = try {
        val json: String = getSharedPrefs().getString(path, null) ?: return defVal
        json.toKotlinObject()
    } catch (_: Exception) {
        null
    }

    inline fun <reified T : Any> Context.getKey(path: String): T? = getKey(path, null)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String): T? =
        getKey(getFolderName(folder, path), null)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String, defVal: T?): T? =
        getKey(getFolderName(folder, path), defVal) ?: defVal
}
