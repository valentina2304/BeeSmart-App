using ApiaryServer.Application.Interfaces;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Data;
using Microsoft.EntityFrameworkCore;

namespace ApiaryServer.Infrastructure.Repositories
{
    public class InspectionRepository : IInspectionRepository
    {
        private readonly AppDbContext _context;

        public InspectionRepository(AppDbContext context)
        {
            _context = context;
        }

        public async Task<IEnumerable<Inspection>> GetAllByUserIdAsync(Guid userId)
        {
            return await LoadSummariesAsync(
                _context.Inspections.Where(i => i.Hive.Apiary.UserId == userId));
        }

        public async Task<IEnumerable<Inspection>> GetByApiaryIdAsync(Guid apiaryId)
        {
            return await LoadSummariesAsync(
                _context.Inspections.Where(i => i.ApiaryId == apiaryId));
        }

        public async Task<IEnumerable<Inspection>> GetByHiveIdAsync(Guid hiveId)
        {
            return await LoadSummariesAsync(
                _context.Inspections.Where(i => i.HiveId == hiveId));
        }

        // List views only need scalar fields, hive/apiary names and the photo COUNT.
        // Photos store their image as base64 in PhotoUrl, so eagerly Include-ing them
        // pulled megabytes of image data from the DB just to call .Photos.Count, making
        // the inspection list very slow. Instead we load the inspections without photos
        // and fetch only the photo ids (never the base64 blob), then attach lightweight
        // stubs so .Photos.Count stays correct. (Full photo content is still loaded by
        // GetByIdWithPhotosAsync for the detail screen.)
        private async Task<List<Inspection>> LoadSummariesAsync(IQueryable<Inspection> query)
        {
            var inspections = await query
                .Include(i => i.Hive)
                .Include(i => i.Apiary)
                .OrderByDescending(i => i.InspectionDate)
                .AsNoTracking()
                .ToListAsync();

            if (inspections.Count == 0)
            {
                return inspections;
            }

            var ids = inspections.Select(i => i.Id).ToList();
            var photoStubs = await _context.InspectionPhotos
                .Where(p => ids.Contains(p.InspectionId))
                .Select(p => new { p.Id, p.InspectionId })
                .ToListAsync();

            var photosByInspection = photoStubs
                .GroupBy(p => p.InspectionId)
                .ToDictionary(
                    g => g.Key,
                    g => (ICollection<InspectionPhoto>)g
                        .Select(p => new InspectionPhoto { Id = p.Id, InspectionId = p.InspectionId })
                        .ToList());

            foreach (var inspection in inspections)
            {
                inspection.Photos = photosByInspection.TryGetValue(inspection.Id, out var stubs)
                    ? stubs
                    : new List<InspectionPhoto>();
            }

            return inspections;
        }

        public async Task<Inspection?> GetByIdAsync(Guid id)
        {
            return await _context.Inspections
                .Include(i => i.Hive)
                    .ThenInclude(h => h.Apiary)
                .FirstOrDefaultAsync(i => i.Id == id);
        }

        public async Task<Inspection?> GetByIdWithPhotosAsync(Guid id)
        {
            return await _context.Inspections
                .Include(i => i.Hive)
                    .ThenInclude(h => h.Apiary)
                .Include(i => i.Photos)
                .FirstOrDefaultAsync(i => i.Id == id);
        }

        public async Task AddAsync(Inspection inspection)
        {
            await _context.Inspections.AddAsync(inspection);
        }

        public Task UpdateAsync(Inspection inspection)
        {
            inspection.UpdatedAt = DateTimeOffset.UtcNow;
            _context.Inspections.Update(inspection);
            return Task.CompletedTask;
        }

        public Task DeleteAsync(Inspection inspection)
        {
            _context.Inspections.Remove(inspection);
            return Task.CompletedTask;
        }

        public async Task<bool> ExistsAsync(Guid id)
        {
            return await _context.Inspections.AnyAsync(i => i.Id == id);
        }

        public async Task<bool> IsOwnedByUserAsync(Guid inspectionId, Guid userId)
        {
            return await _context.Inspections
                .AnyAsync(i => i.Id == inspectionId && i.Hive.Apiary.UserId == userId);
        }

        public async Task SaveChangesAsync()
        {
            await _context.SaveChangesAsync();
        }
    }
}
