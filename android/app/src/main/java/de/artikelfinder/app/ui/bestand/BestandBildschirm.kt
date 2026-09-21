package de.artikelfinder.app.ui.bestand

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import de.artikelfinder.app.data.Bedarf
import de.artikelfinder.app.data.Bestand
import de.artikelfinder.app.ui.alsAnzahl
import de.artikelfinder.app.ui.alsPreis
import de.artikelfinder.app.ui.komponenten.LeerAnzeige
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BestandBildschirm(
    beiArtikel: (String) -> Unit,
    beiScannen: () -> Unit,
    beiZurueck: () -> Unit,
    viewModel: BestandViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mein Vorrat") },
                navigationIcon = {
                    IconButton(onClick = beiZurueck) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = beiScannen,
                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                text = { Text("Einkauf scannen") },
            )
        },
    ) { abstand ->
        Column(modifier = Modifier.fillMaxSize().padding(abstand)) {
            if (zustand.laedt) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (!zustand.laedt && zustand.eintraege.isEmpty()) {
                LeerAnzeige(
                    titel = "Noch kein Vorrat erfasst",
                    hinweis = "Scanne deine gekauften Artikel zu Hause ein. Aus deinen "
                        + "Einkäufen berechnet die App mit der Zeit, wie viel du brauchst und "
                        + "was dich das kostet.",
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (zustand.eintraege.isNotEmpty()) {
                    item { Uebersichtskarte(zustand) }
                }

                items(items = zustand.eintraege, key = { it.artikel.id }) { eintrag ->
                    BestandKarte(
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

@Composable
private fun Uebersichtskarte(zustand: BestandZustand) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${zustand.anzahlArtikel} Artikel · ${zustand.gesamtMenge} Stück",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Vorratswert ${zustand.gesamtwert.alsPreis()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (zustand.monatskosten > 0) {
                Text(
                    text = "Voraussichtliche Kosten: ${zustand.monatskosten.alsPreis()} pro Monat",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val nachkauf = zustand.nachzukaufen
            if (nachkauf.isNotEmpty()) {
                Text(
                    text = "Bald leer: " + nachkauf.joinToString(", ") { it.artikel.name },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun BestandKarte(
    bestand: Bestand,
    beiKlick: () -> Unit,
    beiPlus: () -> Unit,
    beiMinus: () -> Unit,
) {
    Card(onClick = beiKlick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = bestand.artikel.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                bestand.artikel.marke?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = bedarfZeile(bestand.bedarf),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bestand.bedarf.nachkaufEmpfohlen) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                bestand.bedarf.monatskosten?.let {
                    Text(
                        text = "${it.alsPreis()}/Monat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            MengenSteuerung(menge = bestand.menge, beiPlus = beiPlus, beiMinus = beiMinus)
        }
    }
}

@Composable
private fun MengenSteuerung(menge: Int, beiPlus: () -> Unit, beiMinus: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilledTonalIconButton(onClick = beiMinus, enabled = menge > 0, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Remove, contentDescription = "Verbraucht")
        }
        Text(
            text = menge.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        FilledTonalIconButton(onClick = beiPlus, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Gekauft")
        }
    }
}

private fun bedarfZeile(bedarf: Bedarf): String {
    if (!bedarf.hatBedarfsschaetzung) {
        return if (bedarf.anzahlNachkaeufe <= 1) {
            "Bedarf ab dem zweiten Nachkauf"
        } else {
            "Bedarf noch nicht bestimmbar"
        }
    }

    val proMonat = bedarf.bedarfProMonat?.let { "≈ ${it.alsAnzahl()}/Monat" }
    val reichweite = bedarf.reichweiteTage?.let { "reicht noch ~${it.roundToInt()} Tage" }
    return listOfNotNull(proMonat, reichweite).joinToString(" · ")
}
