package de.artikelfinder.app.ui.bearbeiten

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.artikelfinder.app.ui.komponenten.Abschnitt
import de.artikelfinder.app.ui.komponenten.Eingabe
import de.artikelfinder.app.ui.komponenten.KategorieAuswahl
import de.artikelfinder.app.ui.komponenten.LadeAnzeige
import de.artikelfinder.app.ui.komponenten.Unterseitenleiste
import de.artikelfinder.app.ui.theme.Abstand

@Composable
fun BearbeitenBildschirm(
    beiZurueck: () -> Unit,
    beiGespeichert: (String) -> Unit,
    viewModel: BearbeitenViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    var kategorienOffen by remember { mutableStateOf(false) }

    LaunchedEffect(zustand.gespeicherteArtikelId) {
        zustand.gespeicherteArtikelId?.let(beiGespeichert)
    }

    Scaffold(
        topBar = {
            Unterseitenleiste(
                titel = if (zustand.istNeuanlage) "Artikel anlegen" else "Artikel bearbeiten",
                beiZurueck = beiZurueck,
            )
        },
        // Speichern bleibt unten sichtbar, egal wie weit das Formular gescrollt ist.
        bottomBar = {
            if (!zustand.laedt) {
                Speicherleiste(
                    aktiv = zustand.kannSpeichern,
                    speichert = zustand.speichert,
                    fehler = zustand.fehler,
                    beiSpeichern = viewModel::speichern,
                )
            }
        },
    ) { abstand ->
        if (zustand.laedt) {
            LadeAnzeige(modifier = Modifier.padding(abstand))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(abstand)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Abstand.rand, vertical = Abstand.eng),
            verticalArrangement = Arrangement.spacedBy(Abstand.block),
        ) {
            Abschnitt(titel = "Artikel", symbol = Icons.Outlined.Info) {
                Eingabe(
                    wert = zustand.name,
                    beiAenderung = viewModel::nameGeaendert,
                    bezeichnung = "Name *",
                    istFehler = zustand.name.isBlank(),
                )
                Eingabe(
                    wert = zustand.marke,
                    beiAenderung = viewModel::markeGeaendert,
                    bezeichnung = "Marke",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Abstand.eng)) {
                    Eingabe(
                        wert = zustand.ean,
                        beiAenderung = viewModel::eanGeaendert,
                        bezeichnung = "EAN",
                        tastatur = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    Eingabe(
                        wert = zustand.artikelnummer,
                        beiAenderung = viewModel::artikelnummerGeaendert,
                        bezeichnung = "Artikelnr.",
                        modifier = Modifier.weight(1f),
                    )
                }
                Kategoriefeld(
                    name = zustand.kategorien.firstOrNull { it.id == zustand.kategorieId }?.name,
                    beiKlick = { kategorienOffen = true },
                )
            }

            // Preis und Standort nur bei der Neuanlage: bei bestehenden Artikeln laufen sie
            // über die Detailseite, damit die Historie nicht versehentlich überschrieben wirkt.
            if (zustand.istNeuanlage) {
                Abschnitt(titel = "Preis (optional)", symbol = Icons.Outlined.Euro) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Abstand.eng)) {
                        Eingabe(
                            wert = zustand.preis,
                            beiAenderung = viewModel::preisGeaendert,
                            bezeichnung = "Normalpreis",
                            suffix = "€",
                            tastatur = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f),
                        )
                        Eingabe(
                            wert = zustand.werbepreis,
                            beiAenderung = viewModel::werbepreisGeaendert,
                            bezeichnung = "Werbepreis",
                            suffix = "€",
                            tastatur = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Abschnitt(titel = "Standort (optional)", symbol = Icons.Outlined.Place) {
                    Eingabe(
                        wert = zustand.gang,
                        beiAenderung = viewModel::gangGeaendert,
                        bezeichnung = "Gang",
                        platzhalter = "z. B. 7",
                    )
                    Eingabe(
                        wert = zustand.regalBeschreibung,
                        beiAenderung = viewModel::regalGeaendert,
                        bezeichnung = "Regal",
                        platzhalter = "z. B. links, mittleres Fach",
                        einzeilig = false,
                    )
                }
            }

            Abschnitt(titel = "Erfasst von", symbol = Icons.Outlined.Person) {
                Eingabe(
                    wert = zustand.erfasstVon,
                    beiAenderung = viewModel::erfasstVonGeaendert,
                    bezeichnung = "Dein Name (optional)",
                    hinweis = "Taucht im Änderungsverlauf auf.",
                )
            }
        }
    }

    if (kategorienOffen) {
        KategorieAuswahl(
            kategorien = zustand.kategorien,
            gewaehlt = zustand.kategorieId,
            ohneAuswahlText = "Keine Kategorie",
            beiAuswahl = {
                kategorienOffen = false
                viewModel.kategorieGewaehlt(it)
            },
            beiSchliessen = { kategorienOffen = false },
        )
    }
}

/** Sieht aus wie ein Eingabefeld, öffnet aber die Kategorieauswahl. */
@Composable
private fun Kategoriefeld(name: String?, beiKlick: () -> Unit) {
    Box {
        Eingabe(
            wert = name ?: "Keine",
            beiAenderung = {},
            bezeichnung = "Kategorie",
        )
        // Durchsichtige Fläche über dem Feld: das Textfeld selbst würde die Tastatur öffnen.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = beiKlick),
        )
        Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = Abstand.block)
                .size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Speicherleiste(
    aktiv: Boolean,
    speichert: Boolean,
    fehler: String?,
    beiSpeichern: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.imePadding()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Abstand.rand, vertical = Abstand.block),
            verticalArrangement = Arrangement.spacedBy(Abstand.eng),
        ) {
            fehler?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = beiSpeichern,
                enabled = aktiv,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (speichert) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = Abstand.eng).size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                }
                Text("Speichern")
            }
        }
    }
}
