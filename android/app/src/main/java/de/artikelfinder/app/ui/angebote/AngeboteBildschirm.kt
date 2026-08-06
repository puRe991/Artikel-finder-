package de.artikelfinder.app.ui.angebote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.artikelfinder.app.ui.alsPreis
import de.artikelfinder.app.ui.komponenten.ArtikelKarte
import de.artikelfinder.app.ui.komponenten.LadeAnzeige
import de.artikelfinder.app.ui.komponenten.LeerAnzeige

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AngeboteBildschirm(
    beiArtikel: (String) -> Unit,
    beiZurueck: () -> Unit,
    viewModel: AngeboteViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Angebote") },
                navigationIcon = {
                    IconButton(onClick = beiZurueck) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { abstand ->
        Column(modifier = Modifier.fillMaxSize().padding(abstand)) {
            when {
                zustand.laedt -> LadeAnzeige()

                zustand.angebote.isEmpty() -> LeerAnzeige(
                    titel = "Keine laufenden Angebote",
                    hinweis = "Erfasse beim Einkaufen einen Werbepreis mit Enddatum — "
                        + "der Artikel erscheint dann hier, bis die Aktion abläuft.",
                )

                else -> {
                    Zusammenfassung(zustand)

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items = zustand.angebote, key = { it.artikel.id }) { zeile ->
                            Column {
                                Restlaufzeit(zeile)
                                ArtikelKarte(
                                    artikel = zeile.artikel,
                                    beiKlick = { beiArtikel(zeile.artikel.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Zusammenfassung(zustand: AngeboteZustand) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${zustand.angebote.size} laufende Angebote",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            if (zustand.gesamtErsparnis > 0) {
                Text(
                    text = "Ersparnis: ${zustand.gesamtErsparnis.alsPreis()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun Restlaufzeit(zeile: AngebotZeile) {
    val text = when (zeile.tageUebrig) {
        null -> "ohne Enddatum"
        0L -> "läuft heute ab"
        1L -> "läuft morgen ab"
        else -> "noch ${zeile.tageUebrig} Tage"
    }

    Row(
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (zeile.laeuftBaldAb) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (zeile.laeuftBaldAb) FontWeight.Bold else FontWeight.Normal,
            color = if (zeile.laeuftBaldAb) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
