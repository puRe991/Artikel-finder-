package de.artikelfinder.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.artikelfinder.app.data.Aufbauzustand
import de.artikelfinder.app.data.Katalogaufbau
import de.artikelfinder.app.data.local.MarktEintrag
import de.artikelfinder.app.data.markt.Marktverwaltung
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Was der Startbildschirm zeigen muss, bevor die App benutzbar ist. */
sealed interface Startzustand {
    data object Laedt : Startzustand
    data class Katalogaufbau(val fortschritt: Aufbauzustand) : Startzustand
    data class Fehlgeschlagen(val meldung: String) : Startzustand

    /** Katalog steht, aber es ist noch kein Markt gewählt. */
    data object MarktWaehlen : Startzustand

    data class Bereit(val markt: MarktEintrag) : Startzustand
}

/**
 * Beim ersten Start wird der mitgelieferte Katalog in die Datenbank geschrieben — das
 * dauert einige Sekunden und braucht eine sichtbare Rückmeldung. Danach fehlt noch der
 * Markt: ohne ihn wüsste die App nicht, zu welchem Laden ein erfasster Preis gehört und
 * welche Eigenmarken dort überhaupt im Regal stehen.
 */
@HiltViewModel
class StartViewModel @Inject constructor(
    private val aufbau: Katalogaufbau,
    private val maerkte: Marktverwaltung,
) : ViewModel() {

    private val _zustand = MutableStateFlow<Startzustand>(Startzustand.Laedt)
    val zustand: StateFlow<Startzustand> = _zustand.asStateFlow()

    init {
        starten()
    }

    fun starten() {
        viewModelScope.launch {
            aufbau.zustand.collect { fortschritt ->
                _zustand.value = when (fortschritt) {
                    is Aufbauzustand.Fehlgeschlagen -> Startzustand.Fehlgeschlagen(fortschritt.meldung)
                    is Aufbauzustand.Fertig -> marktzustand()
                    else -> Startzustand.Katalogaufbau(fortschritt)
                }
            }
        }

        viewModelScope.launch { aufbau.sicherstellen() }
    }

    /** Nach der Marktwahl aufzurufen, damit der Startbildschirm weiterschaltet. */
    fun marktGewaehlt() {
        viewModelScope.launch { _zustand.value = marktzustand() }
    }

    private suspend fun marktzustand(): Startzustand {
        maerkte.laden()
        return maerkte.aktuell.value?.let(Startzustand::Bereit) ?: Startzustand.MarktWaehlen
    }
}
