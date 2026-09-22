package de.artikelfinder.app.ui.suche

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.artikelfinder.app.data.Artikel
import de.artikelfinder.app.ui.komponenten.ArtikelKarte
import de.artikelfinder.app.ui.komponenten.FehlerAnzeige
import de.artikelfinder.app.ui.komponenten.Hauptleiste
import de.artikelfinder.app.ui.komponenten.KategorieAuswahl
import de.artikelfinder.app.ui.komponenten.LeerAnzeige
import de.artikelfinder.app.ui.theme.Abstand

@Composable
fun SucheBildschirm(
    beiArtikel: (String) -> Unit,
    beiScan: () -> Unit,
    beiNeuemArtikel: () -> Unit,
    viewModel: SucheViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    var kategorienOffen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Hauptleiste(
                titel = "Artikel-Finder",
                aktionen = {
                    IconButton(onClick = beiNeuemArtikel) {
                        Icon(Icons.Default.Add, contentDescription = "Artikel anlegen")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = beiScan,
                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                text = { Text("Scannen") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { abstand ->
        Column(modifier = Modifier.fillMaxSize().padding(abstand)) {

            Suchfeld(
                wert = zustand.suchbegriff,
                beiAenderung = viewModel::suchbegriffGeaendert,
            )

            Filterleiste(
                zustand = zustand,
                beiAngeboten = viewModel::werbepreisFilterUmschalten,
                beiKategorie = { kategorienOffen = true },
            )

            // Fester Platz für den Ladebalken, damit die Liste beim Tippen nicht springt.
            Box(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                if (zustand.laedt) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    zustand.fehler != null -> FehlerAnzeige(
                        meldung = zustand.fehler!!,
                        beiWiederholen = viewModel::aktualisieren,
                    )

                    zustand.zeigtVerlauf -> Trefferliste(
                        ueberschrift = "Zuletzt bearbeitet",
                        artikel = zustand.zuletztBearbeitet,
                        beiArtikel = beiArtikel,
                        leer = {
                            LeerAnzeige(
                                titel = "Noch nichts erfasst",
                                symbol = Icons.Outlined.History,
                                hinweis = "Suche nach einem Namen oder scanne einen Barcode. "
                                    + "Preise und Gänge trägst du beim Einkaufen selbst ein.",
                            )
                        },
                    )

                    else -> Trefferliste(
                        ueberschrift = when (val anzahl = zustand.treffer.size) {
                            0 -> null
                            1 -> "1 Treffer"
                            // Die Suche liefert höchstens eine Seite à 50; mehr heißt: genauer suchen.
                            in 50..Int.MAX_VALUE -> "50+ Treffer – Suche eingrenzen"
                            else -> "$anzahl Treffer"
                        },
                        artikel = zustand.treffer,
                        beiArtikel = beiArtikel,
                        leer = {
                            if (!zustand.laedt) {
                                LeerAnzeige(
                                    titel = "Keine Treffer",
                                    symbol = Icons.Outlined.SearchOff,
                                    hinweis = "Der Artikel ist noch nicht erfasst. Scanne ihn "
                                        + "oder lege ihn von Hand an.",
                                    aktionText = "Artikel anlegen",
                                    beiAktion = beiNeuemArtikel,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (kategorienOffen) {
        KategorieAuswahl(
            kategorien = zustand.kategorien,
            gewaehlt = zustand.gewaehlteKategorieId,
            ohneAuswahlText = "Alle Kategorien",
            beiAuswahl = {
                kategorienOffen = false
                viewModel.kategorieGewaehlt(it)
            },
            beiSchliessen = { kategorienOffen = false },
        )
    }
}

@Composable
private fun Suchfeld(wert: String, beiAenderung: (String) -> Unit) {
    TextField(
        value = wert,
        onValueChange = beiAenderung,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Abstand.rand, vertical = Abstand.minimal),
        placeholder = { Text("Name oder Marke suchen") },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (wert.isNotEmpty()) {
                IconButton(onClick = { beiAenderung("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Suche leeren")
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
private fun Filterleiste(
    zustand: SucheZustand,
    beiAngeboten: () -> Unit,
    beiKategorie: () -> Unit,
) {
    val kategorie = zustand.kategorien.firstOrNull { it.id == zustand.gewaehlteKategorieId }
    val chipFarben = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Abstand.rand),
        horizontalArrangement = Arrangement.spacedBy(Abstand.eng),
    ) {
        FilterChip(
            selected = zustand.nurMitWerbepreis,
            onClick = beiAngeboten,
            label = { Text("Nur Angebote") },
            leadingIcon = {
                Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            colors = chipFarben,
        )

        FilterChip(
            selected = kategorie != null,
            onClick = beiKategorie,
            label = { Text(kategorie?.name ?: "Kategorie", maxLines = 1) },
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            colors = chipFarben,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun Trefferliste(
    ueberschrift: String?,
    artikel: List<Artikel>,
    beiArtikel: (String) -> Unit,
    leer: @Composable () -> Unit,
) {
    if (artikel.isEmpty()) {
        leer()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Unten Platz für den Scan-Knopf, damit er die letzte Karte nicht verdeckt.
        contentPadding = PaddingValues(start = Abstand.rand, end = Abstand.rand, top = Abstand.eng, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(Abstand.eng),
    ) {
        ueberschrift?.let {
            item(key = "ueberschrift") {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Abstand.minimal, bottom = Abstand.minimal),
                )
            }
        }

        items(items = artikel, key = { it.id }) { eintrag ->
            ArtikelKarte(artikel = eintrag, beiKlick = { beiArtikel(eintrag.id) })
        }
    }
}
