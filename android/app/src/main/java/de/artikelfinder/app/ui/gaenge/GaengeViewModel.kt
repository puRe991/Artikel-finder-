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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GaengeViewModel @Inject constructor(
    private val repository: ArtikelRepository,
) : ViewModel() {

    private val _zustand = MutableStateFlow(GaengeZustand())
    val zustand: StateFlow<GaengeZustand> = _zustand.asStateFlow()

    /** Hochzählen startet Abfrage und Beobachtung neu — das ist „Erneut versuchen“. */
    private val versuch = MutableStateFlow(0)

    init {
        versuch
            .flatMapLatest { gangstrom() }
            .onEach { _zustand.value = it }
            .launchIn(viewModelScope)
    }

    fun laden() {
        versuch.value = versuch.value + 1
    }

    /**
     * Erst die Stammdaten, dann die Gangliste — und die beobachtend: erfasst man auf der
     * Detailseite einen Standort, stehen Gänge und Artikelzahlen sofort richtig da.
     */
    private fun gangstrom(): Flow<GaengeZustand> = flow {
        emit(GaengeZustand(laedt = true))

        val markt = repository.markt()
        if (markt !is Abruf.Erfolg) {
            emit(GaengeZustand(laedt = false, fehler = (markt as Abruf.Fehler).meldung))
            return@flow
        }

        emitAll(
            repository.gaenge().map { gaenge ->
                GaengeZustand(markt = markt.wert, gaenge = gaenge, laedt = false)
            }
        )
    }.catch { fehler ->
        emit(GaengeZustand(laedt = false, fehler = fehler.message ?: "Die Gänge konnten nicht geladen werden."))
    }
}

data class GangArtikelZustand(
    val gang: String = "",
    val artikel: List<Artikel> = emptyList(),
    val laedt: Boolean = true,
    val fehler: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GangArtikelViewModel @Inject constructor(
    private val repository: ArtikelRepository,
    zustandHalter: SavedStateHandle,
) : ViewModel() {

    private val gang: String = checkNotNull(zustandHalter[Ziele.ARG_GANG])

    private val _zustand = MutableStateFlow(GangArtikelZustand(gang = gang))
    val zustand: StateFlow<GangArtikelZustand> = _zustand.asStateFlow()

    /** Hochzählen startet Abfrage und Beobachtung neu — das ist „Erneut versuchen“. */
    private val versuch = MutableStateFlow(0)

    init {
        versuch
            .flatMapLatest { artikelstrom() }
            .onEach { _zustand.value = it }
            .launchIn(viewModelScope)
    }

    fun laden() {
        versuch.value = versuch.value + 1
    }

    /**
     * Beobachtend wie die Trefferliste der Suche: der Gang bleibt im Rücken-Stapel stehen,
     * während auf der Detailseite ein Preis erfasst wird.
     */
    private fun artikelstrom(): Flow<GangArtikelZustand> = flow {
        emit(GangArtikelZustand(gang = gang, laedt = true))

        val markt = repository.markt()
        if (markt !is Abruf.Erfolg) {
            emit(
                GangArtikelZustand(
                    gang = gang,
                    laedt = false,
                    fehler = (markt as Abruf.Fehler).meldung,
                )
            )
            return@flow
        }

        emitAll(
            repository.artikelImGang(gang).map { artikel ->
                GangArtikelZustand(gang = gang, artikel = artikel, laedt = false)
            }
        )
    }.catch { fehler ->
        emit(
            GangArtikelZustand(
                gang = gang,
                laedt = false,
                fehler = fehler.message ?: "Der Gang konnte nicht geladen werden.",
            )
        )
    }
}
