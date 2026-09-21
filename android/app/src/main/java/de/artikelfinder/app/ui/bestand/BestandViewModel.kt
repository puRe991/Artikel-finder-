package de.artikelfinder.app.ui.bestand

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.artikelfinder.app.data.ArtikelRepository
import de.artikelfinder.app.data.Bestand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BestandZustand(
    val eintraege: List<Bestand> = emptyList(),
    val laedt: Boolean = true,
) {
    val anzahlArtikel: Int get() = eintraege.size
    val gesamtMenge: Int get() = eintraege.sumOf { it.menge }

    /** Wert des aktuellen Vorrats zum jeweils letzten bekannten Stückpreis. */
    val gesamtwert: Double get() = eintraege.sumOf { it.bedarf.bestandswert ?: 0.0 }

    /** Voraussichtliche Kosten pro Monat über alle Artikel mit Bedarfsschätzung. */
    val monatskosten: Double get() = eintraege.sumOf { it.bedarf.monatskosten ?: 0.0 }

    val nachzukaufen: List<Bestand> get() = eintraege.filter { it.bedarf.nachkaufEmpfohlen }
}

/**
 * Der eigene Vorrat zu Hause: was da ist, was zur Neige geht und was er im Monat kostet.
 * Die Liste kommt als Fluss aus der Datenbank und aktualisiert sich nach jeder Buchung von
 * selbst.
 */
@HiltViewModel
class BestandViewModel @Inject constructor(
    private val repository: ArtikelRepository,
) : ViewModel() {

    private val _zustand = MutableStateFlow(BestandZustand())
    val zustand: StateFlow<BestandZustand> = _zustand.asStateFlow()

    init {
        repository.bestandsUebersicht()
            .onEach { liste -> _zustand.value = BestandZustand(eintraege = liste, laedt = false) }
            .launchIn(viewModelScope)
    }

    fun einkauf(artikelId: String) {
        viewModelScope.launch { repository.einkaufErfassen(artikelId) }
    }

    fun verbrauch(artikelId: String) {
        viewModelScope.launch { repository.verbrauchErfassen(artikelId) }
    }
}
