package de.artikelfinder.app.ui.komponenten

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.artikelfinder.app.data.Artikel
import de.artikelfinder.app.ui.alsPreis
import de.artikelfinder.app.ui.theme.Abstand
import de.artikelfinder.app.ui.theme.aktion

/*
 * Die gemeinsamen Bausteine aller Bildschirme. Jeder Bildschirm setzt sich aus diesen
 * Teilen zusammen, damit Karten, Marken, Preise und Leerzustände überall gleich aussehen.
 */

// ---------------------------------------------------------------------------------------
// Kopfzeilen
// ---------------------------------------------------------------------------------------

/** Kopfzeile der Hauptbereiche (Suche, Angebote, Gänge) — ohne Zurück-Pfeil. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Hauptleiste(
    titel: String,
    untertitel: String? = null,
    aktionen: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(text = titel, style = MaterialTheme.typography.titleLarge)
                untertitel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        actions = aktionen,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Kopfzeile aller Unterseiten — immer mit Zurück-Pfeil links. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Unterseitenleiste(
    titel: String,
    beiZurueck: () -> Unit,
    aktionen: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                text = titel,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = beiZurueck) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
            }
        },
        actions = aktionen,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

// ---------------------------------------------------------------------------------------
// Artikel in Listen
// ---------------------------------------------------------------------------------------

/**
 * Ein Artikel in einer Liste: Bild, Name, Marke, Gang — und rechts der Preis.
 * [hinweis] erscheint unter dem Gang, etwa die Restlaufzeit eines Angebots.
 */
@Composable
fun ArtikelKarte(
    artikel: Artikel,
    beiKlick: () -> Unit,
    modifier: Modifier = Modifier,
    hinweis: (@Composable () -> Unit)? = null,
) {
    Karte(beiKlick = beiKlick, modifier = modifier) {
        Row(
            modifier = Modifier.padding(Abstand.block),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Abstand.block),
        ) {
            Artikelbild(artikel.bildUrl, groesse = 60.dp)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Abstand.minimal),
            ) {
                Text(
                    text = artikel.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                artikel.marke?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (artikel.standort != null || hinweis != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Abstand.eng),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        artikel.standort?.let { GangMarke(it.gang) }
                        hinweis?.invoke()
                    }
                }
            }

            KartenPreis(artikel)
        }
    }
}

