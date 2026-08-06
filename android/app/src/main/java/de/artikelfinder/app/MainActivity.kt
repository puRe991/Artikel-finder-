package de.artikelfinder.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.artikelfinder.app.ui.ArtikelFinderNavigation
import de.artikelfinder.app.ui.StartViewModel
import de.artikelfinder.app.ui.navigation.Ziele
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
                    val istEingerichtet by startViewModel.istEingerichtet.collectAsStateWithLifecycle()

                    // null = Einstellung noch nicht gelesen. Erst danach das Ziel festlegen,
                    // sonst blitzt kurz der falsche Bildschirm auf.
                    when (istEingerichtet) {
                        null -> Unit
                        true -> ArtikelFinderNavigation(startZiel = Ziele.SUCHE)
                        false -> ArtikelFinderNavigation(startZiel = Ziele.EINRICHTUNG)
                    }
                }
            }
        }
    }
}
