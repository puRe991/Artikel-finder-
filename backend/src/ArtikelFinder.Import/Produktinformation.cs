using System.Globalization;

namespace ArtikelFinder.Import;

/// <summary>
/// Uebersetzt die Angaben aus Open Food Facts in das, was im Laden vorgelesen werden kann.
///
/// <para><b>Warum Positivlisten und keine Uebernahme aller Tags.</b> Open Food Facts leitet
/// Allergene teils aus der Zutatenliste ab, und das geht schief: neben den echten Tags
/// (<c>en:milk</c>, <c>en:nuts</c>) stehen dort Bruchstuecke wie <c>en:Butterreinfett</c>,
/// <c>en:Magermilchpulver</c> oder <c>en:Mandelstueckchen</c>. In einer Allergenanzeige
/// waeren das keine Schoenheitsfehler: wer sie liest, haelt sie fuer eine Auskunft. Deshalb
/// wird ausschliesslich uebernommen, was in dieser Liste steht — den vierzehn nach
/// EU-Lebensmittelinformationsverordnung kennzeichnungspflichtigen Allergenen.</para>
///
/// <para>Die Folge ist bewusst asymmetrisch: ein unbekannter Tag verschwindet, statt falsch
/// beschriftet zu erscheinen. Angezeigt wird damit eher zu wenig als zu viel — und die App
/// sagt dazu, dass die Packung die verbindliche Quelle bleibt.</para>
/// </summary>
public static class Produktinformation
{
    /// <summary>Die vierzehn kennzeichnungspflichtigen Allergene (LMIV Anhang II).</summary>
    private static readonly Dictionary<string, string> Allergene = new(StringComparer.OrdinalIgnoreCase)
    {
        ["en:gluten"] = "Glutenhaltiges Getreide",
        ["en:crustaceans"] = "Krebstiere",
        ["en:eggs"] = "Eier",
        ["en:fish"] = "Fisch",
        ["en:peanuts"] = "Erdnüsse",
        ["en:soybeans"] = "Soja",
        ["en:milk"] = "Milch",
        ["en:nuts"] = "Schalenfrüchte",
        ["en:celery"] = "Sellerie",
        ["en:mustard"] = "Senf",
        ["en:sesame-seeds"] = "Sesam",
        ["en:sulphur-dioxide-and-sulphites"] = "Schwefeldioxid und Sulfite",
        ["en:lupin"] = "Lupinen",
        ["en:molluscs"] = "Weichtiere",
    };

    /// <summary>
    /// Auszeichnungen, nach denen im Laden tatsaechlich gefragt wird. Bewusst kurz gehalten:
    /// Open Food Facts fuehrt hunderte Tags, von denen die meisten (<c>fr:triman</c>,
    /// <c>en:rainforest-alliance-cocoa</c>) niemandem bei der Kaufentscheidung helfen.
    /// </summary>
    private static readonly Dictionary<string, string> Auszeichnungen = new(StringComparer.OrdinalIgnoreCase)
    {
        ["en:organic"] = "Bio",
        ["en:eu-organic"] = "Bio",
        ["de:bio"] = "Bio",
        ["en:vegan"] = "Vegan",
        ["en:vegetarian"] = "Vegetarisch",
        ["en:gluten-free"] = "Glutenfrei",
        ["en:no-gluten"] = "Glutenfrei",
        ["en:lactose-free"] = "Laktosefrei",
        ["en:no-lactose"] = "Laktosefrei",
        ["en:palm-oil-free"] = "Ohne Palmöl",
        ["en:no-added-sugar"] = "Ohne Zuckerzusatz",
        ["en:fair-trade"] = "Fairtrade",
        ["en:fairtrade-international"] = "Fairtrade",
        ["en:halal"] = "Halal",
        ["en:kosher"] = "Koscher",
    };

    /// <summary>
    /// Die Naehrwerte je 100 g, in der Reihenfolge der Naehrwerttabelle auf der Packung.
    /// Der Schluessel links steht so in der Katalogdatei, rechts der Name bei OFF.
    /// </summary>
    private static readonly (string Kurz, string Off, string Einheit)[] Naehrwertfelder =
    [
        ("kcal", "energy-kcal_100g", "kcal"),
        ("fett", "fat_100g", "g"),
        ("gesfett", "saturated-fat_100g", "g"),
        ("kh", "carbohydrates_100g", "g"),
        ("zucker", "sugars_100g", "g"),
        ("eiweiss", "proteins_100g", "g"),
        ("salz", "salt_100g", "g"),
    ];

