using ArtikelFinder.Import.OpenFoodFacts;

namespace ArtikelFinder.Import;

/// <summary>
/// Ordnet einem Produkt anhand seiner Open-Food-Facts-Tags eine Kategorie des Marktes zu.
///
/// Beim Import nach Warengruppe braucht es das nicht — dort steht die Zielkategorie schon in
/// der Abfrage. Eine Marke zieht sich dagegen quer durchs Sortiment: unter K-Classic stehen
/// Milch, Toilettenpapier und Katzenfutter nebeneinander. Ohne diese Zuordnung landeten
/// mehrere tausend Artikel kategorielos im Katalog und wären in der Gang-Übersicht nicht
/// auffindbar.
///
/// Aufgelöst wird vom speziellsten Tag zum allgemeinsten: Open Food Facts sortiert
/// <c>categories_tags</c> von der Ober- zur Unterkategorie, rückwärts gelesen gewinnt also
/// die genaueste Angabe. Fehlt sie, greift die nächstgröbere ("en:dairies" →
/// Molkereiprodukte) und zuletzt die Standardkategorie der Datenbank.
///
/// Nicht zugeordnet wird, was sich nicht eindeutig zuordnen lässt — ein Artikel ohne
/// Kategorie ist über die Suche weiterhin zu finden, ein falsch einsortierter schickt dich
/// in den falschen Gang.
/// </summary>
public static class Kategoriezuordnung
{
    private static readonly Dictionary<string, string> NachTag = Aufbauen();

    /// <summary>
    /// Kategorie für ein Produkt, oder <c>null</c>, wenn keine Zuordnung möglich ist.
    /// </summary>
    public static string? Fuer(OffProdukt produkt, string? standardKategorie)
    {
        var tags = produkt.KategorieTags;

        if (tags is not null)
        {
            for (var i = tags.Count - 1; i >= 0; i--)
            {
                if (NachTag.TryGetValue(tags[i], out var kategorie))
                {
                    return kategorie;
                }
            }
        }

        return standardKategorie;
    }

    /// <summary>Alle Tags, für die eine Zuordnung hinterlegt ist.</summary>
    public static IReadOnlyCollection<string> BekannteTags => NachTag.Keys;

    private static Dictionary<string, string> Aufbauen()
    {
        var zuordnung = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);

        // Die Warengruppenliste ist bereits eine geprüfte Tag-zu-Kategorie-Zuordnung.
        foreach (var ziel in Importziel.Standard)
        {
            zuordnung[ziel.OffTag] = ziel.ZielKategorie;
        }

        // Ergänzungen aus den Tags, die im Kaufland-Eigenmarkensortiment tatsächlich
        // vorkommen. Reihenfolge ohne Bedeutung — die Auflösung entscheidet der Tag am
        // Produkt, nicht die Position hier.
        foreach (var (tag, kategorie) in Ergaenzungen())
        {
            zuordnung[tag] = kategorie;
        }

