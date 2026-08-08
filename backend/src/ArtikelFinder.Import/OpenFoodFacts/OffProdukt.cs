using System.Text.Json.Serialization;
using ArtikelFinder.Import;

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

    // --- Angaben fuer die Auskunft im Laden ---

    /// <summary>
    /// Allergene als Tags. Enthaelt neben den echten (<c>en:milk</c>) auch Bruchstuecke aus
    /// der Zutatenanalyse (<c>en:Magermilchpulver</c>) — <see cref="Produktinformation"/>
    /// laesst nur die kennzeichnungspflichtigen durch.
    /// </summary>
    [JsonPropertyName("allergens_tags")]
    public List<string>? AllergenTags { get; set; }

    /// <summary>„Kann Spuren enthalten von …" — dieselbe Behandlung wie die Allergene.</summary>
    [JsonPropertyName("traces_tags")]
    public List<string>? SpurenTags { get; set; }

    /// <summary>Bio, Vegan, Glutenfrei und dergleichen.</summary>
    [JsonPropertyName("labels_tags")]
    public List<string>? AuszeichnungsTags { get; set; }

    [JsonPropertyName("ingredients_text_de")]
    public string? ZutatenDe { get; set; }

    [JsonPropertyName("ingredients_text")]
    public string? Zutaten { get; set; }

    [JsonPropertyName("nutriscore_grade")]
    public string? Nutriscore { get; set; }

    /// <summary>
    /// Rund hundert Eintraege je Produkt, je Naehrwert mehrere Fassungen
    /// (<c>_100g</c>, <c>_serving</c>, <c>_unit</c>). Gelesen werden nur die je 100 g.
    /// </summary>
    [JsonPropertyName("nutriments")]
    public Dictionary<string, object?>? Naehrwerte { get; set; }

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

    /// <summary>Die Menge als eigenes Feld — Grundlage fuer den Grundpreis je Kilo/Liter.</summary>
    public string? MengeFuerKatalog() => MengeBereinigt();

    /// <summary>
    /// Die Angaben, die im Laden vorgelesen werden koennen. Deutsche Zutatenliste hat
    /// Vorrang; fehlt sie, ist die allgemeine besser als keine.
    /// </summary>
    public Produktangaben Angaben() => new(
        Menge: MengeBereinigt(),
        Allergene: Produktinformation.AllergeneLesen(AllergenTags),
        Spuren: Produktinformation.AllergeneLesen(SpurenTags),
        Auszeichnungen: Produktinformation.AuszeichnungenLesen(AuszeichnungsTags),
        Naehrwerte: Produktinformation.NaehrwerteLesen(Naehrwerte),
        Nutriscore: Produktinformation.NutriscoreLesen(Nutriscore),
        Zutaten: Produktinformation.ZutatenLesen(ZutatenDe) ?? Produktinformation.ZutatenLesen(Zutaten));
}

/// <summary>Was ueber einen Artikel Auskunft gibt, fertig fuer Katalog und Anzeige.</summary>
public sealed record Produktangaben(
    string? Menge,
    string? Allergene,
    string? Spuren,
    string? Auszeichnungen,
    string? Naehrwerte,
    string? Nutriscore,
    string? Zutaten)
{
    public bool IstLeer => Menge is null && Allergene is null && Spuren is null
        && Auszeichnungen is null && Naehrwerte is null && Nutriscore is null && Zutaten is null;
}
