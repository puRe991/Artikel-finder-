package de.artikelfinder.app.ui.sicherung

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Sicherung der selbst erfassten Daten.
 *
 * Die Datei legt der Nutzer über die Dateiauswahl des Systems ab — damit landet sie dort,
 * wo er sie wiederfindet (Downloads, Drive, SD-Karte), und die App braucht dafür kein
 * Speicherrecht.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SicherungBildschirm(
    beiZurueck: () -> Unit,
    viewModel: SicherungViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    val zielWaehlen = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/tab-separated-values")
    ) { ziel -> ziel?.let(viewModel::exportieren) }

    val quelleWaehlen = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { quelle -> quelle?.let(viewModel::importieren) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sicherung") },
                navigationIcon = {
                    IconButton(onClick = beiZurueck) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { abstand ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(abstand)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Der Artikelkatalog steckt in der App und ist jederzeit wieder da. " +
                    "Deine Preise und Gänge nicht — die entstehen nur im Laden. Die Sicherung " +
                    "schreibt genau diese Daten in eine Textdatei.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (zustand.laeuft) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            zustand.erfolg?.let { Rueckmeldung(it, fehler = false, beiSchliessen = viewModel::meldungGelesen) }
            zustand.fehler?.let { Rueckmeldung(it, fehler = true, beiSchliessen = viewModel::meldungGelesen) }

            Abschnitt(
                titel = "Sicherung schreiben",
                erklaerung = "Legt eine Datei mit allen selbst erfassten Preisen, Standorten " +
                    "und Artikeln an. Du wählst, wo sie hinsoll.",
            ) {
                Button(
                    onClick = { zielWaehlen.launch(viewModel.dateiname()) },
                    enabled = !zustand.laeuft,
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Text(text = "Sicherung schreiben", modifier = Modifier.padding(start = 8.dp))
                }
            }

            Abschnitt(
                titel = "Sicherung einlesen",
                erklaerung = "Ergänzt die Daten aus einer Sicherungsdatei. Nichts wird " +
                    "gelöscht oder überschrieben, und dieselbe Datei zweimal einzulesen " +
                    "ändert nichts.",
            ) {
                OutlinedButton(
                    // Bewusst ohne Typfilter: viele Dateiauswahlen blenden .tsv-Dateien
                    // sonst aus, und dann sieht der Nutzer seine eigene Sicherung nicht.
                    onClick = { quelleWaehlen.launch(arrayOf("*/*")) },
                    enabled = !zustand.laeuft,
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Text(text = "Sicherung einlesen", modifier = Modifier.padding(start = 8.dp))
                }
            }

            Text(
                text = "Die Datei bezieht sich über die EAN auf den Katalog. Deshalb passen " +
                    "die Preise auch auf einem anderen Handy wieder zum richtigen Artikel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Abschnitt(
    titel: String,
    erklaerung: String,
    inhalt: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = titel, style = MaterialTheme.typography.titleMedium)
            Text(
                text = erklaerung,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            inhalt()
        }
    }
}

@Composable
private fun Rueckmeldung(meldung: String, fehler: Boolean, beiSchliessen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (fehler) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = meldung,
                style = MaterialTheme.typography.bodyMedium,
                color = if (fehler) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            OutlinedButton(onClick = beiSchliessen) { Text("Verstanden") }
        }
    }
}
