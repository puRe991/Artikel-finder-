using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace ArtikelFinder.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class Referenzpreis : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<decimal>(
                name: "Referenzpreis",
                table: "Artikel",
                type: "decimal(10,2)",
                nullable: true);

            migrationBuilder.AddColumn<int>(
                name: "ReferenzpreisAnzahl",
                table: "Artikel",
                type: "INTEGER",
                nullable: true);

            migrationBuilder.AddColumn<decimal>(
                name: "ReferenzpreisHoechster",
                table: "Artikel",
                type: "decimal(10,2)",
                nullable: true);

            migrationBuilder.AddColumn<decimal>(
                name: "ReferenzpreisNiedrigster",
                table: "Artikel",
                type: "decimal(10,2)",
                nullable: true);

            migrationBuilder.AddColumn<DateOnly>(
                name: "ReferenzpreisStand",
                table: "Artikel",
                type: "TEXT",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "Referenzpreis",
                table: "Artikel");

            migrationBuilder.DropColumn(
                name: "ReferenzpreisAnzahl",
                table: "Artikel");

            migrationBuilder.DropColumn(
                name: "ReferenzpreisHoechster",
                table: "Artikel");

            migrationBuilder.DropColumn(
                name: "ReferenzpreisNiedrigster",
                table: "Artikel");

            migrationBuilder.DropColumn(
                name: "ReferenzpreisStand",
                table: "Artikel");
        }
    }
}
