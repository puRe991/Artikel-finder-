package de.artikelfinder.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import de.artikelfinder.app.data.ArtikelDetail
import de.artikelfinder.app.data.Preis
import de.artikelfinder.app.data.Standort
import de.artikelfinder.app.ui.alsDatum
import de.artikelfinder.app.ui.alsDatumZeit
import de.artikelfinder.app.ui.alsPreis
import de.artikelfinder.app.ui.komponenten.Abschnitt
import de.artikelfinder.app.ui.komponenten.Aktionsmarke
import de.artikelfinder.app.ui.komponenten.Artikelbild
import de.artikelfinder.app.ui.komponenten.DatenZeile
import de.artikelfinder.app.ui.komponenten.Erfassungsvermerk
import de.artikelfinder.app.ui.komponenten.FehlerAnzeige
import de.artikelfinder.app.ui.komponenten.LadeAnzeige
import de.artikelfinder.app.ui.komponenten.Unterseitenleiste
import de.artikelfinder.app.ui.theme.Abstand
import de.artikelfinder.app.ui.theme.aktion
import de.artikelfinder.app.ui.werbezeitraumText

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
            Unterseitenleiste(
                // Der Name steht groß im Kopf der Seite; oben reicht die Einordnung.
                titel = "Artikel",
                beiZurueck = beiZurueck,
                aktionen = {
                    zustand.detail?.let { detail ->
                        IconButton(onClick = { beiVerlauf(detail.artikel.id) }) {
                            Icon(Icons.Default.History, contentDescription = "Änderungsverlauf")
                        }
                        IconButton(onClick = { beiBearbeiten(detail.artikel.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Artikel bearbeiten")
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Abstand.rand, vertical = Abstand.eng),
        verticalArrangement = Arrangement.spacedBy(Abstand.block),
    ) {
        Kopf(detail)

        Preisabschnitt(
            preise = detail.preise,
            beiErfassen = beiPreisErfassen,
        )

        Standortabschnitt(
            standort = detail.standorte.firstOrNull(),
            beiErfassen = beiStandortErfassen,
        )

        Abschnitt(titel = "Artikeldaten", symbol = Icons.Outlined.Info) {
            val artikel = detail.artikel
            DatenZeile("EAN", artikel.ean ?: "—")
            DatenZeile("Artikelnummer", artikel.artikelnummer ?: "—")
            DatenZeile("Kategorie", artikel.kategorieName ?: "—")
            DatenZeile("Quelle", if (detail.erstelltVon == "Import") "Open Food Facts" else "Selbst erfasst")
        }
    }
}

@Composable
private fun Kopf(detail: ArtikelDetail) {
    val artikel = detail.artikel

    Row(
        horizontalArrangement = Arrangement.spacedBy(Abstand.rand),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = Abstand.minimal),
    ) {
        Artikelbild(artikel.bildUrl, groesse = 104.dp)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Abstand.minimal),
        ) {
            Text(text = artikel.name, style = MaterialTheme.typography.headlineSmall)
            artikel.marke?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Preisabschnitt(preise: List<Preis>, beiErfassen: () -> Unit) {
    val preis = preise.firstOrNull()

    Abschnitt(
        titel = "Preis",
        symbol = Icons.Outlined.Euro,
        aktion = if (preis != null) {
            { TextButton(onClick = beiErfassen) { Text("Neuer Preis") } }
        } else null,
    ) {
        if (preis == null) {
            Fehlt(
                text = "Für diesen Artikel ist noch kein Preis erfasst.",
                knopf = "Preis erfassen",
                beiKlick = beiErfassen,
            )
            return@Abschnitt
        }

        if (preis.werbepreisAktiv && preis.werbepreis != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Abstand.block),
            ) {
                Text(
                    text = preis.werbepreis.alsPreis(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.aktion.farbe,
                )
                Aktionsmarke()
            }
            Text(
                text = "statt ${preis.preis.alsPreis()}",
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            werbezeitraumText(preis.werbepreisGueltigVon, preis.werbepreisGueltigBis)?.let {
                Text(
                    text = "Aktion $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.aktion.farbe,
                )
            }
        } else {
            Text(
                text = preis.preis.alsPreis(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            // Ein abgelaufener Werbepreis bleibt sichtbar — er sagt etwas über den
            // üblichen Aktionspreis aus.
            if (preis.werbepreis != null) {
                Text(
                    text = "Letzter Aktionspreis: ${preis.werbepreis.alsPreis()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Erfassungsvermerk(
            "Erfasst am ${preis.erfasstAm.alsDatumZeit()}" + (preis.erfasstVon?.let { " von $it" } ?: ""),
        )

        val fruehere = preise.drop(1).take(5)
        if (fruehere.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = Abstand.eng),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                text = "Frühere Preise",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Abstand.minimal),
            )
            fruehere.forEach { frueher ->
                DatenZeile(frueher.erfasstAm.alsDatum(), frueher.preis.alsPreis())
            }
        }
    }
}

@Composable
private fun Standortabschnitt(standort: Standort?, beiErfassen: () -> Unit) {
    Abschnitt(
        titel = "Standort",
        symbol = Icons.Outlined.Place,
        aktion = if (standort != null) {
            { TextButton(onClick = beiErfassen) { Text("Korrigieren") } }
        } else null,
    ) {
        if (standort == null) {
            Fehlt(
                text = "Noch nicht erfasst, in welchem Gang der Artikel steht.",
                knopf = "Standort erfassen",
                beiKlick = beiErfassen,
            )
            return@Abschnitt
        }

        Text(
            text = "Gang ${standort.gang}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        standort.regalBeschreibung?.let {
            Text(text = it, style = MaterialTheme.typography.bodyLarge)
        }
        Erfassungsvermerk(
            "Erfasst am ${standort.erfasstAm.alsDatumZeit()}" + (standort.erfasstVon?.let { " von $it" } ?: ""),
        )
    }
}

/** Platzhalter in einem Abschnitt, dem noch Daten fehlen — mit der passenden Schaltfläche. */
@Composable
private fun Fehlt(text: String, knopf: String, beiKlick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = beiKlick,
        modifier = Modifier.fillMaxWidth().padding(top = Abstand.eng),
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(knopf, modifier = Modifier.padding(start = Abstand.eng))
    }
}
