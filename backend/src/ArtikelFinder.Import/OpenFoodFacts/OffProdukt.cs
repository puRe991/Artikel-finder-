using System.Text.Json.Serialization;

namespace ArtikelFinder.Import.OpenFoodFacts;

/// <summary>Antwort der Open-Food-Facts-Suche (API v2).</summary>
public sealed class OffSuchantwort
{
    [JsonPropertyName("count")]
    public int Anzahl { get; set; }

    [JsonPropertyName("page")]
    public int Seite { get; set; }

    [JsonPropertyName("page_size")]
    public int Seitengroesse { get; set; }

    [JsonPropertyName("products")]
    public List<OffProdukt> Produkte { get; set; } = [];
}

/// <summary>
/// Nur die Felder, die der Katalog braucht. Open Food Facts liefert pro Produkt gut 200
/// Felder; die Abfrage schraenkt per <c>fields</c>-Parameter ein, um Traffic zu sparen.
/// </summary>
public sealed class OffProdukt
{
    /// <summary>Barcode. Bei Open Food Facts ist das die EAN/GTIN.</summary>
    [JsonPropertyName("code")]
    public string? Code { get; set; }

    [JsonPropertyName("product_name")]
    public string? ProduktName { get; set; }

    /// <summary>Deutscher Produktname, falls hinterlegt — hat Vorrang vor dem generischen.</summary>
    [JsonPropertyName("product_name_de")]
    public string? ProduktNameDe { get; set; }

    [JsonPropertyName("brands")]
    public string? Marken { get; set; }

    [JsonPropertyName("quantity")]
    public string? Menge { get; set; }

    /// <summary>Kategorien als Klartext, kommagetrennt, Sprache gemischt.</summary>
    [JsonPropertyName("categories")]
    public string? Kategorien { get; set; }

    [JsonPropertyName("categories_tags")]
    public List<string>? KategorieTags { get; set; }

    [JsonPropertyName("image_front_small_url")]
    public string? BildUrl { get; set; }

    /// <summary>Erste Marke aus der kommagetrennten Liste.</summary>
    public string? ErsteMarke() => Marken?
        .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
        .FirstOrDefault();

    /// <summary>
    /// Bester verfuegbarer Name. Open Food Facts hat viele Produkte mit leerem Namen —
    /// die sind fuer den Katalog wertlos und werden vom Importer uebersprungen.
    /// </summary>
    public string? BesterName()
    {
        var name = !string.IsNullOrWhiteSpace(ProduktNameDe) ? ProduktNameDe : ProduktName;
        if (string.IsNullOrWhiteSpace(name))
        {
            return null;
        }

        name = name.Trim();

        // Die Menge gehoert im Regal zum Namen ("Vollmilch 1 l"), OFF fuehrt sie separat.
        var menge = MengeBereinigt();
        return menge is null ? name : $"{name} {menge}";
    }

    /// <summary>
    /// OFF-Mengenangaben sind Freitext und enthalten haeufig Platzhalter wie "1unknown"
    /// oder "0". Solche Werte im Artikelnamen sehen im Katalog nach Fehler aus.
    /// </summary>
    private string? MengeBereinigt()
    {
        if (string.IsNullOrWhiteSpace(Menge))
        {
            return null;
        }

        var menge = Menge.Trim();

        if (menge.Contains("unknown", StringComparison.OrdinalIgnoreCase)
            || menge.Contains("null", StringComparison.OrdinalIgnoreCase)
            || !menge.Any(char.IsDigit)
            || menge.Length > 30)
        {
            return null;
        }

        return menge;
    }
}
