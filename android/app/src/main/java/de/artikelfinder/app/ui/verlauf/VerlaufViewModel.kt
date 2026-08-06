package de.artikelfinder.app.ui.verlauf

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.artikelfinder.app.data.Abruf
import de.artikelfinder.app.data.ArtikelRepository
import de.artikelfinder.app.data.Verlaufseintrag
import de.artikelfinder.app.ui.navigation.Ziele
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VerlaufZustand(
    val eintraege: List<Verlaufseintrag> = emptyList(),
    val laedt: Boolean = true,
    val fehler: String? = null,
)

@HiltViewModel
class VerlaufViewModel @Inject constructor(
    private val repository: ArtikelRepository,
    zustandHalter: SavedStateHandle,
) : ViewModel() {

    private val artikelId: String = checkNotNull(zustandHalter[Ziele.ARG_ARTIKEL_ID])

    private val _zustand = MutableStateFlow(VerlaufZustand())
    val zustand: StateFlow<VerlaufZustand> = _zustand.asStateFlow()

    init {
        laden()
    }

    fun laden() {
        viewModelScope.launch {
            _zustand.value = _zustand.value.copy(laedt = true, fehler = null)

            _zustand.value = when (val ergebnis = repository.verlauf(artikelId)) {
                is Abruf.Erfolg -> _zustand.value.copy(eintraege = ergebnis.wert, laedt = false)
                is Abruf.Fehler -> _zustand.value.copy(laedt = false, fehler = ergebnis.meldung)
            }
        }
    }
}
