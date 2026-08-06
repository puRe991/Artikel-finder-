package de.artikelfinder.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.artikelfinder.app.data.Abruf
import de.artikelfinder.app.data.ArtikelDetail
import de.artikelfinder.app.data.ArtikelRepository
import de.artikelfinder.app.ui.navigation.Ziele
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailZustand(
    val detail: ArtikelDetail? = null,
    val laedt: Boolean = true,
    val fehler: String? = null,
    val speichert: Boolean = false,
    /** Einmalige Rückmeldung für eine Snackbar. */
    val meldung: String? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: ArtikelRepository,
    zustandHalter: SavedStateHandle,
) : ViewModel() {

    private val artikelId: String = checkNotNull(zustandHalter[Ziele.ARG_ARTIKEL_ID])

    private val _zustand = MutableStateFlow(DetailZustand())
    val zustand: StateFlow<DetailZustand> = _zustand.asStateFlow()

    init {
        laden()
    }

    fun laden() {
        viewModelScope.launch {
            _zustand.value = _zustand.value.copy(laedt = true, fehler = null)

            _zustand.value = when (val ergebnis = repository.holen(artikelId)) {
                is Abruf.Erfolg -> _zustand.value.copy(
                    detail = ergebnis.wert,
                    laedt = false,
                    fehler = null,
                )
                is Abruf.Fehler -> _zustand.value.copy(laedt = false, fehler = ergebnis.meldung)
            }
        }
    }

    fun preisErfassen(
        preis: Double,
        werbepreis: Double?,
        werbepreisGueltigBis: Long?,
        erfasstVon: String?,
    ) {
        speichern { repository.preisErfassen(artikelId, preis, werbepreis, werbepreisGueltigBis, erfasstVon) }
    }

    fun standortErfassen(gang: String, regalBeschreibung: String?, erfasstVon: String?) {
        speichern { repository.standortErfassen(artikelId, gang, regalBeschreibung, erfasstVon) }
    }

    fun meldungGelesen() {
        _zustand.value = _zustand.value.copy(meldung = null)
    }

    /** Nach jeder Erfassung neu laden, damit Preis, Standort und Verlauf zusammenpassen. */
    private fun speichern(block: suspend () -> Abruf<*>) {
        viewModelScope.launch {
            _zustand.value = _zustand.value.copy(speichert = true)

            when (val ergebnis = block()) {
                is Abruf.Erfolg -> {
                    _zustand.value = _zustand.value.copy(speichert = false, meldung = "Gespeichert.")
                    laden()
                }
                is Abruf.Fehler -> _zustand.value = _zustand.value.copy(
                    speichert = false,
                    meldung = ergebnis.meldung,
                )
            }
        }
    }
}
