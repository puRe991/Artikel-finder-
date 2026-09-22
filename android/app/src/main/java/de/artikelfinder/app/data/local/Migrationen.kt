package de.artikelfinder.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 1 → 2: die Tabelle für den eigenen Vorrat kommt hinzu. Bestehende Daten — selbst
 * erfasste Preise und Standorte — dürfen dabei nicht verloren gehen, deshalb eine echte
 * Migration statt eines Neuaufbaus. Es wird nur eine Tabelle ergänzt, nichts geändert.
 *
 * Die Anweisungen entsprechen genau dem, was Room aus `BestandsbewegungEintrag` erzeugt
 * (siehe `app/schemas/…/2.json`). Weicht der Wortlaut ab, verweigert Room beim Start den
 * Dienst — daher wird das SQL Zeichen für Zeichen aus dem generierten Schema übernommen.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bestandsbewegung` (" +
                "`id` TEXT NOT NULL, " +
                "`artikel_id` TEXT NOT NULL, " +
                "`art` TEXT NOT NULL, " +
                "`menge` INTEGER NOT NULL, " +
                "`stueckpreis` REAL, " +
                "`erfasst_am` INTEGER NOT NULL, " +
                "`erfasst_von` TEXT, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`artikel_id`) REFERENCES `artikel`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_bestandsbewegung_artikel_id_erfasst_am` " +
                "ON `bestandsbewegung` (`artikel_id`, `erfasst_am`)"
        )
    }
}
