using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Exceptions;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Services;
using Xunit;

namespace ApiaryServer.Tests;

public class HiveServiceTests
{
    private static readonly Guid UserId = Guid.Parse("11111111-1111-1111-1111-111111111111");
    private static readonly Guid OtherUserId = Guid.Parse("22222222-2222-2222-2222-222222222222");

    [Fact]
    public async Task GetAllHivesAsync_ReturnsOnlyCurrentUsersHives()
    {
        var ownedApiary = TestEntityFactory.Apiary("Owned", UserId);
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        var hiveRepo = new InMemoryHiveRepository();
        hiveRepo.Hives.Add(TestEntityFactory.Hive("Owned hive", ownedApiary));
        hiveRepo.Hives.Add(TestEntityFactory.Hive("Foreign hive", foreignApiary));
        var service = CreateService(hiveRepo, new InMemoryApiaryRepository());

        var hives = (await service.GetAllHivesAsync(UserId)).ToList();

        Assert.Single(hives);
        Assert.Equal("Owned hive", hives[0].Name);
        Assert.Equal("Owned", hives[0].ApiaryName);
    }

    [Fact]
    public async Task GetHivesByApiaryIdAsync_WithForeignApiary_ThrowsBeforeQueryingHives()
    {
        var apiaryRepo = new InMemoryApiaryRepository();
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        apiaryRepo.Apiaries.Add(foreignApiary);
        var hiveRepo = new InMemoryHiveRepository();
        var service = CreateService(hiveRepo, apiaryRepo);

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.GetHivesByApiaryIdAsync(foreignApiary.Id, UserId));

