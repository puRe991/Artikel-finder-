package de.artikelfinder.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
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
import de.artikelfinder.app.data.Bedarf
import de.artikelfinder.app.ui.alsAnzahl
import de.artikelfinder.app.ui.alsDatum
import de.artikelfinder.app.ui.alsDatumZeit
import de.artikelfinder.app.ui.alsPreis
import de.artikelfinder.app.ui.komponenten.AbschnittsTitel
import de.artikelfinder.app.ui.komponenten.Aktionsmarke
import de.artikelfinder.app.ui.komponenten.FehlerAnzeige
import de.artikelfinder.app.ui.komponenten.InfoKarte
import de.artikelfinder.app.ui.komponenten.LadeAnzeige
import de.artikelfinder.app.ui.werbezeitraumText
import kotlin.math.roundToInt

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
    var einkaufDialogOffen by remember { mutableStateOf(false) }
    var korrekturDialogOffen by remember { mutableStateOf(false) }

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
                    beiGekauft = { viewModel.einkaufErfassen(1, null) },
                    beiVerbraucht = { viewModel.verbrauchErfassen(1) },
                    beiEinkaufErfassen = { einkaufDialogOffen = true },
                    beiKorrektur = { korrekturDialogOffen = true },
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

    if (einkaufDialogOffen) {
        EinkaufDialog(
            vorbelegterStueckpreis = zustand.detail?.bedarf?.letzterStueckpreis
                ?: zustand.detail?.preise?.firstOrNull()?.gueltigerPreis,
            beiAbbrechen = { einkaufDialogOffen = false },
            beiSpeichern = { menge, preis ->
                einkaufDialogOffen = false
                viewModel.einkaufErfassen(menge, preis)
            },
        )
    }

    if (korrekturDialogOffen) {
        BestandKorrekturDialog(
            aktuellerBestand = zustand.detail?.bedarf?.aktuellerBestand ?: 0,
            beiAbbrechen = { korrekturDialogOffen = false },
            beiSpeichern = { menge ->
                korrekturDialogOffen = false
                viewModel.bestandKorrigieren(menge)
            },
        )
    }
}

@Composable
private fun Inhalt(
    detail: ArtikelDetail,
    beiPreisErfassen: () -> Unit,
    beiStandortErfassen: () -> Unit,
    beiGekauft: () -> Unit,
    beiVerbraucht: () -> Unit,
    beiEinkaufErfassen: () -> Unit,
    beiKorrektur: () -> Unit,
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
        artikel.marke?.let {
            Text(text = it, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        InfoKarte {
            Zeile("EAN", artikel.ean ?: "—")
            Zeile("Artikelnummer", artikel.artikelnummer ?: "—")
            Zeile("Kategorie", artikel.kategorieName ?: "—")
            Zeile("Quelle", if (detail.erstelltVon == "Import") "Open Food Facts" else "Selbst erfasst")
        }

        BestandAbschnitt(
            bedarf = detail.bedarf,
            beiGekauft = beiGekauft,
            beiVerbraucht = beiVerbraucht,
            beiEinkaufErfassen = beiEinkaufErfassen,
            beiKorrektur = beiKorrektur,
        )

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
private fun BestandAbschnitt(
    bedarf: Bedarf,
    beiGekauft: () -> Unit,
    beiVerbraucht: () -> Unit,
    beiEinkaufErfassen: () -> Unit,
    beiKorrektur: () -> Unit,
) {
    AbschnittsTitel("Bestand & Bedarf")

    InfoKarte {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Zu Hause",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${bedarf.aktuellerBestand} Stück",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = beiVerbraucht,
                    enabled = bedarf.aktuellerBestand > 0,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Verbraucht")
                }
                FilledTonalIconButton(onClick = beiGekauft, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Gekauft")
                }
            }
        }

        if (bedarf.nachkaufEmpfohlen) {
            Text(
                text = "Bald leer — Zeit zum Nachkaufen.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (bedarf.hatBedarfsschaetzung) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            bedarf.bedarfProWoche?.let { Zeile("Bedarf je Woche", "≈ ${it.alsAnzahl()} Stück") }
            bedarf.bedarfProMonat?.let { Zeile("Bedarf je Monat", "≈ ${it.alsAnzahl()} Stück") }
            bedarf.reichweiteTage?.let { Zeile("Reicht noch", "~${it.roundToInt()} Tage") }
            bedarf.monatskosten?.let { Zeile("Kosten je Monat", "≈ ${it.alsPreis()}") }
        } else {
            Text(
                text = "Sobald du diesen Artikel ein zweites Mal kaufst und einscannst, "
                    + "schätzt die App aus deinem Rhythmus, wie viel du brauchst und was es kostet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (bedarf.anzahlKaeufe > 0) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Zeile("Käufe erfasst", bedarf.anzahlKaeufe.toString())
            Zeile("Bisher gekauft", "${bedarf.gekaufteMenge} Stück")
            if (bedarf.gesamtAusgaben > 0) Zeile("Bisher ausgegeben", bedarf.gesamtAusgaben.alsPreis())
            bedarf.letzterKauf?.let { Zeile("Letzter Kauf", it.alsDatum()) }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = beiEinkaufErfassen, modifier = Modifier.weight(1f)) {
            Text("Gekauft erfassen")
        }
        OutlinedButton(onClick = beiKorrektur, modifier = Modifier.weight(1f)) {
            Text("Korrigieren")
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
