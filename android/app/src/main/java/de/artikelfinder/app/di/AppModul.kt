package de.artikelfinder.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.artikelfinder.app.data.local.ArtikelDatenbank
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModul {

    @Provides
    @Singleton
    fun datenbank(@ApplicationContext context: Context): ArtikelDatenbank =
        Room.databaseBuilder(context, ArtikelDatenbank::class.java, "artikelfinder.db")
            // Die Datenbank enthält selbst erfasste Preise und Standorte — sie darf bei
            // einem Schemawechsel nicht einfach verworfen werden. Beim nächsten
            // Versionssprung gehört hier eine echte Migration hin.
            .build()
}
