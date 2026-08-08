package de.artikelfinder.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.artikelfinder.app.data.Produktangaben

/**
 * Die Auskunft zum Artikel: Allergene, Auszeichnungen, Zutaten, Nährwerte.
 *
 * Die Daten stammen aus Open Food Facts — einem Freiwilligenprojekt. Sie sind eine gute
 * erste Auskunft und keine Rechtsgrundlage; das muss auf dem Bildschirm stehen, nicht nur
 * im Quelltext. Wer im Laden gefragt wird, ob etwas Nüsse enthält, soll wissen, worauf er
 * sich stützt.
 */

/**
 * Allergene.
 *
 * **Fehlende Angaben werden ausdrücklich als fehlend gezeigt.** Ein leerer Abschnitt liest
 * sich sonst wie „enthält nichts davon" — und genau diese Verwechslung ist die gefährliche.
 * Deshalb erscheint der Abschnitt immer, auch ohne Daten, und sagt dann klar, dass nichts
 * hinterlegt ist.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Allergene(angaben: Produktangaben, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (angaben.allergene.isNotEmpty()) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Allergene",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            when {
                angaben.allergene.isNotEmpty() -> {
                    Text(text = "Enthält:", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        angaben.allergene.forEach { Merkmal(it, hervorgehoben = true) }
                    }
                }

                else -> Text(
                    text = "Zu diesem Artikel sind keine Allergene hinterlegt. Das heißt " +
                        "nicht, dass keine enthalten sind — die Angabe fehlt schlicht.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (angaben.spuren.isNotEmpty()) {
                Text(
                    text = "Kann Spuren enthalten von:",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    angaben.spuren.forEach { Merkmal(it, hervorgehoben = false) }
                }
            }

            Text(
                text = "Angaben aus Open Food Facts, ohne Gewähr. Verbindlich ist immer " +
                    "die Packung.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Bio, Vegan, Glutenfrei … — das, wonach am Regal gefragt wird. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Auszeichnungen(angaben: Produktangaben, modifier: Modifier = Modifier) {
    if (angaben.auszeichnungen.isEmpty()) return

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        angaben.auszeichnungen.forEach {
            AssistChip(
                onClick = {},
                label = { Text(it) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

/** Die Zutatenliste, eingeklappt — sie ist lang und wird nur auf Nachfrage gebraucht. */
@Composable
fun Zutaten(angaben: Produktangaben, modifier: Modifier = Modifier) {
    val zutaten = angaben.zutaten ?: return
    var offen by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        TextButton(onClick = { offen = !offen }) {
            Text(if (offen) "Zutaten ausblenden" else "Zutaten anzeigen")
            Icon(
                if (offen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        }

        if (offen) {
            Text(
                text = zutaten,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

/** Nährwerte je 100 g, in der Reihenfolge der Tabelle auf der Packung. */
@Composable
fun Naehrwerte(angaben: Produktangaben, modifier: Modifier = Modifier) {
    if (angaben.naehrwerte.isEmpty() && angaben.nutriscore == null) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        angaben.nutriscore?.let { Nutriscore(it) }

        angaben.naehrwerte.forEach { wert ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = wert.bezeichnung,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = wert.wert, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (angaben.naehrwerte.isNotEmpty()) {
            Text(
                text = "je 100 g bzw. 100 ml",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Nutri-Score als farbiger Buchstabe. Die Farben sind die des offiziellen Logos, damit die
 * Einordnung ohne Erklärung verstanden wird — Grün ist besser als Rot.
 */
@Composable
private fun Nutriscore(note: String) {
    val farbe = when (note) {
        "a" -> Color(0xFF1E8F4E)
        "b" -> Color(0xFF85BB2F)
        "c" -> Color(0xFFFECB02)
        "d" -> Color(0xFFEE8100)
        else -> Color(0xFFE63E11)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            color = farbe,
            shape = CircleShape,
            modifier = Modifier.size(32.dp).clip(CircleShape),
        ) {
            Text(
                text = note.uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Text(text = "Nutri-Score", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Merkmal(text: String, hervorgehoben: Boolean) {
    Surface(
        color = if (hervorgehoben) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (hervorgehoben) MaterialTheme.colorScheme.onError
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
