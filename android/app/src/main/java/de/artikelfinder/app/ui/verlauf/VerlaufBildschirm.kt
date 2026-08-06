package de.artikelfinder.app.ui.verlauf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.artikelfinder.app.ui.alsDatumZeit
import de.artikelfinder.app.ui.komponenten.FehlerAnzeige
import de.artikelfinder.app.ui.komponenten.LadeAnzeige
import de.artikelfinder.app.ui.komponenten.LeerAnzeige

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerlaufBildschirm(
    beiZurueck: () -> Unit,
    viewModel: VerlaufViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Änderungsverlauf") },
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

                zustand.eintraege.isEmpty() -> LeerAnzeige(titel = "Keine Einträge")

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(items = zustand.eintraege, key = { it.id }) { eintrag ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(
                                text = eintrag.beschreibung,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = buildString {
                                    append(eintrag.geaendertAm.alsDatumZeit())
                                    eintrag.geaendertVon?.let { append(" · $it") }
                                    append(" · ${eintrag.entitaet}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
