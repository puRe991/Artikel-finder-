package de.artikelfinder.app.ui.markt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.artikelfinder.app.data.Markt
import de.artikelfinder.app.ui.komponenten.AbschnittsTitel
import de.artikelfinder.app.ui.komponenten.FehlerAnzeige
import de.artikelfinder.app.ui.komponenten.InfoKarte
import de.artikelfinder.app.ui.komponenten.LadeAnzeige
import de.artikelfinder.app.ui.komponenten.LeerAnzeige

/**
 * Auswahl des Marktes, in dem gerade eingekauft wird — nach Kategorie sortiert:
 * Supermärkte, Baumärkte, Elektrofachmärkte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarktauswahlBildschirm(
    beiZurueck: () -> Unit,
    viewModel: MarktauswahlViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Markt wählen") },
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

                zustand.fehler != null ->
                    FehlerAnzeige(meldung = zustand.fehler!!, beiWiederholen = viewModel::laden)

                zustand.gruppen.isEmpty() -> LeerAnzeige(titel = "Es ist kein Markt angelegt")

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "hinweis") {
                        InfoKarte {
                            Text(
                                text = "Preise und Gänge werden je Markt erfasst. Was du im "
                                    + "Baumarkt aufnimmst, taucht im Supermarkt nicht auf.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    zustand.gruppen.forEach { gruppe ->
                        item(key = "kategorie-${gruppe.kategorie}") {
                            AbschnittsTitel(gruppe.kategorie)
                        }

                        items(items = gruppe.maerkte, key = { it.id }) { markt ->
                            MarktKarte(
                                markt = markt,
                                gewaehlt = markt.id == zustand.gewaehlteMarktId,
                                beiKlick = { viewModel.waehlen(markt.id, beiZurueck) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarktKarte(markt: Markt, gewaehlt: Boolean, beiKlick: () -> Unit) {
    Card(
        onClick = beiKlick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (gewaehlt) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = markt.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (gewaehlt) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                markt.ort?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (gewaehlt) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Gewählter Markt",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
