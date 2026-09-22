package de.artikelfinder.app.ui.komponenten

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/** Das Eingabefeld aller Formulare der App — überall gleich hoch, gleich beschriftet. */
@Composable
fun Eingabe(
    wert: String,
    beiAenderung: (String) -> Unit,
    bezeichnung: String,
    modifier: Modifier = Modifier,
    platzhalter: String? = null,
    suffix: String? = null,
    hinweis: String? = null,
    tastatur: KeyboardType = KeyboardType.Text,
    einzeilig: Boolean = true,
    istFehler: Boolean = false,
) {
    OutlinedTextField(
        value = wert,
        onValueChange = beiAenderung,
        label = { Text(bezeichnung) },
        placeholder = platzhalter?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        supportingText = hinweis?.let { { Text(it) } },
        singleLine = einzeilig,
        isError = istFehler,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(keyboardType = tastatur),
        modifier = modifier.fillMaxWidth(),
    )
}
