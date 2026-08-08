package de.artikelfinder.app.ui.markt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.artikelfinder.app.data.local.MarktEintrag
import de.artikelfinder.app.data.markt.Ketten
import de.artikelfinder.app.data.markt.Marktverwaltung
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MaerkteZustand(
    val eigene: List<MarktEintrag> = emptyList(),
    val aktuellerId: Int? = null,
    val ketten: List<Ketten.Kette> = Ketten.ALLE,
    val hinzufuegenOffen: Boolean = false,
)

@HiltViewModel
class MaerkteViewModel @Inject constructor(
    private val maerkte: Marktverwaltung,
) : ViewModel() {

    private val _zustand = MutableStateFlow(MaerkteZustand())
    val zustand: StateFlow<MaerkteZustand> = _zustand.asStateFlow()

    init {
        combine(maerkte.alle(), maerkte.aktuell) { alle, aktuell -> alle to aktuell }
            .onEach { (alle, aktuell) ->
                _zustand.value = _zustand.value.copy(
                    eigene = alle,
                    aktuellerId = aktuell?.id,
                    // Ist noch kein Markt angelegt, ist die Kettenliste der ganze Bildschirm.
                    hinzufuegenOffen = _zustand.value.hinzufuegenOffen || alle.isEmpty(),
                )
            }
            .launchIn(viewModelScope)
    }

    fun hinzufuegenUmschalten() {
        _zustand.value = _zustand.value.copy(hinzufuegenOffen = !_zustand.value.hinzufuegenOffen)
    }

    fun anlegen(ketteSchluessel: String, ort: String?, beiFertig: () -> Unit) {
        viewModelScope.launch {
            maerkte.anlegen(ketteSchluessel, ort)
            _zustand.value = _zustand.value.copy(hinzufuegenOffen = false)
            beiFertig()
        }
    }

    fun waehlen(marktId: Int, beiFertig: () -> Unit) {
        viewModelScope.launch {
            maerkte.waehlen(marktId)
            beiFertig()
        }
    }

    fun entfernen(marktId: Int) {
        viewModelScope.launch { maerkte.entfernen(marktId) }
    }
}
