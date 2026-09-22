package de.artikelfinder.app.ui.bestand

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.artikelfinder.app.data.Bedarf
import de.artikelfinder.app.data.Bestand
import de.artikelfinder.app.ui.alsAnzahl
import de.artikelfinder.app.ui.alsPreis
import de.artikelfinder.app.ui.komponenten.Artikelbild
import de.artikelfinder.app.ui.komponenten.BaldLeerMarke
import de.artikelfinder.app.ui.komponenten.Hauptleiste
import de.artikelfinder.app.ui.komponenten.Karte
import de.artikelfinder.app.ui.komponenten.LadeAnzeige
import de.artikelfinder.app.ui.komponenten.LeerAnzeige
import de.artikelfinder.app.ui.komponenten.Mengenschalter
import de.artikelfinder.app.ui.theme.Abstand
import de.artikelfinder.app.ui.theme.aktion
import kotlin.math.roundToInt

@Composable
fun BestandBildschirm(
    beiArtikel: (String) -> Unit,
    beiScannen: () -> Unit,
    viewModel: BestandViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { Hauptleiste(titel = "Mein Vorrat") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = beiScannen,
                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                text = { Text("Einkauf scannen") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { abstand ->
        Column(modifier = Modifier.fillMaxSize().padding(abstand)) {
            when {
                zustand.laedt -> LadeAnzeige()

                zustand.eintraege.isEmpty() -> LeerAnzeige(
                    titel = "Noch kein Vorrat erfasst",
                    symbol = Icons.Outlined.Inventory2,
                    hinweis = "Scanne deine gekauften Artikel zu Hause ein. Aus deinen "
                        + "Einkäufen berechnet die App mit der Zeit, wie viel du brauchst und "
                        + "was dich das kostet.",
                    aktionText = "Einkauf scannen",
                    beiAktion = beiScannen,
                )

                else -> LazyColumn(
                    // Unten Platz für den Scan-Knopf, damit er die letzte Karte nicht verdeckt.
                    contentPadding = PaddingValues(
                        start = Abstand.rand, end = Abstand.rand, top = Abstand.eng, bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Abstand.eng),
                ) {
                    item(key = "uebersicht") {
                        Uebersicht(zustand, modifier = Modifier.padding(bottom = Abstand.minimal))
                    }

                    zustand.nachzukaufen.takeIf { it.isNotEmpty() }?.let { bald ->
                        item(key = "hinweis") { NachkaufHinweis(bald) }
                    }

                    items(items = zustand.eintraege, key = { it.artikel.id }) { eintrag ->
                        VorratKarte(
                            bestand = eintrag,
                            beiKlick = { beiArtikel(eintrag.artikel.id) },
                            beiPlus = { viewModel.einkauf(eintrag.artikel.id) },
                            beiMinus = { viewModel.verbrauch(eintrag.artikel.id) },
                        )
                    }
                }
            }
        }
    }
}

/** Kennzahlen wie auf der Angebotsseite: Menge, Wert, Monatskosten. */
@Composable
private fun Uebersicht(zustand: BestandZustand, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Abstand.eng)) {
        Kennzahl(
            wert = zustand.gesamtMenge.toString(),
            beschriftung = "Stück · ${zustand.anzahlArtikel} Artikel",
            hintergrund = MaterialTheme.colorScheme.primaryContainer,
            vordergrund = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        Kennzahl(
            wert = zustand.gesamtwert.alsPreis(),
            beschriftung = if (zustand.monatskosten > 0) {
                "Wert · ${zustand.monatskosten.alsPreis()}/Monat"
            } else {
                "Wert zu Hause"
            },
            hintergrund = MaterialTheme.colorScheme.secondaryContainer,
            vordergrund = MaterialTheme.colorScheme.onSecondaryContainer,
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
            Text(text = beschriftung, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Die Einkaufsliste in einem Satz: was demnächst leer ist. */
@Composable
private fun NachkaufHinweis(bald: List<Bestand>) {
    Surface(
        color = MaterialTheme.aktion.container,
        contentColor = MaterialTheme.aktion.aufContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(Abstand.rand), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = "Bald nachkaufen", style = MaterialTheme.typography.titleSmall)
            Text(
                text = bald.joinToString(", ") { it.artikel.name },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun VorratKarte(
    bestand: Bestand,
    beiKlick: () -> Unit,
    beiPlus: () -> Unit,
    beiMinus: () -> Unit,
) {
    Karte(beiKlick = beiKlick) {
        Row(
            modifier = Modifier.padding(Abstand.block),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Abstand.block),
        ) {
            Artikelbild(bestand.artikel.bildUrl, groesse = 48.dp)

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = bestand.artikel.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (bestand.bedarf.nachkaufEmpfohlen) {
                    BaldLeerMarke(modifier = Modifier.padding(vertical = 2.dp))
                }
                Text(
                    text = reichweiteZeile(bestand.bedarf),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOfNotNull(
                    bestand.bedarf.bedarfProMonat?.let { "${it.alsAnzahl()} pro Monat" },
                    bestand.bedarf.bestandswert?.let { "Wert ${it.alsPreis()}" },
                ).takeIf { it.isNotEmpty() }?.let { teile ->
                    Text(
                        text = teile.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Mengenschalter(menge = bestand.menge, beiPlus = beiPlus, beiMinus = beiMinus, knopfGroesse = 36.dp)
        }
    }
}

private fun reichweiteZeile(bedarf: Bedarf): String = when {
    bedarf.reichweiteTage != null -> "Reicht noch etwa ${bedarf.reichweiteTage.roundToInt()} Tage"
    bedarf.anzahlNachkaeufe <= 1 -> "Bedarf ab dem zweiten Nachkauf"
    else -> "Bedarf noch nicht bestimmbar"
}
