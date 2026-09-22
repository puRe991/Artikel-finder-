package de.artikelfinder.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.artikelfinder.app.data.Aufbauzustand
import de.artikelfinder.app.ui.ArtikelFinderNavigation
import de.artikelfinder.app.ui.StartViewModel
import de.artikelfinder.app.ui.theme.Abstand
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
                        is Aufbauzustand.Fertig -> ArtikelFinderNavigation()
                        is Aufbauzustand.Fehlgeschlagen -> Aufbaufehler(
                            meldung = aktuell.meldung,
                            beiWiederholen = startViewModel::starten,
                        )
                        else -> Katalogaufbau(aktuell)
                    }
                }
            }
        }
    }
}

@Composable
private fun Katalogaufbau(zustand: Aufbauzustand) {
    Startrahmen {
        Text(
            text = "Der Artikelkatalog wird eingerichtet. Das passiert nur beim ersten Start.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Abstand.eng, bottom = 32.dp),
        )

        if (zustand is Aufbauzustand.Laeuft && zustand.gesamt > 0) {
            LinearProgressIndicator(
                progress = { zustand.erledigt.toFloat() / zustand.gesamt },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                strokeCap = StrokeCap.Round,
            )
            Text(
                text = "${zustand.erledigt} von ${zustand.gesamt} Artikeln",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Abstand.eng),
            )
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun Aufbaufehler(meldung: String, beiWiederholen: () -> Unit) {
    Startrahmen {
        Text(
            text = "Der Katalog konnte nicht eingerichtet werden.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Abstand.eng),
        )
        Text(
            text = meldung,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = Abstand.block),
        )
        Button(onClick = beiWiederholen) { Text("Erneut versuchen") }
    }
}

/** App-Zeichen und Name über dem jeweiligen Inhalt des Startvorgangs. */
@Composable
private fun Startrahmen(inhalt: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Text(
            text = "Artikel-Finder",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = Abstand.rand),
        )
        inhalt()
    }
}
