package ru.minutes.assistant.audio

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Состояние записи, которое сервис публикует, а экраны только читают. */
object RecorderState {
    data class State(
        val active: Boolean = false,
        val paused: Boolean = false,
        val elapsedMs: Long = 0,
        val levels: List<Float> = emptyList(),
        val bookmarks: List<Long> = emptyList(),
    )

    val state = MutableStateFlow(State())

    /** Эмитит id готовой встречи; 0 — запись оказалась пустой и удалена. */
    val finished = MutableSharedFlow<Long>(extraBufferCapacity = 1)
}
