using System.ComponentModel.DataAnnotations;
using ApiaryServer.Domain.Entities;

namespace ApiaryServer.Application.DTOs
{
    // Apiary DTOs
    public record CreateApiaryRequest(
        [Required][MaxLength(100)] string Name,
        [MaxLength(500)] string? Description,
        [MaxLength(200)] string? Location
    );

    public record UpdateApiaryRequest(
        [Required][MaxLength(100)] string Name,
        [MaxLength(500)] string? Description,
        [MaxLength(200)] string? Location
    );

    public record ApiaryResponse(
        Guid Id,
        Guid UserId,
        string Name,
        string? Description,
        string? Location,
        int HiveCount,
        DateTimeOffset CreatedAt,
        DateTimeOffset UpdatedAt
    );

    public record ApiaryDetailResponse(
        Guid Id,
        Guid UserId,
        string Name,
        string? Description,
        string? Location,
        List<HiveResponse> Hives,
        DateTimeOffset CreatedAt,
        DateTimeOffset UpdatedAt
    );

    // Hive DTOs
    public record CreateHiveRequest(
        [Required][MaxLength(100)] string Name,
        [Required] HiveType Type,
        HiveStatus Status = HiveStatus.Active,
        [MaxLength(1000)] string? Notes = null,
        bool ReginaPrezenta = false,
        [Range(0, 99)] int VarstaRegina = 0,
        [Range(0, 99)] int RameAlbine = 0,
        [Range(0, 99)] int RamePuiet = 0,
        [Range(0, 99)] int RameMiere = 0
    );

    public record UpdateHiveRequest(
        [Required][MaxLength(100)] string Name,
        [Required] HiveType Type,
        [Required] HiveStatus Status,
        [MaxLength(1000)] string? Notes,
        bool ReginaPrezenta = false,
        [Range(0, 99)] int VarstaRegina = 0,
        [Range(0, 99)] int RameAlbine = 0,
        [Range(0, 99)] int RamePuiet = 0,
        [Range(0, 99)] int RameMiere = 0
    );

    public record HiveResponse(
        Guid Id,
        Guid ApiaryId,
        string ApiaryName,
        string Name,
        HiveType Type,
        HiveStatus Status,
        string? Notes,
        bool ReginaPrezenta,
        int VarstaRegina,
        int RameAlbine,
        int RamePuiet,
        int RameMiere,
        string? UltimaInspectie,
        DateTimeOffset CreatedAt,
        DateTimeOffset UpdatedAt
    );

    // Task DTOs
    public record CreateTaskRequest(
        [Required][MaxLength(200)] string Title,
        [MaxLength(1000)] string? Description,
        TaskPriority Priority = TaskPriority.Normal,
        DateTimeOffset? DueDate = null,
        Guid? ApiaryId = null,
        Guid? HiveId = null
    );

    public record UpdateTaskRequest(
        [Required][MaxLength(200)] string Title,
        [MaxLength(1000)] string? Description,
        [Required] TaskPriority Priority,
        [Required] ApiaryServer.Domain.Entities.TaskStatus Status,
        DateTimeOffset? DueDate,
        Guid? ApiaryId,
        Guid? HiveId
    );

    public record TaskResponse(
        Guid Id,
        Guid UserId,
        Guid? ApiaryId,
        string? ApiaryName,
        Guid? HiveId,
        string? HiveName,
        string Title,
        string? Description,
        TaskPriority Priority,
        ApiaryServer.Domain.Entities.TaskStatus Status,
        DateTimeOffset? DueDate,
        DateTimeOffset? CompletedAt,
        DateTimeOffset CreatedAt,
        DateTimeOffset UpdatedAt
    );
}
