package ru.minutes.assistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.minutes.assistant.data.Meeting
import ru.minutes.assistant.data.MeetingItem
import ru.minutes.assistant.data.Protocol
import ru.minutes.assistant.graph

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.graph.repo

    val query = MutableStateFlow("")

    val items: StateFlow<List<MeetingItem>> =
        combine(repo.observeItems(), query) { list, q ->
            val s = q.trim()
            if (s.isEmpty()) list
            else list.filter { it.title.contains(s, ignoreCase = true) || it.protocolJson.contains(s, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

class MeetingViewModel(app: Application, private val id: Long) : AndroidViewModel(app) {
    private val repo = app.graph.repo

    val meeting: StateFlow<Meeting?> =
        repo.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(title: String) = viewModelScope.launch { repo.rename(id, title) }
    fun process() = viewModelScope.launch { repo.process(id) }
    fun retranscribe() = viewModelScope.launch { repo.retranscribe(id) }
    fun regenerate(template: String) = viewModelScope.launch { repo.regenerate(id, template) }
    fun toggleAction(index: Int) = viewModelScope.launch { repo.toggleAction(id, index) }
    fun saveProtocol(p: Protocol) = viewModelScope.launch { repo.saveProtocol(id, p) }
    fun renameSpeaker(raw: String, name: String) = viewModelScope.launch { repo.renameSpeaker(id, raw, name) }
    fun editSegment(index: Int, text: String, speaker: String, newName: String?) =
        viewModelScope.launch { repo.editSegment(id, index, text, speaker, newName) }
    fun deleteSegment(index: Int) = viewModelScope.launch { repo.deleteSegment(id, index) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repo.delete(id)
        onDone()
    }
}
