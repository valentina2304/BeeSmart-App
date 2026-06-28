using Microsoft.EntityFrameworkCore;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Data;
using ApiaryServer.Application.Interfaces;

namespace ApiaryServer.Infrastructure.Repositories
{
    public class HiveRepository : IHiveRepository
    {
        private readonly AppDbContext _db;

        public HiveRepository(AppDbContext db) => _db = db;

        public async Task<IEnumerable<Hive>> GetAllByApiaryIdAsync(Guid apiaryId)
        {
            return await _db.Hives
                .Where(h => h.ApiaryId == apiaryId)
                .Include(h => h.Apiary)
                .OrderBy(h => h.Name)
                .ToListAsync();
        }

        public async Task<IEnumerable<Hive>> GetAllByUserIdAsync(Guid userId)
        {
            return await _db.Hives
                .Include(h => h.Apiary)
                .Where(h => h.Apiary.UserId == userId)
                .OrderBy(h => h.Apiary.Name)
                .ThenBy(h => h.Name)
                .ToListAsync();
        }

        public async Task<Hive?> GetByIdAsync(Guid id)
        {
            return await _db.Hives
                .Include(h => h.Apiary)
                .FirstOrDefaultAsync(h => h.Id == id);
        }

        public async Task AddAsync(Hive hive)
        {
            await _db.Hives.AddAsync(hive);
        }

        public Task UpdateAsync(Hive hive)
        {
            hive.UpdatedAt = DateTimeOffset.UtcNow;
            _db.Hives.Update(hive);
            return Task.CompletedTask;
        }

        public Task DeleteAsync(Hive hive)
        {
            _db.Hives.Remove(hive);
            return Task.CompletedTask;
        }

        public async Task<bool> ExistsAsync(Guid id)
        {
            return await _db.Hives.AnyAsync(h => h.Id == id);
        }

        public async Task<bool> IsOwnedByUserAsync(Guid hiveId, Guid userId)
        {
            return await _db.Hives
                .Include(h => h.Apiary)
                .AnyAsync(h => h.Id == hiveId && h.Apiary.UserId == userId);
        }

        public async Task SaveChangesAsync()
        {
            await _db.SaveChangesAsync();
        }
    }
}