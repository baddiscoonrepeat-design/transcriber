@file:OptIn(ExperimentalMaterial3Api::class)

package ru.minutes.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.minutes.assistant.ai.Languages
import ru.minutes.assistant.ai.MeetingTemplate
import ru.minutes.assistant.audio.RecorderService
import ru.minutes.assistant.audio.RecorderState
import ru.minutes.assistant.fmtDuration
import ru.minutes.assistant.graph

@Composable
fun RecordScreen(onBack: () -> Unit, onFinished: (Long) -> Unit) {
    val ctx = LocalContext.current
    val rec by RecorderState.state.collectAsStateWithLifecycle()
    val defaults = remember { ctx.graph.settings.current() }

    var title by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var template by rememberSaveable { mutableStateOf(defaults.template) }
    var language by rememberSaveable { mutableStateOf(defaults.language) }
    var stopping by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        RecorderState.finished.collect {
            stopping = false
            onFinished(it)
        }
    }

    fun launchService() = RecorderService.start(ctx, title.trim(), template, language, notes.trim())

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val micGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (micGranted) launchService()
        else Toast.makeText(ctx, "Для записи нужен доступ к микрофону", Toast.LENGTH_LONG).show()
    }

    fun startRecording() {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) launchService() else permissionLauncher.launch(needed.toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (rec.active) "Запись" else "Новая запись") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (rec.active) {
                ActiveRecording(
                    state = rec,
                    stopping = stopping,
                    onPauseToggle = {
                        RecorderService.send(ctx, if (rec.paused) RecorderService.ACTION_RESUME else RecorderService.ACTION_PAUSE)
                    },
                    onBookmark = { RecorderService.send(ctx, RecorderService.ACTION_BOOKMARK) },
                    onStop = {
                        stopping = true
                        RecorderService.send(ctx, RecorderService.ACTION_STOP)
                    },
                )
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Название (необязательно)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Участники и контекст") },
                        placeholder = { Text("Например: Иван (продажи), Мария (юрист); обсуждаем договор") },
                        minLines = 2,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("Шаблон протокола", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MeetingTemplate.entries.toList()) { t ->
                            FilterChip(
                                selected = template == t.id,
                                onClick = { template = t.id },
                                label = { Text(t.title) },
                            )
                        }
                    }

                    Text("Язык записи", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Languages.forEach { l ->
                            FilterChip(
                                selected = language == l.code,
                                onClick = { language = l.code },
                                label = { Text(l.title) },
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        RoundButton(
                            onClick = ::startRecording,
                            container = Accent,
                            content = Color.White,
                            size = 88.dp,
                            icon = Icons.Rounded.Mic,
                            description = "Начать запись",
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Нажмите, чтобы начать",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ActiveRecording(
    state: RecorderState.State,
    stopping: Boolean,
    onPauseToggle: () -> Unit,
    onBookmark: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            fmtDuration(state.elapsedMs),
            style = TextStyle(
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                fontFeatureSettings = "tnum",
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                stopping -> "Сохраняю запись…"
                state.paused -> "Пауза"
                else -> "Идёт запись"
            },
            color = if (state.paused || stopping) MaterialTheme.colorScheme.onSurfaceVariant else Accent,
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(32.dp))
        Waveform(state.levels, Modifier.fillMaxWidth().height(96.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            if (state.bookmarks.isEmpty()) "Отмечайте важные моменты закладкой — они попадут в протокол"
            else "Закладок: ${state.bookmarks.size} · последняя на ${fmtDuration(state.bookmarks.last())}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(
                onClick = onBookmark,
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurface,
                size = 60.dp,
                icon = Icons.Rounded.BookmarkAdd,
                description = "Закладка",
                enabled = !stopping,
            )
            RoundButton(
                onClick = onStop,
                container = Accent,
                content = Color.White,
                size = 88.dp,
                icon = Icons.Rounded.Stop,
                description = "Остановить запись",
                enabled = !stopping,
            )
            RoundButton(
                onClick = onPauseToggle,
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurface,
                size = 60.dp,
                icon = if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                description = if (state.paused) "Продолжить" else "Пауза",
                enabled = !stopping,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Приложение можно свернуть — запись продолжится в фоне",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun RoundButton(
    onClick: () -> Unit,
    container: Color,
    content: Color,
    size: Dp,
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(size * 0.42f))
        }
    }
}
