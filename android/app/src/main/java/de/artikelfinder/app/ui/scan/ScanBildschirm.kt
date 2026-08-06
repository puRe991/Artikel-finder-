package de.artikelfinder.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import de.artikelfinder.app.ui.komponenten.FehlerAnzeige
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanBildschirm(
    beiArtikel: (String) -> Unit,
    beiUnbekannterEan: (String) -> Unit,
    beiZurueck: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hatBerechtigung by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val berechtigungAnfordern = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { erteilt -> hatBerechtigung = erteilt }

    LaunchedEffect(Unit) {
        if (!hatBerechtigung) {
            berechtigungAnfordern.launch(Manifest.permission.CAMERA)
        }
    }

    // Der Scan-Bildschirm ist nur Zwischenstation: nach dem Treffer sofort weiter, und den
    // Zustand zurücksetzen, damit der nächste Aufruf wieder scanbereit ist.
    LaunchedEffect(zustand.ergebnis) {
        when (val ergebnis = zustand.ergebnis) {
            is ScanErgebnis.Gefunden -> {
                viewModel.zuruecksetzen()
                beiArtikel(ergebnis.artikelId)
            }
            is ScanErgebnis.Unbekannt -> {
                viewModel.zuruecksetzen()
                beiUnbekannterEan(ergebnis.ean)
            }
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Barcode scannen") },
                navigationIcon = {
                    IconButton(onClick = beiZurueck) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { abstand ->
        Box(modifier = Modifier.fillMaxSize().padding(abstand)) {
            when {
                !hatBerechtigung -> KeineBerechtigung(
                    beiAnfordern = { berechtigungAnfordern.launch(Manifest.permission.CAMERA) },
                )

                zustand.fehler != null -> FehlerAnzeige(
                    meldung = zustand.fehler!!,
                    beiWiederholen = viewModel::zuruecksetzen,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> {
                    Kameravorschau(beiBarcode = viewModel::barcodeErkannt)
                    Hinweisleiste(zustand, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

@Composable
private fun Kameravorschau(beiBarcode: (String) -> Unit) {
    val context = LocalContext.current
    val lebenszyklus = LocalLifecycleOwner.current

    // Eigener Thread für die Bildanalyse: auf dem Hauptthread würde die Vorschau ruckeln.
    val analyseAusfuehrer = remember { Executors.newSingleThreadExecutor() }

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_ITF,
                )
                .build()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            analyseAusfuehrer.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val vorschauAnsicht = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val anbieterZukunft = ProcessCameraProvider.getInstance(ctx)
            anbieterZukunft.addListener({
                val anbieter = anbieterZukunft.get()

                val vorschau = Preview.Builder().build().also {
                    it.setSurfaceProvider(vorschauAnsicht.surfaceProvider)
                }

                val analyse = ImageAnalysis.Builder()
                    // Ältere Bilder verwerfen: der Nutzer hält die Kamera bereits woanders hin.
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(analyseAusfuehrer, BarcodeAnalyse(scanner, beiBarcode))
                    }

                runCatching {
                    anbieter.unbindAll()
                    anbieter.bindToLifecycle(
                        lebenszyklus,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        vorschau,
                        analyse,
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))

            vorschauAnsicht
        },
    )
}

@Composable
private fun Hinweisleiste(zustand: ScanZustand, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (zustand.prueft) {
                CircularProgressIndicator()
                Text(
                    text = "Suche ${zustand.gescannteEan} …",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = "Barcode in den Bildausschnitt halten",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun KeineBerechtigung(beiAnfordern: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Zum Scannen braucht die App Zugriff auf die Kamera. "
                + "Die Bilder werden nur auf dem Gerät ausgewertet und nicht hochgeladen.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = beiAnfordern, modifier = Modifier.padding(top = 16.dp)) {
            Text("Kamera freigeben")
        }
    }
}
