package de.artikelfinder.app.data.einstellungen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Serveradresse tippt der Nutzer im Markt auf dem Handy ein. Jede Form, die er dabei
 * naheliegenderweise verwendet, muss zu einer gültigen Retrofit-Basis-URL führen —
 * insbesondere der abschließende Schrägstrich, ohne den Retrofit beim Start abbricht.
 */
class AdresseTest {

    @Test
    fun `blosse IP bekommt Schema, Standardport und Schraegstrich`() {
        assertEquals("http://192.168.178.20:5080/", Adresse.normalisieren("192.168.178.20"))
    }

    @Test
    fun `angegebener Port bleibt erhalten`() {
        assertEquals("http://192.168.178.20:8080/", Adresse.normalisieren("192.168.178.20:8080"))
    }

    @Test
    fun `vollstaendige URL bleibt unveraendert bis auf den Schraegstrich`() {
        assertEquals("http://10.0.2.2:5080/", Adresse.normalisieren("http://10.0.2.2:5080"))
        assertEquals("http://10.0.2.2:5080/", Adresse.normalisieren("http://10.0.2.2:5080/"))
    }

    @Test
    fun `https bleibt https`() {
        assertEquals("https://api.example.de:5080/", Adresse.normalisieren("https://api.example.de"))
    }

    @Test
    fun `Leerzeichen am Rand stoeren nicht`() {
        assertEquals("http://192.168.178.20:5080/", Adresse.normalisieren("  192.168.178.20  "))
    }

    @Test
    fun `Hostname statt IP funktioniert auch`() {
        assertEquals("http://mein-pc:5080/", Adresse.normalisieren("mein-pc"))
    }

    @Test
    fun `Pruefung erkennt brauchbare und unbrauchbare Eingaben`() {
        assertTrue(Adresse.siehtGueltigAus("192.168.178.20"))
        assertTrue(Adresse.siehtGueltigAus("http://10.0.2.2:5080"))
        assertFalse(Adresse.siehtGueltigAus(""))
        assertFalse(Adresse.siehtGueltigAus("   "))
    }
}
