using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace ArtikelFinder.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class Initial : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "Kategorien",
                columns: table => new
                {
                    Id = table.Column<int>(type: "INTEGER", nullable: false)
                        .Annotation("Sqlite:Autoincrement", true),
                    Name = table.Column<string>(type: "TEXT", maxLength: 150, nullable: false),
                    ParentKategorieId = table.Column<int>(type: "INTEGER", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Kategorien", x => x.Id);
                    table.ForeignKey(
                        name: "FK_Kategorien_Kategorien_ParentKategorieId",
                        column: x => x.ParentKategorieId,
                        principalTable: "Kategorien",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "Maerkte",
                columns: table => new
                {
                    Id = table.Column<int>(type: "INTEGER", nullable: false)
                        .Annotation("Sqlite:Autoincrement", true),
                    Name = table.Column<string>(type: "TEXT", maxLength: 200, nullable: false),
                    Kette = table.Column<string>(type: "TEXT", maxLength: 100, nullable: false),
                    Ort = table.Column<string>(type: "TEXT", maxLength: 150, nullable: true),
                    Strasse = table.Column<string>(type: "TEXT", maxLength: 250, nullable: true),
                    GrundrissUrl = table.Column<string>(type: "TEXT", maxLength: 1000, nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Maerkte", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "Artikel",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "TEXT", nullable: false),
                    Name = table.Column<string>(type: "TEXT", maxLength: 300, nullable: false),
                    SuchText = table.Column<string>(type: "TEXT", maxLength: 900, nullable: false),
                    Marke = table.Column<string>(type: "TEXT", maxLength: 120, nullable: true),
                    Ean = table.Column<string>(type: "TEXT", maxLength: 14, nullable: true),
                    Artikelnummer = table.Column<string>(type: "TEXT", maxLength: 60, nullable: true),
                    KategorieId = table.Column<int>(type: "INTEGER", nullable: true),
                    BildUrl = table.Column<string>(type: "TEXT", maxLength: 1000, nullable: true),
                    ErstelltVon = table.Column<string>(type: "TEXT", maxLength: 20, nullable: false),
                    ErstelltAm = table.Column<long>(type: "INTEGER", nullable: false),
                    GeaendertAm = table.Column<long>(type: "INTEGER", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Artikel", x => x.Id);
                    table.ForeignKey(
                        name: "FK_Artikel_Kategorien_KategorieId",
                        column: x => x.KategorieId,
                        principalTable: "Kategorien",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.SetNull);
                });

            migrationBuilder.CreateTable(
                name: "Preise",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "TEXT", nullable: false),
                    ArtikelId = table.Column<Guid>(type: "TEXT", nullable: false),
                    MarktId = table.Column<int>(type: "INTEGER", nullable: false),
                    Wert = table.Column<decimal>(type: "decimal(10,2)", nullable: false),
                    Werbepreis = table.Column<decimal>(type: "decimal(10,2)", nullable: true),
                    WerbepreisGueltigVon = table.Column<long>(type: "INTEGER", nullable: true),
                    WerbepreisGueltigBis = table.Column<long>(type: "INTEGER", nullable: true),
                    ErfasstAm = table.Column<long>(type: "INTEGER", nullable: false),
                    ErfasstVon = table.Column<string>(type: "TEXT", maxLength: 120, nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Preise", x => x.Id);
                    table.ForeignKey(
                        name: "FK_Preise_Artikel_ArtikelId",
                        column: x => x.ArtikelId,
                        principalTable: "Artikel",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_Preise_Maerkte_MarktId",
                        column: x => x.MarktId,
                        principalTable: "Maerkte",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "Standorte",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "TEXT", nullable: false),
                    ArtikelId = table.Column<Guid>(type: "TEXT", nullable: false),
                    MarktId = table.Column<int>(type: "INTEGER", nullable: false),
                    Gang = table.Column<string>(type: "TEXT", maxLength: 20, nullable: false),
                    RegalBeschreibung = table.Column<string>(type: "TEXT", maxLength: 300, nullable: true),
                    KartenX = table.Column<float>(type: "REAL", nullable: true),
                    KartenY = table.Column<float>(type: "REAL", nullable: true),
                    ErfasstAm = table.Column<long>(type: "INTEGER", nullable: false),
                    ErfasstVon = table.Column<string>(type: "TEXT", maxLength: 120, nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Standorte", x => x.Id);
                    table.ForeignKey(
                        name: "FK_Standorte_Artikel_ArtikelId",
                        column: x => x.ArtikelId,
                        principalTable: "Artikel",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_Standorte_Maerkte_MarktId",
                        column: x => x.MarktId,
                        principalTable: "Maerkte",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "Verlauf",
                columns: table => new
                {
                    Id = table.Column<long>(type: "INTEGER", nullable: false)
                        .Annotation("Sqlite:Autoincrement", true),
                    ArtikelId = table.Column<Guid>(type: "TEXT", nullable: false),
                    Entitaet = table.Column<string>(type: "TEXT", maxLength: 30, nullable: false),
                    Aenderungsart = table.Column<string>(type: "TEXT", maxLength: 20, nullable: false),
                    Beschreibung = table.Column<string>(type: "TEXT", maxLength: 500, nullable: false),
                    GeaendertVon = table.Column<string>(type: "TEXT", maxLength: 120, nullable: true),
                    GeaendertAm = table.Column<long>(type: "INTEGER", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Verlauf", x => x.Id);
                    table.ForeignKey(
                        name: "FK_Verlauf_Artikel_ArtikelId",
                        column: x => x.ArtikelId,
                        principalTable: "Artikel",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_Artikel_Artikelnummer",
                table: "Artikel",
                column: "Artikelnummer");

            migrationBuilder.CreateIndex(
                name: "IX_Artikel_Ean",
                table: "Artikel",
                column: "Ean",
                unique: true,
                filter: "\"Ean\" IS NOT NULL");

            migrationBuilder.CreateIndex(
                name: "IX_Artikel_KategorieId",
                table: "Artikel",
                column: "KategorieId");

            migrationBuilder.CreateIndex(
                name: "IX_Artikel_SuchText",
                table: "Artikel",
                column: "SuchText");

            migrationBuilder.CreateIndex(
                name: "IX_Kategorien_ParentKategorieId_Name",
                table: "Kategorien",
                columns: new[] { "ParentKategorieId", "Name" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_Preise_ArtikelId_MarktId_ErfasstAm",
                table: "Preise",
                columns: new[] { "ArtikelId", "MarktId", "ErfasstAm" });

            migrationBuilder.CreateIndex(
                name: "IX_Preise_MarktId",
                table: "Preise",
                column: "MarktId");

            migrationBuilder.CreateIndex(
                name: "IX_Standorte_ArtikelId_MarktId_ErfasstAm",
                table: "Standorte",
                columns: new[] { "ArtikelId", "MarktId", "ErfasstAm" });

            migrationBuilder.CreateIndex(
                name: "IX_Standorte_MarktId_Gang",
                table: "Standorte",
                columns: new[] { "MarktId", "Gang" });

            migrationBuilder.CreateIndex(
                name: "IX_Verlauf_ArtikelId_GeaendertAm",
                table: "Verlauf",
                columns: new[] { "ArtikelId", "GeaendertAm" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "Preise");

            migrationBuilder.DropTable(
                name: "Standorte");

            migrationBuilder.DropTable(
                name: "Verlauf");

            migrationBuilder.DropTable(
                name: "Maerkte");

            migrationBuilder.DropTable(
                name: "Artikel");

            migrationBuilder.DropTable(
                name: "Kategorien");
        }
    }
}
