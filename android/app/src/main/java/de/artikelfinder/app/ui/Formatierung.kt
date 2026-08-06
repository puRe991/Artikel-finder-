package de.artikelfinder.app.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Deutsch = Locale.GERMANY

private val Waehrung: NumberFormat = NumberFormat.getCurrencyInstance(Deutsch)

private val DatumFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy", Deutsch).withZone(ZoneId.systemDefault())

private val DatumZeitFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Deutsch).withZone(ZoneId.systemDefault())

fun Double.alsPreis(): String = Waehrung.format(this)

/**
 * Formatiert einen ISO-8601-Zeitstempel der API. Bei unerwarteten Werten wird der Rohwert
 * durchgereicht — ein Datumsfehler soll keinen Bildschirm zum Absturz bringen.
 */
fun String.alsDatum(): String = formatieren(DatumFormat)

fun String.alsDatumZeit(): String = formatieren(DatumZeitFormat)

private fun String.formatieren(format: DateTimeFormatter): String =
    runCatching { format.format(Instant.parse(this)) }
        .recoverCatching { format.format(java.time.OffsetDateTime.parse(this).toInstant()) }
        .getOrDefault(this)

/** Menschenlesbarer Gültigkeitszeitraum einer Werbeaktion. */
fun werbezeitraumText(von: String?, bis: String?): String? = when {
    von != null && bis != null -> "${von.alsDatum()} – ${bis.alsDatum()}"
    bis != null -> "bis ${bis.alsDatum()}"
    von != null -> "ab ${von.alsDatum()}"
    else -> null
}
