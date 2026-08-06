package de.artikelfinder.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.artikelfinder.app.data.einstellungen.Servereinstellungen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Entscheidet, ob die App mit der Suche oder mit der Einrichtung startet. Ohne
 * eingestellte Serveradresse würde jeder Bildschirm nur Netzwerkfehler zeigen.
 *
 * `null` heißt "noch nicht gelesen" — solange bleibt der Startbildschirm leer, statt
 * kurz die falsche Seite aufblitzen zu lassen.
 */
@HiltViewModel
class StartViewModel @Inject constructor(einstellungen: Servereinstellungen) : ViewModel() {

    val istEingerichtet: StateFlow<Boolean?> = einstellungen.basisUrl
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
