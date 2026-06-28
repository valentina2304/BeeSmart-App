using ApiaryServer.Domain.Entities;

namespace ApiaryServer.Application.Interfaces
{
    public interface IHiveExtractionRepository
    {
        Task<IEnumerable<HiveExtraction>> GetAllByUserIdAsync(Guid userId);
        Task<IEnumerable<HiveExtraction>> GetByApiaryIdAsync(Guid apiaryId);
        Task<IEnumerable<HiveExtraction>> GetByHiveIdAsync(Guid hiveId);
        Task<HiveExtraction?> GetByIdAsync(Guid id);
        Task AddAsync(HiveExtraction extraction);
        Task UpdateAsync(HiveExtraction extraction);
        Task DeleteAsync(HiveExtraction extraction);
        Task<bool> ExistsAsync(Guid id);
        Task<bool> IsOwnedByUserAsync(Guid extractionId, Guid userId);
        Task SaveChangesAsync();
    }
}
