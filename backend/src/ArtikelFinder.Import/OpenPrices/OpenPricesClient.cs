using System.Net.Http.Json;
using System.Text.Json;
using Microsoft.Extensions.Logging;

namespace ArtikelFinder.Import.OpenPrices;

/// <summary>
/// Zugriff auf <c>prices.openfoodfacts.org</c> — die Preisdatenbank der
/// Open-Food-Facts-Familie. Sie enthält von Freiwilligen im Laden erfasste Preise, belegt
/// mit Kassenbon- oder Regaletikett-Foto, unter derselben freien Lizenz wie der Katalog.
///
/// Preise hängen dort am Standort, nicht am Land: eine Länderabfrage gibt es nur für
/// Standorte. Der Importer holt deshalb erst die deutschen Läden und fragt dann je Laden
/// dessen Preise ab.
/// </summary>
public interface IOpenPricesClient
{
    /// <param name="land">Ländername, wie ihn OpenStreetMap führt — für Deutschland "Deutschland".</param>
    Task<OpSeite<OpStandort>?> StandorteAsync(string land, int seite, int seitengroesse, CancellationToken ct);

    Task<OpSeite<OpPreis>?> PreiseAsync(int standortId, int seite, int seitengroesse, CancellationToken ct);
}

public sealed class OpenPricesClient(HttpClient http, ILogger<OpenPricesClient> log) : IOpenPricesClient
{
    private const string Basis = "https://prices.openfoodfacts.org/api/v1/";

    /// <summary>
    /// Gleiche Überlegung wie bei Open Food Facts: der Dienst wird von Freiwilligen
    /// betrieben und ist gelegentlich überlastet. Ein Lauf über tausend Standorte darf an
    /// einem 503 nicht scheitern.
    /// </summary>
    private static readonly TimeSpan[] Wartezeiten =
    [
        TimeSpan.FromSeconds(5),
        TimeSpan.FromSeconds(15),
        TimeSpan.FromSeconds(45),
    ];

    private static readonly JsonSerializerOptions JsonOptionen = new()
    {
        PropertyNameCaseInsensitive = true,
    };

    public Task<OpSeite<OpStandort>?> StandorteAsync(
        string land,
        int seite,
        int seitengroesse,
        CancellationToken ct) =>
        HolenAsync<OpStandort>(
            $"{Basis}locations?osm_address_country__like={Uri.EscapeDataString(land)}"
            + $"&page={seite}&size={seitengroesse}",
            ct);

    public Task<OpSeite<OpPreis>?> PreiseAsync(
        int standortId,
        int seite,
        int seitengroesse,
        CancellationToken ct) =>
        HolenAsync<OpPreis>(
            $"{Basis}prices?location_id={standortId}&page={seite}&size={seitengroesse}",
            ct);

    /// <summary>
    /// Eine Seite holen, mit wachsender Wartezeit erneut versuchen. <c>null</c> heißt:
    /// endgültig nicht bekommen — der Aufrufer zählt das und macht weiter.
    /// </summary>
    private async Task<OpSeite<T>?> HolenAsync<T>(string adresse, CancellationToken ct)
    {
        for (var versuch = 0; ; versuch++)
        {
            var (seite, wiederholbar) = await VersuchenAsync<T>(adresse, ct);

            if (seite is not null)
            {
                return seite;
            }

            if (!wiederholbar || versuch >= Wartezeiten.Length)
            {
                log.LogWarning("Open Prices: {Adresse} nicht geladen — übersprungen.", adresse);
                return null;
            }

            await Task.Delay(Wartezeiten[versuch], ct);
        }
    }

    private async Task<(OpSeite<T>? Seite, bool Wiederholbar)> VersuchenAsync<T>(
        string adresse,
        CancellationToken ct)
    {
        try
        {
            using var antwort = await http.GetAsync(adresse, ct);

            if (antwort.IsSuccessStatusCode)
            {
                return (await antwort.Content.ReadFromJsonAsync<OpSeite<T>>(JsonOptionen, ct), false);
            }

            var status = (int)antwort.StatusCode;
            return (null, status is 429 or >= 500);
        }
        catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException && !ct.IsCancellationRequested)
        {
            log.LogDebug(ex, "Netzfehler bei {Adresse}.", adresse);
            return (null, true);
        }
    }
}
