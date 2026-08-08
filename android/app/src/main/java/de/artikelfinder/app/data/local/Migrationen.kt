package de.artikelfinder.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
val MIGRATIONEN: Array<Migration> = arrayOf(VonEinsAufZwei, VonZweiAufDrei)

/**
 * Die Kette, in der es einen Artikel exklusiv gibt.
 *
 * Nur die Spalte, nicht der Inhalt: gefüllt wird sie von `Markenzuordnung`, die beim Start
 * läuft und über einen Merkposten weiß, ob sie schon dran war. Damit bleibt diese Migration
 * für immer richtig — wächst die Markenliste später, kostet das keine neue Schemaversion,
 * sondern nur eine höhere Zuordnungsversion.
 */
private object VonEinsAufZwei : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE artikel ADD COLUMN eigenmarke_kette TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_artikel_eigenmarke_kette ON artikel (eigenmarke_kette)")

        // Bis hierher gab es genau einen Markt, angelegt mit dem Anzeigenamen der Kette.
        // Ab jetzt steht in der Spalte der Schlüssel, über den die Eigenmarken hängen.
        db.execSQL("UPDATE markt SET kette = 'kaufland' WHERE kette = 'Kaufland'")
    }
}

/**
 * Die Angaben, mit denen die App Auskunft gibt: Allergene, Zutaten, Auszeichnungen,
 * Naehrwerte, Menge, Nutri-Score.
 *
 * Nur die Spalten. Gefuellt werden sie aus der mitgelieferten Katalogdatei — die Angaben
 * kommen mit dem naechsten Katalog, nicht aus dieser Migration. Wer die App aktualisiert,
 * behaelt seine Preise und Gaenge und bekommt die Auskunft dazu, sobald der Katalog neu
 * eingelesen wird.
 */
private object VonZweiAufDrei : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("menge", "allergene", "spuren", "auszeichnungen", "naehrwerte", "nutriscore", "zutaten")
            .forEach { db.execSQL("ALTER TABLE artikel ADD COLUMN $it TEXT") }
    }
}
