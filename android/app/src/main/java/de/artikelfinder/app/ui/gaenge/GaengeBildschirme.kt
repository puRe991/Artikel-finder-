package de.artikelfinder.app.ui.gaenge

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.artikelfinder.app.ui.komponenten.ArtikelKarte
import de.artikelfinder.app.ui.komponenten.FehlerAnzeige
import de.artikelfinder.app.ui.komponenten.LadeAnzeige
import de.artikelfinder.app.ui.komponenten.LeerAnzeige

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GaengeBildschirm(
    beiGang: (String) -> Unit,
    beiZurueck: () -> Unit,
    viewModel: GaengeViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(zustand.markt?.name ?: "Gänge") },
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

                zustand.gaenge.isEmpty() -> LeerAnzeige(
                    titel = "Noch keine Standorte erfasst",
                    hinweis = "Sobald du beim Einkaufen den Gang eines Artikels erfasst, "
                        + "erscheint er hier.",
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = zustand.gaenge, key = { it.gang }) { gang ->
                        Card(
                            onClick = { beiGang(gang.gang) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(
                                        text = "Gang ${gang.gang}",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = "${gang.anzahlArtikel} " +
                                            if (gang.anzahlArtikel == 1) "Artikel" else "Artikel",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GangArtikelBildschirm(
    beiArtikel: (String) -> Unit,
    beiZurueck: () -> Unit,
    viewModel: GangArtikelViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gang ${zustand.gang}") },
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

                zustand.artikel.isEmpty() ->
                    LeerAnzeige(titel = "In diesem Gang ist nichts erfasst")

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = zustand.artikel, key = { it.id }) { artikel ->
                        ArtikelKarte(artikel = artikel, beiKlick = { beiArtikel(artikel.id) })
                    }
                }
            }
        }
    }
}
