using Microsoft.EntityFrameworkCore;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Data;
using ApiaryServer.Application.Interfaces;

namespace ApiaryServer.Infrastructure.Repositories
{
    public class TaskRepository : ITaskRepository
    {
        private readonly AppDbContext _db;

        public TaskRepository(AppDbContext db) => _db = db;

        public async Task<IEnumerable<HiveTask>> GetAllByUserIdAsync(Guid userId)
        {
            return await _db.Tasks
                .Where(t => t.UserId == userId)
                .Include(t => t.Apiary)
                .Include(t => t.Hive)
                .OrderByDescending(t => t.Priority)
                .ThenBy(t => t.DueDate)
                .ToListAsync();
        }

        public async Task<IEnumerable<HiveTask>> GetByApiaryIdAsync(Guid apiaryId)
        {
            return await _db.Tasks
                .Where(t => t.ApiaryId == apiaryId)
                .Include(t => t.Apiary)
                .Include(t => t.Hive)
                .OrderByDescending(t => t.Priority)
                .ThenBy(t => t.DueDate)
                .ToListAsync();
        }

        public async Task<IEnumerable<HiveTask>> GetByHiveIdAsync(Guid hiveId)
        {
            return await _db.Tasks
                .Where(t => t.HiveId == hiveId)
                .Include(t => t.Apiary)
                .Include(t => t.Hive)
                .OrderByDescending(t => t.Priority)
                .ThenBy(t => t.DueDate)
                .ToListAsync();
        }

        public async Task<IEnumerable<HiveTask>> GetPendingByUserIdAsync(Guid userId)
        {
            return await _db.Tasks
                .Where(t => t.UserId == userId && t.Status != ApiaryServer.Domain.Entities.TaskStatus.Completed && t.Status != ApiaryServer.Domain.Entities.        TaskStatus.Cancelled)
                .Include(t => t.Apiary)
                .Include(t => t.Hive)
                .OrderByDescending(t => t.Priority)
                .ThenBy(t => t.DueDate)
                .ToListAsync();
        }

        public async Task<IEnumerable<HiveTask>> GetOverdueByUserIdAsync(Guid userId)
        {
            var now = DateTimeOffset.UtcNow;
            return await _db.Tasks
                .Where(t => t.UserId == userId
                    && t.Status != ApiaryServer.Domain.Entities.TaskStatus.Completed
                    && t.Status != ApiaryServer.Domain.Entities.TaskStatus.Cancelled
                    && t.DueDate.HasValue
                    && t.DueDate.Value < now)
                .Include(t => t.Apiary)
                .Include(t => t.Hive)
                .OrderBy(t => t.DueDate)
                .ToListAsync();
        }

        public async Task<HiveTask?> GetByIdAsync(Guid id)
        {
            return await _db.Tasks
                .Include(t => t.Apiary)
                .Include(t => t.Hive)
                .FirstOrDefaultAsync(t => t.Id == id);
        }

        public async Task AddAsync(HiveTask task)
        {
            await _db.Tasks.AddAsync(task);
        }

        public Task UpdateAsync(HiveTask task)
        {
            task.UpdatedAt = DateTimeOffset.UtcNow;
            _db.Tasks.Update(task);
            return Task.CompletedTask;
        }

        public Task DeleteAsync(HiveTask task)
        {
            _db.Tasks.Remove(task);
            return Task.CompletedTask;
        }

        public async Task<bool> ExistsAsync(Guid id)
        {
            return await _db.Tasks.AnyAsync(t => t.Id == id);
        }

        public async Task<bool> IsOwnedByUserAsync(Guid taskId, Guid userId)
        {
            return await _db.Tasks
                .AnyAsync(t => t.Id == taskId && t.UserId == userId);
        }

        public async Task SaveChangesAsync()
        {
            await _db.SaveChangesAsync();
        }
    }
}