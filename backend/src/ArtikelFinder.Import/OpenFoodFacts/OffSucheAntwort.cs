using System.Text.Json;
using System.Text.Json.Serialization;

namespace ArtikelFinder.Import.OpenFoodFacts;

/// <summary>
/// Antwort von <c>search.openfoodfacts.org</c>. Dieselben Produkte wie in der Produkt-API,
/// aber anders verpackt: die Treffer heissen <c>hits</c> statt <c>products</c>, und Marken
/// kommen als Liste statt als kommagetrennter Text. Deshalb ein eigenes Modell, das
/// anschliessend auf das gemeinsame umgerechnet wird — der Rest des Importers soll den
/// Unterschied nicht sehen.
/// </summary>
public sealed class OffSucheAntwort
{
    [JsonPropertyName("count")]
    public int Anzahl { get; set; }

    [JsonPropertyName("page")]
    public int Seite { get; set; }

    [JsonPropertyName("page_size")]
    public int Seitengroesse { get; set; }

    [JsonPropertyName("hits")]
    public List<OffSuchtreffer> Treffer { get; set; } = [];

    public OffSuchantwort AlsSuchantwort() => new()
    {
        Anzahl = Anzahl,
        Seite = Seite,
        Seitengroesse = Seitengroesse,
        Produkte = [.. Treffer.Select(t => t.AlsProdukt())],
    };
}

public sealed class OffSuchtreffer
{
    [JsonPropertyName("code")]
    public string? Code { get; set; }

    [JsonPropertyName("product_name")]
    public string? ProduktName { get; set; }

    [JsonPropertyName("product_name_de")]
    public string? ProduktNameDe { get; set; }

    /// <summary>
    /// Der Suchdienst liefert Marken als Liste. Vereinzelt steht dort ein einzelner Text —
    /// deshalb der nachsichtige Konverter: ein unerwarteter Typ darf nicht die ganze Seite
    /// kosten.
    /// </summary>
    [JsonPropertyName("brands")]
    [JsonConverter(typeof(TextOderListeKonverter))]
    public List<string>? Marken { get; set; }

    [JsonPropertyName("quantity")]
    public string? Menge { get; set; }

    [JsonPropertyName("categories_tags")]
    public List<string>? KategorieTags { get; set; }

    [JsonPropertyName("image_front_small_url")]
    public string? BildUrl { get; set; }

    public OffProdukt AlsProdukt() => new()
    {
        Code = Code,
        ProduktName = ProduktName,
        ProduktNameDe = ProduktNameDe,
        Marken = Marken is { Count: > 0 } ? string.Join(", ", Marken) : null,
        Menge = Menge,
        KategorieTags = KategorieTags,
        BildUrl = BildUrl,
    };
}

/// <summary>Liest ein Feld, das mal als Text und mal als Liste von Texten ankommt.</summary>
public sealed class TextOderListeKonverter : JsonConverter<List<string>?>
{
    public override List<string>? Read(ref Utf8JsonReader leser, Type typ, JsonSerializerOptions optionen)
    {
        switch (leser.TokenType)
        {
            case JsonTokenType.Null:
                return null;

            case JsonTokenType.String:
                var text = leser.GetString();
                return string.IsNullOrWhiteSpace(text) ? null : [text];

            case JsonTokenType.StartArray:
                var werte = new List<string>();
                while (leser.Read() && leser.TokenType != JsonTokenType.EndArray)
                {
                    if (leser.TokenType == JsonTokenType.String && leser.GetString() is { } wert)
                    {
                        werte.Add(wert);
                    }
                }

                return werte;

            default:
                leser.Skip();
                return null;
        }
    }

    public override void Write(Utf8JsonWriter schreiber, List<string>? wert, JsonSerializerOptions optionen) =>
        throw new NotSupportedException("Der Importer schreibt keine Suchantworten.");
}
