@file:OptIn(ExperimentalMaterial3Api::class)

package ru.minutes.assistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.minutes.assistant.data.Status

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val color = MaterialTheme.colorScheme.surfaceVariant
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = color) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun StatusBadge(status: Status, modifier: Modifier = Modifier) {
    when {
        status == Status.DONE -> Unit
        status.isBusy -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
            Text(status.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        status == Status.ERROR -> Text(
            "Ошибка", modifier, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error,
        )
        status == Status.RECORDING -> Text(
            "Запись", modifier, style = MaterialTheme.typography.labelMedium, color = Accent,
        )
        else -> Text(
            "Без протокола", modifier, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Живая волна уровня звука: новые значения приходят справа. */
@Composable
fun Waveform(levels: List<Float>, modifier: Modifier = Modifier, color: Color = Accent, bars: Int = 56) {
    Canvas(modifier) {
        val gap = 4.dp.toPx()
        val barW = (size.width - gap * (bars - 1)) / bars
        val minH = 4.dp.toPx()
        val visible = levels.takeLast(bars)
        val padded = List(bars - visible.size) { 0f } + visible
        padded.forEachIndexed { i, v ->
            val h = minH + v * (size.height - minH)
            drawRoundRect(
                color = color.copy(alpha = if (v > 0.02f) 1f else 0.35f),
                topLeft = Offset(i * (barW + gap), (size.height - h) / 2),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2, barW / 2),
            )
        }
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    AppCard(Modifier.padding(0.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Accent,
        )
        Spacer(Modifier.size(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun BulletList(items: List<String>) {
    items.forEach { item ->
        Row {
            Text("•  ", style = MaterialTheme.typography.bodyMedium)
            Text(item, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
