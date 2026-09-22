package de.artikelfinder.app.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import de.artikelfinder.app.data.Bedarf
import de.artikelfinder.app.data.Preis
import de.artikelfinder.app.data.Standort
import de.artikelfinder.app.ui.alsAnzahl
import de.artikelfinder.app.ui.alsDatum
import de.artikelfinder.app.ui.alsDatumZeit
import de.artikelfinder.app.ui.alsPreis
import de.artikelfinder.app.ui.komponenten.Abschnitt
import de.artikelfinder.app.ui.komponenten.Aktionsmarke
import de.artikelfinder.app.ui.komponenten.Artikelbild
import de.artikelfinder.app.ui.komponenten.BaldLeerMarke
import de.artikelfinder.app.ui.komponenten.DatenZeile
import de.artikelfinder.app.ui.komponenten.Erfassungsvermerk
import de.artikelfinder.app.ui.komponenten.FehlerAnzeige
import de.artikelfinder.app.ui.komponenten.LadeAnzeige
import de.artikelfinder.app.ui.komponenten.Mengenschalter
import de.artikelfinder.app.ui.komponenten.Unterseitenleiste
import de.artikelfinder.app.ui.theme.Abstand
import de.artikelfinder.app.ui.theme.aktion
import de.artikelfinder.app.ui.werbezeitraumText
import kotlin.math.roundToInt

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

    // Android-Fotopicker: kein Berechtigungsdialog nötig, das Bild wird danach in den
    // App-Speicher kopiert.
    val bildWaehler = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::bildGewaehlt) }

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
                    beiGekauft = { viewModel.einkaufErfassen(1, null) },
                    beiVerbraucht = { viewModel.verbrauchErfassen(1) },
                    beiEinkaufErfassen = { einkaufDialogOffen = true },
                    beiKorrektur = { korrekturDialogOffen = true },
                    beiBildWaehlen = {
                        bildWaehler.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    beiBildEntfernen = viewModel::bildEntfernen,
                )
            }
        }
    }

    if (preisDialogOffen) {
        PreisDialog(
            vorbelegtesAktionsende = zustand.vorbelegtesAktionsende,
            vorbelegterPreis = zustand.detail?.preise?.firstOrNull()?.preis
                ?: zustand.detail?.bedarf?.letzterStueckpreis,
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
    beiBildWaehlen: () -> Unit,
    beiBildEntfernen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Abstand.rand, vertical = Abstand.eng),
        verticalArrangement = Arrangement.spacedBy(Abstand.block),
    ) {
        Kopf(detail, beiBildWaehlen = beiBildWaehlen, beiBildEntfernen = beiBildEntfernen)

        Preisabschnitt(
            preise = detail.preise,
            preisAusEinkaeufen = detail.bedarf.letzterStueckpreis,
            beiErfassen = beiPreisErfassen,
        )

        Vorratabschnitt(
            bedarf = detail.bedarf,
            beiGekauft = beiGekauft,
            beiVerbraucht = beiVerbraucht,
            beiEinkaufErfassen = beiEinkaufErfassen,
            beiKorrektur = beiKorrektur,
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
private fun Kopf(detail: ArtikelDetail, beiBildWaehlen: () -> Unit, beiBildEntfernen: () -> Unit) {
    val artikel = detail.artikel
    val hatBild = !artikel.bildUrl.isNullOrBlank()

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
            // Eigenes Foto aus der Galerie — auch für Katalogartikel ohne oder mit falschem Bild.
            Row(modifier = Modifier.offset(x = (-12).dp)) {
                TextButton(onClick = beiBildWaehlen) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(if (hatBild) "Bild ändern" else "Bild einfügen", modifier = Modifier.padding(start = 6.dp))
                }
                if (hatBild) {
                    TextButton(onClick = beiBildEntfernen) { Text("Entfernen") }
                }
            }
        }
    }
}

