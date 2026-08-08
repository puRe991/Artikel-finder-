package de.artikelfinder.app.ui.markt

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.artikelfinder.app.data.markt.Ketten

/**
 * Marktwahl und Marktwechsel.
 *
 * Beim ersten Start ist noch nichts angelegt, dann ist dieser Bildschirm die Kettenliste
 * und sonst nichts. Später steht oben, was der Nutzer sich angelegt hat — Preise und Gänge
 * hängen am Markt, „Kaufland Gießen" und „Kaufland Wetzlar" sind zwei verschiedene Läden
 * mit verschiedenen Gängen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaerkteBildschirm(
    ersteWahl: Boolean,
    beiFertig: () -> Unit,
    beiZurueck: (() -> Unit)? = null,
    viewModel: MaerkteViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    var ketteFuerOrt by rememberSaveable { mutableStateOf<String?>(null) }
    var zuLoeschen by rememberSaveable { mutableStateOf<Int?>(null) }

    ketteFuerOrt?.let { kette ->
        OrtsDialog(
            kettenname = Ketten.kette(kette)?.name ?: kette,
            beiAbbrechen = { ketteFuerOrt = null },
            beiBestaetigen = { ort ->
                ketteFuerOrt = null
                viewModel.anlegen(kette, ort, beiFertig)
            },
        )
    }

    zuLoeschen?.let { id ->
        val markt = zustand.eigene.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { zuLoeschen = null },
            title = { Text("${markt?.name ?: "Markt"} entfernen?") },
            text = {
                Text(
                    "Die dort erfassten Preise und Gänge bleiben in der Datenbank und in " +
                        "der Sicherung, sind in der App aber nicht mehr zu sehen."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.entfernen(id)
                    zuLoeschen = null
                }) { Text("Entfernen") }
            },
            dismissButton = { TextButton(onClick = { zuLoeschen = null }) { Text("Abbrechen") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ersteWahl) "In welchem Markt kaufst du ein?" else "Märkte") },
                navigationIcon = {
                    beiZurueck?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                },
                actions = {
                    if (!ersteWahl && zustand.eigene.isNotEmpty()) {
                        IconButton(onClick = viewModel::hinzufuegenUmschalten) {
                            Icon(Icons.Default.Add, contentDescription = "Markt hinzufügen")
                        }
                    }
                },
            )
        },
    ) { abstand ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(abstand),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (ersteWahl) {
                item {
                    Text(
                        text = "Preise und Gänge hängen am Markt — dieselbe Butter steht " +
                            "woanders im Regal und kostet woanders anders. Die Wahl blendet " +
                            "außerdem Eigenmarken fremder Ketten aus; „ja!\" gibt es im " +
                            "Kaufland nicht. Weitere Märkte kannst du jederzeit ergänzen.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            if (zustand.eigene.isNotEmpty()) {
                item { Abschnittstitel("Meine Märkte") }

                items(items = zustand.eigene, key = { it.id }) { markt ->
                    Card(
                        onClick = { viewModel.waehlen(markt.id, beiFertig) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (markt.id == zustand.aktuellerId) {
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        } else {
                            CardDefaults.cardColors()
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null)

                            Column(modifier = Modifier.weight(1f)) {
                                Text(markt.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = Ketten.kette(markt.kette)?.name ?: markt.kette,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (markt.id == zustand.aktuellerId) {
                                Icon(Icons.Default.Check, contentDescription = "Gewählt")
                            } else {
                                IconButton(onClick = { zuLoeschen = markt.id }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Entfernen")
                                }
                            }
                        }
                    }
                }
            }

            if (zustand.hinzufuegenOffen) {
                if (zustand.eigene.isNotEmpty()) item { Abschnittstitel("Markt hinzufügen") }

                items(items = zustand.ketten, key = { it.schluessel }) { kette ->
                    Card(
                        onClick = { ketteFuerOrt = kette.schluessel },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = kette.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Abschnittstitel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/**
 * Der Ort ist freiwillig. Wer nur in einer Filiale einkauft, braucht ihn nicht — wer zwei
 * Kauflands nutzt, kann sie ohne ihn nicht auseinanderhalten.
 */
@Composable
private fun OrtsDialog(
    kettenname: String,
    beiAbbrechen: () -> Unit,
    beiBestaetigen: (String?) -> Unit,
) {
    var ort by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = beiAbbrechen,
        title = { Text(kettenname) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Wenn du mehrere Filialen dieser Kette nutzt, hilft der Ort beim " +
                        "Auseinanderhalten.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = ort,
                    onValueChange = { ort = it },
                    label = { Text("Ort (freiwillig)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { beiBestaetigen(ort.ifBlank { null }) }) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = beiAbbrechen) { Text("Abbrechen") } },
    )
}
