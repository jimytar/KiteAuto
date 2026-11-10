package com.jimytar.kiteauto.car

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.jimytar.kiteauto.web.PageMedia

/**
 * Platform media session mirroring what the car browser's page plays, so media buttons (steering wheel, phone)
 * reach it: play/pause/next/seek are routed back to the page. No media-app entry in Android Auto, just the session.
 * Lives as long as the session's [BrowserDisplay]; polls the page once a second.
 */
class CarMediaSession(context: Context, private val browser: BrowserDisplay) {
    private val handler = Handler(Looper.getMainLooper())
    private var last: PageMedia? = null
    private val artLoader = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var artworkUrl: String? = null
    private var artwork: android.graphics.Bitmap? = null

    private val session = MediaSession(context, "KiteAuto").apply {
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() { browser.browser?.play() }
            override fun onPause() { browser.browser?.pause() }
            override fun onSkipToNext() { browser.browser?.next() }
            override fun onSeekTo(pos: Long) { browser.browser?.seekTo(pos) }
        })
        isActive = true
    }

    private val poll = object : Runnable {
        override fun run() {
            val page = browser.browser
            if (page == null) publish(null) else page.readMedia(::publish)
            handler.postDelayed(this, POLL_MS)
        }
    }

    init {
        handler.post(poll)
    }

    private fun publish(media: PageMedia?) {
        if (media == last) return
        last = media
        val state = when {
            media == null -> PlaybackState.STATE_NONE
            media.playing -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SEEK_TO
                )
                .setState(state, media?.positionMs ?: 0, if (media?.playing == true) 1f else 0f)
                .build()
        )
        if (media != null) {
            if (media.artworkUrl != artworkUrl) loadArtwork(media.artworkUrl)
            session.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, media.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, media.artist)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, media.durationMs)
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
                    .build()
            )
        }
    }

    /** Thumbnail for the dashboard card (YouTube thumbnail / page Media Session artwork), loaded off the UI thread. */
    private fun loadArtwork(url: String?) {
        artworkUrl = url
        artwork = null
        if (url == null) return
        artLoader.execute {
            val bitmap = runCatching {
                java.net.URL(url).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }
            }.onFailure { android.util.Log.w("KiteAuto", "Artwork $url: $it") }.getOrNull()
                ?.let { android.graphics.Bitmap.createScaledBitmap(it, 480, 480 * it.height / maxOf(1, it.width), true) }
            handler.post {
                if (artworkUrl == url) {
                    artwork = bitmap
                    last = null // republish with the artwork
                }
            }
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        artLoader.shutdownNow()
        session.release()
    }

    private companion object {
        const val POLL_MS = 1000L
    }
}
