package de.artikelfinder.app.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Preiserfassung direkt am Regal. Bewusst als Dialog und nicht als eigener Bildschirm:
 * der Vorgang dauert wenige Sekunden und soll den Artikel im Blick lassen.
 */
@Composable
fun PreisDialog(
    vorbelegtesAktionsende: Long? = null,
    beiAbbrechen: () -> Unit,
    beiSpeichern: (preis: Double, werbepreis: Double?, gueltigBis: Long?, erfasstVon: String?) -> Unit,
) {
    var preisText by remember { mutableStateOf("") }
    var werbepreisText by remember { mutableStateOf("") }
    // Vorbelegt mit dem zuletzt genutzten Aktionsende: bei einem Prospekt mit 40 Angeboten
    // spart das 40-mal dieselbe Datumseingabe.
    var gueltigBisText by remember {
        mutableStateOf(vorbelegtesAktionsende?.let { alsTagesDatum(it) } ?: "")
    }
    var erfasstVon by remember { mutableStateOf("") }

    val preis = preisText.alsBetrag()
    val werbepreis = werbepreisText.alsBetrag()
    val gueltigBis = gueltigBisText.alsTagesende()

    val fehler = when {
        preisText.isNotBlank() && preis == null -> "Preis konnte nicht gelesen werden."
        werbepreisText.isNotBlank() && werbepreis == null -> "Werbepreis konnte nicht gelesen werden."
        preis != null && werbepreis != null && werbepreis > preis ->
            "Der Werbepreis darf nicht über dem Normalpreis liegen."
        gueltigBisText.isNotBlank() && gueltigBis == null -> "Datum bitte als TT.MM.JJJJ angeben."
        werbepreis == null && gueltigBisText.isNotBlank() ->
            "Ein Aktionszeitraum ohne Werbepreis ergibt keinen Sinn."
        else -> null
    }

    AlertDialog(
        onDismissRequest = beiAbbrechen,
        title = { Text("Preis erfassen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = preisText,
                    onValueChange = { preisText = it },
                    label = { Text("Normalpreis in €") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = werbepreisText,
                    onValueChange = { werbepreisText = it },
                    label = { Text("Werbepreis in € (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = gueltigBisText,
                    onValueChange = { gueltigBisText = it },
                    label = { Text("Aktion gültig bis (TT.MM.JJJJ)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = erfasstVon,
                    onValueChange = { erfasstVon = it },
                    label = { Text("Erfasst von (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                fehler?.let { Text(text = it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = preis != null && fehler == null,
                onClick = {
                    beiSpeichern(preis!!, werbepreis, gueltigBis, erfasstVon.ifBlank { null })
                },
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = beiAbbrechen) { Text("Abbrechen") } },
    )
}

@Composable
fun StandortDialog(
    beiAbbrechen: () -> Unit,
    beiSpeichern: (gang: String, regal: String?, erfasstVon: String?) -> Unit,
) {
    var gang by remember { mutableStateOf("") }
    var regal by remember { mutableStateOf("") }
    var erfasstVon by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = beiAbbrechen,
        title = { Text("Standort erfassen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = gang,
                    onValueChange = { gang = it },
                    label = { Text("Gang") },
                    placeholder = { Text("z. B. 7") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = regal,
                    onValueChange = { regal = it },
                    label = { Text("Regal (optional)") },
                    placeholder = { Text("z. B. links, mittleres Fach") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = erfasstVon,
                    onValueChange = { erfasstVon = it },
                    label = { Text("Erfasst von (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = gang.isNotBlank(),
                onClick = {
                    beiSpeichern(gang.trim(), regal.ifBlank { null }, erfasstVon.ifBlank { null })
                },
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = beiAbbrechen) { Text("Abbrechen") } },
    )
}

/** Millisekunden zurueck in die Eingabeform TT.MM.JJJJ. */
fun alsTagesDatum(zeitpunkt: Long): String =
    java.time.Instant.ofEpochMilli(zeitpunkt)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

/** Akzeptiert Komma und Punkt — auf der deutschen Tastatur liegt das Komma näher. */
fun String.alsBetrag(): Double? = trim()
    .replace(',', '.')
    .takeIf { it.isNotBlank() }
    ?.toDoubleOrNull()
    ?.takeIf { it > 0 }

/**
 * Wandelt "31.12.2026" in Millisekunden seit 1970 um — als Ende des Tages in der
 * Zeitzone des Geräts, damit eine Aktion am angegebenen Datum noch gilt.
 */
fun String.alsTagesende(): Long? = runCatching {
    LocalDate.parse(trim(), DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        .atTime(23, 59, 59)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}.getOrNull()
