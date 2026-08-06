package de.artikelfinder.app.ui.bearbeiten

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.artikelfinder.app.ui.komponenten.AbschnittsTitel
import de.artikelfinder.app.ui.komponenten.LadeAnzeige

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BearbeitenBildschirm(
    beiZurueck: () -> Unit,
    beiGespeichert: (String) -> Unit,
    viewModel: BearbeitenViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    LaunchedEffect(zustand.gespeicherteArtikelId) {
        zustand.gespeicherteArtikelId?.let(beiGespeichert)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (zustand.istNeuanlage) "Artikel anlegen" else "Artikel bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = beiZurueck) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { abstand ->
        if (zustand.laedt) {
            LadeAnzeige(modifier = Modifier.padding(abstand))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(abstand)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = zustand.name,
                onValueChange = viewModel::nameGeaendert,
                label = { Text("Name *") },
                singleLine = true,
                isError = zustand.name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = zustand.marke,
                onValueChange = viewModel::markeGeaendert,
                label = { Text("Marke") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = zustand.ean,
                onValueChange = viewModel::eanGeaendert,
                label = { Text("EAN") },
                supportingText = { Text("Wird beim Scannen automatisch gefüllt.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = zustand.artikelnummer,
                onValueChange = viewModel::artikelnummerGeaendert,
                label = { Text("Artikelnummer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            AbschnittsTitel("Kategorie")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = zustand.kategorieId == null,
                    onClick = { viewModel.kategorieGewaehlt(null) },
                    label = { Text("Keine") },
                )
                zustand.kategorien.forEach { kategorie ->
                    FilterChip(
                        selected = zustand.kategorieId == kategorie.id,
                        onClick = { viewModel.kategorieGewaehlt(kategorie.id) },
                        label = { Text(kategorie.name) },
                    )
                }
            }

            // Preis und Standort nur bei der Neuanlage: bei bestehenden Artikeln laufen sie
            // über die Detailseite, damit die Historie nicht versehentlich überschrieben wirkt.
            if (zustand.istNeuanlage) {
                AbschnittsTitel("Preis (optional)")
                OutlinedTextField(
                    value = zustand.preis,
                    onValueChange = viewModel::preisGeaendert,
                    label = { Text("Normalpreis in €") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = zustand.werbepreis,
                    onValueChange = viewModel::werbepreisGeaendert,
                    label = { Text("Werbepreis in €") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                AbschnittsTitel("Standort (optional)")
                OutlinedTextField(
                    value = zustand.gang,
                    onValueChange = viewModel::gangGeaendert,
                    label = { Text("Gang") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = zustand.regalBeschreibung,
                    onValueChange = viewModel::regalGeaendert,
                    label = { Text("Regal") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = zustand.erfasstVon,
                onValueChange = viewModel::erfasstVonGeaendert,
                label = { Text("Erfasst von") },
                supportingText = { Text("Taucht im Änderungsverlauf auf.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            zustand.fehler?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = viewModel::speichern,
                enabled = zustand.kannSpeichern,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                if (zustand.speichert) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text("Speichern")
            }
        }
    }
}
