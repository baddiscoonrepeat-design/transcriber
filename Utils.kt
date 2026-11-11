package ru.minutes.assistant

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%02d:%02d", m, s)
}

private val ruLocale = Locale("ru")

fun fmtDate(ts: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm", ruLocale).format(Date(ts))

fun fmtDateFull(ts: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm (EEEE)", ruLocale).format(Date(ts))

fun defaultTitle(ts: Long): String = "Встреча " + SimpleDateFormat("dd.MM HH:mm", ruLocale).format(Date(ts))
