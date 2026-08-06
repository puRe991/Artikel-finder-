using System.Net.Http.Json;
using System.Text.Json;
using Microsoft.Extensions.Logging;

namespace ArtikelFinder.Import.OpenFoodFacts;

/// <summary>
/// Zugriff auf die Open-Food-Facts-Suche (API v2).
///
/// Zwei Regeln aus deren Nutzungsbedingungen sind hier fest verdrahtet:
/// ein aussagekraeftiger User-Agent mit Kontaktmoeglichkeit und eine Wartezeit zwischen
/// den Requests. Die Such-API ist auf 10 Anfragen pro Minute gedeckelt.
/// </summary>
public sealed class OffClient(HttpClient http, ILogger<OffClient> log)
{
    /// <summary>Nur diese Felder anfordern — spart bei 100 Produkten pro Seite viel Traffic.</summary>
    private const string Felder =
        "code,product_name,product_name_de,brands,quantity,categories,categories_tags,image_front_small_url";

    /// <summary>
    /// Open Food Facts ist ein Freiwilligenprojekt und antwortet regelmaessig mit 503 oder
    /// 429, ohne dass etwas kaputt waere. Ein Lauf ueber alle Warengruppen darf daran nicht
    /// scheitern, deshalb wird mit wachsender Wartezeit erneut gefragt.
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

    public async Task<OffSuchantwort?> SuchenAsync(
        string kategorieTag,
        string land,
        int seite,
        int seitengroesse,
        CancellationToken ct)
    {
        var pfad = "api/v2/search"
            + $"?categories_tags={Uri.EscapeDataString(kategorieTag)}"
            + $"&countries_tags={Uri.EscapeDataString(land)}"
            + $"&fields={Felder}"
            + $"&page={seite}"
            + $"&page_size={seitengroesse}";

        for (var versuch = 0; ; versuch++)
        {
            var (antwort, wiederholbar) = await VersuchenAsync(pfad, seite, ct);
            if (antwort is not null || !wiederholbar || versuch >= Wartezeiten.Length)
            {
                if (antwort is null && wiederholbar)
                {
                    log.LogWarning(
                        "Seite {Seite} von {Tag} auch nach {Versuche} Versuchen nicht erreichbar — übersprungen.",
                        seite, kategorieTag, Wartezeiten.Length + 1);
                }

                return antwort;
            }

            var warten = Wartezeiten[versuch];
            log.LogInformation(
                "Seite {Seite} von {Tag} nicht verfügbar, neuer Versuch in {Sekunden}s.",
                seite, kategorieTag, warten.TotalSeconds);

            await Task.Delay(warten, ct);
        }
    }

    /// <summary>Ein einzelner Versuch. <c>Wiederholbar</c> unterscheidet "gerade überlastet"
    /// von "gibt es nicht".</summary>
    private async Task<(OffSuchantwort? Antwort, bool Wiederholbar)> VersuchenAsync(
        string pfad,
        int seite,
        CancellationToken ct)
    {
        try
        {
            using var antwort = await http.GetAsync(pfad, ct);

            if (antwort.IsSuccessStatusCode)
            {
                return (await antwort.Content.ReadFromJsonAsync<OffSuchantwort>(JsonOptionen, ct), false);
            }

            var status = (int)antwort.StatusCode;
            var wiederholbar = status is 429 or >= 500;

            if (!wiederholbar)
            {
                log.LogWarning("Open Food Facts antwortete mit {Status} auf Seite {Seite}.", status, seite);
            }

            return (null, wiederholbar);
        }
        catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException && !ct.IsCancellationRequested)
        {
            log.LogDebug(ex, "Netzfehler auf Seite {Seite}.", seite);
            return (null, true);
        }
    }
}
