using System.ComponentModel.DataAnnotations;
using ApiaryServer.Domain.Entities;

namespace ApiaryServer.Application.DTOs
{
    public record CreateExtractionRequest(
        [Required] Guid HiveId,
        [Required] DateTimeOffset ExtractionDate,
        [Required] ExtractionType Type,
        [Required][Range(typeof(decimal), "0.01", "999999.99")] decimal Quantity,
        [Required][MaxLength(20)] string Unit,
        [MaxLength(1000)] string? Notes
    );

    public record UpdateExtractionRequest(
        [Required] DateTimeOffset ExtractionDate,
        [Required] ExtractionType Type,
        [Required][Range(typeof(decimal), "0.01", "999999.99")] decimal Quantity,
        [Required][MaxLength(20)] string Unit,
        [MaxLength(1000)] string? Notes
    );

    public record ExtractionResponse(
        Guid Id,
        Guid HiveId,
        string HiveName,
        Guid ApiaryId,
        string ApiaryName,
        DateTimeOffset ExtractionDate,
        ExtractionType Type,
        decimal Quantity,
        string Unit,
        string? Notes,
        DateTimeOffset CreatedAt,
        DateTimeOffset UpdatedAt
    );
}
