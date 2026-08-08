package de.artikelfinder.app.data.local

import androidx.room.migration.Migration

/**
 * Alle Schemaschritte, in der Reihenfolge ihrer Versionen.
 *
 * Der Katalog in der Datenbank ist jederzeit aus den Assets wiederherstellbar — die selbst
 * erfassten Preise, Standorte und Gänge sind es nicht. Ein `fallbackToDestructiveMigration`
 * würde genau diese Arbeit stillschweigend wegwerfen; deshalb gibt es ihn nicht, und
 * deshalb muss jede Versionserhöhung hier einen Eintrag bekommen.
 *
 * Beim Anheben von `ArtikelDatenbank.VERSION`:
 *
 *  1. Entität ändern und die Version erhöhen.
 *  2. Einmal bauen — Room legt `app/schemas/…/<neueVersion>.json` an. Die Datei gehört
 *     mit ins Repository, sonst ist die Migration weder schreib- noch prüfbar.
 *  3. Hier eine `Migration(alt, neu)` ergänzen.
 *  4. `MigrationTest` spielt den Weg von der ältesten Version bis zur aktuellen nach und
 *     prüft, dass die erfassten Daten ihn überstehen.
 *
 * Vergisst man Schritt 3, schlägt `MigrationTest` fehl — nicht erst das Handy des Nutzers
 * beim nächsten Update.
 */
val MIGRATIONEN: Array<Migration> = emptyArray()
