package ru.minutes.assistant.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import ru.minutes.assistant.data.AudioPart
import java.io.File

/** Плеер, склеивающий части записи в одну непрерывную дорожку с «глобальным» временем. */
class PlayerController(context: Context, parts: List<AudioPart>) {

    private val starts: LongArray
    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    init {
        var acc = 0L
        starts = LongArray(parts.size) { i ->
            val s = acc
            acc += parts[i].durationMs
            s
        }
        player.setMediaItems(parts.map { MediaItem.fromUri(Uri.fromFile(File(it.path))) })
        player.prepare()
    }

    val isPlaying: Boolean get() = player.isPlaying

    fun globalPosition(): Long {
        if (starts.isEmpty()) return 0
        val i = player.currentMediaItemIndex.coerceIn(0, starts.lastIndex)
        return starts[i] + player.currentPosition.coerceAtLeast(0)
    }

    fun toggle() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0, 0)
            player.play()
        }
    }

    fun seekGlobal(ms: Long) {
        if (starts.isEmpty()) return
        val i = starts.indexOfLast { it <= ms }.coerceAtLeast(0)
        player.seekTo(i, (ms - starts[i]).coerceAtLeast(0))
    }

    fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)

    fun release() = player.release()
}