/** Werbepreis groß in Rot, Normalpreis klein durchgestrichen darüber. */
@Composable
private fun KartenPreis(artikel: Artikel) {
    val preis = artikel.preis

    if (preis == null) {
        Text(
            text = "–,–– €",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        return
    }

    Column(horizontalAlignment = Alignment.End) {
        if (preis.werbepreisAktiv && preis.werbepreis != null) {
            Text(
                text = preis.preis.alsPreis(),
                style = MaterialTheme.typography.bodySmall,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = preis.werbepreis.alsPreis(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.aktion.farbe,
            )
        } else {
            Text(
                text = preis.preis.alsPreis(),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Marken (kleine Pillen)
// ---------------------------------------------------------------------------------------

/** Kleine abgerundete Marke. Grundlage für Gang-, Aktions- und Laufzeitmarken. */
@Composable
fun Marke(
    text: String,
    hintergrund: Color,
    vordergrund: Color,
    modifier: Modifier = Modifier,
    symbol: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(hintergrund)
            .padding(start = if (symbol != null) 6.dp else 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        symbol?.let {
            Icon(imageVector = it, contentDescription = null, tint = vordergrund, modifier = Modifier.size(13.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = vordergrund,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GangMarke(gang: String, modifier: Modifier = Modifier) {
    Marke(
        text = "Gang $gang",
        hintergrund = MaterialTheme.colorScheme.primaryContainer,
        vordergrund = MaterialTheme.colorScheme.onPrimaryContainer,
        symbol = Icons.Default.Place,
        modifier = modifier,
    )
}

@Composable
fun Aktionsmarke(modifier: Modifier = Modifier) {
    Marke(
        text = "AKTION",
        hintergrund = MaterialTheme.aktion.farbe,
        vordergrund = MaterialTheme.aktion.aufFarbe,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------------------
// Karten und Abschnitte
// ---------------------------------------------------------------------------------------

/** Die eine Kartenform der App: weiße Fläche, dünner Rand, keine Schatten. */
@Composable
fun Karte(
    modifier: Modifier = Modifier,
    beiKlick: (() -> Unit)? = null,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    val farben = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val rand = CardDefaults.outlinedCardBorder().copy(width = 1.dp)
    val form = MaterialTheme.shapes.medium

    if (beiKlick != null) {
        Card(onClick = beiKlick, modifier = modifier.fillMaxWidth(), shape = form, colors = farben, border = rand, content = inhalt)
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = form, colors = farben, border = rand, content = inhalt)
    }
}

/**
 * Karte mit Überschrift. [aktion] sitzt rechts in der Kopfzeile — so liegt die Schaltfläche
 * immer neben dem, was sie ändert.
 */
@Composable
fun Abschnitt(
    titel: String,
    modifier: Modifier = Modifier,
    symbol: ImageVector? = null,
    aktion: (@Composable () -> Unit)? = null,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    Karte(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(start = Abstand.rand, end = if (aktion == null) Abstand.rand else Abstand.minimal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Abstand.eng),
        ) {
            symbol?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = titel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            aktion?.invoke()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Abstand.rand, end = Abstand.rand, bottom = Abstand.rand),
            verticalArrangement = Arrangement.spacedBy(Abstand.minimal),
            content = inhalt,
        )
    }
}

/** Bezeichnung links, Wert rechts. Für Stammdaten wie EAN oder Kategorie. */
@Composable
fun DatenZeile(bezeichnung: String, wert: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(Abstand.block),
    ) {
        Text(
            text = bezeichnung,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = wert,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Kleine Zeile „Erfasst am … von …“ unter Preis und Standort. */
@Composable
fun Erfassungsvermerk(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Abstand.eng),
    )
}

// ---------------------------------------------------------------------------------------
// Bilder
// ---------------------------------------------------------------------------------------

/** Quadratisches Produktbild mit Korb-Symbol als Platzhalter. */
@Composable
fun Artikelbild(url: String?, groesse: Dp, modifier: Modifier = Modifier) {
    val form = MaterialTheme.shapes.small

    Box(
        modifier = modifier
            .size(groesse)
            .clip(form)
            // Produktfotos haben fast immer weißen Grund — die Kachel ist deshalb weiß, sobald
            // es ein Bild gibt, und sonst in der Flächenfarbe (hell wie dunkel unauffällig).
            .background(if (url.isNullOrBlank()) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, form),
        contentAlignment = Alignment.Center,
    ) {
        // Liegt unter dem Bild: sichtbar ohne Bild-URL, beim Laden und ohne Netz.
        Icon(
            imageVector = Icons.Outlined.ShoppingBasket,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
            modifier = Modifier.size(groesse * 0.42f),
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Zustände: Laden, Fehler, leer
// ---------------------------------------------------------------------------------------

@Composable
fun LadeAnzeige(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Leerer Zustand: Symbol im Kreis, Titel, Erklärung und optional eine Schaltfläche. */
@Composable
fun LeerAnzeige(
    titel: String,
    modifier: Modifier = Modifier,
    symbol: ImageVector = Icons.Outlined.ShoppingBasket,
    hinweis: String? = null,
    aktionText: String? = null,
    beiAktion: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Abstand.eng),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = symbol,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Text(
            text = titel,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Abstand.eng),
        )
        hinweis?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (aktionText != null && beiAktion != null) {
            FilledTonalButton(onClick = beiAktion, modifier = Modifier.padding(top = Abstand.eng)) {
                Text(aktionText)
            }
        }
    }
}

@Composable
fun FehlerAnzeige(
    meldung: String,
    modifier: Modifier = Modifier,
    beiWiederholen: (() -> Unit)? = null,
) {
    LeerAnzeige(
        titel = "Da ist etwas schiefgegangen",
        hinweis = meldung,
        symbol = Icons.Default.ErrorOutline,
        aktionText = beiWiederholen?.let { "Erneut versuchen" },
        beiAktion = beiWiederholen,
        modifier = modifier,
    )
}