@Composable
private fun Preisabschnitt(preise: List<Preis>, preisAusEinkaeufen: Double?, beiErfassen: () -> Unit) {
    val preis = preise.firstOrNull()

    Abschnitt(
        titel = "Preis",
        symbol = Icons.Outlined.Euro,
        aktion = if (preis != null || preisAusEinkaeufen != null) {
            { TextButton(onClick = beiErfassen) { Text("Preis ändern") } }
        } else null,
    ) {
        // Noch kein Ladenpreis, aber aus den eigenen Einkäufen ist einer bekannt: den zeigen
        // statt „kein Preis" — sonst widerspricht die Seite dem Vorrat darunter.
        if (preis == null && preisAusEinkaeufen != null) {
            Text(
                text = preisAusEinkaeufen.alsPreis(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Aus deinen Einkäufen übernommen — noch kein Ladenpreis erfasst.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Abschnitt
        }

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

@Composable
private fun Vorratabschnitt(
    bedarf: Bedarf,
    beiGekauft: () -> Unit,
    beiVerbraucht: () -> Unit,
    beiEinkaufErfassen: () -> Unit,
    beiKorrektur: () -> Unit,
) {
    Abschnitt(
        titel = "Vorrat & Bedarf",
        symbol = Icons.Outlined.Inventory2,
        aktion = { TextButton(onClick = beiKorrektur) { Text("Korrigieren") } },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Zu Hause",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Abstand.eng),
                ) {
                    Text(
                        text = "${bedarf.aktuellerBestand} Stück",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (bedarf.nachkaufEmpfohlen) BaldLeerMarke()
                }
            }
            Mengenschalter(
                menge = bedarf.aktuellerBestand,
                beiPlus = beiGekauft,
                beiMinus = beiVerbraucht,
                // Die Menge steht links schon groß.
                zeigeMenge = false,
                modifier = Modifier.padding(start = Abstand.eng),
            )
        }

        if (bedarf.hatBedarfsschaetzung) {
            Trennlinie()
            bedarf.reichweiteTage?.let { DatenZeile("Reicht noch", "etwa ${it.roundToInt()} Tage") }
            bedarf.bedarfProWoche?.let { DatenZeile("Bedarf je Woche", "≈ ${it.alsAnzahl()} Stück") }
            bedarf.bedarfProMonat?.let { DatenZeile("Bedarf je Monat", "≈ ${it.alsAnzahl()} Stück") }
            bedarf.monatskosten?.let { DatenZeile("Kosten je Monat", "≈ ${it.alsPreis()}") }
        } else {
            Text(
                text = "Kaufst du den Artikel an einem weiteren Tag nach und scannst ihn ein, "
                    + "schätzt die App aus deinem Rhythmus, wie viel du brauchst und was es "
                    + "kostet. Mehrere Packungen aus einem Einkauf zählen als ein Nachkauf.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Abstand.eng),
            )
        }

        if (bedarf.letzterStueckpreis != null || bedarf.anzahlKaeufe > 0) {
            Trennlinie()
            bedarf.letzterStueckpreis?.let { DatenZeile("Stückpreis", it.alsPreis()) }
            bedarf.bestandswert?.let { DatenZeile("Wert zu Hause", it.alsPreis()) }
            if (bedarf.anzahlKaeufe > 0) {
                DatenZeile("Bisher gekauft", "${bedarf.gekaufteMenge} Stück in ${bedarf.anzahlKaeufe} Käufen")
                if (bedarf.gesamtAusgaben > 0) DatenZeile("Bisher ausgegeben", bedarf.gesamtAusgaben.alsPreis())
                bedarf.letzterKauf?.let { DatenZeile("Letzter Kauf", it.alsDatum()) }
            }
        }

        OutlinedButton(
            onClick = beiEinkaufErfassen,
            modifier = Modifier.fillMaxWidth().padding(top = Abstand.eng),
        ) {
            Icon(Icons.Outlined.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Einkauf mit Menge und Preis erfassen", modifier = Modifier.padding(start = Abstand.eng))
        }
    }
}

@Composable
private fun Trennlinie() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = Abstand.eng),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
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
