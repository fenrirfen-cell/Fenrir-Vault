package com.nuvio.app.readmode

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Fenrir: the opening film (res/raw/fenrir_splash_video.mp4), silent, played once on a
 * TextureView so it sits inside Compose like any picture. Calls [onEnd] when it finishes or if it
 * can't play; everything is wrapped so a bad device player can't crash the app.
 */
@Composable
fun FenrirSplashFilm(onEnd: () -> Unit) {
    val end by rememberUpdatedState(onEnd)
    val holder = remember { arrayOfNulls<MediaPlayer>(1) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { holder[0]?.release() }
            holder[0] = null
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        val ok = runCatching {
                            val afd = ctx.resources.openRawResourceFd(com.nuvio.app.R.raw.fenrir_splash_video)
                            val mp = MediaPlayer()
                            holder[0] = mp
                            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            afd.close()
                            mp.setSurface(Surface(st))
                            mp.setVolume(0f, 0f)
                            mp.isLooping = false
                            mp.setOnCompletionListener { end() }
                            mp.setOnErrorListener { _, _, _ -> end(); true }
                            mp.setOnPreparedListener { it.start() }
                            mp.prepareAsync()
                        }.isSuccess
                        if (!ok) end()
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        runCatching { holder[0]?.release() }
                        holder[0] = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
    )
}
