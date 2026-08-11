using ArtikelFinder.Api.Entities;
using ArtikelFinder.Shared;
using Microsoft.EntityFrameworkCore;

namespace ArtikelFinder.Api.Data;

public class ArtikelFinderDbContext(DbContextOptions<ArtikelFinderDbContext> options) : DbContext(options)
{
    public DbSet<Artikel> Artikel => Set<Artikel>();
    public DbSet<Kategorie> Kategorien => Set<Kategorie>();
    public DbSet<Markt> Maerkte => Set<Markt>();
    public DbSet<Preis> Preise => Set<Preis>();
    public DbSet<Standort> Standorte => Set<Standort>();
    public DbSet<Verlaufseintrag> Verlauf => Set<Verlaufseintrag>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<Markt>(b =>
        {
            b.Property(m => m.Name).HasMaxLength(200).IsRequired();
            b.Property(m => m.Kette).HasMaxLength(100).IsRequired();
            b.Property(m => m.Ort).HasMaxLength(150);
            b.Property(m => m.Strasse).HasMaxLength(250);
            b.Property(m => m.GrundrissUrl).HasMaxLength(1000);
        });

        modelBuilder.Entity<Kategorie>(b =>
        {
            b.Property(k => k.Name).HasMaxLength(150).IsRequired();

            b.HasOne(k => k.ParentKategorie)
                .WithMany(k => k.Unterkategorien)
                .HasForeignKey(k => k.ParentKategorieId)
                .OnDelete(DeleteBehavior.Restrict);

            // Geschwister muessen unterscheidbar bleiben; der Import matcht darueber.
            b.HasIndex(k => new { k.ParentKategorieId, k.Name }).IsUnique();
        });

        modelBuilder.Entity<Artikel>(b =>
        {
            b.Property(a => a.Name).HasMaxLength(300).IsRequired();
            // Doppelte Laenge von Name + Marke: der Index haelt Lang- und Kurzform der Umlaute.
            b.Property(a => a.SuchText).HasMaxLength(900).IsRequired();
            b.Property(a => a.Marke).HasMaxLength(120);
            b.Property(a => a.Ean).HasMaxLength(14);
            b.Property(a => a.Artikelnummer).HasMaxLength(60);
            b.Property(a => a.BildUrl).HasMaxLength(1000);
            b.Property(a => a.ErstelltVon).HasConversion<string>().HasMaxLength(20);

            b.Property(a => a.Referenzpreis).HasColumnType("decimal(10,2)");
            b.Property(a => a.ReferenzpreisNiedrigster).HasColumnType("decimal(10,2)");
            b.Property(a => a.ReferenzpreisHoechster).HasColumnType("decimal(10,2)");

            // Gefilterter Unique-Index: EAN ist optional, aber wenn vorhanden eindeutig.
            // Sowohl SQLite als auch PostgreSQL unterstuetzen partielle Indizes.
            b.HasIndex(a => a.Ean)
                .IsUnique()
                .HasFilter("\"Ean\" IS NOT NULL");

            b.HasIndex(a => a.SuchText);
            b.HasIndex(a => a.Artikelnummer);

            b.HasOne(a => a.Kategorie)
                .WithMany(k => k.Artikel)
                .HasForeignKey(a => a.KategorieId)
                .OnDelete(DeleteBehavior.SetNull);
        });

        modelBuilder.Entity<Preis>(b =>
        {
            b.Property(p => p.Wert).HasColumnType("decimal(10,2)");
            b.Property(p => p.Werbepreis).HasColumnType("decimal(10,2)");
            b.Property(p => p.ErfasstVon).HasMaxLength(120);

            b.HasOne(p => p.Artikel)
                .WithMany(a => a.Preise)
                .HasForeignKey(p => p.ArtikelId)
                .OnDelete(DeleteBehavior.Cascade);

            b.HasOne(p => p.Markt)
                .WithMany(m => m.Preise)
                .HasForeignKey(p => p.MarktId)
                .OnDelete(DeleteBehavior.Restrict);

            // Deckt die Hauptabfrage ab: juengster Preis je Artikel und Markt.
            b.HasIndex(p => new { p.ArtikelId, p.MarktId, p.ErfasstAm });
        });

        modelBuilder.Entity<Standort>(b =>
        {
            b.Property(s => s.Gang).HasMaxLength(20).IsRequired();
            b.Property(s => s.RegalBeschreibung).HasMaxLength(300);
            b.Property(s => s.ErfasstVon).HasMaxLength(120);

            b.HasOne(s => s.Artikel)
                .WithMany(a => a.Standorte)
                .HasForeignKey(s => s.ArtikelId)
                .OnDelete(DeleteBehavior.Cascade);

            b.HasOne(s => s.Markt)
                .WithMany(m => m.Standorte)
                .HasForeignKey(s => s.MarktId)
                .OnDelete(DeleteBehavior.Restrict);

            b.HasIndex(s => new { s.ArtikelId, s.MarktId, s.ErfasstAm });

            // "Was liegt in Gang 7?" ist eine eigenstaendige Sicht auf die Daten.
            b.HasIndex(s => new { s.MarktId, s.Gang });
        });

        modelBuilder.Entity<Verlaufseintrag>(b =>
        {
            b.Property(v => v.Entitaet).HasMaxLength(30).IsRequired();
            b.Property(v => v.Beschreibung).HasMaxLength(500).IsRequired();
            b.Property(v => v.GeaendertVon).HasMaxLength(120);
            b.Property(v => v.Aenderungsart).HasConversion<string>().HasMaxLength(20);

            b.HasOne(v => v.Artikel)
                .WithMany(a => a.Verlauf)
                .HasForeignKey(v => v.ArtikelId)
                .OnDelete(DeleteBehavior.Cascade);

            b.HasIndex(v => new { v.ArtikelId, v.GeaendertAm });
        });

        if (Database.IsSqlite())
        {
            SqliteZeitKonverter.Anwenden(modelBuilder);
        }
    }

    public override int SaveChanges(bool acceptAllChangesOnSuccess)
    {
        SuchtextAktualisieren();
        return base.SaveChanges(acceptAllChangesOnSuccess);
    }

    public override Task<int> SaveChangesAsync(
        bool acceptAllChangesOnSuccess,
        CancellationToken cancellationToken = default)
    {
        SuchtextAktualisieren();
        return base.SaveChangesAsync(acceptAllChangesOnSuccess, cancellationToken);
    }

    /// <summary>
    /// Haelt <see cref="Artikel.SuchText"/> automatisch synchron. Zentral hier statt in
    /// jedem Service, damit auch der Import-Job und Tests korrekte Suchtexte schreiben.
    /// </summary>
    private void SuchtextAktualisieren()
    {
        foreach (var eintrag in ChangeTracker.Entries<Artikel>())
        {
            if (eintrag.State is not (EntityState.Added or EntityState.Modified))
            {
                continue;
            }

            var artikel = eintrag.Entity;
            artikel.SuchText = Suchtext.FuerIndex($"{artikel.Name} {artikel.Marke}".Trim());
        }
    }
}
