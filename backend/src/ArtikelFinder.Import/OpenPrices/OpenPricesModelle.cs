using System.Text.Json.Serialization;
using ArtikelFinder.Import.OpenFoodFacts;

namespace ArtikelFinder.Import.OpenPrices;

/// <summary>
/// Eine Seite der Open-Prices-API. Anders als Open Food Facts liefert sie die Gesamtzahl der
/// Seiten mit — der Importer muss nicht raten, ob nach einer kurzen Seite noch etwas kommt.
/// </summary>
public sealed class OpSeite<T>
{
    [JsonPropertyName("items")]
    public List<T> Eintraege { get; set; } = [];

    [JsonPropertyName("page")]
    public int Seite { get; set; }

    [JsonPropertyName("pages")]
    public int Seiten { get; set; }

    [JsonPropertyName("total")]
    public int Gesamt { get; set; }
}

/// <summary>
/// Ein Laden, in dem jemand Preise erfasst hat. Die Stammdaten stammen aus OpenStreetMap,
/// deshalb die <c>osm_</c>-Felder.
/// </summary>
public sealed class OpStandort
{
    [JsonPropertyName("id")]
    public int Id { get; set; }

    [JsonPropertyName("osm_name")]
    public string? Name { get; set; }

    /// <summary>Handelskette, z.B. "Kaufland". Bei kleinen Läden leer.</summary>
    [JsonPropertyName("osm_brand")]
    public string? Kette { get; set; }

    [JsonPropertyName("osm_address_city")]
    public string? Ort { get; set; }

    [JsonPropertyName("osm_address_country")]
    public string? Land { get; set; }

    /// <summary>
    /// Wie viele Preise an diesem Standort hängen. Standorte ohne Preise braucht der
    /// Importer gar nicht erst abzufragen — das spart bei knapp tausend Läden gut vierzig
    /// Anfragen.
    /// </summary>
    [JsonPropertyName("price_count")]
    public int Preisanzahl { get; set; }

    public string Anzeigename() =>
        (string.IsNullOrWhiteSpace(Kette) ? Name : Kette) is { Length: > 0 } name
            ? string.IsNullOrWhiteSpace(Ort) ? name : $"{name} {Ort}"
            : $"Standort {Id}";
}

/// <summary>
/// Eine einzelne Preiserfassung. Wer sie erfasst hat und mit welchem Kassenbon-Foto sie
/// belegt ist, interessiert den Katalog nicht — nur Barcode, Betrag und Datum.
/// </summary>
public sealed class OpPreis
{
    /// <summary><c>PRODUCT</c> für einen Barcode, <c>CATEGORY</c> für lose Ware ohne EAN.</summary>
    [JsonPropertyName("type")]
    public string? Art { get; set; }

    [JsonPropertyName("product_code")]
    public string? ProduktCode { get; set; }

    [JsonPropertyName("price")]
    public decimal Preis { get; set; }

    [JsonPropertyName("price_is_discounted")]
    public bool IstAktionspreis { get; set; }

    /// <summary>Der Normalpreis, wenn ein Aktionspreis erfasst wurde. Oft, aber nicht immer gesetzt.</summary>
    [JsonPropertyName("price_without_discount")]
    public decimal? PreisOhneAktion { get; set; }

    [JsonPropertyName("currency")]
    public string? Waehrung { get; set; }

    [JsonPropertyName("date")]
    public DateOnly? Datum { get; set; }

    /// <summary>
    /// Die Produktdaten liefert Open Prices bei jedem Preis mit — dieselben Felder wie Open
    /// Food Facts, nur anders benannt. Damit lässt sich ein Artikel anlegen, den der Katalog
    /// noch nicht kennt, ohne eine zweite API zu fragen.
    /// </summary>
    [JsonPropertyName("product")]
    public OpProdukt? Produkt { get; set; }
}

/// <summary>Das an einem Preis hängende Produkt, gespiegelt aus Open Food Facts.</summary>
public sealed class OpProdukt
{
    [JsonPropertyName("code")]
    public string? Code { get; set; }

    [JsonPropertyName("product_name")]
    public string? Name { get; set; }

    /// <summary>Kommagetrennt wie bei Open Food Facts.</summary>
    [JsonPropertyName("brands")]
    public string? Marken { get; set; }

    [JsonPropertyName("quantity")]
    public string? Menge { get; set; }

    [JsonPropertyName("categories_tags")]
    public List<string>? KategorieTags { get; set; }

    [JsonPropertyName("image_url")]
    public string? BildUrl { get; set; }

    /// <summary>
    /// Auf das gemeinsame Produktmodell umrechnen. Namensaufbereitung, Mengenanhang und
    /// Kategoriezuordnung gelten damit für alle Quellen gleich — ein Artikel aus Open Prices
    /// sieht im Katalog aus wie einer aus Open Food Facts.
    /// </summary>
    public OffProdukt AlsOffProdukt() => new()
    {
        Code = Code,
        ProduktName = Name,
        Marken = Marken,
        Menge = Menge,
        KategorieTags = KategorieTags,
        BildUrl = BildUrl,
    };
}