    /// <summary>Nur die kennzeichnungspflichtigen Allergene, auf Deutsch, ohne Dubletten.</summary>
    public static string? AllergeneLesen(IEnumerable<string>? tags) => Uebersetzen(tags, Allergene);

    /// <summary>Auszeichnungen wie Bio, Vegan oder Glutenfrei, auf Deutsch.</summary>
    public static string? AuszeichnungenLesen(IEnumerable<string>? tags) => Uebersetzen(tags, Auszeichnungen);

    /// <summary>
    /// Die Naehrwerte als <c>kcal=250;fett=12.5;salz=0.2</c>. Eine Spalte statt sieben:
    /// die Katalogdatei bleibt lesbar, und fehlende Werte kosten keinen leeren Platz.
    /// </summary>
    public static string? NaehrwerteLesen(IReadOnlyDictionary<string, object?>? naehrwerte)
    {
        if (naehrwerte is null || naehrwerte.Count == 0)
        {
            return null;
        }

        var teile = new List<string>(Naehrwertfelder.Length);

        foreach (var (kurz, off, _) in Naehrwertfelder)
        {
            if (!naehrwerte.TryGetValue(off, out var roh) || roh is null)
            {
                continue;
            }

            if (!ZahlLesen(roh, out var wert) || wert < 0 || wert > 10_000)
            {
                continue;
            }

            teile.Add($"{kurz}={wert.ToString("0.##", CultureInfo.InvariantCulture)}");
        }

        return teile.Count == 0 ? null : string.Join(';', teile);
    }

    /// <summary>Nutri-Score a–e. Alles andere (unknown, not-applicable) faellt weg.</summary>
    public static string? NutriscoreLesen(string? roh)
    {
        var wert = roh?.Trim().ToLowerInvariant();
        return wert?.Length == 1 && wert[0] is >= 'a' and <= 'e' ? wert : null;
    }

    /// <summary>
    /// Die Zutatenliste. Open Food Facts enthaelt hier haeufig Reste der Texterkennung —
    /// sehr kurze Fragmente sagen nichts und sehr lange sprengen die Katalogdatei.
    /// </summary>
    public static string? ZutatenLesen(string? roh)
    {
        var text = roh?.Replace('\n', ' ').Replace('\t', ' ').Trim();
        if (string.IsNullOrWhiteSpace(text) || text.Length < 10)
        {
            return null;
        }

        // Mehrfache Leerzeichen zusammenziehen, sonst stehen in der App Luecken.
        text = string.Join(' ', text.Split(' ', StringSplitOptions.RemoveEmptyEntries));

        return text.Length > 1500 ? text[..1500].TrimEnd() + " …" : text;
    }

    private static string? Uebersetzen(IEnumerable<string>? tags, Dictionary<string, string> liste)
    {
        if (tags is null)
        {
            return null;
        }

        var treffer = new List<string>();

        foreach (var tag in tags)
        {
            if (liste.TryGetValue(tag.Trim(), out var deutsch) && !treffer.Contains(deutsch))
            {
                treffer.Add(deutsch);
            }
        }

        return treffer.Count == 0 ? null : string.Join(", ", treffer);
    }

    /// <summary>
    /// Open Food Facts liefert Naehrwerte mal als Zahl, mal als Zeichenkette — und dort
    /// gelegentlich mit Komma statt Punkt.
    /// </summary>
    private static bool ZahlLesen(object roh, out double wert)
    {
        switch (roh)
        {
            case double d:
                wert = d;
                return !double.IsNaN(d) && !double.IsInfinity(d);

            case int i:
                wert = i;
                return true;

            case System.Text.Json.JsonElement element:
                if (element.ValueKind == System.Text.Json.JsonValueKind.Number)
                {
                    return element.TryGetDouble(out wert);
                }

                return ZahlAusText(element.ValueKind == System.Text.Json.JsonValueKind.String
                    ? element.GetString()
                    : null, out wert);

            default:
                return ZahlAusText(roh.ToString(), out wert);
        }
    }

    private static bool ZahlAusText(string? text, out double wert)
    {
        wert = 0;
        if (string.IsNullOrWhiteSpace(text))
        {
            return false;
        }

        return double.TryParse(
            text.Replace(',', '.'),
            NumberStyles.Float,
            CultureInfo.InvariantCulture,
            out wert);
    }
}
