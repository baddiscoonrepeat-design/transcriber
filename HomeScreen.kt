@file:OptIn(ExperimentalMaterial3Api::class)

package ru.minutes.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.minutes.assistant.ai.MeetingTemplate
import ru.minutes.assistant.audio.RecorderState
import ru.minutes.assistant.data.MeetingItem
import ru.minutes.assistant.data.Status
import ru.minutes.assistant.data.parseProtocolOrNull
import ru.minutes.assistant.data.statusEnum
import ru.minutes.assistant.fmtDate
import ru.minutes.assistant.fmtDuration

@Composable
fun HomeScreen(
    onOpen: (Long) -> Unit,
    onRecord: () -> Unit,
    onSettings: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val rec by RecorderState.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Протоколы", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Настройки") }
                },
            )
        },
        floatingActionButton = {
            if (!rec.active) {
                ExtendedFloatingActionButton(
                    onClick = onRecord,
                    containerColor = Accent,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Rounded.Mic, null) },
                    text = { Text("Запись", fontWeight = FontWeight.SemiBold) },
                )
            }
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding() + 8.dp,
                bottom = pad.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text("Поиск по встречам") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (rec.active) {
                item("recording") { RecordingBanner(rec.elapsedMs, rec.paused, onRecord) }
            }
            if (items.isEmpty()) {
                item("empty") { EmptyState(searching = query.isNotBlank()) }
            }
            items(items, key = { it.id }) { MeetingRow(it, onOpen) }
        }
    }
}

@Composable
private fun RecordingBanner(elapsedMs: Long, paused: Boolean, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(Accent, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(
                if (paused) "Запись на паузе" else "Идёт запись",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(fmtDuration(elapsedMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(searching: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = 64.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.GraphicEq, null,
            Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (searching) "Ничего не найдено" else "Пока нет записей",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (searching) "Попробуйте другой запрос."
            else "Нажмите «Запись» — приложение расшифрует разговор и составит протокол с решениями и задачами.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MeetingRow(item: MeetingItem, onOpen: (Long) -> Unit) {
    val status = item.statusEnum
    val summary = remember(item.protocolJson) { parseProtocolOrNull(item.protocolJson)?.summary.orEmpty() }

    AppCard(Modifier.fillMaxWidth(), onClick = { onOpen(item.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            StatusBadge(status)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${fmtDate(item.createdAt)} · ${fmtDuration(item.durationMs)} · ${MeetingTemplate.byId(item.template).title}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            summary.isNotBlank() -> {
                Spacer(Modifier.height(8.dp))
                Text(summary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            status == Status.ERROR && item.error.isNotBlank() -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    item.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
