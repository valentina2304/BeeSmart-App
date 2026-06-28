using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Exceptions;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Services;
using Microsoft.Extensions.Logging;
using Xunit;

namespace ApiaryServer.Tests;

public class ApiaryServiceTests
{
    private static readonly Guid UserId = Guid.Parse("11111111-1111-1111-1111-111111111111");
    private static readonly Guid OtherUserId = Guid.Parse("22222222-2222-2222-2222-222222222222");

    [Fact]
    public async Task GetAllApiariesAsync_MapsHiveCountsForUser()
    {
        var repo = new FakeApiaryRepository();
        repo.Apiaries.Add(Apiary("A1", UserId, hives: 2));
        repo.Apiaries.Add(Apiary("A2", UserId, hives: 0));
        repo.Apiaries.Add(Apiary("Other", OtherUserId, hives: 5));
        var service = CreateService(repo);

        var result = (await service.GetAllApiariesAsync(UserId)).ToList();

        Assert.Equal(2, result.Count);
        Assert.Equal("A1", result[0].Name);
        Assert.Equal(2, result[0].HiveCount);
        Assert.Equal("A2", result[1].Name);
        Assert.Equal(0, result[1].HiveCount);
    }

    [Fact]
    public async Task GetApiaryByIdAsync_MissingApiaryThrowsNotFound()
    {
        var service = CreateService(new FakeApiaryRepository());

        await Assert.ThrowsAsync<ApiaryNotFoundException>(() =>
            service.GetApiaryByIdAsync(Guid.NewGuid(), UserId));
    }

