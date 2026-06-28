using Microsoft.EntityFrameworkCore;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Data;
using ApiaryServer.Application.Interfaces;

namespace ApiaryServer.Infrastructure.Repositories
{
    public class ApiaryRepository : IApiaryRepository
    {
        private readonly AppDbContext _db;

        public ApiaryRepository(AppDbContext db) => _db = db;

        public async Task<IEnumerable<Apiary>> GetAllByUserIdAsync(Guid userId)
        {
            return await _db.Apiaries
                .Where(a => a.UserId == userId)
                .Include(a => a.Hives)
                .OrderBy(a => a.Name)
                .ToListAsync();
        }

        public async Task<Apiary?> GetByIdAsync(Guid id)
        {
            return await _db.Apiaries.FindAsync(id);
        }

        public async Task<Apiary?> GetByIdWithHivesAsync(Guid id)
        {
            return await _db.Apiaries
                .Include(a => a.Hives)
                .FirstOrDefaultAsync(a => a.Id == id);
        }

        public async Task AddAsync(Apiary apiary)
        {
            await _db.Apiaries.AddAsync(apiary);
        }

        public Task UpdateAsync(Apiary apiary)
        {
            apiary.UpdatedAt = DateTimeOffset.UtcNow;
            _db.Apiaries.Update(apiary);
            return Task.CompletedTask;
        }

        public Task DeleteAsync(Apiary apiary)
        {
            _db.Apiaries.Remove(apiary);
            return Task.CompletedTask;
        }

        public async Task<bool> ExistsAsync(Guid id)
        {
            return await _db.Apiaries.AnyAsync(a => a.Id == id);
        }

        public async Task<bool> IsOwnedByUserAsync(Guid apiaryId, Guid userId)
        {
            return await _db.Apiaries
                .AnyAsync(a => a.Id == apiaryId && a.UserId == userId);
        }

        public async Task SaveChangesAsync()
        {
            await _db.SaveChangesAsync();
        }
    }
}