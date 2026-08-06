using ArtikelFinder.Api.Entities;
using Microsoft.EntityFrameworkCore;

namespace ArtikelFinder.Api.Data;

/// <summary>
/// Legt den Standardmarkt und ein grobes Kategorieraster an. Bewusst nicht als
/// EF-<c>HasData</c>-Seed: die Daten sind Startwerte, die der Nutzer aendern darf, und
/// sollen nicht bei jeder Migration wieder ueberschrieben werden.
/// </summary>
public static class Startdaten
{
    private static readonly (string Oberkategorie, string[] Unterkategorien)[] Raster =
    [
        ("Obst & Gemüse", ["Obst", "Gemüse", "Salate & Kräuter"]),
        ("Molkereiprodukte", ["Milch", "Joghurt & Quark", "Käse", "Butter & Margarine"]),
        ("Fleisch & Wurst", ["Frischfleisch", "Wurstwaren", "Geflügel"]),
        ("Brot & Backwaren", ["Brot", "Brötchen", "Kuchen & Gebäck"]),
        ("Tiefkühl", ["Tiefkühlgemüse", "Pizza & Fertiggerichte", "Eis"]),
        ("Getränke", ["Wasser", "Säfte", "Limonaden", "Kaffee & Tee", "Bier & Wein"]),
        ("Grundnahrungsmittel", ["Nudeln & Reis", "Konserven", "Öl & Essig", "Gewürze", "Mehl & Backzutaten"]),
        ("Süßwaren & Snacks", ["Schokolade", "Kekse", "Chips & Salziges"]),
        ("Drogerie", ["Körperpflege", "Waschmittel", "Reinigung", "Papierwaren"]),
        ("Tierbedarf", ["Hundefutter", "Katzenfutter", "Kleintier & Vogel", "Zubehör"]),
        ("Haushalt & Sonstiges", []),
    ];

    public static async Task AnwendenAsync(ArtikelFinderDbContext db, CancellationToken ct = default)
    {
        var etwasGeaendert = false;

        if (!await db.Maerkte.AnyAsync(ct))
        {
            db.Maerkte.Add(new Markt
            {
                Id = 1,
                Name = "Kaufland Gießen",
                Kette = "Kaufland",
                Ort = "Gießen",
                Strasse = null,
            });
            etwasGeaendert = true;
        }

        if (!await db.Kategorien.AnyAsync(ct))
        {
            foreach (var (oberkategorie, unterkategorien) in Raster)
            {
                var eltern = new Kategorie { Name = oberkategorie };
                db.Kategorien.Add(eltern);

                foreach (var name in unterkategorien)
                {
                    db.Kategorien.Add(new Kategorie { Name = name, ParentKategorie = eltern });
                }
            }

            etwasGeaendert = true;
        }

        if (etwasGeaendert)
        {
            await db.SaveChangesAsync(ct);
        }
    }
}
