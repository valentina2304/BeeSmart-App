using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Exceptions;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Services;
using Microsoft.Extensions.Logging;
using Xunit;
using TaskStatus = ApiaryServer.Domain.Entities.TaskStatus;

namespace ApiaryServer.Tests;

public class TaskServiceTests
{
    private static readonly Guid UserId = Guid.Parse("11111111-1111-1111-1111-111111111111");
    private static readonly Guid OtherUserId = Guid.Parse("22222222-2222-2222-2222-222222222222");

    [Fact]
    public async Task CreateTaskAsync_WithOwnedApiaryAndHive_PersistsTaskForUser()
    {
        var apiaryRepo = new FakeApiaryRepository();
        var hiveRepo = new FakeHiveRepository();
        var taskRepo = new FakeTaskRepository();
        var apiary = Apiary("Main", UserId);
        var hive = Hive("Hive 1", apiary);
        apiaryRepo.Apiaries.Add(apiary);
        hiveRepo.Hives.Add(hive);
        var service = CreateService(taskRepo, apiaryRepo, hiveRepo);

        var response = await service.CreateTaskAsync(
            new CreateTaskRequest(
                "Inspect hive",
                "Check queen",
                TaskPriority.High,
                DateTimeOffset.Parse("2026-06-01T00:00:00Z"),
                apiary.Id,
                hive.Id),
            UserId);

        Assert.Single(taskRepo.Tasks);
        Assert.Equal(UserId, taskRepo.Tasks[0].UserId);
        Assert.Equal(apiary.Id, taskRepo.Tasks[0].ApiaryId);
        Assert.Equal(hive.Id, taskRepo.Tasks[0].HiveId);
        Assert.Equal("Inspect hive", response.Title);
        Assert.Equal(TaskPriority.High, response.Priority);
        Assert.Equal(1, taskRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task CreateTaskAsync_WithForeignApiary_DoesNotPersist()
    {
        var apiaryRepo = new FakeApiaryRepository();
        apiaryRepo.Apiaries.Add(Apiary("Foreign", OtherUserId));
        var taskRepo = new FakeTaskRepository();
        var service = CreateService(taskRepo, apiaryRepo, new FakeHiveRepository());

        await Assert.ThrowsAsync<ApiaryNotFoundException>(() =>
            service.CreateTaskAsync(
                new CreateTaskRequest("Task", null, ApiaryId: apiaryRepo.Apiaries[0].Id),
                UserId));

        Assert.Empty(taskRepo.Tasks);
        Assert.Equal(0, taskRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task CreateTaskAsync_WithForeignHive_DoesNotPersist()
    {
        var foreignApiary = Apiary("Foreign", OtherUserId);
        var hiveRepo = new FakeHiveRepository();
        hiveRepo.Hives.Add(Hive("Foreign hive", foreignApiary));
        var taskRepo = new FakeTaskRepository();
        var service = CreateService(taskRepo, new FakeApiaryRepository(), hiveRepo);

        await Assert.ThrowsAsync<HiveNotFoundException>(() =>
            service.CreateTaskAsync(
                new CreateTaskRequest("Task", null, HiveId: hiveRepo.Hives[0].Id),
                UserId));

        Assert.Empty(taskRepo.Tasks);
        Assert.Equal(0, taskRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task GetTasksByApiaryIdAsync_WithForeignApiary_ThrowsUnauthorized()
    {
        var apiaryRepo = new FakeApiaryRepository();
        var apiary = Apiary("Foreign", OtherUserId);
        apiaryRepo.Apiaries.Add(apiary);
        var service = CreateService(new FakeTaskRepository(), apiaryRepo, new FakeHiveRepository());

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.GetTasksByApiaryIdAsync(apiary.Id, UserId));
    }

    [Fact]
    public async Task UpdateTaskAsync_WhenCompleted_SetsCompletedAt()
    {
        var taskRepo = new FakeTaskRepository();
        var task = Task("Inspect", UserId, status: TaskStatus.Pending);
        taskRepo.Tasks.Add(task);
        var service = CreateService(taskRepo, new FakeApiaryRepository(), new FakeHiveRepository());

        var response = await service.UpdateTaskAsync(
            task.Id,
            new UpdateTaskRequest(
                "Inspect done",
                "Finished",
                TaskPriority.Normal,
                TaskStatus.Completed,
                null,
                null,
                null),
            UserId);

        Assert.Equal(TaskStatus.Completed, task.Status);
        Assert.NotNull(task.CompletedAt);
        Assert.NotNull(response.CompletedAt);
        Assert.Equal(1, taskRepo.UpdateCalls);
        Assert.Equal(1, taskRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task UpdateTaskAsync_WhenReopened_ClearsCompletedAt()
    {
        var taskRepo = new FakeTaskRepository();
        var task = Task("Done", UserId, status: TaskStatus.Completed);
        task.CompletedAt = DateTimeOffset.Parse("2026-01-01T00:00:00Z");
        taskRepo.Tasks.Add(task);
        var service = CreateService(taskRepo, new FakeApiaryRepository(), new FakeHiveRepository());

        await service.UpdateTaskAsync(
            task.Id,
            new UpdateTaskRequest(
                "Reopened",
                null,
                TaskPriority.Low,
                TaskStatus.InProgress,
                null,
                null,
                null),
            UserId);

        Assert.Equal(TaskStatus.InProgress, task.Status);
        Assert.Null(task.CompletedAt);
    }

    [Fact]
    public async Task CompleteTaskAsync_MarksCompletedAndSaves()
    {
        var taskRepo = new FakeTaskRepository();
        var task = Task("Inspect", UserId, status: TaskStatus.Pending);
        taskRepo.Tasks.Add(task);
        var service = CreateService(taskRepo, new FakeApiaryRepository(), new FakeHiveRepository());

        var response = await service.CompleteTaskAsync(task.Id, UserId);

        Assert.Equal(TaskStatus.Completed, response.Status);
        Assert.NotNull(response.CompletedAt);
        Assert.Equal(1, taskRepo.UpdateCalls);
        Assert.Equal(1, taskRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task UncompleteTaskAsync_ReopensCompletedTaskAndClearsCompletedAt()
    {
        var taskRepo = new FakeTaskRepository();
        var task = Task("Done", UserId, status: TaskStatus.Completed);
        task.CompletedAt = DateTimeOffset.Parse("2026-01-01T00:00:00Z");
        taskRepo.Tasks.Add(task);
        var service = CreateService(taskRepo, new FakeApiaryRepository(), new FakeHiveRepository());

        var response = await service.UncompleteTaskAsync(task.Id, UserId);

        Assert.Equal(TaskStatus.Pending, task.Status);
        Assert.Null(task.CompletedAt);
        Assert.Equal(TaskStatus.Pending, response.Status);
        Assert.Null(response.CompletedAt);
        Assert.Equal(1, taskRepo.UpdateCalls);
        Assert.Equal(1, taskRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task UncompleteTaskAsync_WithForeignTask_DoesNotUpdate()
    {
        var taskRepo = new FakeTaskRepository();
        var task = Task("Foreign", OtherUserId, status: TaskStatus.Completed);
        task.CompletedAt = DateTimeOffset.Parse("2026-01-01T00:00:00Z");
        taskRepo.Tasks.Add(task);
        var service = CreateService(taskRepo, new FakeApiaryRepository(), new FakeHiveRepository());

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.UncompleteTaskAsync(task.Id, UserId));

        Assert.Equal(TaskStatus.Completed, task.Status);
        Assert.NotNull(task.CompletedAt);
        Assert.Equal(0, taskRepo.UpdateCalls);
        Assert.Equal(0, taskRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task DeleteTaskAsync_WithForeignTask_DoesNotDelete()
    {
        var taskRepo = new FakeTaskRepository();
        taskRepo.Tasks.Add(Task("Foreign", OtherUserId));
        var service = CreateService(taskRepo, new FakeApiaryRepository(), new FakeHiveRepository());

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.DeleteTaskAsync(taskRepo.Tasks[0].Id, UserId));

        Assert.Single(taskRepo.Tasks);
        Assert.Equal(0, taskRepo.DeleteCalls);
        Assert.Equal(0, taskRepo.SaveChangesCalls);
    }

    private static TaskService CreateService(
        FakeTaskRepository taskRepo,
        FakeApiaryRepository apiaryRepo,
        FakeHiveRepository hiveRepo) =>
        new(taskRepo, apiaryRepo, hiveRepo, new TestLogger<TaskService>());

    private static Apiary Apiary(string name, Guid userId) =>
        new()
        {
            Id = Guid.NewGuid(),
            UserId = userId,
            Name = name,
            Description = "desc",
            Location = "loc"
        };

    private static Hive Hive(string name, Apiary apiary) =>
        new()
        {
            Id = Guid.NewGuid(),
            ApiaryId = apiary.Id,
            Apiary = apiary,
            Name = name,
            Type = HiveType.Langstroth,
            Status = HiveStatus.Active
        };

    private static HiveTask Task(
        string title,
        Guid userId,
        TaskStatus status = TaskStatus.Pending,
        Apiary? apiary = null,
        Hive? hive = null) =>
        new()
        {
            Id = Guid.NewGuid(),
            UserId = userId,
            ApiaryId = apiary?.Id,
            Apiary = apiary,
            HiveId = hive?.Id,
            Hive = hive,
            Title = title,
            Priority = TaskPriority.Normal,
            Status = status,
            CreatedAt = DateTimeOffset.Parse("2026-01-01T00:00:00Z"),
            UpdatedAt = DateTimeOffset.Parse("2026-01-02T00:00:00Z")
        };

    private sealed class FakeTaskRepository : ITaskRepository
    {
        public List<HiveTask> Tasks { get; } = new();
        public int UpdateCalls { get; private set; }
        public int DeleteCalls { get; private set; }
        public int SaveChangesCalls { get; private set; }

        public Task<IEnumerable<HiveTask>> GetAllByUserIdAsync(Guid userId) =>
            System.Threading.Tasks.Task.FromResult(Tasks.Where(t => t.UserId == userId).AsEnumerable());

        public Task<IEnumerable<HiveTask>> GetByApiaryIdAsync(Guid apiaryId) =>
            System.Threading.Tasks.Task.FromResult(Tasks.Where(t => t.ApiaryId == apiaryId).AsEnumerable());

        public Task<IEnumerable<HiveTask>> GetByHiveIdAsync(Guid hiveId) =>
            System.Threading.Tasks.Task.FromResult(Tasks.Where(t => t.HiveId == hiveId).AsEnumerable());

        public Task<IEnumerable<HiveTask>> GetPendingByUserIdAsync(Guid userId) =>
            System.Threading.Tasks.Task.FromResult(
                Tasks.Where(t => t.UserId == userId && t.Status is not TaskStatus.Completed and not TaskStatus.Cancelled)
                    .AsEnumerable());

        public Task<IEnumerable<HiveTask>> GetOverdueByUserIdAsync(Guid userId) =>
            System.Threading.Tasks.Task.FromResult(
                Tasks.Where(t =>
                        t.UserId == userId
                        && t.DueDate.HasValue
                        && t.DueDate.Value < DateTimeOffset.UtcNow
                        && t.Status is not TaskStatus.Completed and not TaskStatus.Cancelled)
                    .AsEnumerable());

        public Task<HiveTask?> GetByIdAsync(Guid id) =>
            System.Threading.Tasks.Task.FromResult(Tasks.SingleOrDefault(t => t.Id == id));

        public Task AddAsync(HiveTask task)
        {
            Tasks.Add(task);
            return System.Threading.Tasks.Task.CompletedTask;
        }

        public Task UpdateAsync(HiveTask task)
        {
            UpdateCalls++;
            return System.Threading.Tasks.Task.CompletedTask;
        }

        public Task DeleteAsync(HiveTask task)
        {
            DeleteCalls++;
            Tasks.Remove(task);
            return System.Threading.Tasks.Task.CompletedTask;
        }

        public Task<bool> ExistsAsync(Guid id) =>
            System.Threading.Tasks.Task.FromResult(Tasks.Any(t => t.Id == id));

        public Task<bool> IsOwnedByUserAsync(Guid taskId, Guid userId) =>
            System.Threading.Tasks.Task.FromResult(Tasks.Any(t => t.Id == taskId && t.UserId == userId));

        public Task SaveChangesAsync()
        {
            SaveChangesCalls++;
            return System.Threading.Tasks.Task.CompletedTask;
        }
    }

    private sealed class FakeApiaryRepository : IApiaryRepository
    {
        public List<Apiary> Apiaries { get; } = new();

        public Task<IEnumerable<Apiary>> GetAllByUserIdAsync(Guid userId) =>
            System.Threading.Tasks.Task.FromResult(Apiaries.Where(a => a.UserId == userId).AsEnumerable());

        public Task<Apiary?> GetByIdAsync(Guid id) =>
            System.Threading.Tasks.Task.FromResult(Apiaries.SingleOrDefault(a => a.Id == id));

        public Task<Apiary?> GetByIdWithHivesAsync(Guid id) =>
            System.Threading.Tasks.Task.FromResult(Apiaries.SingleOrDefault(a => a.Id == id));

        public Task AddAsync(Apiary apiary)
        {
            Apiaries.Add(apiary);
            return System.Threading.Tasks.Task.CompletedTask;
        }

        public Task UpdateAsync(Apiary apiary) => System.Threading.Tasks.Task.CompletedTask;

        public Task DeleteAsync(Apiary apiary)
        {
            Apiaries.Remove(apiary);
            return System.Threading.Tasks.Task.CompletedTask;
        }

        public Task<bool> ExistsAsync(Guid id) =>
            System.Threading.Tasks.Task.FromResult(Apiaries.Any(a => a.Id == id));

        public Task<bool> IsOwnedByUserAsync(Guid apiaryId, Guid userId) =>
            System.Threading.Tasks.Task.FromResult(Apiaries.Any(a => a.Id == apiaryId && a.UserId == userId));

        public Task SaveChangesAsync() => System.Threading.Tasks.Task.CompletedTask;
    }

    private sealed class FakeHiveRepository : IHiveRepository
    {
        public List<Hive> Hives { get; } = new();

        public Task<IEnumerable<Hive>> GetAllByApiaryIdAsync(Guid apiaryId) =>
            System.Threading.Tasks.Task.FromResult(Hives.Where(h => h.ApiaryId == apiaryId).AsEnumerable());

        public Task<IEnumerable<Hive>> GetAllByUserIdAsync(Guid userId) =>
            System.Threading.Tasks.Task.FromResult(Hives.Where(h => h.Apiary.UserId == userId).AsEnumerable());

        public Task<Hive?> GetByIdAsync(Guid id) =>
            System.Threading.Tasks.Task.FromResult(Hives.SingleOrDefault(h => h.Id == id));

        public Task AddAsync(Hive hive)
        {
            Hives.Add(hive);
            return System.Threading.Tasks.Task.CompletedTask;
        }

        public Task UpdateAsync(Hive hive) => System.Threading.Tasks.Task.CompletedTask;

        public Task DeleteAsync(Hive hive)
        {
            Hives.Remove(hive);
            return System.Threading.Tasks.Task.CompletedTask;
        }

        public Task<bool> ExistsAsync(Guid id) =>
            System.Threading.Tasks.Task.FromResult(Hives.Any(h => h.Id == id));

        public Task<bool> IsOwnedByUserAsync(Guid hiveId, Guid userId) =>
            System.Threading.Tasks.Task.FromResult(Hives.Any(h => h.Id == hiveId && h.Apiary.UserId == userId));

        public Task SaveChangesAsync() => System.Threading.Tasks.Task.CompletedTask;
    }

    private sealed class TestLogger<T> : ILogger<T>
    {
        public IDisposable? BeginScope<TState>(TState state) where TState : notnull => null;
        public bool IsEnabled(LogLevel logLevel) => false;
        public void Log<TState>(
            LogLevel logLevel,
            EventId eventId,
            TState state,
            Exception? exception,
            Func<TState, Exception?, string> formatter)
        {
        }
    }
}
