package de.artikelfinder.app.ui.suche

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.artikelfinder.app.ui.komponenten.ArtikelKarte
import de.artikelfinder.app.ui.komponenten.FehlerAnzeige
import de.artikelfinder.app.ui.komponenten.LeerAnzeige

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SucheBildschirm(
    beiArtikel: (String) -> Unit,
    beiScan: () -> Unit,
    beiGaengen: () -> Unit,
    beiAngeboten: () -> Unit,
    beiNeuemArtikel: () -> Unit,
    beiSicherung: () -> Unit,
    viewModel: SucheViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    var menueOffen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artikel-Finder") },
                actions = {
                    IconButton(onClick = beiAngeboten) {
                        Icon(Icons.Default.LocalOffer, contentDescription = "Angebote")
                    }
                    IconButton(onClick = beiGaengen) {
                        Icon(Icons.Default.Map, contentDescription = "Gänge")
                    }
                    // Alles, was nicht beim Einkaufen gebraucht wird, liegt im Überlauf —
                    // die Kopfzeile gehört den drei Handgriffen im Laden.
                    IconButton(onClick = { menueOffen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Weitere Aktionen")
                    }
                    DropdownMenu(expanded = menueOffen, onDismissRequest = { menueOffen = false }) {
                        DropdownMenuItem(
                            text = { Text("Sicherung") },
                            onClick = {
                                menueOffen = false
                                beiSicherung()
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = beiScan,
                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                text = { Text("Scannen") },
            )
        },
    ) { abstand ->
        Column(modifier = Modifier.fillMaxSize().padding(abstand)) {

            OutlinedTextField(
                value = zustand.suchbegriff,
                onValueChange = viewModel::suchbegriffGeaendert,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text("Artikel suchen") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (zustand.suchbegriff.isNotEmpty()) {
                        IconButton(onClick = { viewModel.suchbegriffGeaendert("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Suche leeren")
                        }
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
            )

            Filterleiste(zustand, viewModel)

            if (zustand.laedt) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    zustand.fehler != null -> FehlerAnzeige(
                        meldung = zustand.fehler!!,
                        beiWiederholen = viewModel::aktualisieren,
                    )

                    zustand.zeigtVerlauf -> Trefferliste(
                        titel = "Zuletzt bearbeitet",
                        artikel = zustand.zuletztBearbeitet,
                        leerTitel = "Noch nichts erfasst",
                        leerHinweis = "Suche nach einem Namen oder scanne einen Barcode. "
                            + "Preise und Gänge trägst du beim Einkaufen selbst ein.",
                        beiArtikel = beiArtikel,
                    )

                    else -> Trefferliste(
                        titel = null,
                        artikel = zustand.treffer,
                        leerTitel = "Keine Treffer",
                        leerHinweis = if (zustand.laedt) null
                        else "Der Artikel ist noch nicht erfasst. Über „Scannen“ oder das "
                            + "Formular kannst du ihn anlegen.",
                        beiArtikel = beiArtikel,
                        beiAnlegen = beiNeuemArtikel.takeIf { !zustand.laedt },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Filterleiste(zustand: SucheZustand, viewModel: SucheViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = zustand.nurMitWerbepreis,
            onClick = viewModel::werbepreisFilterUmschalten,
            label = { Text("Nur Angebote") },
            colors = FilterChipDefaults.filterChipColors(),
        )

        FilterChip(
            selected = zustand.gewaehlteKategorieId == null,
            onClick = { viewModel.kategorieGewaehlt(null) },
            label = { Text("Alle Kategorien") },
        )

        zustand.kategorien.forEach { kategorie ->
            FilterChip(
                selected = zustand.gewaehlteKategorieId == kategorie.id,
                onClick = { viewModel.kategorieGewaehlt(kategorie.id) },
                label = { Text(kategorie.name) },
            )
        }
    }
}

@Composable
private fun Trefferliste(
    titel: String?,
    artikel: List<de.artikelfinder.app.data.Artikel>,
    leerTitel: String,
    leerHinweis: String?,
    beiArtikel: (String) -> Unit,
    beiAnlegen: (() -> Unit)? = null,
) {
    if (artikel.isEmpty()) {
        Column {
            LeerAnzeige(titel = leerTitel, hinweis = leerHinweis)
            beiAnlegen?.let {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    androidx.compose.material3.OutlinedButton(onClick = it) {
                        Text("Artikel anlegen")
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        titel?.let {
            item {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(items = artikel, key = { it.id }) { eintrag ->
            ArtikelKarte(artikel = eintrag, beiKlick = { beiArtikel(eintrag.id) })
        }
    }
}
