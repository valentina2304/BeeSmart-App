using ApiaryServer.Application.Interfaces;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Data;
using Microsoft.EntityFrameworkCore;

namespace ApiaryServer.Infrastructure.Repositories
{
    public class HiveExtractionRepository : IHiveExtractionRepository
    {
        private readonly AppDbContext _context;

        public HiveExtractionRepository(AppDbContext context)
        {
            _context = context;
        }

        public async Task<IEnumerable<HiveExtraction>> GetAllByUserIdAsync(Guid userId)
        {
            return await _context.HiveExtractions
                .Include(e => e.Hive)
                .Include(e => e.Apiary)
                .Where(e => e.Hive.Apiary.UserId == userId)
                .OrderByDescending(e => e.ExtractionDate)
                .ToListAsync();
        }

        public async Task<IEnumerable<HiveExtraction>> GetByApiaryIdAsync(Guid apiaryId)
        {
            return await _context.HiveExtractions
                .Include(e => e.Hive)
                .Include(e => e.Apiary)
                .Where(e => e.ApiaryId == apiaryId)
                .OrderByDescending(e => e.ExtractionDate)
                .ToListAsync();
        }

        public async Task<IEnumerable<HiveExtraction>> GetByHiveIdAsync(Guid hiveId)
        {
            return await _context.HiveExtractions
                .Include(e => e.Hive)
                .Include(e => e.Apiary)
                .Where(e => e.HiveId == hiveId)
                .OrderByDescending(e => e.ExtractionDate)
                .ToListAsync();
        }

        public async Task<HiveExtraction?> GetByIdAsync(Guid id)
        {
            return await _context.HiveExtractions
                .Include(e => e.Hive)
                    .ThenInclude(h => h.Apiary)
                .Include(e => e.Apiary)
                .FirstOrDefaultAsync(e => e.Id == id);
        }

        public async Task AddAsync(HiveExtraction extraction)
        {
            await _context.HiveExtractions.AddAsync(extraction);
        }

        public Task UpdateAsync(HiveExtraction extraction)
        {
            extraction.UpdatedAt = DateTimeOffset.UtcNow;
            _context.HiveExtractions.Update(extraction);
            return Task.CompletedTask;
        }

        public Task DeleteAsync(HiveExtraction extraction)
        {
            _context.HiveExtractions.Remove(extraction);
            return Task.CompletedTask;
        }

        public async Task<bool> ExistsAsync(Guid id)
        {
            return await _context.HiveExtractions.AnyAsync(e => e.Id == id);
        }

        public async Task<bool> IsOwnedByUserAsync(Guid extractionId, Guid userId)
        {
            return await _context.HiveExtractions
                .AnyAsync(e => e.Id == extractionId && e.Hive.Apiary.UserId == userId);
        }

        public async Task SaveChangesAsync()
        {
            await _context.SaveChangesAsync();
        }
    }
}
