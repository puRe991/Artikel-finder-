using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace ArtikelFinder.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class Produktangaben : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "Allergene",
                table: "Artikel",
                type: "TEXT",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "Auszeichnungen",
                table: "Artikel",
                type: "TEXT",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "Menge",
                table: "Artikel",
                type: "TEXT",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "Naehrwerte",
                table: "Artikel",
                type: "TEXT",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "Nutriscore",
                table: "Artikel",
                type: "TEXT",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "Spuren",
                table: "Artikel",
                type: "TEXT",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "Zutaten",
                table: "Artikel",
                type: "TEXT",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "Allergene",
                table: "Artikel");

            migrationBuilder.DropColumn(
                name: "Auszeichnungen",
                table: "Artikel");

            migrationBuilder.DropColumn(
                name: "Menge",
                table: "Artikel");

            migrationBuilder.DropColumn(
                name: "Naehrwerte",
                table: "Artikel");

            migrationBuilder.DropColumn(
                name: "Nutriscore",
                table: "Artikel");

            migrationBuilder.DropColumn(
                name: "Spuren",
                table: "Artikel");

            migrationBuilder.DropColumn(
                name: "Zutaten",
                table: "Artikel");
        }
    }
}
