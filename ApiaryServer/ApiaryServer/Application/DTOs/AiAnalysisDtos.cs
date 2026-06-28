using System.ComponentModel.DataAnnotations;
using System.Text.Json;

namespace ApiaryServer.Application.DTOs
{
    public record AnalyzeCellsRequest(
        [Required] string ImageBase64
    );

    public record AnalyzeCellsResponse(
        string Status,
        Dictionary<string, int> Results,
        string? Message,
        JsonElement? Quality = null,
        IReadOnlyList<CellDetectionDto>? CellDetections = null
    );

    public record SaveInspectionAiAnalysisRequest(
        [Required] Dictionary<string, int> Results,
        string? Status,
        string? Message,
        IReadOnlyList<CellDetectionDto>? CellDetections = null
    );

    public record CellDetectionDto(
        int X,
        int Y,
        int Radius,
        double NormalizedX,
        double NormalizedY,
        double NormalizedRadius,
        string ClassName,
        double Confidence
    );

    public record InspectionAiAnalysisResponse(
        Guid Id,
        Guid InspectionId,
        Guid HiveId,
        Guid ApiaryId,
        DateTimeOffset InspectionDate,
        string Status,
        Dictionary<string, int> Results,
        string? Message,
        int TotalCells,
        int CappedBroodCells,
        int LarvaeCells,
        int EggsCells,
        int HoneyCells,
        int PollenCells,
        int EmptyCells,
        int OtherCells,
        int BroodCells,
        int StoresCells,
        decimal? BroodDensity,
        decimal? LarvaeToCappedRatio,
        decimal? StoresRatio,
        DateTimeOffset CreatedAt,
        IReadOnlyList<CellDetectionDto>? CellDetections = null
    );
}
