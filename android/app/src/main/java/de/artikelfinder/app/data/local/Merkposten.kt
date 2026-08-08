package de.artikelfinder.app.data.local

/**
 * Die Schlüssel der `merkposten`-Tabelle, an einer Stelle.
 *
 * In der Tabelle steht zweierlei nebeneinander: was der Nutzer eingegeben hat, und was sich
 * die App über sich selbst merkt. Für die Sicherung ist der Unterschied wesentlich — die
 * gewählte Markt-Id gilt nur auf diesem Gerät, und der Stand der Markenzuordnung gehört zur
 * Programmfassung, nicht zu den Daten. Beides in eine Sicherung zu schreiben hieße, dem
 * Zielgerät beim Einspielen einen fremden Markt unterzuschieben.
 */
object Merkposten {

    /** Der zuletzt eingetippte Aktionszeitraum. Echte Eingabe des Nutzers. */
    const val AKTIONSENDE = "aktionsende"

    /** Der gewählte Markt. Die Id gilt nur auf diesem Gerät. */
    const val MARKT = "markt"

    /** Bis zu welcher Fassung der Markenliste zugeordnet wurde. Gehört zur App. */
    const val MARKENZUORDNUNG = "markenzuordnung"

    /** Was in eine Sicherung gehört: alles, was der Nutzer selbst eingegeben hat. */
    val SICHERBAR = setOf(AKTIONSENDE)
}
