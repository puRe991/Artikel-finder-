package de.artikelfinder.app.ui.einrichtung

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.artikelfinder.app.ui.komponenten.InfoKarte

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EinrichtungBildschirm(
    beiFertig: () -> Unit,
    beiZurueck: (() -> Unit)? = null,
    viewModel: EinrichtungViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    LaunchedEffect(zustand.gespeichert) {
        if (zustand.gespeichert) beiFertig()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Serveradresse") },
                navigationIcon = {
                    beiZurueck?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Auf welchem Rechner läuft die Artikel-Finder-API?",
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = zustand.eingabe,
                onValueChange = viewModel::eingabeGeaendert,
                label = { Text("Adresse") },
                placeholder = { Text("192.168.178.20") },
                supportingText = { Text("Ohne Portangabe wird :5080 ergänzt.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::pruefenUndSpeichern,
                enabled = zustand.kannPruefen,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (zustand.prueft) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp).padding(end = 4.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text("Verbindung testen und speichern")
            }

            zustand.meldung?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (zustand.erfolgreich) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }

            InfoKarte {
                Text("So findest du die Adresse", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Auf dem Rechner, auf dem „dotnet run“ läuft:\n\n"
                        + "• Windows: ipconfig → IPv4-Adresse\n"
                        + "• Linux/Mac: ip addr bzw. ifconfig\n\n"
                        + "Handy und Rechner müssen im selben WLAN sein. "
                        + "Im Android-Emulator ist die Adresse 10.0.2.2.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
