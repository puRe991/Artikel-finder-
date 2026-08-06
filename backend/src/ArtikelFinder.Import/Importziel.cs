namespace ArtikelFinder.Import;

/// <summary>
/// Ein Import-Lauf pro Warengruppe: Open-Food-Facts-Tag links, eigene Kategorie rechts.
/// Der Umweg ueber eine feste Liste ist Absicht — OFF-Kategorien sind mehrsprachig,
/// uneinheitlich tief verschachtelt und teils von Nutzern frei vergeben. Eine Automatik
/// darueber produziert mehr Fehlzuordnungen als sie Arbeit spart.
/// </summary>
public sealed record Importziel(string OffTag, string ZielKategorie)
{
    /// <summary>
    /// Grundabdeckung fuer einen Lebensmittelvollsortimenter. Die Zielkategorien
    /// entsprechen dem Raster aus <c>Startdaten</c>.
    /// </summary>
    public static readonly IReadOnlyList<Importziel> Standard =
    [
        new("en:milks", "Milch"),
        new("en:yogurts", "Joghurt & Quark"),
        new("en:cheeses", "Käse"),
        new("en:butters", "Butter & Margarine"),
        new("en:breads", "Brot"),
        new("en:breakfast-cereals", "Mehl & Backzutaten"),
        new("en:pastas", "Nudeln & Reis"),
        new("en:rices", "Nudeln & Reis"),
        new("en:canned-foods", "Konserven"),
        new("en:vegetable-oils", "Öl & Essig"),
        new("en:spices", "Gewürze"),
        new("en:chocolates", "Schokolade"),
        new("en:biscuits", "Kekse"),
        new("en:crisps", "Chips & Salziges"),
        new("en:waters", "Wasser"),
        new("en:fruit-juices", "Säfte"),
        new("en:sodas", "Limonaden"),
        new("en:coffees", "Kaffee & Tee"),
        new("en:teas", "Kaffee & Tee"),
        new("en:beers", "Bier & Wein"),
        new("en:wines", "Bier & Wein"),
        new("en:frozen-foods", "Tiefkühlgemüse"),
        new("en:pizzas", "Pizza & Fertiggerichte"),
        new("en:ice-creams", "Eis"),
        new("en:sausages", "Wurstwaren"),
        new("en:fresh-fruits", "Obst"),
        new("en:fresh-vegetables", "Gemüse"),
    ];
}
