package de.artikelfinder.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

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
    version = ArtikelDatenbank.VERSION,
    exportSchema = true,
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
         * Bei jeder Schemaänderung erhöhen — und dazu eine Migration in [MIGRATIONEN]
         * eintragen. Die Konstante steht hier, damit der Migrationstest gegen dieselbe
         * Zahl prüfen kann wie die Annotation.
         */
        const val VERSION = 2

        const val NAME = "artikelfinder.db"
    }
}
