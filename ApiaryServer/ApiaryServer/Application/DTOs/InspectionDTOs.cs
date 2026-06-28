using System.ComponentModel.DataAnnotations;

namespace ApiaryServer.Application.DTOs
{
    // Request DTOs
    public record CreateInspectionRequest(
        [Required] Guid HiveId,
        [Required] DateTimeOffset InspectionDate,
        decimal? Temperature,
        int? FramesCount,
        int? BroodFrames,
        int? HoneyFrames,
        int? PollenFrames,
        bool QueenSeen,
        bool EggsSeen,
        bool LarvaeSeen,
        bool QueenCellsSeen = false,
        bool QueenCellsWithEggs = false,
        bool BeardingAtEntrance = false,
        bool SpaceNeeded = false,
        string? BroodPattern = null,
        int? HoneyCappingPercent = null,
        bool FeedingGiven = false,
        bool WaterAvailable = false,
        bool MoistureOrMold = false,
        bool DeadBeesAtEntrance = false,
        bool UnusualBehavior = false,
        string? Temperament = null,
        int? OldCombsToReplace = null,
        [StringLength(2000)] string? Notes = null
    );

    public record UpdateInspectionRequest(
        [Required] DateTimeOffset InspectionDate,
        decimal? Temperature,
        int? FramesCount,
        int? BroodFrames,
        int? HoneyFrames,
        int? PollenFrames,
        bool QueenSeen,
        bool EggsSeen,
        bool LarvaeSeen,
        bool QueenCellsSeen = false,
        bool QueenCellsWithEggs = false,
        bool BeardingAtEntrance = false,
        bool SpaceNeeded = false,
        string? BroodPattern = null,
        int? HoneyCappingPercent = null,
        bool FeedingGiven = false,
        bool WaterAvailable = false,
        bool MoistureOrMold = false,
        bool DeadBeesAtEntrance = false,
        bool UnusualBehavior = false,
        string? Temperament = null,
        int? OldCombsToReplace = null,
        [StringLength(2000)] string? Notes = null
    );

    public record AddInspectionPhotoRequest(
        [Required] string PhotoUrl,
        string? Description
    );

    public record UpdateInspectionPhotoRequest(
        string? Description
    );

    // Response DTOs
    public record InspectionResponse(
        Guid Id,
        Guid HiveId,
        string HiveName,
        Guid ApiaryId,
        string ApiaryName,
        DateTimeOffset InspectionDate,
        decimal? Temperature,
        int? FramesCount,
        int? BroodFrames,
        int? HoneyFrames,
        int? PollenFrames,
        bool QueenSeen,
        bool EggsSeen,
        bool LarvaeSeen,
        int PhotosCount,
        DateTimeOffset CreatedAt,
        DateTimeOffset UpdatedAt,
        bool QueenCellsSeen = false,
        bool QueenCellsWithEggs = false,
        bool BeardingAtEntrance = false,
        bool SpaceNeeded = false,
        string? BroodPattern = null,
        int? HoneyCappingPercent = null,
        bool FeedingGiven = false,
        bool WaterAvailable = false,
        bool MoistureOrMold = false,
        bool DeadBeesAtEntrance = false,
        bool UnusualBehavior = false,
        string? Temperament = null,
        int? OldCombsToReplace = null,
        string? Notes = null
    );

    public record InspectionDetailResponse(
        Guid Id,
        Guid HiveId,
        string HiveName,
        Guid ApiaryId,
        string ApiaryName,
        DateTimeOffset InspectionDate,
        decimal? Temperature,
        int? FramesCount,
        int? BroodFrames,
        int? HoneyFrames,
        int? PollenFrames,
        bool QueenSeen,
        bool EggsSeen,
        bool LarvaeSeen,
        List<InspectionPhotoResponse> Photos,
        DateTimeOffset CreatedAt,
        DateTimeOffset UpdatedAt,
        bool QueenCellsSeen = false,
        bool QueenCellsWithEggs = false,
        bool BeardingAtEntrance = false,
        bool SpaceNeeded = false,
        string? BroodPattern = null,
        int? HoneyCappingPercent = null,
        bool FeedingGiven = false,
        bool WaterAvailable = false,
        bool MoistureOrMold = false,
        bool DeadBeesAtEntrance = false,
        bool UnusualBehavior = false,
        string? Temperament = null,
        int? OldCombsToReplace = null,
        string? Notes = null
    );

    public record InspectionPhotoResponse(
        Guid Id,
        Guid InspectionId,
        string PhotoUrl,
        string? Description,
        DateTimeOffset CreatedAt
    );
}
