package de.artikelfinder.app.ui.gaenge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.artikelfinder.app.data.Abruf
import de.artikelfinder.app.data.Artikel
import de.artikelfinder.app.data.ArtikelRepository
import de.artikelfinder.app.data.Gang
import de.artikelfinder.app.data.Markt
import de.artikelfinder.app.ui.navigation.Ziele
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GaengeZustand(
    val markt: Markt? = null,
    val gaenge: List<Gang> = emptyList(),
    val laedt: Boolean = true,
    val fehler: String? = null,
)

/**
 * Standortübersicht des MVP: eine Liste der belegten Gänge. Die interaktive
 * Grundriss-Karte aus Phase 2 ersetzt später die Liste, nutzt aber dieselben Daten —
 * die Koordinatenfelder liegen dafür schon im Standort-Modell.
 */
@HiltViewModel
class GaengeViewModel @Inject constructor(
    private val repository: ArtikelRepository,
) : ViewModel() {

    private val _zustand = MutableStateFlow(GaengeZustand())
    val zustand: StateFlow<GaengeZustand> = _zustand.asStateFlow()

    init {
        laden()
    }

    fun laden() {
        viewModelScope.launch {
            _zustand.value = _zustand.value.copy(laedt = true, fehler = null)

            when (val markt = repository.standardMarkt()) {
                is Abruf.Erfolg -> {
                    _zustand.value = _zustand.value.copy(markt = markt.wert)

                    _zustand.value = when (val gaenge = repository.gaenge(markt.wert.id)) {
                        is Abruf.Erfolg -> _zustand.value.copy(gaenge = gaenge.wert, laedt = false)
                        is Abruf.Fehler -> _zustand.value.copy(laedt = false, fehler = gaenge.meldung)
                    }
                }
                is Abruf.Fehler -> _zustand.value =
                    _zustand.value.copy(laedt = false, fehler = markt.meldung)
            }
        }
    }
}

data class GangArtikelZustand(
    val gang: String = "",
    val artikel: List<Artikel> = emptyList(),
    val laedt: Boolean = true,
    val fehler: String? = null,
)

@HiltViewModel
class GangArtikelViewModel @Inject constructor(
    private val repository: ArtikelRepository,
    zustandHalter: SavedStateHandle,
) : ViewModel() {

    private val gang: String = checkNotNull(zustandHalter[Ziele.ARG_GANG])

    private val _zustand = MutableStateFlow(GangArtikelZustand(gang = gang))
    val zustand: StateFlow<GangArtikelZustand> = _zustand.asStateFlow()

    init {
        laden()
    }

    fun laden() {
        viewModelScope.launch {
            _zustand.value = _zustand.value.copy(laedt = true, fehler = null)

            val markt = repository.standardMarkt()
            if (markt !is Abruf.Erfolg) {
                _zustand.value = _zustand.value.copy(
                    laedt = false,
                    fehler = (markt as Abruf.Fehler).meldung,
                )
                return@launch
            }

            _zustand.value = when (val artikel = repository.artikelImGang(markt.wert.id, gang)) {
                is Abruf.Erfolg -> _zustand.value.copy(artikel = artikel.wert, laedt = false)
                is Abruf.Fehler -> _zustand.value.copy(laedt = false, fehler = artikel.meldung)
            }
        }
    }
}
