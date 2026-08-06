using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Storage.ValueConversion;

namespace ArtikelFinder.Api.Data;

/// <summary>
/// SQLite kennt keinen nativen Datumstyp und lehnt <c>ORDER BY</c> auf
/// <see cref="DateTimeOffset"/> ab. Genau das braucht die App aber staendig ("juengster
/// Preis je Artikel"), sonst muesste die Sortierung im Speicher passieren — bei
/// sechsstelligen Artikelzahlen keine Option.
///
/// Deshalb werden alle DateTimeOffset-Spalten auf SQLite als UTC-Ticks (BIGINT)
/// gespeichert: sortierbar, vergleichbar und ohne Genauigkeitsverlust. Auf PostgreSQL
/// bleibt der native <c>timestamptz</c> erhalten — die Konvertierung wird dort schlicht
/// nicht angewendet.
/// </summary>
public static class SqliteZeitKonverter
{
    private static readonly ValueConverter<DateTimeOffset, long> Konverter = new(
        wert => wert.ToUniversalTime().Ticks,
        ticks => new DateTimeOffset(ticks, TimeSpan.Zero));

    private static readonly ValueConverter<DateTimeOffset?, long?> NullbarerKonverter = new(
        wert => wert.HasValue ? wert.Value.ToUniversalTime().Ticks : null,
        ticks => ticks.HasValue ? new DateTimeOffset(ticks.Value, TimeSpan.Zero) : null);

    public static void Anwenden(ModelBuilder modelBuilder)
    {
        foreach (var entitaet in modelBuilder.Model.GetEntityTypes())
        {
            foreach (var eigenschaft in entitaet.GetProperties())
            {
                if (eigenschaft.ClrType == typeof(DateTimeOffset))
                {
                    eigenschaft.SetValueConverter(Konverter);
                }
                else if (eigenschaft.ClrType == typeof(DateTimeOffset?))
                {
                    eigenschaft.SetValueConverter(NullbarerKonverter);
                }
            }
        }
    }
}
