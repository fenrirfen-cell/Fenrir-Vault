package com.lagradost.cloudstream3

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.utils.DataStore.setKey

/**
 * CloudStream plugins call into the CloudStream *app* (not just its library) through this
 * class: mostly for the app context and its saved settings. Fenrir Vault isn't the CloudStream
 * app, so this stands in with the same names, backed by Fenrir Vault's own context.
 * (Mirrors recloudstream/cloudstream's CloudStreamApp, GPL-3.0.)
 */
open class CloudStreamApp {
    companion object {
        @JvmStatic
        tailrec fun Context.getActivity(): Activity? = when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.getActivity()
            else -> null
        }

        /** The app context, set when Fenrir Vault starts its plugin host. */
        @JvmStatic
        val context: Context?
            get() = com.lagradost.api.getContext() as? Context

        @JvmStatic
        fun <T : Any> getKeyClass(path: String, valueType: Class<T>): T? =
            context?.getKey(path, valueType)

        @JvmStatic
        fun <T : Any> setKeyClass(path: String, value: T) {
            context?.setKey(path, value)
        }

        @JvmStatic
        fun removeKeys(folder: String): Int? = context?.removeKeys(folder)

        @JvmStatic
        fun <T> setKey(path: String, value: T) {
            context?.setKey(path, value)
        }

        @JvmStatic
        fun <T> setKey(folder: String, path: String, value: T) {
            context?.setKey(folder, path, value)
        }

        inline fun <reified T : Any> getKey(path: String, defVal: T?): T? = context?.getKey(path, defVal)

        inline fun <reified T : Any> getKey(path: String): T? = context?.getKey(path)

        inline fun <reified T : Any> getKey(folder: String, path: String): T? = context?.getKey(folder, path)

        inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? =
            context?.getKey(folder, path, defVal)

        @JvmStatic
        fun getKeys(folder: String): List<String>? = context?.getKeys(folder)

        @JvmStatic
        fun removeKey(folder: String, path: String) {
            context?.removeKey(folder, path)
        }

        @JvmStatic
        fun removeKey(path: String) {
            context?.removeKey(path)
        }

        /** Opens a link in the phone's browser. */
        @JvmStatic
        @JvmOverloads
        fun openBrowser(url: String, fallbackWebview: Boolean = false, fragment: Any? = null) {
            val ctx = context ?: return
            runCatching {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }

        /** Shared settings file CloudStream plugins read and write. */
        @JvmStatic
        fun prefs() = context?.getSharedPrefs()
    }
}

/** Older plugins use CloudStream's previous name for the same thing. */
open class AcraApplication {
    companion object {
        @JvmStatic
        val context: Context? get() = CloudStreamApp.context

        @JvmStatic
        fun <T : Any> getKeyClass(path: String, valueType: Class<T>): T? = CloudStreamApp.getKeyClass(path, valueType)

        @JvmStatic
        fun <T : Any> setKeyClass(path: String, value: T) = CloudStreamApp.setKeyClass(path, value)

        @JvmStatic
        fun removeKeys(folder: String): Int? = CloudStreamApp.removeKeys(folder)

        @JvmStatic
        fun <T> setKey(path: String, value: T) = CloudStreamApp.setKey(path, value)

        @JvmStatic
        fun <T> setKey(folder: String, path: String, value: T) = CloudStreamApp.setKey(folder, path, value)

        inline fun <reified T : Any> getKey(path: String, defVal: T?): T? = CloudStreamApp.getKey(path, defVal)

        inline fun <reified T : Any> getKey(path: String): T? = CloudStreamApp.getKey(path)

        inline fun <reified T : Any> getKey(folder: String, path: String): T? = CloudStreamApp.getKey(folder, path)

        inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? =
            CloudStreamApp.getKey(folder, path, defVal)

        @JvmStatic
        fun getKeys(folder: String): List<String>? = CloudStreamApp.getKeys(folder)

        @JvmStatic
        fun removeKey(folder: String, path: String) = CloudStreamApp.removeKey(folder, path)

        @JvmStatic
        fun removeKey(path: String) = CloudStreamApp.removeKey(path)

        @JvmStatic
        @JvmOverloads
        fun openBrowser(url: String, fallbackWebview: Boolean = false, fragment: Any? = null) =
            CloudStreamApp.openBrowser(url, fallbackWebview, fragment)
    }
}
