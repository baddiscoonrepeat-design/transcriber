@file:OptIn(ExperimentalMaterial3Api::class)

package ru.minutes.assistant.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.minutes.assistant.data.ActionItem
import ru.minutes.assistant.data.AppJson
import ru.minutes.assistant.data.KeyPoint
import ru.minutes.assistant.data.Protocol
import ru.minutes.assistant.data.toJson

private val ProtocolSaver = Saver<Protocol, String>(
    save = { it.toJson() },
    restore = { runCatching { AppJson.decodeFromString<Protocol>(it) }.getOrNull() },
)

/** Убирает пустые строки и пустые элементы, чтобы в протокол не попадал мусор. */
private fun Protocol.cleaned(): Protocol = copy(
    summary = summary.trim(),
    nextMeeting = nextMeeting.trim(),
    participants = participants.map { it.trim() }.filter { it.isNotEmpty() },
    agenda = agenda.map { it.trim() }.filter { it.isNotEmpty() },
    decisions = decisions.map { it.trim() }.filter { it.isNotEmpty() },
    openQuestions = openQuestions.map { it.trim() }.filter { it.isNotEmpty() },
    keyPoints = keyPoints
        .map { it.copy(topic = it.topic.trim(), details = it.details.trim()) }
        .filter { it.topic.isNotEmpty() || it.details.isNotEmpty() },
    actionItems = actionItems
        .map { it.copy(task = it.task.trim(), owner = it.owner.trim(), due = it.due.trim()) }
        .filter { it.task.isNotEmpty() },
)

/** Полноэкранный редактор протокола: все разделы можно править, добавлять и удалять. */
@Composable
fun ProtocolEditor(initial: Protocol, onSave: (Protocol) -> Unit, onCancel: () -> Unit) {
    var draft by rememberSaveable(stateSaver = ProtocolSaver) { mutableStateOf(initial) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val changed = draft.cleaned() != initial.cleaned()
    fun tryCancel() {
        if (changed) confirmDiscard = true else onCancel()
    }
    BackHandler { tryCancel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактирование") },
                navigationIcon = {
                    IconButton(onClick = ::tryCancel) { Icon(Icons.Rounded.Close, "Закрыть без сохранения") }
                },
                actions = {
                    TextButton(onClick = { onSave(draft.cleaned()) }, enabled = changed) { Text("Сохранить") }
                },
            )
        },
        modifier = Modifier.imePadding(),
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Block("Резюме") {
                    OutlinedTextField(
                        value = draft.summary,
                        onValueChange = { draft = draft.copy(summary = it) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        minLines = 3,
                    )
                }
            }
            item {
                StringListBlock("Участники", "Добавить участника", draft.participants) {
                    draft = draft.copy(participants = it)
                }
            }
            item {
                StringListBlock("Повестка", "Добавить пункт", draft.agenda) { draft = draft.copy(agenda = it) }
            }
            item {
                Block("Ключевые вопросы") {
                    draft.keyPoints.forEachIndexed { i, k ->
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = k.topic,
                                    onValueChange = { v -> draft = draft.copy(keyPoints = draft.keyPoints.set(i, k.copy(topic = v))) },
                                    label = { Text("Тема") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                )
                                OutlinedTextField(
                                    value = k.details,
                                    onValueChange = { v -> draft = draft.copy(keyPoints = draft.keyPoints.set(i, k.copy(details = v))) },
                                    label = { Text("Что обсуждали") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    minLines = 2,
                                )
                            }
                            RemoveButton { draft = draft.copy(keyPoints = draft.keyPoints.removeAt(i)) }
                        }
                    }
                    AddButton("Добавить вопрос") { draft = draft.copy(keyPoints = draft.keyPoints + KeyPoint()) }
                }
            }
            item {
                StringListBlock("Решения", "Добавить решение", draft.decisions) { draft = draft.copy(decisions = it) }
            }
            item {
                Block("Задачи") {
                    draft.actionItems.forEachIndexed { i, a ->
                        ActionEditor(
                            a,
                            onChange = { v -> draft = draft.copy(actionItems = draft.actionItems.set(i, v)) },
                            onRemove = { draft = draft.copy(actionItems = draft.actionItems.removeAt(i)) },
                        )
                    }
                    AddButton("Добавить задачу") { draft = draft.copy(actionItems = draft.actionItems + ActionItem()) }
                }
            }
            item {
                StringListBlock("Открытые вопросы", "Добавить вопрос", draft.openQuestions) {
                    draft = draft.copy(openQuestions = it)
                }
            }
            item {
                Block("Следующая встреча") {
                    OutlinedTextField(
                        value = draft.nextMeeting,
                        onValueChange = { draft = draft.copy(nextMeeting = it) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Отменить изменения?") },
            text = { Text("Внесённые правки не будут сохранены.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onCancel() }) {
                    Text("Отменить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Продолжить правку") } },
        )
    }
}

@Composable
private fun ActionEditor(a: ActionItem, onChange: (ActionItem) -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = a.task,
                onValueChange = { onChange(a.copy(task = it)) },
                label = { Text("Задача") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                minLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = a.owner,
                    onValueChange = { onChange(a.copy(owner = it)) },
                    label = { Text("Ответственный") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = a.due,
                    onValueChange = { onChange(a.copy(due = it)) },
                    label = { Text("Срок") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("high" to "Высокий", "medium" to "Средний", "low" to "Низкий").forEach { (code, title) ->
                    FilterChip(
                        selected = a.priority.lowercase() == code,
                        onClick = { onChange(a.copy(priority = if (a.priority.lowercase() == code) "" else code)) },
                        label = { Text(title) },
                    )
                }
            }
        }
        RemoveButton(onRemove)
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Accent)
        content()
    }
}

@Composable
private fun StringListBlock(title: String, addLabel: String, items: List<String>, onChange: (List<String>) -> Unit) {
    Block(title) {
        items.forEachIndexed { i, text ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { onChange(items.set(i, it)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                )
                RemoveButton { onChange(items.removeAt(i)) }
            }
        }
        AddButton(addLabel) { onChange(items + "") }
    }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Rounded.Delete, "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Rounded.Add, null)
        Spacer(Modifier.padding(start = 6.dp))
        Text(label)
    }
}

private fun <T> List<T>.set(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }
private fun <T> List<T>.removeAt(index: Int): List<T> = toMutableList().also { it.removeAt(index) }
