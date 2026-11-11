@file:OptIn(ExperimentalMaterial3Api::class)

package ru.minutes.assistant.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ru.minutes.assistant.ai.Languages
import ru.minutes.assistant.ai.MeetingTemplate
import ru.minutes.assistant.data.AiSettings
import ru.minutes.assistant.graph

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ctx.graph.settings }
    val initial = remember { store.current() }

    var sttUrl by rememberSaveable { mutableStateOf(initial.sttBaseUrl) }
    var sttKey by rememberSaveable { mutableStateOf(initial.sttKey) }
    var sttModel by rememberSaveable { mutableStateOf(initial.sttModel) }
    var provider by rememberSaveable { mutableStateOf(initial.llmProvider) }
    var llmUrl by rememberSaveable { mutableStateOf(initial.llmBaseUrl) }
    var llmKey by rememberSaveable { mutableStateOf(initial.llmKey) }
    var llmModel by rememberSaveable { mutableStateOf(initial.llmModel) }
    var language by rememberSaveable { mutableStateOf(initial.language) }
    var template by rememberSaveable { mutableStateOf(initial.template) }
    var autoProcess by rememberSaveable { mutableStateOf(initial.autoProcess) }
    var diarize by rememberSaveable { mutableStateOf(initial.diarize) }
    var diarizeModel by rememberSaveable { mutableStateOf(initial.diarizeModel) }

    fun pickProvider(p: String) {
        if (p == provider) return
        provider = p
        if (p == AiSettings.PROVIDER_ANTHROPIC) {
            llmUrl = "https://api.anthropic.com"
            llmModel = "claude-sonnet-5-5"
        } else {
            llmUrl = "https://api.openai.com/v1"
            llmModel = "gpt-4o-mini"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Ключи хранятся на устройстве в зашифрованном виде. Аудио и текст встреч отправляются только тем сервисам, адреса которых указаны ниже.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Header("Расшифровка речи")
            Text(
                "Любой сервис с OpenAI-совместимым методом /audio/transcriptions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Field("Адрес API", sttUrl) { sttUrl = it }
            SecretField("API-ключ", sttKey) { sttKey = it }
            Field("Модель", sttModel) { sttModel = it }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Разделять по говорящим", fontWeight = FontWeight.Medium)
                    Text(
                        "Расшифровка с подписями «Спикер A / B…», имена можно задать вручную. " +
                            "Нужна модель с диаризацией (по умолчанию gpt-4o-transcribe-diarize у OpenAI) " +
                            "или совместимый сервис, возвращающий поле speaker.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = diarize, onCheckedChange = { diarize = it })
            }
            if (diarize) Field("Модель с диаризацией", diarizeModel) { diarizeModel = it }

            Header("Составление протокола")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = provider == AiSettings.PROVIDER_OPENAI,
                    onClick = { pickProvider(AiSettings.PROVIDER_OPENAI) },
                    label = { Text("OpenAI-совместимый") },
                )
                FilterChip(
                    selected = provider == AiSettings.PROVIDER_ANTHROPIC,
                    onClick = { pickProvider(AiSettings.PROVIDER_ANTHROPIC) },
                    label = { Text("Claude (Anthropic)") },
                )
            }
            Field("Адрес API", llmUrl) { llmUrl = it }
            SecretField("API-ключ (пусто — использовать ключ расшифровки)", llmKey) { llmKey = it }
            Field("Модель", llmModel) { llmModel = it }

            Header("По умолчанию")
            Text("Язык записи", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Languages.forEach { l ->
                    FilterChip(selected = language == l.code, onClick = { language = l.code }, label = { Text(l.title) })
                }
            }
            Text("Шаблон протокола", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MeetingTemplate.entries.toList()) { t ->
                    FilterChip(selected = template == t.id, onClick = { template = t.id }, label = { Text(t.title) })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Обрабатывать сразу после записи", fontWeight = FontWeight.Medium)
                    Text(
                        "Расшифровка и протокол запустятся автоматически",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autoProcess, onCheckedChange = { autoProcess = it })
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    store.save(
                        AiSettings(
                            sttBaseUrl = sttUrl, sttKey = sttKey, sttModel = sttModel,
                            llmProvider = provider, llmBaseUrl = llmUrl, llmKey = llmKey, llmModel = llmModel,
                            language = language, template = template, autoProcess = autoProcess,
                            diarize = diarize, diarizeModel = diarizeModel,
                        )
                    )
                    Toast.makeText(ctx, "Сохранено", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Сохранить") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Показать ключ")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
