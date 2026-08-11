using ArtikelFinder.Import.OpenPrices;

namespace ArtikelFinder.Import;

/// <summary>Ein einzelner brauchbarer Preis aus Open Prices, auf das Nötige eingedampft.</summary>
public readonly record struct Preisbeobachtung(decimal Wert, DateOnly Datum)
{
    /// <summary>Oberhalb davon ist im Supermarktregal kein Preis mehr plausibel — ein
    /// Tippfehler beim Erfassen (2999 statt 29,99) würde den Richtwert sonst unbrauchbar
    /// machen.</summary>
    private const decimal Hoechstpreis = 1000m;

    /// <summary>
    /// Filtert eine Erfassung auf einen verwertbaren <b>Normalpreis</b>, oder gibt
    /// <c>null</c> zurück.
    ///
    /// Aussortiert wird:
    /// <list type="bullet">
    /// <item>lose Ware ohne Barcode (<c>CATEGORY</c>) — sie ist keinem Artikel zuzuordnen,</item>
    /// <item>alles außer Euro,</item>
    /// <item>Erfassungen ohne Datum oder älter als die Altersgrenze,</item>
    /// <item>Aktionspreise, zu denen der Normalpreis fehlt.</item>
    /// </list>
    ///
    /// Der letzte Punkt ist der wichtigste: ein Aktionspreis aus einer beliebigen Filiale ist
    /// kein Angebot in Gießen, sondern zöge den Richtwert nur unter den Regalpreis. Ist der
    /// Normalpreis mitgeliefert, wird der genommen.
    /// </summary>
    public static Preisbeobachtung? Aus(OpPreis preis, DateOnly fruehestens)
    {
        if (!string.Equals(preis.Art, "PRODUCT", StringComparison.OrdinalIgnoreCase)
            || !string.Equals(preis.Waehrung, "EUR", StringComparison.OrdinalIgnoreCase))
        {
            return null;
        }

        if (preis.Datum is not { } datum || datum < fruehestens)
        {
            return null;
        }

        var wert = preis.IstAktionspreis ? preis.PreisOhneAktion : preis.Preis;

        return wert is > 0 and <= Hoechstpreis ? new Preisbeobachtung(wert.Value, datum) : null;
    }
}

/// <summary>
/// Richtwert für einen Artikel, zusammengefasst aus allen Erfassungen zu seiner EAN.
///
/// Das ist ausdrücklich <b>kein</b> Preis des eigenen Marktes: er stammt aus deutschen
/// Läden aller Ketten und ist Wochen bis Monate alt. Deshalb hängt er am Artikel und nicht
/// an der Preistabelle, in der die selbst erfassten Preise stehen — die bleiben unberührt.
/// Damit man den Wert einschätzen kann, wird die Spanne, die Zahl der Erfassungen und das
/// Datum der jüngsten mitgeführt.
/// </summary>
/// <param name="Wert">Median aller Erfassungen. Robuster als der Mittelwert: ein einzelner
/// Ausreißer verschiebt ihn nicht.</param>
/// <param name="Niedrigster">Günstigste Erfassung.</param>
/// <param name="Hoechster">Teuerste Erfassung.</param>
/// <param name="Anzahl">Wie viele Erfassungen eingegangen sind.</param>
/// <param name="Stand">Datum der jüngsten Erfassung.</param>
public sealed record Referenzpreis(
    decimal Wert,
    decimal Niedrigster,
    decimal Hoechster,
    int Anzahl,
    DateOnly Stand)
{
    public static Referenzpreis? Aus(IEnumerable<Preisbeobachtung> beobachtungen)
    {
        var alle = beobachtungen.ToList();
        if (alle.Count == 0)
        {
            return null;
        }

        var sortiert = alle.Select(b => b.Wert).Order().ToList();
        var mitte = sortiert.Count / 2;
        var median = sortiert.Count % 2 == 1
            ? sortiert[mitte]
            : Math.Round((sortiert[mitte - 1] + sortiert[mitte]) / 2m, 2, MidpointRounding.AwayFromZero);

        return new Referenzpreis(
            median,
            sortiert[0],
            sortiert[^1],
            sortiert.Count,
            alle.Max(b => b.Datum));
    }

    /// <summary>
    /// Schreibt den Richtwert an einen Artikel. Die fünf Felder gehören zusammen — halb
    /// gesetzt wäre der Wert nicht einzuschätzen.
    /// </summary>
    public void AnwendenAuf(Api.Entities.Artikel artikel)
    {
        artikel.Referenzpreis = Wert;
        artikel.ReferenzpreisNiedrigster = Niedrigster;
        artikel.ReferenzpreisHoechster = Hoechster;
        artikel.ReferenzpreisAnzahl = Anzahl;
        artikel.ReferenzpreisStand = Stand;
    }
}
