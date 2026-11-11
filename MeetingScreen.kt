@file:OptIn(ExperimentalMaterial3Api::class)

package ru.minutes.assistant.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.minutes.assistant.ai.MeetingTemplate
import ru.minutes.assistant.audio.PlayerController
import ru.minutes.assistant.data.Meeting
import ru.minutes.assistant.data.Protocol
import ru.minutes.assistant.data.Segment
import ru.minutes.assistant.data.Status
import ru.minutes.assistant.data.bookmarks
import ru.minutes.assistant.data.parseProtocolOrNull
import ru.minutes.assistant.data.parts
import ru.minutes.assistant.data.segments
import ru.minutes.assistant.data.speakerLabel
import ru.minutes.assistant.data.speakerNames
import ru.minutes.assistant.data.statusEnum
import ru.minutes.assistant.fmtDate
import ru.minutes.assistant.fmtDuration
import ru.minutes.assistant.graph
import java.io.File

private enum class Dlg { NONE, RENAME, DELETE, REGEN }

private val SPEEDS = listOf(1f, 1.5f, 2f, 0.75f)

@Composable
fun MeetingScreen(id: Long, onBack: () -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val vm: MeetingViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MeetingViewModel(ctx.applicationContext as Application, id) }
        },
    )
    val meetingState by vm.meeting.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(Dlg.NONE) }
    var pos by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var speedIdx by remember { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf(false) }

    val meeting = meetingState
    if (meeting == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val parts = remember(meeting.partsJson) { meeting.parts.filter { File(it.path).exists() } }
    val player = remember(parts) { if (parts.isEmpty()) null else PlayerController(ctx, parts) }
    DisposableEffect(player) { onDispose { player?.release() } }
    LaunchedEffect(player) {
        while (true) {
            if (player != null) {
                pos = player.globalPosition()
                playing = player.isPlaying
            }
            delay(250)
        }
    }

    val protocol = remember(meeting.protocolJson) { parseProtocolOrNull(meeting.protocolJson) }
    val segments = remember(meeting.segmentsJson) { meeting.segments }
    val bookmarks = remember(meeting.bookmarksJson) { meeting.bookmarks }
    val names = remember(meeting.speakersJson) { meeting.speakerNames }
    val status = meeting.statusEnum
    val total = maxOf(meeting.durationMs, 1L)

    val seek: (Long) -> Unit = { ms ->
        player?.seekGlobal(ms)
        pos = ms
    }

    fun copy(label: String, text: String) {
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
    }

    fun requestProcess() {
        val s = ctx.graph.settings.current()
        if (s.sttReady && s.llmReady) {
            vm.process()
        } else {
            Toast.makeText(ctx, "Сначала укажите ключи в настройках", Toast.LENGTH_LONG).show()
            onSettings()
        }
    }

    if (editing) {
        ProtocolEditor(
            initial = protocol ?: Protocol(),
            onSave = { vm.saveProtocol(it); editing = false },
            onCancel = { editing = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(meeting.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                },
                actions = {
                    if (tab == 0 && !status.isBusy && status != Status.RECORDING) {
                        IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.EditNote, "Редактировать протокол") }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Меню") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            MenuRow("Переименовать", Icons.Rounded.Edit) {
                                menuOpen = false; dialog = Dlg.RENAME
                            }
                            MenuRow("Редактировать протокол", Icons.Rounded.EditNote, !status.isBusy) {
                                menuOpen = false; editing = true
                            }
                            MenuRow("Поделиться протоколом", Icons.Rounded.Share, protocol != null) {
                                menuOpen = false
                                protocol?.let { Exporter.shareText(ctx, meeting.title, Exporter.markdown(meeting, it)) }
                            }
                            MenuRow("Экспорт в PDF", Icons.Rounded.PictureAsPdf, protocol != null) {
                                menuOpen = false
                                protocol?.let { p ->
                                    scope.launch {
                                        try {
                                            val file = withContext(Dispatchers.IO) { PdfExporter.export(ctx, meeting, p) }
                                            Exporter.sharePdf(ctx, file, meeting.title)
                                        } catch (e: Exception) {
                                            Toast.makeText(ctx, "Не удалось создать PDF: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                            MenuRow("Копировать протокол", Icons.Rounded.ContentCopy, protocol != null) {
                                menuOpen = false
                                protocol?.let { copy("Протокол", Exporter.markdown(meeting, it)) }
                            }
                            MenuRow("Копировать расшифровку", Icons.Rounded.ContentCopy, segments.isNotEmpty()) {
                                menuOpen = false
                                copy("Расшифровка", Exporter.transcript(meeting, segments))
                            }
                            MenuRow("Пересоздать протокол…", Icons.Rounded.Refresh, segments.isNotEmpty() && !status.isBusy) {
                                menuOpen = false; dialog = Dlg.REGEN
                            }
                            MenuRow("Расшифровать заново", Icons.Rounded.GraphicEq, parts.isNotEmpty() && !status.isBusy) {
                                menuOpen = false; vm.retranscribe()
                            }
                            MenuRow("Удалить", Icons.Rounded.Delete) {
                                menuOpen = false; dialog = Dlg.DELETE
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (player != null) {
                PlayerBar(
                    pos = pos,
                    total = total,
                    playing = playing,
                    speed = SPEEDS[speedIdx],
                    onToggle = { player.toggle() },
                    onSeek = seek,
                    onSpeed = {
                        speedIdx = (speedIdx + 1) % SPEEDS.size
                        player.setSpeed(SPEEDS[speedIdx])
                    },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            StatusPanel(meeting, onProcess = ::requestProcess, onSettings = onSettings)
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Протокол") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Расшифровка") })
            }
            when (tab) {
                0 -> ProtocolTab(meeting, protocol, bookmarks, status, onSeek = seek, onToggle = { vm.toggleAction(it) })
                else -> TranscriptTab(
                    segments = segments,
                    bookmarks = bookmarks,
                    names = names,
                    pos = pos,
                    playing = playing,
                    editable = !status.isBusy,
                    onSeek = seek,
                    onRenameSpeaker = { raw, name -> vm.renameSpeaker(raw, name) },
                    onEditSegment = { i, text, speaker, newName -> vm.editSegment(i, text, speaker, newName) },
                    onDeleteSegment = { vm.deleteSegment(it) },
                )
            }
        }
    }

    when (dialog) {
        Dlg.RENAME -> {
            var text by remember { mutableStateOf(meeting.title) }
            AlertDialog(
                onDismissRequest = { dialog = Dlg.NONE },
                title = { Text("Название встречи") },
                text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        if (text.isNotBlank()) vm.rename(text)
                        dialog = Dlg.NONE
                    }) { Text("Сохранить") }
                },
                dismissButton = { TextButton(onClick = { dialog = Dlg.NONE }) { Text("Отмена") } },
            )
        }
        Dlg.DELETE -> AlertDialog(
            onDismissRequest = { dialog = Dlg.NONE },
            title = { Text("Удалить встречу?") },
            text = { Text("Запись, расшифровка и протокол будут удалены с устройства без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = Dlg.NONE
                    vm.delete(onBack)
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = Dlg.NONE }) { Text("Отмена") } },
        )
        Dlg.REGEN -> {
            var selected by remember { mutableStateOf(meeting.template) }
            AlertDialog(
                onDismissRequest = { dialog = Dlg.NONE },
                title = { Text("Шаблон протокола") },
                text = {
                    Column {
                        Text(
                            "Протокол будет составлен заново, ручные правки в нём пропадут. Имена говорящих и правки расшифровки учитываются.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        MeetingTemplate.entries.forEach { t ->
                            Row(
                                Modifier.fillMaxWidth().clickable { selected = t.id }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected == t.id, onClick = { selected = t.id })
                                Text(t.title)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = Dlg.NONE
                        vm.regenerate(selected)
                    }) { Text("Пересоздать") }
                },
                dismissButton = { TextButton(onClick = { dialog = Dlg.NONE }) { Text("Отмена") } },
            )
        }
        Dlg.NONE -> Unit
    }
}

