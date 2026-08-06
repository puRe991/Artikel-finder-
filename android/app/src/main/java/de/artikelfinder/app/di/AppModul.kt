package de.artikelfinder.app.di

import android.content.Context
import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.artikelfinder.app.BuildConfig
import de.artikelfinder.app.data.local.ArtikelCacheDao
import de.artikelfinder.app.data.local.ArtikelDatenbank
import de.artikelfinder.app.data.remote.ArtikelApi
import de.artikelfinder.app.data.remote.BasisUrlInterceptor
import javax.inject.Qualifier
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** Der Client, der die eingestellte Serveradresse einsetzt. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiClient

@Module
@InstallIn(SingletonComponent::class)
object AppModul {

    @Provides
    @Singleton
    fun json(): Json = Json {
        // Damit ein Serverupdate mit neuen Feldern keine ältere App-Version abstürzen lässt.
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * Ohne Adressumschreibung — für den Verbindungstest bei der Einrichtung, der gegen die
     * gerade eingetippte Adresse läuft und nicht gegen die gespeicherte.
     */
    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        // Beim Einrichten soll ein falscher Host schnell auffallen, nicht erst nach 15 s.
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    @Provides
    @Singleton
    @ApiClient
    fun apiOkHttp(basis: OkHttpClient, umschreiber: BasisUrlInterceptor): OkHttpClient =
        basis.newBuilder()
            // Im Markt ist das WLAN oft schlecht; lieber etwas länger warten als abbrechen.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(umschreiber)
            .build()

    @Provides
    @Singleton
    fun retrofit(@ApiClient client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        // Platzhalter: Schema, Host und Port ersetzt der BasisUrlInterceptor zur Laufzeit.
        .baseUrl(BuildConfig.API_BASIS_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun artikelApi(retrofit: Retrofit): ArtikelApi = retrofit.create(ArtikelApi::class.java)

    @Provides
    @Singleton
    fun datenbank(@ApplicationContext context: Context): ArtikelDatenbank =
        Room.databaseBuilder(context, ArtikelDatenbank::class.java, "artikelfinder-cache.db")
            // Der Inhalt ist reiner Cache — bei einem Schemawechsel ist Neuaufbau billiger
            // als eine Migration.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun artikelCacheDao(datenbank: ArtikelDatenbank): ArtikelCacheDao = datenbank.artikelCacheDao()
}