        return zuordnung;
    }

    private static (string Tag, string Kategorie)[] Ergaenzungen() =>
    [
        // --- Molkereiprodukte ---
        ("en:dairies", "Molkereiprodukte"),
        ("en:milks-liquid-and-powder", "Milch"),
        ("en:uht-milks", "Milch"),
        ("en:homogenized-milks", "Milch"),
        ("en:pasteurised-milks", "Milch"),
        ("en:creams", "Milch"),
        ("en:fermented-milk-products", "Joghurt & Quark"),
        ("en:fermented-dairy-desserts", "Joghurt & Quark"),
        ("en:dairy-desserts", "Joghurt & Quark"),
        ("en:plain-yogurts", "Joghurt & Quark"),
        ("en:quarks", "Joghurt & Quark"),
        ("en:skyrs", "Joghurt & Quark"),
        ("en:plain-skyrs", "Joghurt & Quark"),
        ("en:cream-cheeses", "Käse"),
        ("en:hard-cheeses", "Käse"),
        ("en:soft-cheeses", "Käse"),
        ("en:margarines", "Butter & Margarine"),
        ("en:spreadable-fats", "Butter & Margarine"),
        ("en:dairy-spreads", "Butter & Margarine"),

        // --- Brot & Backwaren ---
        ("en:crispbreads", "Brot"),
        ("en:sliced-breads", "Brot"),
        ("en:baguettes", "Brot"),
        ("en:german-breads", "Brot"),
        ("en:bread-rolls", "Brötchen"),
        ("en:cakes", "Kuchen & Gebäck"),
        ("en:pastries", "Kuchen & Gebäck"),
        ("en:viennoiseries", "Kuchen & Gebäck"),

        // --- Grundnahrungsmittel ---
        ("en:cereals-and-their-products", "Mehl & Backzutaten"),
        ("en:flours", "Mehl & Backzutaten"),
        ("en:wheat-flours", "Mehl & Backzutaten"),
        ("en:sugars", "Mehl & Backzutaten"),
        ("en:noodles", "Nudeln & Reis"),
        ("en:durum-wheat-pasta", "Nudeln & Reis"),
        ("en:canned-vegetables", "Konserven"),
        ("en:canned-fruits", "Konserven"),
        ("en:canned-fishes", "Konserven"),
        ("en:canned-plant-based-foods", "Konserven"),
        ("en:legumes-and-their-products", "Konserven"),
        ("en:olive-oils", "Öl & Essig"),
        ("en:sunflower-oils", "Öl & Essig"),
        ("en:vinegars", "Öl & Essig"),
        ("en:condiments", "Gewürze"),
        ("en:sauces", "Gewürze"),
        ("en:salts", "Gewürze"),
        ("en:mustards", "Gewürze"),
        ("en:ketchup", "Gewürze"),

        // --- Süßwaren & Snacks ---
        ("en:sweet-snacks", "Süßwaren & Snacks"),
        ("en:cocoa-and-its-products", "Schokolade"),
        ("en:chocolate-candies", "Schokolade"),
        ("en:biscuits-and-cakes", "Kekse"),
        ("en:biscuits-and-crackers", "Kekse"),
        ("en:salty-snacks", "Chips & Salziges"),
        ("en:appetizers", "Chips & Salziges"),
        ("en:chips-and-fries", "Chips & Salziges"),

        // --- Getränke ---
        ("en:beverages", "Getränke"),
        ("en:non-alcoholic-beverages", "Getränke"),
        ("en:spring-waters", "Wasser"),
        ("en:mineral-waters", "Wasser"),
        ("en:natural-mineral-waters", "Wasser"),
        ("en:carbonated-waters", "Wasser"),
        ("en:juices-and-nectars", "Säfte"),
        ("en:juices", "Säfte"),
        ("en:nectars", "Säfte"),
        ("en:fruit-based-beverages", "Säfte"),
        ("en:sodas", "Limonaden"),
        ("en:soft-drinks", "Limonaden"),
        ("en:carbonated-drinks", "Limonaden"),
        ("en:energy-drinks", "Limonaden"),
        ("en:ground-coffees", "Kaffee & Tee"),
        ("en:instant-coffees", "Kaffee & Tee"),
        ("en:herbal-teas", "Kaffee & Tee"),
        ("en:sparkling-wines", "Bier & Wein"),
        ("en:lagers", "Bier & Wein"),

        // --- Fleisch & Wurst ---
        ("en:meats-and-their-products", "Fleisch & Wurst"),
        ("en:meats", "Frischfleisch"),
        ("en:fresh-meats", "Frischfleisch"),
        ("en:ground-meat-preparations", "Frischfleisch"),
        ("en:beef", "Frischfleisch"),
        ("en:pork", "Frischfleisch"),
        ("en:prepared-meats", "Wurstwaren"),
        ("en:hams", "Wurstwaren"),
        ("en:cooked-hams", "Wurstwaren"),
        ("en:salamis", "Wurstwaren"),
        ("en:meat-spreads", "Wurstwaren"),
        ("en:poultry", "Geflügel"),
        ("en:chickens", "Geflügel"),
        ("en:turkeys", "Geflügel"),

        // --- Obst & Gemüse ---
        ("en:fruits", "Obst"),
        ("en:vegetables", "Gemüse"),
        ("en:salads", "Salate & Kräuter"),
        ("en:fresh-herbs", "Salate & Kräuter"),

        // --- Tiefkühl ---
        ("en:frozen-foods", "Tiefkühl"),
        ("en:frozen-vegetables", "Tiefkühlgemüse"),
        ("en:frozen-pizzas", "Pizza & Fertiggerichte"),
        ("en:meals", "Pizza & Fertiggerichte"),
        ("en:ready-made-meals", "Pizza & Fertiggerichte"),

        // --- Drogerie: fast nur aus Open Beauty Facts und Open Products Facts ---
        ("en:hygiene", "Körperpflege"),
        ("en:toothpastes", "Körperpflege"),
        ("en:soaps", "Körperpflege"),
        ("en:shampoos", "Körperpflege"),
        ("en:shower-gels", "Körperpflege"),
        ("en:deodorants", "Körperpflege"),
        ("en:body-lotions", "Körperpflege"),
        ("en:intimate-hygiene", "Körperpflege"),
        ("en:moist-wipes", "Körperpflege"),
        ("en:baby-wipes", "Körperpflege"),
        ("de:Zahncreme", "Körperpflege"),
        ("de:Zahnbürsten", "Körperpflege"),
        ("de:Mundpflege", "Körperpflege"),
        ("de:Mundspülung", "Körperpflege"),
        ("de:Zahnpflege", "Körperpflege"),
        ("en:laundry-detergents", "Waschmittel"),
        ("en:detergents", "Reinigung"),
        ("en:household-chemicals", "Reinigung"),
        ("en:cleaning-products", "Reinigung"),
        ("en:household-paper-products", "Papierwaren"),
        ("en:toilet-papers", "Papierwaren"),
        ("uk:Туалетний папір", "Papierwaren"),
        ("en:paper-towels", "Papierwaren"),
        ("en:facial-tissues", "Papierwaren"),

        // --- Tierbedarf: aus Open Pet Food Facts ---
        ("en:pet-food", "Tierbedarf"),
        ("en:dog-and-cat-food", "Tierbedarf"),
        ("de:Tierbedarfe", "Tierbedarf"),
        ("en:dog-food", "Hundefutter"),
        ("en:wet-dog-food", "Hundefutter"),
        ("en:dry-dog-food", "Hundefutter"),
        ("en:cat-food", "Katzenfutter"),
        ("en:wet-cat-food", "Katzenfutter"),
        ("en:dry-cat-food", "Katzenfutter"),
        ("en:adult-cat-food", "Katzenfutter"),
        ("en:bird-food", "Kleintier & Vogel"),
        ("en:rodent-food", "Kleintier & Vogel"),

        // --- Haushalt: aus Open Products Facts ---
        ("en:home-garden", "Haushalt & Sonstiges"),
        ("en:household-supplies", "Haushalt & Sonstiges"),
        ("en:kitchen-dining", "Haushalt & Sonstiges"),
        ("en:office-supplies", "Haushalt & Sonstiges"),
        ("en:electronics", "Haushalt & Sonstiges"),
        ("en:batteries", "Haushalt & Sonstiges"),
    ];
}
