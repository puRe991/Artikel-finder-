namespace ArtikelFinder.Shared;

/// <summary>Hilfsfunktionen fuer Barcodes. Gemeinsam genutzt von API und Import, damit
/// gescannte und importierte EANs garantiert in derselben Form in der DB landen.</summary>
public static class Ean
{
    /// <summary>
    /// Bringt eine EAN auf Normalform: Leerzeichen und Bindestriche raus, fuehrende Nullen
    /// bleiben erhalten. Gibt <c>null</c> zurueck, wenn nichts Gueltiges uebrig bleibt.
    /// </summary>
    public static string? Normalisieren(string? eingabe)
    {
        if (string.IsNullOrWhiteSpace(eingabe))
        {
            return null;
        }

        var ziffern = new string([.. eingabe.Where(char.IsDigit)]);

        // EAN-8, UPC-A (12), EAN-13 und GTIN-14 sind die Formate, die im Handel vorkommen.
        return ziffern.Length is >= 8 and <= 14 ? ziffern : null;
    }

    /// <summary>
    /// Prueft die GTIN-Pruefziffer (gilt fuer EAN-8, UPC-A, EAN-13 und GTIN-14).
    /// Fehlschlaege sind meist Tippfehler bei manueller Eingabe.
    /// </summary>
    public static bool PruefzifferKorrekt(string? ean)
    {
        var normalisiert = Normalisieren(ean);
        if (normalisiert is null || normalisiert.Length is not (8 or 12 or 13 or 14))
        {
            return false;
        }

        // Von rechts nach links, die Stelle direkt links der Pruefziffer wiegt 3.
        var summe = 0;
        var gewicht = 3;
        for (var i = normalisiert.Length - 2; i >= 0; i--)
        {
            summe += (normalisiert[i] - '0') * gewicht;
            gewicht = gewicht == 3 ? 1 : 3;
        }

        var erwartet = (10 - (summe % 10)) % 10;
        return erwartet == normalisiert[^1] - '0';
    }
}
