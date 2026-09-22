package de.artikelfinder.app.ui.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.artikelfinder.app.data.Abruf
import de.artikelfinder.app.data.ArtikelRepository
import de.artikelfinder.app.ui.navigation.Ziele
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

sealed interface ScanErgebnis {
    /** Artikel ist bekannt — weiter zur Detailseite. */
    data class Gefunden(val artikelId: String) : ScanErgebnis

    /** Barcode unbekannt — die App bietet das Anlegen mit vorbelegter EAN an. */
    data class Unbekannt(val ean: String) : ScanErgebnis
}

/** Rückmeldung nach dem Einbuchen eines gekauften Artikels in den Vorrat. */
data class BestandsMeldung(val name: String, val neuerBestand: Int)

data class ScanZustand(
    val zumBestand: Boolean = false,
    val prueft: Boolean = false,
    val gescannteEan: String? = null,
    val ergebnis: ScanErgebnis? = null,
    /** Nur im Bestand-Modus: die zuletzt eingebuchte Ware. */
    val letzteMeldung: BestandsMeldung? = null,
    /** Nur im Bestand-Modus: wie viel in dieser Sitzung erfasst wurde. */
    val erfassteArtikel: Int = 0,
    val erfassteMenge: Int = 0,
    val fehler: String? = null,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: ArtikelRepository,
    zustandHalter: SavedStateHandle,
) : ViewModel() {

    private val zumBestand: Boolean =
        zustandHalter.get<String>(Ziele.ARG_ZWECK) == Ziele.ZWECK_BESTAND

    private val _zustand = MutableStateFlow(ScanZustand(zumBestand = zumBestand))
    val zustand: StateFlow<ScanZustand> = _zustand.asStateFlow()

    // Serialisiert die Bestandsbuchungen beim fortlaufenden Scannen — die Kamera kann
    // mehrere Treffer melden, bevor eine Buchung durch ist.
    private val schloss = Mutex()

    fun barcodeErkannt(ean: String) {
        if (zumBestand) einkaufBuchen(ean) else artikelSuchen(ean)
    }

    private fun artikelSuchen(ean: String) {
        // Die Kamera liefert weiter Bilder, während die Abfrage läuft — nur der erste
        // Treffer zählt.
        if (_zustand.value.prueft || _zustand.value.ergebnis != null) return

        _zustand.value = _zustand.value.copy(prueft = true, gescannteEan = ean)

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

    private fun einkaufBuchen(ean: String) {
        // Unbekannte EAN führt zum Anlegen — danach nicht weiterbuchen, bis quittiert.
        if (_zustand.value.ergebnis != null) return

        viewModelScope.launch {
            schloss.withLock {
                if (_zustand.value.ergebnis != null) return@withLock
                _zustand.update { it.copy(prueft = true, gescannteEan = ean) }

                when (val ergebnis = repository.einkaufPerEan(ean)) {
                    is Abruf.Erfolg -> {
                        val bestaetigung = ergebnis.wert
                        _zustand.update { zustand ->
                            if (bestaetigung == null) {
                                zustand.copy(prueft = false, ergebnis = ScanErgebnis.Unbekannt(ean))
                            } else {
                                zustand.copy(
                                    prueft = false,
                                    letzteMeldung = BestandsMeldung(
                                        bestaetigung.name, bestaetigung.neuerBestand
                                    ),
                                    erfassteArtikel = zustand.erfassteArtikel + 1,
                                    erfassteMenge = zustand.erfassteMenge + 1,
                                )
                            }
                        }
                    }

                    is Abruf.Fehler ->
                        _zustand.update { it.copy(prueft = false, fehler = ergebnis.meldung) }
                }
            }
        }
    }

    /** Nach einem Fehler oder der Rückkehr aus einem Folgebildschirm wieder scanbereit. */
    fun zuruecksetzen() {
        _zustand.update {
            it.copy(prueft = false, gescannteEan = null, ergebnis = null, fehler = null)
        }
    }

    /** Die eingeblendete Bestätigung wurde angezeigt — der Zähler bleibt erhalten. */
    fun meldungGelesen() {
        _zustand.update { it.copy(letzteMeldung = null) }
    }
}
