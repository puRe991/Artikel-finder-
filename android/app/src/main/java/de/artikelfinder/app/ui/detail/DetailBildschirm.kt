package de.artikelfinder.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.artikelfinder.app.data.ArtikelDetail
import de.artikelfinder.app.ui.alsDatumZeit
import de.artikelfinder.app.ui.alsPreis
import de.artikelfinder.app.ui.komponenten.AbschnittsTitel
import de.artikelfinder.app.ui.komponenten.Aktionsmarke
import de.artikelfinder.app.ui.komponenten.FehlerAnzeige
import de.artikelfinder.app.ui.komponenten.InfoKarte
import de.artikelfinder.app.ui.komponenten.LadeAnzeige
import de.artikelfinder.app.ui.werbezeitraumText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailBildschirm(
    beiZurueck: () -> Unit,
    beiBearbeiten: (String) -> Unit,
    beiVerlauf: (String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val snackbarZustand = remember { SnackbarHostState() }

    var preisDialogOffen by remember { mutableStateOf(false) }
    var standortDialogOffen by remember { mutableStateOf(false) }

    LaunchedEffect(zustand.meldung) {
        zustand.meldung?.let {
            snackbarZustand.showSnackbar(it)
            viewModel.meldungGelesen()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(zustand.detail?.artikel?.name ?: "Artikel") },
                navigationIcon = {
                    IconButton(onClick = beiZurueck) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    zustand.detail?.let { detail ->
                        IconButton(onClick = { beiVerlauf(detail.artikel.id) }) {
                            Icon(Icons.Default.History, contentDescription = "Änderungsverlauf")
                        }
                        IconButton(onClick = { beiBearbeiten(detail.artikel.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Bearbeiten")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarZustand) },
    ) { abstand ->
        Column(modifier = Modifier.fillMaxSize().padding(abstand)) {
            when {
                zustand.laedt && zustand.detail == null -> LadeAnzeige()

                zustand.fehler != null && zustand.detail == null ->
                    FehlerAnzeige(meldung = zustand.fehler!!, beiWiederholen = viewModel::laden)

                zustand.detail != null -> Inhalt(
                    detail = zustand.detail!!,
                    beiPreisErfassen = { preisDialogOffen = true },
                    beiStandortErfassen = { standortDialogOffen = true },
                )
            }
        }
    }

    if (preisDialogOffen) {
        PreisDialog(
            vorbelegtesAktionsende = zustand.vorbelegtesAktionsende,
            beiAbbrechen = { preisDialogOffen = false },
            beiSpeichern = { preis, werbepreis, bis, von ->
                preisDialogOffen = false
                viewModel.preisErfassen(preis, werbepreis, bis, von)
            },
        )
    }

    if (standortDialogOffen) {
        StandortDialog(
            beiAbbrechen = { standortDialogOffen = false },
            beiSpeichern = { gang, regal, von ->
                standortDialogOffen = false
                viewModel.standortErfassen(gang, regal, von)
            },
        )
    }
}

@Composable
private fun Inhalt(
    detail: ArtikelDetail,
    beiPreisErfassen: () -> Unit,
    beiStandortErfassen: () -> Unit,
) {
    val artikel = detail.artikel

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        artikel.bildUrl?.takeIf { it.isNotBlank() }?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
        }

        Text(text = artikel.name, style = MaterialTheme.typography.headlineSmall)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            artikel.marke?.let {
                Text(text = it, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            artikel.angaben.menge?.let {
                Text(text = "· $it", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Auszeichnungen(artikel.angaben)

        // Vor Preis und Standort: wer im Laden nach Allergenen gefragt wird, soll nicht
        // erst scrollen muessen.
        Allergene(artikel.angaben)

        InfoKarte {
            Zeile("EAN", artikel.ean ?: "—")
            Zeile("Artikelnummer", artikel.artikelnummer ?: "—")
            Zeile("Kategorie", artikel.kategorieName ?: "—")
            Zeile("Quelle", if (detail.erstelltVon == "Import") "Open Food Facts" else "Selbst erfasst")
        }

        AbschnittsTitel("Preis")
        val preis = detail.preise.firstOrNull()
        if (preis == null) {
            Text(
                text = "Für diesen Artikel ist noch kein Preis erfasst.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            InfoKarte {
                if (preis.werbepreisAktiv && preis.werbepreis != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = preis.werbepreis.alsPreis(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Aktionsmarke()
                    }
                    Text(
                        text = "statt ${preis.preis.alsPreis()}",
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    werbezeitraumText(preis.werbepreisGueltigVon, preis.werbepreisGueltigBis)?.let {
                        Text(text = "Aktion $it", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(
                        text = preis.preis.alsPreis(),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    // Ein abgelaufener Werbepreis bleibt sichtbar — er sagt etwas über den
                    // üblichen Aktionspreis aus.
                    if (preis.werbepreis != null) {
                        Text(
                            text = "Letzter Aktionspreis: ${preis.werbepreis.alsPreis()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = "Erfasst am ${preis.erfasstAm.alsDatumZeit()}"
                        + (preis.erfasstVon?.let { " von $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Button(onClick = beiPreisErfassen, modifier = Modifier.fillMaxWidth()) {
            Text(if (preis == null) "Preis erfassen" else "Neuen Preis erfassen")
        }

        AbschnittsTitel("Standort")
        val standort = detail.standorte.firstOrNull()
        if (standort == null) {
            Text(
                text = "Noch kein Standort erfasst.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            InfoKarte {
                Text(text = "Gang ${standort.gang}", style = MaterialTheme.typography.titleLarge)
                standort.regalBeschreibung?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = "Erfasst am ${standort.erfasstAm.alsDatumZeit()}"
                        + (standort.erfasstVon?.let { " von $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        OutlinedButton(onClick = beiStandortErfassen, modifier = Modifier.fillMaxWidth()) {
            Text(if (standort == null) "Standort erfassen" else "Standort korrigieren")
        }

        if (artikel.angaben.zutaten != null) {
            AbschnittsTitel("Zutaten")
            Zutaten(artikel.angaben)
        }

        if (artikel.angaben.naehrwerte.isNotEmpty() || artikel.angaben.nutriscore != null) {
            AbschnittsTitel("Nährwerte")
            InfoKarte { Naehrwerte(artikel.angaben) }
        }

        if (detail.preise.size > 1) {
            AbschnittsTitel("Frühere Preise")
            detail.preise.drop(1).take(5).forEach { frueher ->
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = frueher.erfasstAm.alsDatumZeit(),
                        style = MaterialTheme.typography.bodySmall)
                    Text(text = frueher.preis.alsPreis(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Zeile(bezeichnung: String, wert: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = bezeichnung,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = wert, style = MaterialTheme.typography.bodyMedium)
    }
}
