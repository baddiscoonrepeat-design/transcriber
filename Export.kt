package ru.minutes.assistant.ui

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import ru.minutes.assistant.ai.MeetingTemplate
import ru.minutes.assistant.data.Meeting
import ru.minutes.assistant.data.Protocol
import ru.minutes.assistant.data.Segment
import ru.minutes.assistant.data.formatLine
import ru.minutes.assistant.data.speakerNames
import ru.minutes.assistant.fmtDate
import ru.minutes.assistant.fmtDuration
import java.io.File

private fun priorityRu(p: String) = when (p.lowercase()) {
    "high" -> "высокий"
    "medium" -> "средний"
    "low" -> "низкий"
    else -> ""
}

private fun Protocol.actionMeta(i: Int): String = actionItems[i].let { a ->
    listOfNotNull(
        a.owner.takeIf { it.isNotBlank() }?.let { "ответственный: $it" },
        a.due.takeIf { it.isNotBlank() }?.let { "срок: $it" },
        priorityRu(a.priority).takeIf { it.isNotBlank() }?.let { "приоритет: $it" },
    ).joinToString(" · ")
}

object Exporter {

    fun markdown(m: Meeting, p: Protocol): String = buildString {
        fun list(title: String, items: List<String>) {
            if (items.isEmpty()) return
            appendLine("## $title")
            items.forEach { appendLine("- $it") }
            appendLine()
        }

        appendLine("# ${m.title}")
        appendLine("_${fmtDate(m.createdAt)} · ${fmtDuration(m.durationMs)} · ${MeetingTemplate.byId(m.template).title}_")
        appendLine()
        if (p.summary.isNotBlank()) {
            appendLine("## Резюме"); appendLine(p.summary); appendLine()
        }
        list("Участники", p.participants)
        list("Повестка", p.agenda)
        if (p.keyPoints.isNotEmpty()) {
            appendLine("## Ключевые вопросы")
            p.keyPoints.forEach {
                appendLine("### ${it.topic}")
                if (it.details.isNotBlank()) appendLine(it.details)
                appendLine()
            }
        }
        list("Решения", p.decisions)
        if (p.actionItems.isNotEmpty()) {
            appendLine("## Задачи")
            p.actionItems.forEachIndexed { i, a ->
                val meta = p.actionMeta(i)
                appendLine("- [${if (a.done) "x" else " "}] ${a.task}" + if (meta.isNotEmpty()) " — $meta" else "")
            }
            appendLine()
        }
        list("Открытые вопросы", p.openQuestions)
        if (p.nextMeeting.isNotBlank()) {
            appendLine("## Следующая встреча"); appendLine(p.nextMeeting)
        }
    }.trim()

    fun transcript(m: Meeting, segments: List<Segment>): String =
        "${m.title}\n${fmtDate(m.createdAt)}\n\n" +
            segments.joinToString("\n") { it.formatLine(m.speakerNames) }

    fun shareText(ctx: Context, subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
        ctx.startActivity(Intent.createChooser(send, "Поделиться").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun sharePdf(ctx: Context, file: File, subject: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Отправить PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Рисует протокол в многостраничный A4-PDF средствами Android, без сторонних библиотек. */
object PdfExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48f

    fun export(ctx: Context, m: Meeting, p: Protocol): File {
        val contentW = (PAGE_W - 2 * MARGIN).toInt()

        fun paint(size: Float, bold: Boolean = false, color: Int = 0xFF111113.toInt()) = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        val h1 = paint(20f, bold = true)
        val h2 = paint(12f, bold = true, color = 0xFFE5484D.toInt())
        val body = paint(11f)
        val meta = paint(9.5f, color = 0xFF6B6B73.toInt())

        val doc = PdfDocument()
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas = page.canvas
        var y = MARGIN

        fun newPage() {
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun draw(text: String, paint: TextPaint, spaceBefore: Float = 0f, indent: Float = 0f) {
            if (text.isBlank()) return
            if (y > MARGIN) y += spaceBefore
            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, paint, contentW - indent.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .build()
            var line = 0
            while (line < layout.lineCount) {
                val avail = PAGE_H - MARGIN - y
                var last = line
                while (last < layout.lineCount &&
                    layout.getLineBottom(last) - layout.getLineTop(line) <= avail
                ) last++
                if (last == line) {
                    newPage()
                    continue
                }
                val top = layout.getLineTop(line)
                val bottom = layout.getLineBottom(last - 1)
                canvas.save()
                canvas.translate(MARGIN + indent, y - top)
                canvas.clipRect(0f, top.toFloat(), (contentW - indent), bottom.toFloat())
                layout.draw(canvas)
                canvas.restore()
                y += (bottom - top)
                line = last
                if (line < layout.lineCount) newPage()
            }
        }

        fun section(title: String, items: List<String>) {
            if (items.isEmpty()) return
            draw(title.uppercase(), h2, spaceBefore = 16f)
            items.forEach { draw("•  $it", body, spaceBefore = 3f, indent = 0f) }
        }

        draw(m.title, h1)
        draw("${fmtDate(m.createdAt)} · ${fmtDuration(m.durationMs)} · ${MeetingTemplate.byId(m.template).title}", meta, spaceBefore = 4f)
        if (p.summary.isNotBlank()) {
            draw("РЕЗЮМЕ", h2, spaceBefore = 18f)
            draw(p.summary, body, spaceBefore = 4f)
        }
        section("Участники", listOf(p.participants.joinToString(", ")).filter { it.isNotBlank() })
        section("Повестка", p.agenda)
        if (p.keyPoints.isNotEmpty()) {
            draw("КЛЮЧЕВЫЕ ВОПРОСЫ", h2, spaceBefore = 16f)
            p.keyPoints.forEach {
                draw(it.topic, paint(11f, bold = true), spaceBefore = 8f)
                draw(it.details, body, spaceBefore = 2f)
            }
        }
        section("Решения", p.decisions)
        if (p.actionItems.isNotEmpty()) {
            draw("ЗАДАЧИ", h2, spaceBefore = 16f)
            p.actionItems.forEachIndexed { i, a ->
                val mark = if (a.done) "☑" else "☐"
                draw("$mark  ${a.task}", body, spaceBefore = 5f)
                val info = p.actionMeta(i)
                if (info.isNotEmpty()) draw(info, meta, spaceBefore = 1f, indent = 16f)
            }
        }
        section("Открытые вопросы", p.openQuestions)
        if (p.nextMeeting.isNotBlank()) {
            draw("СЛЕДУЮЩАЯ ВСТРЕЧА", h2, spaceBefore = 16f)
            draw(p.nextMeeting, body, spaceBefore = 4f)
        }

        doc.finishPage(page)

        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val safe = m.title.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').take(40).ifBlank { "protocol" }
        val out = File(dir, "$safe.pdf")
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }
}
