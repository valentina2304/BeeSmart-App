using System.ComponentModel.DataAnnotations;
using ApiaryServer.Domain.Entities;

namespace ApiaryServer.Application.DTOs
{
    public record CreateTreatmentRequest(
        [Required] Guid HiveId,
        [Required] DateTimeOffset TreatmentDate,
        [Required] TreatmentType Type,
        [Required][MaxLength(200)] string ProductName,
        [MaxLength(200)] string? Substance,
        [MaxLength(100)] string? Dosage,
        [MaxLength(1000)] string? Notes,
        DateTimeOffset? NextTreatmentDate
    );

    public record UpdateTreatmentRequest(
        [Required] DateTimeOffset TreatmentDate,
        [Required] TreatmentType Type,
        [Required][MaxLength(200)] string ProductName,
        [MaxLength(200)] string? Substance,
        [MaxLength(100)] string? Dosage,
        [MaxLength(1000)] string? Notes,
        DateTimeOffset? NextTreatmentDate
    );

    public record TreatmentResponse(
        Guid Id,
        Guid HiveId,
        string HiveName,
        Guid ApiaryId,
        string ApiaryName,
        DateTimeOffset TreatmentDate,
        TreatmentType Type,
        string ProductName,
        string? Substance,
        string? Dosage,
        string? Notes,
        DateTimeOffset? NextTreatmentDate,
        DateTimeOffset CreatedAt,
        DateTimeOffset UpdatedAt
    );
}