        Assert.Equal(0, hiveRepo.GetAllByApiaryIdCalls);
    }

    [Fact]
    public async Task GetHiveByIdAsync_WithMissingHive_Throws()
    {
        var service = CreateService(new InMemoryHiveRepository(), new InMemoryApiaryRepository());

        await Assert.ThrowsAsync<HiveNotFoundException>(() =>
            service.GetHiveByIdAsync(Guid.NewGuid(), UserId));
    }

    [Fact]
    public async Task GetHiveByIdAsync_WithForeignHive_Throws()
    {
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        var hiveRepo = new InMemoryHiveRepository();
        hiveRepo.Hives.Add(TestEntityFactory.Hive("Foreign hive", foreignApiary));
        var service = CreateService(hiveRepo, new InMemoryApiaryRepository());

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.GetHiveByIdAsync(hiveRepo.Hives[0].Id, UserId));
    }

    [Fact]
    public async Task CreateHiveAsync_WithMissingApiary_DoesNotPersist()
    {
        var hiveRepo = new InMemoryHiveRepository();
        var service = CreateService(hiveRepo, new InMemoryApiaryRepository());

        await Assert.ThrowsAsync<ApiaryNotFoundException>(() =>
            service.CreateHiveAsync(Guid.NewGuid(), CreateHiveRequest(), UserId));

        Assert.Empty(hiveRepo.Hives);
        Assert.Equal(0, hiveRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task CreateHiveAsync_WithForeignApiary_DoesNotPersist()
    {
        var apiaryRepo = new InMemoryApiaryRepository();
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        apiaryRepo.Apiaries.Add(foreignApiary);
        var hiveRepo = new InMemoryHiveRepository();
        var service = CreateService(hiveRepo, apiaryRepo);

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.CreateHiveAsync(foreignApiary.Id, CreateHiveRequest(), UserId));

        Assert.Empty(hiveRepo.Hives);
        Assert.Equal(0, hiveRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task CreateHiveAsync_WithOwnedApiary_PersistsAndMapsResponse()
    {
        var apiaryRepo = new InMemoryApiaryRepository();
        var apiary = TestEntityFactory.Apiary("Main", UserId);
        apiaryRepo.Apiaries.Add(apiary);
        var hiveRepo = new InMemoryHiveRepository();
        var service = CreateService(hiveRepo, apiaryRepo);

        var response = await service.CreateHiveAsync(apiary.Id, CreateHiveRequest(), UserId);

        Assert.Single(hiveRepo.Hives);
        Assert.Equal(apiary.Id, hiveRepo.Hives[0].ApiaryId);
        Assert.Equal("Hive A", hiveRepo.Hives[0].Name);
        Assert.True(hiveRepo.Hives[0].ReginaPrezenta);
        Assert.Equal(5, hiveRepo.Hives[0].RameAlbine);
        Assert.Equal("Main", response.ApiaryName);
        Assert.Equal(1, hiveRepo.AddCalls);
        Assert.Equal(1, hiveRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task UpdateHiveAsync_WithOwnedHive_UpdatesAllEditableFields()
    {
        var apiary = TestEntityFactory.Apiary("Main", UserId);
        var hive = TestEntityFactory.Hive("Old", apiary);
        var hiveRepo = new InMemoryHiveRepository();
        hiveRepo.Hives.Add(hive);
        var service = CreateService(hiveRepo, new InMemoryApiaryRepository());

        var response = await service.UpdateHiveAsync(
            hive.Id,
            new UpdateHiveRequest(
                "Updated",
                HiveType.Dadant,
                HiveStatus.Weak,
                "updated notes",
                false,
                3,
                6,
                2,
                1),
            UserId);

        Assert.Equal("Updated", hive.Name);
        Assert.Equal(HiveType.Dadant, hive.Type);
        Assert.Equal(HiveStatus.Weak, hive.Status);
        Assert.Equal("updated notes", hive.Notes);
        Assert.False(hive.ReginaPrezenta);
        Assert.Equal(3, response.VarstaRegina);
        Assert.Equal(1, hiveRepo.UpdateCalls);
        Assert.Equal(1, hiveRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task UpdateHiveAsync_WithForeignHive_DoesNotUpdate()
    {
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        var hive = TestEntityFactory.Hive("Foreign hive", foreignApiary);
        var hiveRepo = new InMemoryHiveRepository();
        hiveRepo.Hives.Add(hive);
        var service = CreateService(hiveRepo, new InMemoryApiaryRepository());

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.UpdateHiveAsync(hive.Id, UpdateHiveRequest(), UserId));

        Assert.Equal("Foreign hive", hive.Name);
        Assert.Equal(0, hiveRepo.UpdateCalls);
        Assert.Equal(0, hiveRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task DeleteHiveAsync_WithOwnedHive_RemovesAndSaves()
    {
        var apiary = TestEntityFactory.Apiary("Main", UserId);
        var hive = TestEntityFactory.Hive("Hive", apiary);
        var hiveRepo = new InMemoryHiveRepository();
        hiveRepo.Hives.Add(hive);
        var service = CreateService(hiveRepo, new InMemoryApiaryRepository());

        await service.DeleteHiveAsync(hive.Id, UserId);

        Assert.Empty(hiveRepo.Hives);
        Assert.Equal(1, hiveRepo.DeleteCalls);
        Assert.Equal(1, hiveRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task DeleteHiveAsync_WithMissingHive_Throws()
    {
        var hiveRepo = new InMemoryHiveRepository();
        var service = CreateService(hiveRepo, new InMemoryApiaryRepository());

        await Assert.ThrowsAsync<HiveNotFoundException>(() =>
            service.DeleteHiveAsync(Guid.NewGuid(), UserId));

        Assert.Equal(0, hiveRepo.DeleteCalls);
        Assert.Equal(0, hiveRepo.SaveChangesCalls);
    }

    private static HiveService CreateService(
        InMemoryHiveRepository hiveRepo,
        InMemoryApiaryRepository apiaryRepo) =>
        new(hiveRepo, apiaryRepo, new NoOpLogger<HiveService>());

    private static CreateHiveRequest CreateHiveRequest() =>
        new(
            "Hive A",
            HiveType.Langstroth,
            HiveStatus.Active,
            "new notes",
            true,
            1,
            5,
            2,
            4);

    private static UpdateHiveRequest UpdateHiveRequest() =>
        new(
            "Updated",
            HiveType.Dadant,
            HiveStatus.Active,
            "updated",
            true,
            2,
            7,
            3,
            2);
}
