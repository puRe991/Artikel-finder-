package de.artikelfinder.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.artikelfinder.app.ui.bearbeiten.BearbeitenBildschirm
import de.artikelfinder.app.ui.detail.DetailBildschirm
import de.artikelfinder.app.ui.einrichtung.EinrichtungBildschirm
import de.artikelfinder.app.ui.gaenge.GaengeBildschirm
import de.artikelfinder.app.ui.gaenge.GangArtikelBildschirm
import de.artikelfinder.app.ui.navigation.Ziele
import de.artikelfinder.app.ui.scan.ScanBildschirm
import de.artikelfinder.app.ui.suche.SucheBildschirm
import de.artikelfinder.app.ui.verlauf.VerlaufBildschirm

@Composable
fun ArtikelFinderNavigation(
    startZiel: String,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = startZiel) {

        composable(Ziele.EINRICHTUNG) {
            // Beim ersten Start gibt es kein Zurück — ohne Serveradresse geht nichts.
            val istErstEinrichtung = startZiel == Ziele.EINRICHTUNG
            val zurueck: (() -> Unit)? =
                if (istErstEinrichtung) null else fun() { navController.popBackStack() }

            EinrichtungBildschirm(
                beiFertig = {
                    if (istErstEinrichtung) {
                        navController.navigate(Ziele.SUCHE) {
                            popUpTo(Ziele.EINRICHTUNG) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                beiZurueck = zurueck,
            )
        }

        composable(Ziele.SUCHE) {
            SucheBildschirm(
                beiArtikel = { navController.navigate(Ziele.detail(it)) },
                beiScan = { navController.navigate(Ziele.SCAN) },
                beiGaengen = { navController.navigate(Ziele.GAENGE) },
                beiNeuemArtikel = { navController.navigate(Ziele.bearbeiten()) },
                beiEinstellungen = { navController.navigate(Ziele.EINRICHTUNG) },
            )
        }

        composable(Ziele.SCAN) {
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

        composable(Ziele.GAENGE) {
            GaengeBildschirm(
                beiGang = { navController.navigate(Ziele.gangArtikel(it)) },
                beiZurueck = navController::popBackStack,
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
