namespace ArtikelFinder.Import;

/// <summary>
/// Eine Kaufland-Eigenmarke, so wie sie bei Open Food Facts getaggt ist.
///
/// Die Liste ist von Hand gepflegt und nicht geraten: jeder Tag wurde gegen die API
/// geprüft. Schreibweisen gehen bei Open Food Facts wild durcheinander ("K Classic",
/// "K-Classic", "Kclassic"), der <c>brands_tags</c>-Filter normalisiert sie aber alle auf
/// dieselbe Form — deshalb genau ein Tag je Marke.
/// </summary>
/// <param name="MarkenTag">Wert für den <c>brands_tags</c>-Filter.</param>
/// <param name="Anzeigename">Klartextname für Protokoll und Zusammenfassung.</param>
public sealed record Eigenmarke(string MarkenTag, string Anzeigename)
{
    /// <summary>
    /// Das Kaufland-Eigenmarkensortiment. K-Classic ist mit Abstand die größte Marke, die
    /// übrigen decken Rand- und Spezialsortimente ab.
    ///
    /// <c>kaufland</c> ist bewusst dabei, obwohl es keine Eigenmarke im engeren Sinne ist:
    /// unter diesem Tag stehen zum einen Eigenmarkenartikel, bei denen jemand "Kaufland"
    /// statt der Marke eingetragen hat, zum anderen Fremdprodukte, die es dort zu kaufen
    /// gibt. Beides gehört in einen Katalog für den Kaufland-Markt; die Marke am Artikel
    /// bleibt dabei die tatsächliche.
    /// </summary>
    public static readonly IReadOnlyList<Eigenmarke> Standard =
    [
        new("k-classic", "K-Classic"),
        new("k-classic-bio", "K-Classic Bio"),
        new("k-bio", "K-Bio"),
        new("k-take-it-veggie", "K-take it veggie"),
        new("k-free", "K-Free"),
        new("k-favourites", "K-Favourites"),
        new("k-to-go", "K-to go"),
        new("k-purland", "K-Purland"),
        new("purland", "Purland"),
        new("bevola", "Bevola"),
        new("exquisit", "exquisit"),
        new("kaufland", "Kaufland"),
    ];
}
