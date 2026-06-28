using ApiaryServer.Application.DTOs;

namespace ApiaryServer.Application.Interfaces
{
    public interface IHiveExtractionService
    {
        Task<IEnumerable<ExtractionResponse>> GetAllExtractionsAsync(Guid userId);
        Task<IEnumerable<ExtractionResponse>> GetExtractionsByApiaryIdAsync(Guid apiaryId, Guid userId);
        Task<IEnumerable<ExtractionResponse>> GetExtractionsByHiveIdAsync(Guid hiveId, Guid userId);
        Task<ExtractionResponse> GetExtractionByIdAsync(Guid id, Guid userId);
        Task<ExtractionResponse> CreateExtractionAsync(CreateExtractionRequest dto, Guid userId);
        Task<ExtractionResponse> UpdateExtractionAsync(Guid id, UpdateExtractionRequest dto, Guid userId);
        Task DeleteExtractionAsync(Guid id, Guid userId);
    }
}
