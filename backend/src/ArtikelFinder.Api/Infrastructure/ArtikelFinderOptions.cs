using System.ComponentModel.DataAnnotations;

namespace ArtikelFinder.Api.Infrastructure;

public sealed class ArtikelFinderOptions
{
    public const string Abschnitt = "ArtikelFinder";

    /// <summary>
    /// Markt, der verwendet wird, wenn ein Request keinen angibt. Im Einzelnutzerbetrieb
    /// ist das der Kaufland Giessen; ab Phase 3 waehlt der Nutzer den Markt in der App.
    /// </summary>
    [Range(1, int.MaxValue)]
    public int StandardMarktId { get; set; } = 1;

    /// <summary>Obergrenze fuer die Seitengroesse, damit ein Client den Katalog nicht am Stueck zieht.</summary>
    [Range(1, 500)]
    public int MaxSeitengroesse { get; set; } = 100;

    [Range(1, 500)]
    public int StandardSeitengroesse { get; set; } = 25;
}
