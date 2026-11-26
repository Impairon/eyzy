package com.abo.avatarblackout

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class QuranPlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())

    private val positionUpdater = object : Runnable {
        override fun run() {
            savePositionNow()
            if (player?.isPlaying == true) handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                handler.removeCallbacks(positionUpdater)
                savePositionNow()
                if (isPlaying) handler.postDelayed(positionUpdater, SAVE_INTERVAL_MS)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val timelineId = mediaItem?.mediaId?.substringBefore('|') ?: return
                if (timelineId == QuranAudioRepository.MAIN_TIMELINE ||
                    timelineId == QuranAudioRepository.SIDE_TIMELINE ||
                    timelineId.startsWith("book:")
                ) {
                    QuranAudioRepository.setActiveTimeline(this@QuranPlaybackService, timelineId)
                    if (timelineId.startsWith("book:")) {
                        AudioBookRepository.setActive(
                            this@QuranPlaybackService,
                            timelineId.removePrefix("book:")
                        )
                    }
                }
                savePositionNow()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // Save the item being left before persisting the newly selected item.
                savePosition(
                    oldPosition.mediaItem?.mediaId,
                    oldPosition.positionMs
                )
                savePositionNow()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                    savePositionNow()
                }
            }
        })
    }

    private fun savePositionNow() {
        val current = player ?: return
        savePosition(current.currentMediaItem?.mediaId, current.currentPosition)
    }

    private fun savePosition(mediaId: String?, positionMs: Long) {
        val parts = mediaId?.split('|') ?: return
        if (parts.size != 2) return
        val timelineId = parts[0]
        val chapterIndex = parts[1].toIntOrNull() ?: return
        when {
            timelineId == QuranAudioRepository.MAIN_TIMELINE ||
                timelineId == QuranAudioRepository.SIDE_TIMELINE -> {
                QuranAudioRepository.saveProgress(this, timelineId, chapterIndex, positionMs)
            }
            timelineId.startsWith("book:") -> {
                AudioBookRepository.saveProgress(
                    this, timelineId.removePrefix("book:"), chapterIndex, positionMs
                )
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        handler.removeCallbacks(positionUpdater)
        savePositionNow()
        player?.release()
        mediaSession?.release()
        mediaSession = null
        player = null
        super.onDestroy()
    }

    companion object {
        private const val SAVE_INTERVAL_MS = 1_000L
    }
}
