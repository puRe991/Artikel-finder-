package de.artikelfinder.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.artikelfinder.app.data.Aufbauzustand
import de.artikelfinder.app.ui.ArtikelFinderNavigation
import de.artikelfinder.app.ui.StartViewModel
import de.artikelfinder.app.ui.Startzustand
import de.artikelfinder.app.ui.markt.MaerkteBildschirm
import de.artikelfinder.app.ui.theme.ArtikelFinderTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ArtikelFinderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val startViewModel: StartViewModel = hiltViewModel()
                    val zustand by startViewModel.zustand.collectAsStateWithLifecycle()

                    when (val aktuell = zustand) {
                        is Startzustand.Bereit -> ArtikelFinderNavigation()

                        // Ohne Markt weiss die App nicht, zu welchem Laden ein erfasster
                        // Preis gehoert — deshalb geht es hier nicht ohne Wahl weiter.
                        is Startzustand.MarktWaehlen -> MaerkteBildschirm(
                            ersteWahl = true,
                            beiFertig = startViewModel::marktGewaehlt,
                        )

                        is Startzustand.Fehlgeschlagen -> Aufbaufehler(
                            meldung = aktuell.meldung,
                            beiWiederholen = startViewModel::starten,
                        )

                        is Startzustand.Katalogaufbau -> Katalogaufbau(aktuell.fortschritt)
                        is Startzustand.Laedt -> Katalogaufbau(Aufbauzustand.Pruefen)
                    }
                }
            }
        }
    }
}

@Composable
private fun Katalogaufbau(zustand: Aufbauzustand) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Artikel-Finder", style = MaterialTheme.typography.headlineMedium)

        Text(
            text = "Der Artikelkatalog wird eingerichtet. Das passiert nur beim ersten Start.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        if (zustand is Aufbauzustand.Laeuft && zustand.gesamt > 0) {
            LinearProgressIndicator(
                progress = { zustand.erledigt.toFloat() / zustand.gesamt },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${zustand.erledigt} von ${zustand.gesamt} Artikeln",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun Aufbaufehler(meldung: String, beiWiederholen: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Der Katalog konnte nicht eingerichtet werden.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = meldung,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Button(onClick = beiWiederholen) { Text("Erneut versuchen") }
    }
}
