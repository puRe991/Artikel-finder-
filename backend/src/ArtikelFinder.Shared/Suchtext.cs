using System.Globalization;
using System.Text;

namespace ArtikelFinder.Shared;

/// <summary>
/// Normalisiert Suchbegriffe und Artikelnamen auf eine gemeinsame Form, damit die
/// Volltextsuche ohne datenbankspezifische Collations auskommt und auf SQLite wie auf
/// PostgreSQL identisch trifft.
/// </summary>
public static class Suchtext
{
    /// <summary>Schreibweise, die Deutsche beim Tippen ohne Umlaut-Taste erwarten.</summary>
    private static readonly (char Umlaut, string Lang, string Kurz)[] Umlaute =
    [
        ('ä', "ae", "a"),
        ('ö', "oe", "o"),
        ('ü', "ue", "u"),
        ('ß', "ss", "ss"),
    ];

    /// <summary>
    /// Kanonische Form eines Suchbegriffs: klein, ohne Satzzeichen, Umlaute ausgeschrieben.
    /// "Müller Käse!" wird zu "mueller kaese".
    /// </summary>
    public static string Normalisieren(string? eingabe) => Aufbereiten(eingabe, kurzform: false);

    /// <summary>
    /// Der Text, der in der Datenbank landet. Enthaelt zusaetzlich die Kurzform der
    /// Umlaute, damit sowohl "mueller" als auch "muller" den Artikel "Müller" finden —
    /// beide Schreibweisen sind im Alltag ueblich.
    /// </summary>
    public static string FuerIndex(string? eingabe)
    {
        var lang = Aufbereiten(eingabe, kurzform: false);
        var kurz = Aufbereiten(eingabe, kurzform: true);

        return string.Equals(lang, kurz, StringComparison.Ordinal) ? lang : $"{lang} {kurz}";
    }

    private static string Aufbereiten(string? eingabe, bool kurzform)
    {
        if (string.IsNullOrWhiteSpace(eingabe))
        {
            return string.Empty;
        }

        var ersetzt = new StringBuilder(eingabe.Length + 4);
        foreach (var zeichen in eingabe.ToLowerInvariant())
        {
            var gefunden = false;
            foreach (var (umlaut, lang, kurz) in Umlaute)
            {
                if (zeichen == umlaut)
                {
                    ersetzt.Append(kurzform ? kurz : lang);
                    gefunden = true;
                    break;
                }
            }

            if (!gefunden)
            {
                ersetzt.Append(zeichen);
            }
        }

        // Verbleibende Akzente (é, ñ, …) auf den Basisbuchstaben zurueckfuehren.
        var zerlegt = ersetzt.ToString().Normalize(NormalizationForm.FormD);
        var ergebnis = new StringBuilder(zerlegt.Length);
        var letzterWarTrenner = false;

        foreach (var zeichen in zerlegt)
        {
            if (CharUnicodeInfo.GetUnicodeCategory(zeichen) == UnicodeCategory.NonSpacingMark)
            {
                continue;
            }

            if (char.IsLetterOrDigit(zeichen))
            {
                ergebnis.Append(zeichen);
                letzterWarTrenner = false;
            }
            else if (!letzterWarTrenner && ergebnis.Length > 0)
            {
                // Satzzeichen und Mehrfach-Leerzeichen werden zu genau einem Trenner.
                ergebnis.Append(' ');
                letzterWarTrenner = true;
            }
        }

        return ergebnis.ToString().TrimEnd().Normalize(NormalizationForm.FormC);
    }
}
