using ApiaryServer.Domain.Entities;

namespace ApiaryServer.Application.Interfaces
{
    public interface IApiaryRepository
    {
        Task<IEnumerable<Apiary>> GetAllByUserIdAsync(Guid userId);
        Task<Apiary?> GetByIdAsync(Guid id);
        Task<Apiary?> GetByIdWithHivesAsync(Guid id);
        Task AddAsync(Apiary apiary);
        Task UpdateAsync(Apiary apiary);
        Task DeleteAsync(Apiary apiary);
        Task<bool> ExistsAsync(Guid id);
        Task<bool> IsOwnedByUserAsync(Guid apiaryId, Guid userId);
        Task SaveChangesAsync();
    }
}
