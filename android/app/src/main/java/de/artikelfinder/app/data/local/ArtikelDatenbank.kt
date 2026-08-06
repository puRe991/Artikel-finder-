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
    ],
    version = 1,
    exportSchema = false,
)
abstract class ArtikelDatenbank : RoomDatabase() {
    abstract fun artikelDao(): ArtikelDao
    abstract fun preisDao(): PreisDao
    abstract fun standortDao(): StandortDao
    abstract fun verlaufDao(): VerlaufDao
    abstract fun stammdatenDao(): StammdatenDao
}
