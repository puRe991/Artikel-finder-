using System.Text.Json.Serialization;
using ArtikelFinder.Api.Data;
using ArtikelFinder.Api.Infrastructure;
using ArtikelFinder.Api.Services;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

builder.Services
    .AddOptions<ArtikelFinderOptions>()
    .Bind(builder.Configuration.GetSection(ArtikelFinderOptions.Abschnitt))
    .ValidateDataAnnotations()
    .ValidateOnStart();

// SQLite lokal, PostgreSQL sobald "Datenbank:Anbieter" auf "postgres" steht. Das Modell
// ist providerneutral gehalten (partielle Indizes, decimal(10,2)), damit der Wechsel in
// Phase 3 nur diese Zeilen und einen neuen Migrationsordner braucht.
var anbieter = builder.Configuration["Datenbank:Anbieter"] ?? "sqlite";
var verbindung = builder.Configuration.GetConnectionString("ArtikelFinder")
    ?? "Data Source=artikelfinder.db";

builder.Services.AddDbContext<ArtikelFinderDbContext>(optionen =>
{
    if (anbieter.Equals("postgres", StringComparison.OrdinalIgnoreCase))
    {
        throw new NotSupportedException(
            "Der PostgreSQL-Anbieter ist für Phase 3 vorgesehen. Dafür Npgsql.EntityFrameworkCore.PostgreSQL "
            + "referenzieren, hier UseNpgsql(verbindung) eintragen und die Migrationen neu erzeugen.");
    }

    optionen.UseSqlite(verbindung);
});

builder.Services.AddSingleton<IZeitgeber, SystemZeitgeber>();
builder.Services.AddScoped<KategorieService>();
builder.Services.AddScoped<ArtikelService>();

builder.Services
    .AddControllers()
    .AddJsonOptions(o =>
    {
        // Enums als Klartext, damit die Kotlin-Seite lesbare Werte bekommt.
        o.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter());
    });

builder.Services.AddProblemDetails();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

// Im MVP laeuft die API unauthentifiziert im lokalen Netz; die Android-App ist der einzige
// Client. Mit den Nutzerkonten aus Phase 3 kommt hier Authentifizierung dazu.
const string EntwicklungsCors = "entwicklung";
builder.Services.AddCors(o => o.AddPolicy(EntwicklungsCors, p => p
    .AllowAnyOrigin()
    .AllowAnyMethod()
    .AllowAnyHeader()));

var app = builder.Build();

using (var bereich = app.Services.CreateScope())
{
    var db = bereich.ServiceProvider.GetRequiredService<ArtikelFinderDbContext>();
    await db.Database.MigrateAsync();
    await Startdaten.AnwendenAsync(db);
}

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
    app.UseCors(EntwicklungsCors);
}

app.UseExceptionHandler();
app.UseStatusCodePages();
app.MapControllers();
app.MapGet("/health", () => Results.Ok(new { status = "ok" }));

app.Run();

/// <summary>Sichtbar fuer die Integrationstests (WebApplicationFactory).</summary>
public partial class Program;
