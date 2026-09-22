package de.artikelfinder.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.artikelfinder.app.ui.angebote.AngeboteBildschirm
import de.artikelfinder.app.ui.bearbeiten.BearbeitenBildschirm
import de.artikelfinder.app.ui.bestand.BestandBildschirm
import de.artikelfinder.app.ui.detail.DetailBildschirm
import de.artikelfinder.app.ui.gaenge.GaengeBildschirm
import de.artikelfinder.app.ui.gaenge.GangArtikelBildschirm
import de.artikelfinder.app.ui.navigation.Ziele
import de.artikelfinder.app.ui.scan.ScanBildschirm
import de.artikelfinder.app.ui.suche.SucheBildschirm
import de.artikelfinder.app.ui.verlauf.VerlaufBildschirm

/** Die Hauptbereiche, zwischen denen die untere Leiste wechselt. */
private enum class Hauptbereich(
    val ziel: String,
    val beschriftung: String,
    val symbol: ImageVector,
    val symbolAktiv: ImageVector,
) {
    SUCHE(Ziele.SUCHE, "Suchen", Icons.Outlined.Search, Icons.Filled.Search),
    ANGEBOTE(Ziele.ANGEBOTE, "Angebote", Icons.Outlined.LocalOffer, Icons.Filled.LocalOffer),
    GAENGE(Ziele.GAENGE, "Gänge", Icons.Outlined.ViewModule, Icons.Filled.ViewModule),
    VORRAT(Ziele.BESTAND, "Vorrat", Icons.Outlined.Inventory2, Icons.Filled.Inventory2),
}

@Composable
fun ArtikelFinderNavigation(navController: NavHostController = rememberNavController()) {
    val eintrag by navController.currentBackStackEntryAsState()
    val ziel = eintrag?.destination

    // Die Leiste gehört nur zu den Hauptbereichen. Auf Detail-, Scan- und Formularseiten
    // führt der Zurück-Pfeil weiter — zwei Navigationswege auf einmal verwirren nur.
    val zeigtLeiste = Hauptbereich.entries.any { bereich ->
        ziel?.hierarchy?.any { it.route == bereich.ziel } == true
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (zeigtLeiste) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Hauptbereich.entries.forEach { bereich ->
                        val aktiv = ziel?.hierarchy?.any { it.route == bereich.ziel } == true
                        NavigationBarItem(
                            selected = aktiv,
                            onClick = { navController.zuBereich(bereich.ziel) },
                            icon = {
                                Icon(
                                    imageVector = if (aktiv) bereich.symbolAktiv else bereich.symbol,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(bereich.beschriftung) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { abstand ->
        NavHost(
            navController = navController,
            startDestination = Ziele.SUCHE,
            // Die innere Scaffold jedes Bildschirms soll den Platz der Leiste nicht noch
            // einmal als Systemleiste einrechnen.
            modifier = Modifier.padding(abstand).consumeWindowInsets(abstand),
        ) {

            composable(Ziele.SUCHE) {
                SucheBildschirm(
                    beiArtikel = { navController.navigate(Ziele.detail(it)) },
                    beiScan = { navController.navigate(Ziele.scan()) },
                    beiNeuemArtikel = { navController.navigate(Ziele.bearbeiten()) },
                )
            }

            composable(Ziele.ANGEBOTE) {
                AngeboteBildschirm(
                    beiArtikel = { navController.navigate(Ziele.detail(it)) },
                )
            }

            composable(Ziele.GAENGE) {
                GaengeBildschirm(
                    beiGang = { navController.navigate(Ziele.gangArtikel(it)) },
                )
            }

            composable(Ziele.BESTAND) {
                BestandBildschirm(
                    beiArtikel = { navController.navigate(Ziele.detail(it)) },
                    beiScannen = { navController.navigate(Ziele.scan(Ziele.ZWECK_BESTAND)) },
                )
            }

            composable(
                route = Ziele.SCAN,
                arguments = listOf(
                    navArgument(Ziele.ARG_ZWECK) {
                        type = NavType.StringType
                        defaultValue = Ziele.ZWECK_SUCHE
                    },
                ),
            ) {
                ScanBildschirm(
                    // Der Scan-Bildschirm selbst gehört nicht in den Zurück-Stapel: nach dem
                    // Treffer soll „Zurück“ zur Suche führen, nicht wieder in die Kamera.
                    beiArtikel = {
                        navController.navigate(Ziele.detail(it)) {
                            popUpTo(Ziele.SCAN) { inclusive = true }
                        }
                    },
                    beiUnbekannterEan = { ean ->
                        navController.navigate(Ziele.bearbeiten(ean = ean)) {
                            popUpTo(Ziele.SCAN) { inclusive = true }
                        }
                    },
                    beiZurueck = navController::popBackStack,
                )
            }

            composable(
                route = Ziele.DETAIL,
                arguments = listOf(navArgument(Ziele.ARG_ARTIKEL_ID) { type = NavType.StringType }),
            ) {
                DetailBildschirm(
                    beiZurueck = navController::popBackStack,
                    beiBearbeiten = { navController.navigate(Ziele.bearbeiten(artikelId = it)) },
                    beiVerlauf = { navController.navigate(Ziele.verlauf(it)) },
                )
            }

            composable(
                route = Ziele.VERLAUF,
                arguments = listOf(navArgument(Ziele.ARG_ARTIKEL_ID) { type = NavType.StringType }),
            ) {
                VerlaufBildschirm(beiZurueck = navController::popBackStack)
            }

            composable(
                route = Ziele.BEARBEITEN,
                arguments = listOf(
                    navArgument(Ziele.ARG_ARTIKEL_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(Ziele.ARG_EAN) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                BearbeitenBildschirm(
                    beiZurueck = navController::popBackStack,
                    beiGespeichert = { artikelId ->
                        // Nach dem Speichern direkt auf die Detailseite, und das Formular aus
                        // dem Stapel nehmen.
                        navController.navigate(Ziele.detail(artikelId)) {
                            popUpTo(Ziele.BEARBEITEN) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                route = Ziele.GANG_ARTIKEL,
                arguments = listOf(navArgument(Ziele.ARG_GANG) { type = NavType.StringType }),
            ) {
                GangArtikelBildschirm(
                    beiArtikel = { navController.navigate(Ziele.detail(it)) },
                    beiZurueck = navController::popBackStack,
                )
            }
        }
    }
}

/**
 * Wechsel zwischen den Hauptbereichen wie in jeder Android-App mit unterer Leiste: jeder
 * Bereich behält Suchbegriff und Scrollposition, und „Zurück“ führt aus jedem Bereich zur Suche.
 */
private fun NavHostController.zuBereich(ziel: String) {
    navigate(ziel) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
