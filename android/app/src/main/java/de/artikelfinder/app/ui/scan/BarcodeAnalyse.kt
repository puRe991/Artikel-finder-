package de.artikelfinder.app.ui.scan

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Wertet Kamerabilder lokal auf dem Gerät aus (ML Kit, kein Netzverkehr).
 *
 * Ein Treffer wird nur gemeldet, wenn derselbe Code zweimal hintereinander erkannt wurde.
 * Einzelbild-Fehlerkennungen sind bei schrägen oder verknitterten Etiketten häufig, und
 * ein falscher Barcode führt hier direkt zum falschen Artikel.
 */
class BarcodeAnalyse(
    private val scanner: BarcodeScanner,
    private val beiTreffer: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private var letzterKandidat: String? = null
    private var bestaetigt = false

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(bild: ImageProxy) {
        val medienBild = bild.image
        if (medienBild == null || bestaetigt) {
            bild.close()
            return
        }

        val eingabe = InputImage.fromMediaImage(medienBild, bild.imageInfo.rotationDegrees)

        scanner.process(eingabe)
            .addOnSuccessListener { barcodes ->
                val wert = barcodes.firstNotNullOfOrNull { it.gueltigerRohwert() }

                when {
                    wert == null -> letzterKandidat = null
                    wert == letzterKandidat -> {
                        bestaetigt = true
                        beiTreffer(wert)
                    }
                    else -> letzterKandidat = wert
                }
            }
            // Ohne dieses close() blockiert die Analysekette nach wenigen Bildern.
            .addOnCompleteListener { bild.close() }
    }

    private fun Barcode.gueltigerRohwert(): String? {
        val wert = rawValue?.trim() ?: return null

        // Nur Produktbarcodes: QR-Codes und Textcodes am Regal sind hier Rauschen.
        val istProduktformat = format in setOf(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_ITF,
        )

        return wert.takeIf { istProduktformat && it.length in 8..14 && it.all(Char::isDigit) }
    }
}
