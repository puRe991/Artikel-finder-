using System.Net.Http.Json;
using System.Text.Json;
using Microsoft.Extensions.Logging;

namespace ArtikelFinder.Import.OpenFoodFacts;

/// <summary>
/// Zugriff auf die Suche der Open-Food-Facts-Familie (API v2). Welche Datenbank gefragt wird
/// und wonach gefiltert wird, steht in der <see cref="OffAbfrage"/>.
///
/// Zwei Regeln aus deren Nutzungsbedingungen sind hier fest verdrahtet:
/// ein aussagekraeftiger User-Agent mit Kontaktmoeglichkeit und eine Wartezeit zwischen
/// den Requests. Die Such-API ist auf 10 Anfragen pro Minute gedeckelt.
/// </summary>
/// <summary>
/// Ergebnis einer Seitenabfrage. Der Unterschied zwischen "vorübergehend nicht erreichbar"
/// und "es gibt nichts mehr" ist entscheidend: beim ersten Fall darf der Importer die Seite
/// überspringen und weitermachen, beim zweiten muss er die Warengruppe beenden.
/// </summary>
public readonly record struct OffSeitenergebnis
{
    private OffSeitenergebnis(OffSuchantwort? antwort, bool istFehlgeschlagen)
    {
        Antwort = antwort;
        IstFehlgeschlagen = istFehlgeschlagen;
    }

    public OffSuchantwort? Antwort { get; }

    /// <summary>Vorübergehender Ausfall — die Seite fehlt, die Warengruppe ist nicht zu Ende.</summary>
    public bool IstFehlgeschlagen { get; }

    public static OffSeitenergebnis Geladen(OffSuchantwort antwort) => new(antwort, false);
    public static OffSeitenergebnis Fehlgeschlagen => new(null, true);

    /// <summary>Die Anfrage war nicht wiederholbar falsch — die Warengruppe endet hier.</summary>
    public static OffSeitenergebnis Abgelehnt => new(null, false);
}

public interface IOffClient
{
    Task<OffSeitenergebnis> SuchenAsync(
        OffAbfrage abfrage,
        int seite,
        int seitengroesse,
        CancellationToken ct);
}

public sealed class OffClient(HttpClient http, ILogger<OffClient> log) : IOffClient
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

    public async Task<OffSeitenergebnis> SuchenAsync(
        OffAbfrage abfrage,
        int seite,
        int seitengroesse,
        CancellationToken ct)
    {
        // Absolute Adresse statt BaseAddress: die Schwesterdatenbanken liegen unter eigenen
        // Hostnamen, sprechen aber dieselbe API.
        var pfad = $"{abfrage.Datenbank.BasisAdresse}api/v2/search"
            + $"?{abfrage.Feld}={Uri.EscapeDataString(abfrage.Wert)}"
            + $"&fields={Felder}"
            + $"&page={seite}"
            + $"&page_size={seitengroesse}";

        if (!string.IsNullOrWhiteSpace(abfrage.Land))
        {
            pfad += $"&countries_tags={Uri.EscapeDataString(abfrage.Land)}";
        }

        for (var versuch = 0; ; versuch++)
        {
            var (antwort, wiederholbar) = await VersuchenAsync(pfad, seite, ct);

            if (antwort is not null)
            {
                return OffSeitenergebnis.Geladen(antwort);
            }

            if (!wiederholbar)
            {
                // Die Anfrage selbst ist falsch (unbekannter Tag o.ae.) — Wiederholen hilft nicht.
                return OffSeitenergebnis.Abgelehnt;
            }

            if (versuch >= Wartezeiten.Length)
            {
                log.LogWarning(
                    "Seite {Seite} von {Abfrage} auch nach {Versuche} Versuchen nicht erreichbar — übersprungen.",
                    seite, abfrage, Wartezeiten.Length + 1);

                return OffSeitenergebnis.Fehlgeschlagen;
            }

            var warten = Wartezeiten[versuch];
            log.LogInformation(
                "Seite {Seite} von {Abfrage} nicht verfügbar, neuer Versuch in {Sekunden}s.",
                seite, abfrage, warten.TotalSeconds);

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
