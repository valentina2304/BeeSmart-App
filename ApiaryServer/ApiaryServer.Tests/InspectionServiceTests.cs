using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Exceptions;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Services;
using Xunit;

namespace ApiaryServer.Tests;

public class InspectionServiceTests
{
    private static readonly Guid UserId = Guid.Parse("11111111-1111-1111-1111-111111111111");
    private static readonly Guid OtherUserId = Guid.Parse("22222222-2222-2222-2222-222222222222");

    [Fact]
    public async Task GetInspectionsByApiaryIdAsync_WithForeignEmptyApiary_ThrowsBeforeQuery()
    {
        var apiaryRepo = new InMemoryApiaryRepository();
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        apiaryRepo.Apiaries.Add(foreignApiary);
        var inspectionRepo = new FakeInspectionRepository();
        var service = CreateService(inspectionRepo, new FakeInspectionPhotoRepository(), new InMemoryHiveRepository(), apiaryRepo);

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.GetInspectionsByApiaryIdAsync(foreignApiary.Id, UserId));

        Assert.Equal(0, inspectionRepo.GetByApiaryIdCalls);
    }

    [Fact]
    public async Task GetInspectionsByApiaryIdAsync_WithOwnedApiary_MapsPhotoCount()
    {
        var apiary = TestEntityFactory.Apiary("Main", UserId);
        var hive = TestEntityFactory.Hive("Hive", apiary);
        var inspection = TestEntityFactory.Inspection(hive);
        inspection.Photos.Add(TestEntityFactory.Photo(inspection, "front"));
        inspection.Photos.Add(TestEntityFactory.Photo(inspection, "back"));
        var apiaryRepo = new InMemoryApiaryRepository();
        apiaryRepo.Apiaries.Add(apiary);
        var inspectionRepo = new FakeInspectionRepository();
        inspectionRepo.Inspections.Add(inspection);
        var service = CreateService(inspectionRepo, new FakeInspectionPhotoRepository(), new InMemoryHiveRepository(), apiaryRepo);

        var response = (await service.GetInspectionsByApiaryIdAsync(apiary.Id, UserId)).Single();

        Assert.Equal("Hive", response.HiveName);
        Assert.Equal("Main", response.ApiaryName);
        Assert.Equal(2, response.PhotosCount);
        Assert.Equal(1, inspectionRepo.GetByApiaryIdCalls);
    }

    [Fact]
    public async Task GetInspectionsByHiveIdAsync_WithForeignHive_ThrowsBeforeQuery()
    {
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        var foreignHive = TestEntityFactory.Hive("Foreign hive", foreignApiary);
        var hiveRepo = new InMemoryHiveRepository();
        hiveRepo.Hives.Add(foreignHive);
        var inspectionRepo = new FakeInspectionRepository();
        var service = CreateService(inspectionRepo, new FakeInspectionPhotoRepository(), hiveRepo, new InMemoryApiaryRepository());

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.GetInspectionsByHiveIdAsync(foreignHive.Id, UserId));

        Assert.Equal(0, inspectionRepo.GetByHiveIdCalls);
    }

    [Fact]
    public async Task GetInspectionByIdAsync_WithMissingInspection_Throws()
    {
        var service = CreateService(new FakeInspectionRepository(), new FakeInspectionPhotoRepository(), new InMemoryHiveRepository(), new InMemoryApiaryRepository());

        await Assert.ThrowsAsync<InspectionNotFoundException>(() =>
            service.GetInspectionByIdAsync(Guid.NewGuid(), UserId));
    }

    [Fact]
    public async Task GetInspectionByIdAsync_WithForeignInspection_Throws()
    {
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        var inspection = TestEntityFactory.Inspection(TestEntityFactory.Hive("Foreign hive", foreignApiary));
        var inspectionRepo = new FakeInspectionRepository();
        inspectionRepo.Inspections.Add(inspection);
        var service = CreateService(inspectionRepo, new FakeInspectionPhotoRepository(), new InMemoryHiveRepository(), new InMemoryApiaryRepository());

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.GetInspectionByIdAsync(inspection.Id, UserId));
    }

    [Fact]
    public async Task AddPhotoAsync_WithMissingInspection_DoesNotPersist()
    {
        var photoRepo = new FakeInspectionPhotoRepository();
        var service = CreateService(new FakeInspectionRepository(), photoRepo, new InMemoryHiveRepository(), new InMemoryApiaryRepository());

        await Assert.ThrowsAsync<InspectionNotFoundException>(() =>
            service.AddPhotoAsync(Guid.NewGuid(), AddPhotoRequest(), UserId));

        Assert.Empty(photoRepo.Photos);
        Assert.Equal(0, photoRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task AddPhotoAsync_WithForeignInspection_DoesNotPersist()
    {
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        var inspection = TestEntityFactory.Inspection(TestEntityFactory.Hive("Foreign hive", foreignApiary));
        var inspectionRepo = new FakeInspectionRepository();
        inspectionRepo.Inspections.Add(inspection);
        var photoRepo = new FakeInspectionPhotoRepository();
        var service = CreateService(inspectionRepo, photoRepo, new InMemoryHiveRepository(), new InMemoryApiaryRepository());

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.AddPhotoAsync(inspection.Id, AddPhotoRequest(), UserId));

        Assert.Empty(photoRepo.Photos);
        Assert.Equal(0, photoRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task AddPhotoAsync_WithOwnedInspection_PersistsAndMapsResponse()
    {
        var apiary = TestEntityFactory.Apiary("Main", UserId);
        var inspection = TestEntityFactory.Inspection(TestEntityFactory.Hive("Hive", apiary));
        var inspectionRepo = new FakeInspectionRepository();
        inspectionRepo.Inspections.Add(inspection);
        var photoRepo = new FakeInspectionPhotoRepository();
        var service = CreateService(inspectionRepo, photoRepo, new InMemoryHiveRepository(), new InMemoryApiaryRepository());

        var response = await service.AddPhotoAsync(inspection.Id, AddPhotoRequest(), UserId);

        Assert.Single(photoRepo.Photos);
        Assert.Equal(inspection.Id, response.InspectionId);
        Assert.Equal("https://example.test/new.jpg", response.PhotoUrl);
        Assert.Equal("new description", response.Description);
        Assert.Equal(1, photoRepo.AddCalls);
        Assert.Equal(1, photoRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task UpdatePhotoAsync_WithMissingPhoto_Throws()
    {
        var photoRepo = new FakeInspectionPhotoRepository();
        var service = CreateService(new FakeInspectionRepository(), photoRepo, new InMemoryHiveRepository(), new InMemoryApiaryRepository());

        await Assert.ThrowsAsync<InspectionPhotoNotFoundException>(() =>
            service.UpdatePhotoAsync(Guid.NewGuid(), new UpdateInspectionPhotoRequest("updated"), UserId));
    }

    [Fact]
    public async Task UpdatePhotoAsync_WithForeignPhoto_DoesNotUpdate()
    {
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        var photo = TestEntityFactory.Photo(
            TestEntityFactory.Inspection(TestEntityFactory.Hive("Foreign hive", foreignApiary)),
            "old");
        var photoRepo = new FakeInspectionPhotoRepository();
        photoRepo.Photos.Add(photo);
        var service = CreateService(new FakeInspectionRepository(), photoRepo, new InMemoryHiveRepository(), new InMemoryApiaryRepository());

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.UpdatePhotoAsync(photo.Id, new UpdateInspectionPhotoRequest("updated"), UserId));

        Assert.Equal("old", photo.Description);
        Assert.Equal(0, photoRepo.UpdateCalls);
        Assert.Equal(0, photoRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task UpdatePhotoAsync_WithOwnedPhoto_UpdatesDescriptionAndSaves()
    {
        var apiary = TestEntityFactory.Apiary("Main", UserId);
        var photo = TestEntityFactory.Photo(TestEntityFactory.Inspection(TestEntityFactory.Hive("Hive", apiary)), "old");
        var photoRepo = new FakeInspectionPhotoRepository();
        photoRepo.Photos.Add(photo);
        var service = CreateService(new FakeInspectionRepository(), photoRepo, new InMemoryHiveRepository(), new InMemoryApiaryRepository());

        var response = await service.UpdatePhotoAsync(photo.Id, new UpdateInspectionPhotoRequest("updated"), UserId);

        Assert.Equal("updated", photo.Description);
        Assert.Equal("updated", response.Description);
        Assert.Equal(1, photoRepo.UpdateCalls);
        Assert.Equal(1, photoRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task DeletePhotoAsync_WithOwnedPhoto_RemovesAndSaves()
    {
        var apiary = TestEntityFactory.Apiary("Main", UserId);
        var photo = TestEntityFactory.Photo(TestEntityFactory.Inspection(TestEntityFactory.Hive("Hive", apiary)));
        var photoRepo = new FakeInspectionPhotoRepository();
        photoRepo.Photos.Add(photo);
        var service = CreateService(new FakeInspectionRepository(), photoRepo, new InMemoryHiveRepository(), new InMemoryApiaryRepository());

        await service.DeletePhotoAsync(photo.Id, UserId);

        Assert.Empty(photoRepo.Photos);
        Assert.Equal(1, photoRepo.DeleteCalls);
        Assert.Equal(1, photoRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task DeletePhotoAsync_WithForeignPhoto_DoesNotDelete()
    {
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        var photo = TestEntityFactory.Photo(TestEntityFactory.Inspection(TestEntityFactory.Hive("Foreign hive", foreignApiary)));
        var photoRepo = new FakeInspectionPhotoRepository();
        photoRepo.Photos.Add(photo);
        var service = CreateService(new FakeInspectionRepository(), photoRepo, new InMemoryHiveRepository(), new InMemoryApiaryRepository());

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.DeletePhotoAsync(photo.Id, UserId));

        Assert.Single(photoRepo.Photos);
        Assert.Equal(0, photoRepo.DeleteCalls);
        Assert.Equal(0, photoRepo.SaveChangesCalls);
    }

    private static InspectionService CreateService(
        FakeInspectionRepository inspectionRepo,
        FakeInspectionPhotoRepository photoRepo,
        InMemoryHiveRepository hiveRepo,
        InMemoryApiaryRepository apiaryRepo) =>
        new(inspectionRepo, photoRepo, hiveRepo, apiaryRepo, null!, new NoOpLogger<InspectionService>());

    private static AddInspectionPhotoRequest AddPhotoRequest() =>
        new("https://example.test/new.jpg", "new description");

    private sealed class FakeInspectionRepository : IInspectionRepository
    {
        public List<Inspection> Inspections { get; } = new();
        public int GetByApiaryIdCalls { get; private set; }
        public int GetByHiveIdCalls { get; private set; }
        public int AddCalls { get; private set; }
        public int UpdateCalls { get; private set; }
        public int DeleteCalls { get; private set; }
        public int SaveChangesCalls { get; private set; }

        public Task<IEnumerable<Inspection>> GetAllByUserIdAsync(Guid userId) =>
            Task.FromResult(Inspections.Where(i => i.Hive.Apiary.UserId == userId).AsEnumerable());

        public Task<IEnumerable<Inspection>> GetByApiaryIdAsync(Guid apiaryId)
        {
            GetByApiaryIdCalls++;
            return Task.FromResult(Inspections.Where(i => i.ApiaryId == apiaryId).AsEnumerable());
        }

        public Task<IEnumerable<Inspection>> GetByHiveIdAsync(Guid hiveId)
        {
            GetByHiveIdCalls++;
            return Task.FromResult(Inspections.Where(i => i.HiveId == hiveId).AsEnumerable());
        }

        public Task<Inspection?> GetByIdAsync(Guid id) =>
            Task.FromResult(Inspections.SingleOrDefault(i => i.Id == id));

        public Task<Inspection?> GetByIdWithPhotosAsync(Guid id) =>
            Task.FromResult(Inspections.SingleOrDefault(i => i.Id == id));

        public Task AddAsync(Inspection inspection)
        {
            AddCalls++;
            Inspections.Add(inspection);
            return Task.CompletedTask;
        }

        public Task UpdateAsync(Inspection inspection)
        {
            UpdateCalls++;
            return Task.CompletedTask;
        }

        public Task DeleteAsync(Inspection inspection)
        {
            DeleteCalls++;
            Inspections.Remove(inspection);
            return Task.CompletedTask;
        }

        public Task<bool> ExistsAsync(Guid id) =>
            Task.FromResult(Inspections.Any(i => i.Id == id));

        public Task<bool> IsOwnedByUserAsync(Guid inspectionId, Guid userId) =>
            Task.FromResult(Inspections.Any(i => i.Id == inspectionId && i.Hive.Apiary.UserId == userId));

        public Task SaveChangesAsync()
        {
            SaveChangesCalls++;
            return Task.CompletedTask;
        }
    }

    private sealed class FakeInspectionPhotoRepository : IInspectionPhotoRepository
    {
        public List<InspectionPhoto> Photos { get; } = new();
        public int AddCalls { get; private set; }
        public int UpdateCalls { get; private set; }
        public int DeleteCalls { get; private set; }
        public int SaveChangesCalls { get; private set; }

        public Task<IEnumerable<InspectionPhoto>> GetByInspectionIdAsync(Guid inspectionId) =>
            Task.FromResult(Photos.Where(p => p.InspectionId == inspectionId).AsEnumerable());

        public Task<InspectionPhoto?> GetByIdAsync(Guid id) =>
            Task.FromResult(Photos.SingleOrDefault(p => p.Id == id));

        public Task AddAsync(InspectionPhoto photo)
        {
            AddCalls++;
            Photos.Add(photo);
            return Task.CompletedTask;
        }

        public Task UpdateAsync(InspectionPhoto photo)
        {
            UpdateCalls++;
            return Task.CompletedTask;
        }

        public Task DeleteAsync(InspectionPhoto photo)
        {
            DeleteCalls++;
            Photos.Remove(photo);
            return Task.CompletedTask;
        }

        public Task<bool> ExistsAsync(Guid id) =>
            Task.FromResult(Photos.Any(p => p.Id == id));

        public Task SaveChangesAsync()
        {
            SaveChangesCalls++;
            return Task.CompletedTask;
        }
    }
}
