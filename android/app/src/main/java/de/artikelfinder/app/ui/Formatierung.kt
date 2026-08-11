package de.artikelfinder.app.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
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

fun Long.alsDatum(): String = DatumFormat.format(Instant.ofEpochMilli(this))

fun Long.alsDatumZeit(): String = DatumZeitFormat.format(Instant.ofEpochMilli(this))

/**
 * Ein ISO-Datum aus der Katalogdatei ("2026-03-14") in deutscher Schreibweise. Unlesbare
 * Angaben werden verschwiegen statt roh angezeigt — der Stand ist eine Nebeninformation.
 */
fun String.alsIsoDatum(): String? = runCatching {
    DatumFormat.format(LocalDate.parse(this).atStartOfDay(ZoneId.systemDefault()))
}.getOrNull()

/** Menschenlesbarer Gültigkeitszeitraum einer Werbeaktion. */
fun werbezeitraumText(von: Long?, bis: Long?): String? = when {
    von != null && bis != null -> "${von.alsDatum()} – ${bis.alsDatum()}"
    bis != null -> "bis ${bis.alsDatum()}"
    von != null -> "ab ${von.alsDatum()}"
    else -> null
}
