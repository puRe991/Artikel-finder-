package de.artikelfinder.app.ui.komponenten

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.artikelfinder.app.data.Kategorie
import de.artikelfinder.app.ui.theme.Abstand

/**
 * Kategorien als Liste zum Antippen, gegliedert nach Oberkategorie. Ersetzt die lange
 * Chip-Reihe: gut vierzig Kategorien lassen sich nicht sinnvoll seitlich durchwischen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KategorieAuswahl(
    kategorien: List<Kategorie>,
    gewaehlt: Int?,
    ohneAuswahlText: String,
    beiAuswahl: (Int?) -> Unit,
    beiSchliessen: () -> Unit,
) {
    val zustand = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gruppen = remember(kategorien) { gruppieren(kategorien) }

    ModalBottomSheet(
        onDismissRequest = beiSchliessen,
        sheetState = zustand,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = "Kategorie wählen",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Abstand.rand + 8.dp, vertical = Abstand.eng),
        )

        LazyColumn(modifier = Modifier.navigationBarsPadding()) {
            item {
                Eintrag(
                    text = ohneAuswahlText,
                    gewaehlt = gewaehlt == null,
                    fett = true,
                    beiKlick = { beiAuswahl(null) },
                )
            }

            gruppen.forEach { (ober, unter) ->
                item(key = "trenner-${ober.id}") {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Abstand.rand, vertical = Abstand.minimal),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                item(key = ober.id) {
                    Eintrag(
                        text = ober.name,
                        gewaehlt = gewaehlt == ober.id,
                        fett = true,
                        beiKlick = { beiAuswahl(ober.id) },
                    )
                }
                items(items = unter, key = { it.id }) { kategorie ->
                    Eintrag(
                        text = kategorie.name,
                        gewaehlt = gewaehlt == kategorie.id,
                        fett = false,
                        eingerueckt = true,
                        beiKlick = { beiAuswahl(kategorie.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Eintrag(
    text: String,
    gewaehlt: Boolean,
    fett: Boolean,
    beiKlick: () -> Unit,
    eingerueckt: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = beiKlick)
            .padding(start = if (eingerueckt) 40.dp else 24.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Abstand.block),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (fett || gewaehlt) FontWeight.SemiBold else FontWeight.Normal,
            color = if (gewaehlt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (gewaehlt) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Ausgewählt",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Oberkategorie → ihre Unterkategorien, in der Reihenfolge des Katalogs. */
private fun gruppieren(kategorien: List<Kategorie>): List<Pair<Kategorie, List<Kategorie>>> {
    val oberkategorien = kategorien.filter { " > " !in it.pfad }
    return oberkategorien.map { ober ->
        ober to kategorien.filter { it.pfad.startsWith("${ober.name} > ") }
    }
}
