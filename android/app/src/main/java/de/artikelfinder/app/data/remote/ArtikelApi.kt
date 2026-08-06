package de.artikelfinder.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ArtikelApi {

    @GET("api/artikel")
    suspend fun suchen(
        @Query("q") suchbegriff: String? = null,
        @Query("kategorieId") kategorieId: Int? = null,
        @Query("marktId") marktId: Int? = null,
        @Query("nurMitStandort") nurMitStandort: Boolean = false,
        @Query("nurMitWerbepreis") nurMitWerbepreis: Boolean = false,
        @Query("seite") seite: Int = 1,
        @Query("groesse") groesse: Int = 25,
    ): SeitenErgebnisDto<ArtikelListeDto>

    @GET("api/artikel/{id}")
    suspend fun holen(@Path("id") id: String): ArtikelDetailDto

    /**
     * Barcode-Lookup. Liefert bewusst [Response], weil 404 hier kein Fehler ist, sondern
     * "unbekannter Artikel" bedeutet — der Auslöser für das Anlegen-Formular.
     */
    @GET("api/artikel/ean/{ean}")
    suspend fun perEan(@Path("ean") ean: String): Response<ArtikelDetailDto>

    @POST("api/artikel")
    suspend fun anlegen(@Body eingabe: ArtikelAnlegenDto): ArtikelDetailDto

    @PUT("api/artikel/{id}")
    suspend fun aendern(
        @Path("id") id: String,
        @Body eingabe: ArtikelAendernDto,
        @Query("geaendertVon") geaendertVon: String? = null,
    ): ArtikelDetailDto

    @DELETE("api/artikel/{id}")
    suspend fun loeschen(@Path("id") id: String): Response<Unit>

    @POST("api/artikel/{id}/preise")
    suspend fun preisErfassen(@Path("id") id: String, @Body eingabe: PreisErfassenDto): PreisDto

    @POST("api/artikel/{id}/standorte")
    suspend fun standortErfassen(@Path("id") id: String, @Body eingabe: StandortErfassenDto): StandortDto

    @GET("api/artikel/{id}/verlauf")
    suspend fun verlauf(@Path("id") id: String): List<VerlaufEintragDto>

    @GET("api/kategorien")
    suspend fun kategorien(): List<KategorieDto>

    @GET("api/maerkte")
    suspend fun maerkte(): List<MarktDto>

    @GET("api/maerkte/standard")
    suspend fun standardMarkt(): MarktDto

    @GET("api/maerkte/{marktId}/gaenge")
    suspend fun gaenge(@Path("marktId") marktId: Int): List<GangDto>

    @GET("api/maerkte/{marktId}/gaenge/{gang}/artikel")
    suspend fun artikelImGang(
        @Path("marktId") marktId: Int,
        @Path("gang") gang: String,
    ): List<ArtikelListeDto>
}
