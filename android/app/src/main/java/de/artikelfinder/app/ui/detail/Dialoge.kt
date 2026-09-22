package de.artikelfinder.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import de.artikelfinder.app.ui.komponenten.Eingabe
import de.artikelfinder.app.ui.theme.Abstand
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
    vorbelegterPreis: Double? = null,
    beiAbbrechen: () -> Unit,
    beiSpeichern: (preis: Double, werbepreis: Double?, gueltigBis: Long?, erfasstVon: String?) -> Unit,
) {
    // Bestehenden Preis vorbelegen, damit „ändern" heißt: den Wert sehen und anpassen.
    var preisText by remember { mutableStateOf(vorbelegterPreis?.alsEingabe() ?: "") }
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
        icon = { Icon(Icons.Outlined.Euro, contentDescription = null) },
        title = { Text(if (vorbelegterPreis != null) "Preis ändern" else "Preis erfassen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Abstand.eng)) {
                    Eingabe(
                        wert = preisText,
                        beiAenderung = { preisText = it },
                        bezeichnung = "Normalpreis",
                        suffix = "€",
                        tastatur = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    Eingabe(
                        wert = werbepreisText,
                        beiAenderung = { werbepreisText = it },
                        bezeichnung = "Werbepreis",
                        suffix = "€",
                        tastatur = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                Eingabe(
                    wert = gueltigBisText,
                    beiAenderung = { gueltigBisText = it },
                    bezeichnung = "Aktion gültig bis",
                    platzhalter = "TT.MM.JJJJ",
                )
                Eingabe(
                    wert = erfasstVon,
                    beiAenderung = { erfasstVon = it },
                    bezeichnung = "Erfasst von (optional)",
                )
                fehler?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
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
        icon = { Icon(Icons.Outlined.Place, contentDescription = null) },
        title = { Text("Standort erfassen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
                Eingabe(
                    wert = gang,
                    beiAenderung = { gang = it },
                    bezeichnung = "Gang",
                    platzhalter = "z. B. 7",
                )
                Eingabe(
                    wert = regal,
                    beiAenderung = { regal = it },
                    bezeichnung = "Regal (optional)",
                    platzhalter = "z. B. links, mittleres Fach",
                    einzeilig = false,
                )
                Eingabe(
                    wert = erfasstVon,
                    beiAenderung = { erfasstVon = it },
                    bezeichnung = "Erfasst von (optional)",
                )
            }
        },
        confirmButton = {
            Button(
                enabled = gang.isNotBlank(),
                onClick = {
                    beiSpeichern(gang.trim(), regal.ifBlank { null }, erfasstVon.ifBlank { null })
                },
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = beiAbbrechen) { Text("Abbrechen") } },
    )
}

/**
 * Einen gekauften Artikel in den Vorrat buchen. Menge und Stückpreis sind vorbelegt: eine
 * Packung zum zuletzt bekannten Preis ist der Normalfall, alles andere lässt sich anpassen.
 */
@Composable
fun EinkaufDialog(
    vorbelegterStueckpreis: Double? = null,
    beiAbbrechen: () -> Unit,
    beiSpeichern: (menge: Int, stueckpreis: Double?) -> Unit,
) {
    var mengeText by remember { mutableStateOf("1") }
    var preisText by remember { mutableStateOf(vorbelegterStueckpreis?.alsEingabe() ?: "") }

    val menge = mengeText.trim().toIntOrNull()
    val preis = preisText.alsBetrag()

    val fehler = when {
        menge == null || menge <= 0 -> "Bitte eine Menge von mindestens 1 angeben."
        preisText.isNotBlank() && preis == null -> "Stückpreis konnte nicht gelesen werden."
        else -> null
    }

    AlertDialog(
        onDismissRequest = beiAbbrechen,
        icon = { Icon(Icons.Outlined.ShoppingCart, contentDescription = null) },
        title = { Text("Gekauft erfassen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Abstand.eng)) {
                    Eingabe(
                        wert = mengeText,
                        beiAenderung = { mengeText = it.filter(Char::isDigit) },
                        bezeichnung = "Menge",
                        suffix = "Stk.",
                        tastatur = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    Eingabe(
                        wert = preisText,
                        beiAenderung = { preisText = it },
                        bezeichnung = "Stückpreis",
                        suffix = "€",
                        tastatur = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                fehler?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(enabled = fehler == null, onClick = { beiSpeichern(menge!!, preis) }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = beiAbbrechen) { Text("Abbrechen") } },
    )
}

/** Den Bestand von Hand auf einen genauen Wert setzen — etwa nach dem Nachzählen im Schrank. */
@Composable
fun BestandKorrekturDialog(
    aktuellerBestand: Int,
    beiAbbrechen: () -> Unit,
    beiSpeichern: (neueMenge: Int) -> Unit,
) {
    var mengeText by remember { mutableStateOf(aktuellerBestand.toString()) }
    val menge = mengeText.trim().toIntOrNull()
    val fehler = if (menge == null || menge < 0) "Bitte eine Zahl ab 0 angeben." else null

    AlertDialog(
        onDismissRequest = beiAbbrechen,
        icon = { Icon(Icons.Outlined.Inventory2, contentDescription = null) },
        title = { Text("Bestand korrigieren") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Abstand.eng)) {
                Eingabe(
                    wert = mengeText,
                    beiAenderung = { mengeText = it.filter(Char::isDigit) },
                    bezeichnung = "Zu Hause vorhanden",
                    suffix = "Stk.",
                    tastatur = KeyboardType.Number,
                )
                fehler?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(enabled = fehler == null, onClick = { beiSpeichern(menge!!) }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = beiAbbrechen) { Text("Abbrechen") } },
    )
}

/** Betrag in der Eingabeform mit Komma, etwa „2,49". */
private fun Double.alsEingabe(): String = String.format(java.util.Locale.GERMANY, "%.2f", this)

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
