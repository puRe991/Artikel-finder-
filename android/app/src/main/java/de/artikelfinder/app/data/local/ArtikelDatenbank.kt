package de.artikelfinder.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ArtikelEintrag::class,
        KategorieEintrag::class,
        MarktEintrag::class,
        PreisEintrag::class,
        StandortEintrag::class,
        VerlaufEintrag::class,
        MerkpostenEintrag::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class ArtikelDatenbank : RoomDatabase() {
    abstract fun artikelDao(): ArtikelDao
    abstract fun preisDao(): PreisDao
    abstract fun standortDao(): StandortDao
    abstract fun verlaufDao(): VerlaufDao
    abstract fun stammdatenDao(): StammdatenDao
    abstract fun merkpostenDao(): MerkpostenDao

    companion object {
        /**
         * Der Richtpreis kommt hinzu. Eine echte Migration statt eines Neuaufbaus, weil in
         * der Datenbank die selbst erfassten Preise und Standorte stehen — die dürfen ein
         * App-Update nicht kosten.
         *
         * Die Spalten bleiben zunächst leer; gefüllt werden sie vom Katalogaufbau, der die
         * mitgelieferte Katalogdatei erneut liest, sobald er einen neuen Katalogstand
         * feststellt.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE artikel ADD COLUMN ref_preis REAL")
                db.execSQL("ALTER TABLE artikel ADD COLUMN ref_preis_min REAL")
                db.execSQL("ALTER TABLE artikel ADD COLUMN ref_preis_max REAL")
                db.execSQL("ALTER TABLE artikel ADD COLUMN ref_preis_anzahl INTEGER")
                db.execSQL("ALTER TABLE artikel ADD COLUMN ref_preis_stand TEXT")
            }
        }
    }
}
