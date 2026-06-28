using ApiaryServer.Application.Interfaces;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Data;
using Microsoft.EntityFrameworkCore;

namespace ApiaryServer.Infrastructure.Repositories
{
    public class InspectionPhotoRepository : IInspectionPhotoRepository
    {
        private readonly AppDbContext _context;

        public InspectionPhotoRepository(AppDbContext context)
        {
            _context = context;
        }

        public async Task<IEnumerable<InspectionPhoto>> GetByInspectionIdAsync(Guid inspectionId)
        {
            return await _context.InspectionPhotos
                .Where(p => p.InspectionId == inspectionId)
                .OrderBy(p => p.CreatedAt)
                .ToListAsync();
        }

        public async Task<InspectionPhoto?> GetByIdAsync(Guid id)
        {
            return await _context.InspectionPhotos
                .Include(p => p.Inspection)
                    .ThenInclude(i => i.Hive)
                        .ThenInclude(h => h.Apiary)
                .FirstOrDefaultAsync(p => p.Id == id);
        }

        public async Task AddAsync(InspectionPhoto photo)
        {
            await _context.InspectionPhotos.AddAsync(photo);
        }

        public Task UpdateAsync(InspectionPhoto photo)
        {
            _context.InspectionPhotos.Update(photo);
            return Task.CompletedTask;
        }

        public Task DeleteAsync(InspectionPhoto photo)
        {
            _context.InspectionPhotos.Remove(photo);
            return Task.CompletedTask;
        }

        public async Task<bool> ExistsAsync(Guid id)
        {
            return await _context.InspectionPhotos.AnyAsync(p => p.Id == id);
        }

        public async Task SaveChangesAsync()
        {
            await _context.SaveChangesAsync();
        }
    }
}
