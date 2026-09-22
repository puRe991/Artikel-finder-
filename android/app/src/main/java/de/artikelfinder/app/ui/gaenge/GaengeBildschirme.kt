package de.artikelfinder.app.ui.gaenge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.artikelfinder.app.data.Gang
import de.artikelfinder.app.ui.komponenten.ArtikelKarte
import de.artikelfinder.app.ui.komponenten.FehlerAnzeige
import de.artikelfinder.app.ui.komponenten.Hauptleiste
import de.artikelfinder.app.ui.komponenten.Karte
import de.artikelfinder.app.ui.komponenten.LadeAnzeige
import de.artikelfinder.app.ui.komponenten.LeerAnzeige
import de.artikelfinder.app.ui.komponenten.Unterseitenleiste
import de.artikelfinder.app.ui.theme.Abstand

@Composable
fun GaengeBildschirm(
    beiGang: (String) -> Unit,
    viewModel: GaengeViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { Hauptleiste(titel = "Gänge", untertitel = zustand.markt?.name) },
    ) { abstand ->
        Column(modifier = Modifier.fillMaxSize().padding(abstand)) {
            when {
                zustand.laedt -> LadeAnzeige()

                zustand.fehler != null ->
                    FehlerAnzeige(meldung = zustand.fehler!!, beiWiederholen = viewModel::laden)

                zustand.gaenge.isEmpty() -> LeerAnzeige(
                    titel = "Noch keine Standorte erfasst",
                    symbol = Icons.Outlined.Place,
                    hinweis = "Sobald du beim Einkaufen den Gang eines Artikels erfasst, "
                        + "erscheint er hier.",
                )

                // Kacheln statt Liste: die Gangnummer ist das, wonach man sucht — sie soll
                // groß und auf einen Blick erfassbar sein.
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = PaddingValues(horizontal = Abstand.rand, vertical = Abstand.eng),
                    horizontalArrangement = Arrangement.spacedBy(Abstand.eng),
                    verticalArrangement = Arrangement.spacedBy(Abstand.eng),
                ) {
                    items(items = zustand.gaenge, key = { it.gang }) { gang ->
                        GangKachel(gang = gang, beiKlick = { beiGang(gang.gang) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GangKachel(gang: Gang, beiKlick: () -> Unit) {
    Karte(beiKlick = beiKlick) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Abstand.rand),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Gang",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = gang.gang,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${gang.anzahlArtikel} Artikel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun GangArtikelBildschirm(
    beiArtikel: (String) -> Unit,
    beiZurueck: () -> Unit,
    viewModel: GangArtikelViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { Unterseitenleiste(titel = "Gang ${zustand.gang}", beiZurueck = beiZurueck) },
    ) { abstand ->
        Column(modifier = Modifier.fillMaxSize().padding(abstand)) {
            when {
                zustand.laedt -> LadeAnzeige()

                zustand.fehler != null ->
                    FehlerAnzeige(meldung = zustand.fehler!!, beiWiederholen = viewModel::laden)

                zustand.artikel.isEmpty() -> LeerAnzeige(
                    titel = "In diesem Gang ist nichts erfasst",
                    symbol = Icons.Outlined.Place,
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = Abstand.rand, vertical = Abstand.eng),
                    verticalArrangement = Arrangement.spacedBy(Abstand.eng),
                ) {
                    items(items = zustand.artikel, key = { it.id }) { artikel ->
                        ArtikelKarte(artikel = artikel, beiKlick = { beiArtikel(artikel.id) })
                    }
                }
            }
        }
    }
}
