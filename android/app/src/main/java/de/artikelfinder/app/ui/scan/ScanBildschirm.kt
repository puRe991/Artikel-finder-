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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import de.artikelfinder.app.ui.komponenten.LeerAnzeige
import kotlinx.coroutines.delay
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

    // In der Suche ist der Scan nur Zwischenstation: nach dem Treffer sofort weiter. Im
    // Vorrat-Modus bleibt der Scanner offen, nur unbekannte Codes führen zum Anlegen.
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

    // Die Einbuch-Bestätigung kurz zeigen, dann ausblenden — der Zähler bleibt.
    LaunchedEffect(zustand.letzteMeldung) {
        if (zustand.letzteMeldung != null) {
            delay(2500)
            viewModel.meldungGelesen()
        }
    }

    // Die Kamera füllt den ganzen Bildschirm; Kopfzeile und Hinweis liegen darüber.
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            !hatBerechtigung -> Surface(modifier = Modifier.fillMaxSize()) {
                KeineBerechtigung(
                    beiAnfordern = { berechtigungAnfordern.launch(Manifest.permission.CAMERA) },
                )
            }

            zustand.fehler != null -> Surface(modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) {
                    FehlerAnzeige(meldung = zustand.fehler!!, beiWiederholen = viewModel::zuruecksetzen)
                }
            }

            else -> {
                Kameravorschau(beiBarcode = viewModel::barcodeErkannt)
                Zielrahmen()
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Hinweisleiste(zustand)
                    if (zustand.zumBestand) {
                        Einkaufsleiste(erfasst = zustand.erfassteMenge, beiFertig = beiZurueck)
                    }
                }
            }
        }

        val aufKamera = hatBerechtigung && zustand.fehler == null
        TopAppBar(
            title = { Text(if (zustand.zumBestand) "Einkauf scannen" else "Barcode scannen") },
            navigationIcon = {
                IconButton(onClick = beiZurueck) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                }
            },
            colors = if (aufKamera) {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                )
            } else {
                TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            },
        )
    }
}

/**
 * Abgedunkelter Rand mit einem hellen Rechteck in der Mitte: zeigt, wohin der Barcode gehört.
 * Die Erkennung selbst wertet das ganze Bild aus — der Rahmen ist nur eine Zielhilfe.
 */
@Composable
private fun Zielrahmen() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val breite = size.width * 0.78f
        val hoehe = breite * 0.55f
        val links = (size.width - breite) / 2
        val oben = (size.height - hoehe) / 2.2f
        val ecken = CornerRadius(24.dp.toPx())

        val ausschnitt = Path().apply {
            addRect(Rect(Offset.Zero, size))
            addRoundRect(RoundRect(Rect(Offset(links, oben), Size(breite, hoehe)), ecken))
            fillType = PathFillType.EvenOdd
        }
        drawPath(ausschnitt, Color.Black.copy(alpha = 0.55f))

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(links, oben),
            size = Size(breite, hoehe),
            cornerRadius = ecken,
            style = Stroke(width = 3.dp.toPx()),
        )
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
    val meldung = zustand.letzteMeldung

    Surface(
        color = if (meldung != null) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.7f),
        contentColor = if (meldung != null) MaterialTheme.colorScheme.onPrimary else Color.White,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                zustand.prueft -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    Text(text = "Suche ${zustand.gescannteEan} …", style = MaterialTheme.typography.bodyMedium)
                }

                meldung != null -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = "${meldung.name} · jetzt ${meldung.neuerBestand} zu Hause",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                else -> Text(
                    text = if (zustand.zumBestand) {
                        "Gekaufte Artikel nacheinander in den Rahmen halten"
                    } else {
                        "Barcode in den Rahmen halten"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Nur beim Einkauf-Scannen: wie viel schon gebucht ist, und der Weg zurück. */
@Composable
private fun Einkaufsleiste(erfasst: Int, beiFertig: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Eingebucht",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (erfasst == 1) "1 Stück" else "$erfasst Stück",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Button(onClick = beiFertig) { Text("Fertig") }
        }
    }
}

@Composable
private fun KeineBerechtigung(beiAnfordern: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LeerAnzeige(
            titel = "Kamerazugriff nötig",
            symbol = Icons.Outlined.PhotoCamera,
            hinweis = "Zum Scannen braucht die App Zugriff auf die Kamera. "
                + "Die Bilder werden nur auf dem Gerät ausgewertet und nicht hochgeladen.",
            aktionText = "Kamera freigeben",
            beiAktion = beiAnfordern,
        )
    }
}
