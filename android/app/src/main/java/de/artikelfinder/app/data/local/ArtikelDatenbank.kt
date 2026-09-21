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
        BestandsbewegungEintrag::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ArtikelDatenbank : RoomDatabase() {
    abstract fun artikelDao(): ArtikelDao
    abstract fun preisDao(): PreisDao
    abstract fun standortDao(): StandortDao
    abstract fun verlaufDao(): VerlaufDao
    abstract fun stammdatenDao(): StammdatenDao
    abstract fun merkpostenDao(): MerkpostenDao
    abstract fun bestandDao(): BestandDao
}
