package de.artikelfinder.app.ui.angebote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.artikelfinder.app.ui.alsPreis
import de.artikelfinder.app.ui.komponenten.ArtikelKarte
import de.artikelfinder.app.ui.komponenten.Hauptleiste
import de.artikelfinder.app.ui.komponenten.LadeAnzeige
import de.artikelfinder.app.ui.komponenten.LeerAnzeige
import de.artikelfinder.app.ui.komponenten.Marke
import de.artikelfinder.app.ui.theme.Abstand
import de.artikelfinder.app.ui.theme.aktion

@Composable
fun AngeboteBildschirm(
    beiArtikel: (String) -> Unit,
    viewModel: AngeboteViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    Scaffold(topBar = { Hauptleiste(titel = "Angebote") }) { abstand ->
        Column(modifier = Modifier.fillMaxSize().padding(abstand)) {
            when {
                zustand.laedt -> LadeAnzeige()

                zustand.angebote.isEmpty() -> LeerAnzeige(
                    titel = "Keine laufenden Angebote",
                    symbol = Icons.Outlined.LocalOffer,
                    hinweis = "Erfasse beim Einkaufen einen Werbepreis mit Enddatum — "
                        + "der Artikel erscheint dann hier, bis die Aktion abläuft.",
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = Abstand.rand, vertical = Abstand.eng),
                    verticalArrangement = Arrangement.spacedBy(Abstand.eng),
                ) {
                    item(key = "zusammenfassung") {
                        Zusammenfassung(zustand, modifier = Modifier.padding(bottom = Abstand.minimal))
                    }

                    items(items = zustand.angebote, key = { it.artikel.id }) { zeile ->
                        ArtikelKarte(
                            artikel = zeile.artikel,
                            beiKlick = { beiArtikel(zeile.artikel.id) },
                            hinweis = { Restlaufzeit(zeile) },
                        )
                    }
                }
            }
        }
    }
}

/** Zwei Kennzahlen nebeneinander: wie viele Angebote laufen, und was sie zusammen sparen. */
@Composable
private fun Zusammenfassung(zustand: AngeboteZustand, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Abstand.eng),
    ) {
        Kennzahl(
            wert = zustand.angebote.size.toString(),
            beschriftung = if (zustand.angebote.size == 1) "laufendes Angebot" else "laufende Angebote",
            hintergrund = MaterialTheme.colorScheme.primaryContainer,
            vordergrund = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        Kennzahl(
            wert = zustand.gesamtErsparnis.alsPreis(),
            beschriftung = "Ersparnis gesamt",
            hintergrund = MaterialTheme.aktion.container,
            vordergrund = MaterialTheme.aktion.aufContainer,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Kennzahl(
    wert: String,
    beschriftung: String,
    hintergrund: Color,
    vordergrund: Color,
    modifier: Modifier = Modifier,
) {
    Surface(color = hintergrund, contentColor = vordergrund, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(modifier = Modifier.padding(Abstand.rand)) {
            Text(text = wert, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text = beschriftung, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Restlaufzeit(zeile: AngebotZeile) {
    val text = when (zeile.tageUebrig) {
        null -> "ohne Enddatum"
        0L -> "endet heute"
        1L -> "endet morgen"
        else -> "noch ${zeile.tageUebrig} Tage"
    }

    if (zeile.laeuftBaldAb) {
        Marke(
            text = text,
            hintergrund = MaterialTheme.aktion.farbe,
            vordergrund = MaterialTheme.aktion.aufFarbe,
            symbol = Icons.Outlined.Schedule,
        )
    } else {
        Marke(
            text = text,
            hintergrund = MaterialTheme.colorScheme.surfaceContainerHigh,
            vordergrund = MaterialTheme.colorScheme.onSurfaceVariant,
            symbol = Icons.Outlined.Schedule,
        )
    }
}
