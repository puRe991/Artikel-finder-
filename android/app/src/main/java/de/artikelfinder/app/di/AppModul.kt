package de.artikelfinder.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.artikelfinder.app.data.local.ArtikelDatenbank
import de.artikelfinder.app.data.local.MIGRATIONEN
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModul {

    @Provides
    @Singleton
    fun datenbank(@ApplicationContext context: Context): ArtikelDatenbank =
        Room.databaseBuilder(context, ArtikelDatenbank::class.java, ArtikelDatenbank.NAME)
            // Die Datenbank enthält selbst erfasste Preise und Standorte — sie darf bei
            // einem Schemawechsel nicht einfach verworfen werden. Kein
            // `fallbackToDestructiveMigration`: fehlt eine Migration, soll es beim Testen
            // auffallen und nicht auf dem Handy.
            .addMigrations(*MIGRATIONEN)
            .build()
}
