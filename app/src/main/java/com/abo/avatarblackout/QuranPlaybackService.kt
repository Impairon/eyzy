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
                val timelineId = mediaItem?.mediaId?.substringBefore('|')
                if (timelineId == QuranAudioRepository.MAIN_TIMELINE ||
                    timelineId == QuranAudioRepository.SIDE_TIMELINE
                ) {
                    QuranAudioRepository.setActiveTimeline(this@QuranPlaybackService, timelineId)
                }
                savePositionNow()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
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
        val mediaId = current.currentMediaItem?.mediaId ?: return
        val parts = mediaId.split('|')
        if (parts.size != 2) return
        val timelineId = parts[0]
        if (timelineId != QuranAudioRepository.MAIN_TIMELINE &&
            timelineId != QuranAudioRepository.SIDE_TIMELINE
        ) return
        val chapterIndex = parts[1].toIntOrNull() ?: return
        QuranAudioRepository.saveProgress(
            this, timelineId, chapterIndex, current.currentPosition
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        handler.removeCallbacks(positionUpdater)
        savePositionNow()
        mediaSession?.run {
            player?.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    companion object {
        private const val SAVE_INTERVAL_MS = 1_000L
    }
}
