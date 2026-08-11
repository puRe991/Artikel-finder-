package de.artikelfinder.app.ui.markt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.artikelfinder.app.data.Abruf
import de.artikelfinder.app.data.ArtikelRepository
import de.artikelfinder.app.data.Marktgruppe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MarktauswahlZustand(
    val gruppen: List<Marktgruppe> = emptyList(),
    val gewaehlteMarktId: Int? = null,
    val laedt: Boolean = true,
    val fehler: String? = null,
)

/**
 * Die Marktauswahl: welcher Markt gerade gilt. Preise und Gänge hängen am Markt — der
 * Wechsel entscheidet also, welche Zahlen die App zeigt und wohin neue Erfassungen gehen.
 */
@HiltViewModel
class MarktauswahlViewModel @Inject constructor(
    private val repository: ArtikelRepository,
) : ViewModel() {

    private val _zustand = MutableStateFlow(MarktauswahlZustand())
    val zustand: StateFlow<MarktauswahlZustand> = _zustand.asStateFlow()

    init {
        laden()
    }

    fun laden() {
        viewModelScope.launch {
            _zustand.value = _zustand.value.copy(laedt = true, fehler = null)

            _zustand.value = when (val ergebnis = repository.maerkte()) {
                is Abruf.Erfolg -> _zustand.value.copy(
                    gruppen = ergebnis.wert,
                    gewaehlteMarktId = repository.aktiveMarktId(),
                    laedt = false,
                )
                is Abruf.Fehler -> _zustand.value.copy(laedt = false, fehler = ergebnis.meldung)
            }
        }
    }

    /** Wählt den Markt und meldet erst zurück, wenn er wirklich steht. */
    fun waehlen(marktId: Int, beiErfolg: () -> Unit) {
        viewModelScope.launch {
            _zustand.value = when (val ergebnis = repository.marktWaehlen(marktId)) {
                is Abruf.Erfolg -> {
                    beiErfolg()
                    _zustand.value.copy(gewaehlteMarktId = marktId, fehler = null)
                }
                is Abruf.Fehler -> _zustand.value.copy(fehler = ergebnis.meldung)
            }
        }
    }
}
