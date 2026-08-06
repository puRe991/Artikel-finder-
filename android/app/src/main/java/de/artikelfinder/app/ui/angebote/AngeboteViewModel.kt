package de.artikelfinder.app.ui.angebote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.artikelfinder.app.data.Artikel
import de.artikelfinder.app.data.ArtikelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class AngebotZeile(
    val artikel: Artikel,
    /** Verbleibende volle Tage, `null` wenn die Aktion kein Enddatum hat. */
    val tageUebrig: Long?,
) {
    /** Ab hier lohnt der Hinweis „schnell noch mitnehmen". */
    val laeuftBaldAb: Boolean get() = tageUebrig != null && tageUebrig <= 1

    val ersparnis: Double?
        get() = artikel.preis?.let { p ->
            p.werbepreis?.let { w -> (p.preis - w).takeIf { it > 0 } }
        }
}

data class AngeboteZustand(
    val angebote: List<AngebotZeile> = emptyList(),
    val laedt: Boolean = true,
) {
    val gesamtErsparnis: Double get() = angebote.sumOf { it.ersparnis ?: 0.0 }
}

/**
 * Übersicht der laufenden Werbepreise. Beantwortet die Frage, die im Markt zählt:
 * was ist gerade im Angebot und was läuft demnächst aus.
 */
@HiltViewModel
class AngeboteViewModel @Inject constructor(repository: ArtikelRepository) : ViewModel() {

    private val _zustand = MutableStateFlow(AngeboteZustand())
    val zustand: StateFlow<AngeboteZustand> = _zustand.asStateFlow()

    init {
        repository.aktiveAngebote()
            .onEach { liste ->
                val jetzt = System.currentTimeMillis()

                _zustand.value = AngeboteZustand(
                    angebote = liste.map { artikel ->
                        AngebotZeile(
                            artikel = artikel,
                            tageUebrig = artikel.preis?.werbepreisGueltigBis?.let {
                                TimeUnit.MILLISECONDS.toDays((it - jetzt).coerceAtLeast(0))
                            },
                        )
                    },
                    laedt = false,
                )
            }
            .launchIn(viewModelScope)
    }
}