    [Fact]
    public async Task GetApiaryByIdAsync_ApiaryOwnedByOtherUserThrowsUnauthorized()
    {
        var repo = new FakeApiaryRepository();
        var apiary = Apiary("Foreign", OtherUserId);
        repo.Apiaries.Add(apiary);
        var service = CreateService(repo);

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.GetApiaryByIdAsync(apiary.Id, UserId));
    }

    [Fact]
    public async Task CreateApiaryAsync_PersistsNewApiaryForCurrentUser()
    {
        var repo = new FakeApiaryRepository();
        var service = CreateService(repo);

        var response = await service.CreateApiaryAsync(
            new CreateApiaryRequest("New yard", "Near orchard", "Cluj"),
            UserId);

        Assert.Single(repo.Apiaries);
        Assert.Equal(UserId, repo.Apiaries[0].UserId);
        Assert.Equal("New yard", repo.Apiaries[0].Name);
        Assert.Equal("Near orchard", repo.Apiaries[0].Description);
        Assert.Equal("Cluj", repo.Apiaries[0].Location);
        Assert.Equal(1, repo.SaveChangesCalls);
        Assert.Equal(repo.Apiaries[0].Id, response.Id);
        Assert.Equal(0, response.HiveCount);
    }

    [Fact]
    public async Task UpdateApiaryAsync_UpdatesOwnedApiaryAndReturnsFreshHiveCount()
    {
        var repo = new FakeApiaryRepository();
        var apiary = Apiary("Old", UserId, hives: 3);
        repo.Apiaries.Add(apiary);
        var service = CreateService(repo);

        var response = await service.UpdateApiaryAsync(
            apiary.Id,
            new UpdateApiaryRequest("Renamed", "Updated", "Sibiu"),
            UserId);

        Assert.Equal("Renamed", apiary.Name);
        Assert.Equal("Updated", apiary.Description);
        Assert.Equal("Sibiu", apiary.Location);
        Assert.Equal(1, repo.UpdateCalls);
        Assert.Equal(1, repo.SaveChangesCalls);
        Assert.Equal(3, response.HiveCount);
    }

    [Fact]
    public async Task UpdateApiaryAsync_DoesNotUpdateForeignApiary()
    {
        var repo = new FakeApiaryRepository();
        var apiary = Apiary("Foreign", OtherUserId);
        repo.Apiaries.Add(apiary);
        var service = CreateService(repo);

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.UpdateApiaryAsync(
                apiary.Id,
                new UpdateApiaryRequest("Should not apply", null, null),
                UserId));

        Assert.Equal("Foreign", apiary.Name);
        Assert.Equal(0, repo.UpdateCalls);
        Assert.Equal(0, repo.SaveChangesCalls);
    }

    [Fact]
    public async Task DeleteApiaryAsync_RemovesOwnedApiary()
    {
        var repo = new FakeApiaryRepository();
        var apiary = Apiary("Owned", UserId);
        repo.Apiaries.Add(apiary);
        var service = CreateService(repo);

        await service.DeleteApiaryAsync(apiary.Id, UserId);

        Assert.Empty(repo.Apiaries);
        Assert.Equal(1, repo.DeleteCalls);
        Assert.Equal(1, repo.SaveChangesCalls);
    }

    [Fact]
    public async Task DeleteApiaryAsync_DoesNotDeleteForeignApiary()
    {
        var repo = new FakeApiaryRepository();
        var apiary = Apiary("Foreign", OtherUserId);
        repo.Apiaries.Add(apiary);
        var service = CreateService(repo);

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.DeleteApiaryAsync(apiary.Id, UserId));

        Assert.Single(repo.Apiaries);
        Assert.Equal(0, repo.DeleteCalls);
        Assert.Equal(0, repo.SaveChangesCalls);
    }

    private static ApiaryService CreateService(FakeApiaryRepository repo) =>
        new(repo, new TestLogger<ApiaryService>());

    private static Apiary Apiary(string name, Guid userId, int hives = 0)
    {
        var apiary = new Apiary
        {
            Id = Guid.NewGuid(),
            UserId = userId,
            Name = name,
            Description = "desc",
            Location = "loc",
            CreatedAt = DateTimeOffset.Parse("2025-01-01T00:00:00Z"),
            UpdatedAt = DateTimeOffset.Parse("2025-01-02T00:00:00Z")
        };

        for (var i = 0; i < hives; i++)
        {
            apiary.Hives.Add(new Hive
            {
                Id = Guid.NewGuid(),
                ApiaryId = apiary.Id,
                Apiary = apiary,
                Name = $"Hive {i + 1}",
                Type = HiveType.Langstroth,
                Status = HiveStatus.Active
            });
        }

        return apiary;
    }

    private sealed class FakeApiaryRepository : IApiaryRepository
    {
        public List<Apiary> Apiaries { get; } = new();
        public int UpdateCalls { get; private set; }
        public int DeleteCalls { get; private set; }
        public int SaveChangesCalls { get; private set; }

        public Task<IEnumerable<Apiary>> GetAllByUserIdAsync(Guid userId) =>
            Task.FromResult(Apiaries.Where(a => a.UserId == userId).AsEnumerable());

        public Task<Apiary?> GetByIdAsync(Guid id) =>
            Task.FromResult(Apiaries.SingleOrDefault(a => a.Id == id));

        public Task<Apiary?> GetByIdWithHivesAsync(Guid id) =>
            Task.FromResult(Apiaries.SingleOrDefault(a => a.Id == id));

        public Task AddAsync(Apiary apiary)
        {
            Apiaries.Add(apiary);
            return Task.CompletedTask;
        }

        public Task UpdateAsync(Apiary apiary)
        {
            UpdateCalls++;
            return Task.CompletedTask;
        }

        public Task DeleteAsync(Apiary apiary)
        {
            DeleteCalls++;
            Apiaries.Remove(apiary);
            return Task.CompletedTask;
        }

        public Task<bool> ExistsAsync(Guid id) =>
            Task.FromResult(Apiaries.Any(a => a.Id == id));

        public Task<bool> IsOwnedByUserAsync(Guid apiaryId, Guid userId) =>
            Task.FromResult(Apiaries.Any(a => a.Id == apiaryId && a.UserId == userId));

        public Task SaveChangesAsync()
        {
            SaveChangesCalls++;
            return Task.CompletedTask;
        }
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
