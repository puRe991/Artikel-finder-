package de.artikelfinder.app.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Deutsch = Locale.GERMANY

private val Waehrung: NumberFormat = NumberFormat.getCurrencyInstance(Deutsch)

private val Anzahl: NumberFormat = NumberFormat.getNumberInstance(Deutsch).apply {
    maximumFractionDigits = 1
    minimumFractionDigits = 0
}

private val DatumFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy", Deutsch).withZone(ZoneId.systemDefault())

private val DatumZeitFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Deutsch).withZone(ZoneId.systemDefault())

fun Double.alsPreis(): String = Waehrung.format(this)

/** Gerundete Stückzahl in deutscher Schreibweise, etwa „8,3". */
fun Double.alsAnzahl(): String = Anzahl.format(this)

fun Long.alsDatum(): String = DatumFormat.format(Instant.ofEpochMilli(this))

fun Long.alsDatumZeit(): String = DatumZeitFormat.format(Instant.ofEpochMilli(this))

/** Menschenlesbarer Gültigkeitszeitraum einer Werbeaktion. */
fun werbezeitraumText(von: Long?, bis: Long?): String? = when {
    von != null && bis != null -> "${von.alsDatum()} – ${bis.alsDatum()}"
    bis != null -> "bis ${bis.alsDatum()}"
    von != null -> "ab ${von.alsDatum()}"
    else -> null
}
