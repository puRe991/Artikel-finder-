package de.artikelfinder.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.artikelfinder.app.data.Abruf
import de.artikelfinder.app.data.ArtikelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ScanErgebnis {
    /** Artikel ist bekannt — weiter zur Detailseite. */
    data class Gefunden(val artikelId: String) : ScanErgebnis

    /** Barcode unbekannt — die App bietet das Anlegen mit vorbelegter EAN an. */
    data class Unbekannt(val ean: String) : ScanErgebnis
}

data class ScanZustand(
    val prueft: Boolean = false,
    val gescannteEan: String? = null,
    val ergebnis: ScanErgebnis? = null,
    val fehler: String? = null,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: ArtikelRepository,
) : ViewModel() {

    private val _zustand = MutableStateFlow(ScanZustand())
    val zustand: StateFlow<ScanZustand> = _zustand.asStateFlow()

    fun barcodeErkannt(ean: String) {
        // Die Kamera liefert weiter Bilder, während die Abfrage läuft — nur der erste
        // Treffer zählt.
        if (_zustand.value.prueft || _zustand.value.ergebnis != null) return

        _zustand.value = ScanZustand(prueft = true, gescannteEan = ean)

        viewModelScope.launch {
            _zustand.value = when (val ergebnis = repository.perEan(ean)) {
                is Abruf.Erfolg -> _zustand.value.copy(
                    prueft = false,
                    ergebnis = ergebnis.wert
                        ?.let { ScanErgebnis.Gefunden(it.artikel.id) }
                        ?: ScanErgebnis.Unbekannt(ean),
                )

                is Abruf.Fehler -> _zustand.value.copy(prueft = false, fehler = ergebnis.meldung)
            }
        }
    }

    /** Nach einem Fehler oder nach der Rückkehr aus der Detailansicht wieder scanbereit. */
    fun zuruecksetzen() {
        _zustand.value = ScanZustand()
    }
}
