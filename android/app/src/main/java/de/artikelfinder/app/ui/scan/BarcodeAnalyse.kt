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
 * Einzelbild-Fehlerkennungen sind bei schrägen oder verknitterten Etiketten häufig, und ein
 * falscher Barcode führt hier direkt zum falschen Artikel.
 *
 * Nach einem Treffer wird derselbe Code gesperrt, bis er aus dem Bild verschwindet oder ein
 * anderer erscheint. So zählt ein Etikett, das noch vor der Kamera liegt, nicht doppelt —
 * und beim Abarbeiten des Einkaufs kann Artikel für Artikel gescannt werden, ohne dass der
 * Scanner neu gestartet werden muss.
 */
class BarcodeAnalyse(
    private val scanner: BarcodeScanner,
    private val beiTreffer: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private var letzterKandidat: String? = null
    private var gesperrterCode: String? = null

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(bild: ImageProxy) {
        val medienBild = bild.image
        if (medienBild == null) {
            bild.close()
            return
        }

        val eingabe = InputImage.fromMediaImage(medienBild, bild.imageInfo.rotationDegrees)

        scanner.process(eingabe)
            .addOnSuccessListener { barcodes ->
                val wert = barcodes.firstNotNullOfOrNull { it.gueltigerRohwert() }

                when {
                    // Kein Code im Bild: Sperre lösen, damit derselbe Artikel danach erneut
                    // (bewusst) gezählt werden kann.
                    wert == null -> {
                        letzterKandidat = null
                        gesperrterCode = null
                    }
                    // Der eben gemeldete Code liegt noch vor der Kamera — nicht doppelt zählen.
                    wert == gesperrterCode -> Unit
                    // Zweite Erkennung in Folge bestätigt den Treffer.
                    wert == letzterKandidat -> {
                        gesperrterCode = wert
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
