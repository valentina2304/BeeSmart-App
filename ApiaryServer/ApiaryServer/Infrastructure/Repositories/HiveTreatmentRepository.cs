using ApiaryServer.Application.Interfaces;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Data;
using Microsoft.EntityFrameworkCore;

namespace ApiaryServer.Infrastructure.Repositories
{
    public class HiveTreatmentRepository : IHiveTreatmentRepository
    {
        private readonly AppDbContext _context;

        public HiveTreatmentRepository(AppDbContext context)
        {
            _context = context;
        }

        public async Task<IEnumerable<HiveTreatment>> GetAllByUserIdAsync(Guid userId)
        {
            return await _context.HiveTreatments
                .Include(t => t.Hive)
                .Include(t => t.Apiary)
                .Where(t => t.Hive.Apiary.UserId == userId)
                .OrderByDescending(t => t.TreatmentDate)
                .ToListAsync();
        }

        public async Task<IEnumerable<HiveTreatment>> GetByApiaryIdAsync(Guid apiaryId)
        {
            return await _context.HiveTreatments
                .Include(t => t.Hive)
                .Include(t => t.Apiary)
                .Where(t => t.ApiaryId == apiaryId)
                .OrderByDescending(t => t.TreatmentDate)
                .ToListAsync();
        }

        public async Task<IEnumerable<HiveTreatment>> GetByHiveIdAsync(Guid hiveId)
        {
            return await _context.HiveTreatments
                .Include(t => t.Hive)
                .Include(t => t.Apiary)
                .Where(t => t.HiveId == hiveId)
                .OrderByDescending(t => t.TreatmentDate)
                .ToListAsync();
        }

        public async Task<HiveTreatment?> GetByIdAsync(Guid id)
        {
            return await _context.HiveTreatments
                .Include(t => t.Hive)
                    .ThenInclude(h => h.Apiary)
                .Include(t => t.Apiary)
                .FirstOrDefaultAsync(t => t.Id == id);
        }

        public async Task AddAsync(HiveTreatment treatment)
        {
            await _context.HiveTreatments.AddAsync(treatment);
        }

        public Task UpdateAsync(HiveTreatment treatment)
        {
            treatment.UpdatedAt = DateTimeOffset.UtcNow;
            _context.HiveTreatments.Update(treatment);
            return Task.CompletedTask;
        }

        public Task DeleteAsync(HiveTreatment treatment)
        {
            _context.HiveTreatments.Remove(treatment);
            return Task.CompletedTask;
        }

        public async Task<bool> ExistsAsync(Guid id)
        {
            return await _context.HiveTreatments.AnyAsync(t => t.Id == id);
        }

        public async Task<bool> IsOwnedByUserAsync(Guid treatmentId, Guid userId)
        {
            return await _context.HiveTreatments
                .AnyAsync(t => t.Id == treatmentId && t.Hive.Apiary.UserId == userId);
        }

        public async Task SaveChangesAsync()
        {
            await _context.SaveChangesAsync();
        }
    }
}