@Composable
private fun MenuRow(text: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, null) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun PlayerBar(
    pos: Long,
    total: Long,
    playing: Boolean,
    speed: Float,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeed: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Slider(
                value = pos.coerceIn(0L, total).toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..total.toFloat(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggle) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "Пауза" else "Слушать запись",
                    )
                }
                Text(
                    "${fmtDuration(pos)} / ${fmtDuration(total)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSpeed) {
                    Text(if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x")
                }
            }
        }
    }
}

@Composable
private fun StatusPanel(m: Meeting, onProcess: () -> Unit, onSettings: () -> Unit) {
    val status = m.statusEnum
    val total = remember(m.partsJson) { m.parts.size }

    when {
        status == Status.QUEUED -> PanelCard {
            Text("В очереди на обработку", fontWeight = FontWeight.SemiBold)
            Text(
                "Начнём, как только появится сеть.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        status == Status.TRANSCRIBING -> PanelCard {
            Text("Расшифровка записи", fontWeight = FontWeight.SemiBold)
            if (total > 1) {
                Text(
                    "Часть ${minOf(m.doneParts + 1, total)} из $total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (total > 1) {
                LinearProgressIndicator(progress = { m.doneParts.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        status == Status.SUMMARIZING -> PanelCard {
            Text("Составляю протокол", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        status == Status.ERROR -> PanelCard {
            Text("Не удалось обработать запись", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            if (m.error.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(m.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onProcess) { Text("Повторить") }
                OutlinedButton(onClick = onSettings) { Text("Настройки") }
            }
        }

        status == Status.RECORDED -> PanelCard {
            Text("Протокол ещё не создан", fontWeight = FontWeight.SemiBold)
            if (m.error.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(m.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onProcess) { Text("Создать протокол") }
        }

        else -> Unit
    }
}

@Composable
private fun PanelCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        AppCard(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun ProtocolTab(
    m: Meeting,
    p: Protocol?,
    bookmarks: List<Long>,
    status: Status,
    onSeek: (Long) -> Unit,
    onToggle: (Int) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "${fmtDate(m.createdAt)} · ${fmtDuration(m.durationMs)} · ${MeetingTemplate.byId(m.template).title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (bookmarks.isNotEmpty()) {
            item {
                Column {
                    Text("Ваши закладки", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(bookmarks) { ms ->
                            AssistChip(
                                onClick = { onSeek(ms) },
                                label = { Text(fmtDuration(ms)) },
                                leadingIcon = { Icon(Icons.Rounded.Bookmark, null, Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
            }
        }

        if (p == null) {
            item {
                Text(
                    if (status.isBusy) "Протокол появится здесь, когда обработка завершится."
                    else "Протокола пока нет.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@LazyColumn
        }

        if (p.summary.isNotBlank()) item { Section("Резюме") { Text(p.summary, style = MaterialTheme.typography.bodyMedium) } }
        if (p.participants.isNotEmpty()) {
            item { Section("Участники") { Text(p.participants.joinToString(", "), style = MaterialTheme.typography.bodyMedium) } }
        }
        if (p.agenda.isNotEmpty()) item { Section("Повестка") { BulletList(p.agenda) } }
        if (p.keyPoints.isNotEmpty()) {
            item {
                Section("Ключевые вопросы") {
                    p.keyPoints.forEach { k ->
                        Column {
                            Text(k.topic, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            if (k.details.isNotBlank()) {
                                Text(
                                    k.details,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (p.decisions.isNotEmpty()) item { Section("Решения") { BulletList(p.decisions) } }
        if (p.actionItems.isNotEmpty()) {
            item {
                Section("Задачи") {
                    p.actionItems.forEachIndexed { i, a ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Checkbox(checked = a.done, onCheckedChange = { onToggle(i) })
                            Column(Modifier.padding(top = 12.dp)) {
                                Text(
                                    a.task,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (a.done) TextDecoration.LineThrough else null,
                                    color = if (a.done) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                                )
                                val info = listOfNotNull(
                                    a.owner.takeIf { it.isNotBlank() }?.let { "Ответственный: $it" },
                                    a.due.takeIf { it.isNotBlank() }?.let { "Срок: $it" },
                                    when (a.priority.lowercase()) {
                                        "high" -> "Приоритет: высокий"
                                        "medium" -> "Приоритет: средний"
                                        "low" -> "Приоритет: низкий"
                                        else -> null
                                    },
                                ).joinToString(" · ")
                                if (info.isNotEmpty()) {
                                    Text(
                                        info,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (p.openQuestions.isNotEmpty()) item { Section("Открытые вопросы") { BulletList(p.openQuestions) } }
        if (p.nextMeeting.isNotBlank()) {
            item { Section("Следующая встреча") { Text(p.nextMeeting, style = MaterialTheme.typography.bodyMedium) } }
        }
    }
}

private val SpeakerColors = listOf(
    Color(0xFFE5484D), Color(0xFF3E63DD), Color(0xFF30A46C), Color(0xFFF76B15),
    Color(0xFF8E4EC6), Color(0xFF12A594), Color(0xFFD6409F), Color(0xFF978365),
)

@Composable
private fun TranscriptTab(
    segments: List<Segment>,
    bookmarks: List<Long>,
    names: Map<String, String>,
    pos: Long,
    playing: Boolean,
    editable: Boolean,
    onSeek: (Long) -> Unit,
    onRenameSpeaker: (raw: String, name: String) -> Unit,
    onEditSegment: (index: Int, text: String, speaker: String, newName: String?) -> Unit,
    onDeleteSegment: (index: Int) -> Unit,
) {
    if (segments.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
            Text("Расшифровки пока нет.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val speakers = remember(segments) { segments.map { it.speaker }.filter { it.isNotBlank() }.distinct() }
    val colorOf: (String) -> Color = { raw ->
        SpeakerColors[speakers.indexOf(raw).coerceAtLeast(0) % SpeakerColors.size]
    }
    val active = if (pos > 0 || playing) segments.indexOfLast { it.startMs <= pos } else -1
    val marked = remember(segments, bookmarks) {
        bookmarks.map { b -> segments.indexOfLast { it.startMs <= b } }.toSet()
    }

    var renaming by remember { mutableStateOf<String?>(null) }
    var editIdx by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (speakers.isNotEmpty()) {
            item {
                Column(Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                    Text(
                        "Говорящие — нажмите, чтобы задать имя",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(speakers) { raw ->
                            AssistChip(
                                onClick = { renaming = raw },
                                label = { Text(speakerLabel(raw, names)) },
                                leadingIcon = { Icon(Icons.Rounded.Person, null, Modifier.size(18.dp), tint = colorOf(raw)) },
                            )
                        }
                    }
                }
            }
        }
        itemsIndexed(segments) { i, s ->
            val newTurn = s.speaker.isNotBlank() && (i == 0 || segments[i - 1].speaker != s.speaker)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (i == active) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .clickable { onSeek(s.startMs) }
                    .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Text(
                    fmtDuration(s.startMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Accent,
                    modifier = Modifier.width(56.dp).padding(top = 2.dp),
                )
                Column(Modifier.weight(1f)) {
                    if (newTurn) {
                        Text(
                            speakerLabel(s.speaker, names),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colorOf(s.speaker),
                        )
                    }
                    Text(s.text, style = MaterialTheme.typography.bodyMedium)
                }
                if (i in marked) {
                    Icon(Icons.Rounded.Bookmark, "Закладка", Modifier.size(16.dp).align(Alignment.Top), tint = Accent)
                }
                if (editable) {
                    IconButton(onClick = { editIdx = i }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Rounded.Edit, "Править реплику", Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }

    renaming?.let { raw ->
        var text by remember(raw) { mutableStateOf(names[raw].orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Кто это говорит?") },
            text = {
                Column {
                    Text(
                        "Имя заменит «${speakerLabel(raw, names)}» во всей расшифровке и попадёт в протокол при его пересоздании. " +
                            "Если двум меткам задать одно имя, они объединятся.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Имя") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onRenameSpeaker(raw, text); renaming = null }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Отмена") } },
        )
    }

    editIdx?.takeIf { it in segments.indices }?.let { idx ->
        val seg = segments[idx]
        var text by remember(idx) { mutableStateOf(seg.text) }
        var speaker by remember(idx) { mutableStateOf(seg.speaker) }
        var creating by remember(idx) { mutableStateOf(false) }
        var newName by remember(idx) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editIdx = null },
            title = { Text("Реплика ${fmtDuration(seg.startMs)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                    Text("Говорящий", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = !creating && speaker.isBlank(),
                                onClick = { creating = false; speaker = "" },
                                label = { Text("Не указан") },
                            )
                        }
                        items(speakers) { raw ->
                            FilterChip(
                                selected = !creating && speaker == raw,
                                onClick = { creating = false; speaker = raw },
                                label = { Text(speakerLabel(raw, names)) },
                            )
                        }
                        item {
                            FilterChip(
                                selected = creating,
                                onClick = { creating = true },
                                label = { Text("Новый…") },
                            )
                        }
                    }
                    if (creating) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Имя нового говорящего") },
                            singleLine = true,
                        )
                    }
                    TextButton(onClick = { onDeleteSegment(idx); editIdx = null }) {
                        Text("Удалить реплику", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank() && (!creating || newName.isNotBlank()),
                    onClick = {
                        onEditSegment(idx, text, speaker, if (creating) newName else null)
                        editIdx = null
                    },
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editIdx = null }) { Text("Отмена") } },
        )
    }
}
