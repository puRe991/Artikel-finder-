package de.artikelfinder.app.ui.verlauf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.artikelfinder.app.data.Verlaufseintrag
import de.artikelfinder.app.ui.alsDatumZeit
import de.artikelfinder.app.ui.komponenten.FehlerAnzeige
import de.artikelfinder.app.ui.komponenten.Karte
import de.artikelfinder.app.ui.komponenten.LadeAnzeige
import de.artikelfinder.app.ui.komponenten.LeerAnzeige
import de.artikelfinder.app.ui.komponenten.Unterseitenleiste
import de.artikelfinder.app.ui.theme.Abstand

@Composable
fun VerlaufBildschirm(
    beiZurueck: () -> Unit,
    viewModel: VerlaufViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { Unterseitenleiste(titel = "Änderungsverlauf", beiZurueck = beiZurueck) },
    ) { abstand ->
        Column(modifier = Modifier.fillMaxSize().padding(abstand)) {
            when {
                zustand.laedt -> LadeAnzeige()

                zustand.fehler != null ->
                    FehlerAnzeige(meldung = zustand.fehler!!, beiWiederholen = viewModel::laden)

                zustand.eintraege.isEmpty() -> LeerAnzeige(
                    titel = "Keine Einträge",
                    symbol = Icons.Outlined.History,
                )

                // Ein Artikel hat selten mehr als ein paar Dutzend Einträge — eine Karte
                // mit Trennlinien liest sich wie ein Protokoll, nicht wie lose Zettel.
                else -> Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Abstand.rand, vertical = Abstand.eng),
                ) {
                    Karte {
                        zustand.eintraege.forEachIndexed { index, eintrag ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 64.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            Eintrag(eintrag)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Eintrag(eintrag: Verlaufseintrag) {
    val symbol = when (eintrag.entitaet) {
        "Preis" -> Icons.Outlined.Euro
        "Standort" -> Icons.Outlined.Place
        else -> Icons.Outlined.Edit
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(Abstand.block),
        horizontalArrangement = Arrangement.spacedBy(Abstand.block),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = symbol,
                    contentDescription = eintrag.entitaet,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = eintrag.beschreibung, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = buildString {
                    append(eintrag.geaendertAm.alsDatumZeit())
                    eintrag.geaendertVon?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
