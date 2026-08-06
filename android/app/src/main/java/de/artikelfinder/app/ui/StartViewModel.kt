package de.artikelfinder.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.artikelfinder.app.data.Aufbauzustand
import de.artikelfinder.app.data.Katalogaufbau
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Beim ersten Start wird der mitgelieferte Katalog in die Datenbank geschrieben. Das
 * dauert einige Sekunden und braucht deshalb eine sichtbare Rückmeldung — danach startet
 * die App sofort.
 */
@HiltViewModel
class StartViewModel @Inject constructor(private val aufbau: Katalogaufbau) : ViewModel() {

    val zustand: StateFlow<Aufbauzustand> = aufbau.zustand

    init {
        starten()
    }

    fun starten() {
        viewModelScope.launch { aufbau.sicherstellen() }
    }
}
