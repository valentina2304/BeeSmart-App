using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace ApiaryServer.Migrations
{
    /// <inheritdoc />
    public partial class AddInspectionV2Fields : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "BeardingAtEntrance",
                table: "Inspections",
                type: "bit",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<string>(
                name: "BroodPattern",
                table: "Inspections",
                type: "nvarchar(max)",
                nullable: true);

            migrationBuilder.AddColumn<bool>(
                name: "DeadBeesAtEntrance",
                table: "Inspections",
                type: "bit",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<bool>(
                name: "FeedingGiven",
                table: "Inspections",
                type: "bit",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<int>(
                name: "HoneyCappingPercent",
                table: "Inspections",
                type: "int",
                nullable: true);

            migrationBuilder.AddColumn<bool>(
                name: "MoistureOrMold",
                table: "Inspections",
                type: "bit",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<int>(
                name: "OldCombsToReplace",
                table: "Inspections",
                type: "int",
                nullable: true);

            migrationBuilder.AddColumn<bool>(
                name: "QueenCellsSeen",
                table: "Inspections",
                type: "bit",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<bool>(
                name: "QueenCellsWithEggs",
                table: "Inspections",
                type: "bit",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<bool>(
                name: "SpaceNeeded",
                table: "Inspections",
                type: "bit",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<string>(
                name: "Temperament",
                table: "Inspections",
                type: "nvarchar(max)",
                nullable: true);

            migrationBuilder.AddColumn<bool>(
                name: "UnusualBehavior",
                table: "Inspections",
                type: "bit",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<bool>(
                name: "WaterAvailable",
                table: "Inspections",
                type: "bit",
                nullable: false,
                defaultValue: false);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "BeardingAtEntrance",
                table: "Inspections");

            migrationBuilder.DropColumn(
                name: "BroodPattern",
                table: "Inspections");

            migrationBuilder.DropColumn(
                name: "DeadBeesAtEntrance",
                table: "Inspections");

            migrationBuilder.DropColumn(
                name: "FeedingGiven",
                table: "Inspections");

            migrationBuilder.DropColumn(
                name: "HoneyCappingPercent",
                table: "Inspections");

            migrationBuilder.DropColumn(
                name: "MoistureOrMold",
                table: "Inspections");

            migrationBuilder.DropColumn(
                name: "OldCombsToReplace",
                table: "Inspections");

            migrationBuilder.DropColumn(
                name: "QueenCellsSeen",
                table: "Inspections");

            migrationBuilder.DropColumn(
                name: "QueenCellsWithEggs",
                table: "Inspections");

            migrationBuilder.DropColumn(
                name: "SpaceNeeded",
                table: "Inspections");

            migrationBuilder.DropColumn(
                name: "Temperament",
                table: "Inspections");

            migrationBuilder.DropColumn(
                name: "UnusualBehavior",
                table: "Inspections");

            migrationBuilder.DropColumn(
                name: "WaterAvailable",
                table: "Inspections");
        }
    }
}
