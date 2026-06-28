using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Exceptions;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Repositories;
using Microsoft.Extensions.Logging;

namespace ApiaryServer.Infrastructure.Services
{
    public class HiveService : IHiveService
    {
        private readonly IHiveRepository _hiveRepo;
        private readonly IApiaryRepository _apiaryRepo;
        private readonly ILogger<HiveService> _logger;

        public HiveService(
            IHiveRepository hiveRepo,
            IApiaryRepository apiaryRepo,
            ILogger<HiveService> logger)
        {
            _hiveRepo = hiveRepo;
            _apiaryRepo = apiaryRepo;
            _logger = logger;
        }

        public async Task<IEnumerable<HiveResponse>> GetAllHivesAsync(Guid userId)
        {
            var hives = await _hiveRepo.GetAllByUserIdAsync(userId);

            return hives.Select(h => new HiveResponse(
                h.Id,
                h.ApiaryId,
                h.Apiary.Name,
                h.Name,
                h.Type,
                h.Status,
                h.Notes,
                h.ReginaPrezenta,
                h.VarstaRegina,
                h.RameAlbine,
                h.RamePuiet,
                h.RameMiere,
                h.UltimaInspectie,
                h.CreatedAt,
                h.UpdatedAt
            ));
        }

        public async Task<IEnumerable<HiveResponse>> GetHivesByApiaryIdAsync(Guid apiaryId, Guid userId)
        {
            // Verify apiary ownership
            if (!await _apiaryRepo.IsOwnedByUserAsync(apiaryId, userId))
            {
                _logger.LogWarning("Unauthorized access to apiary {ApiaryId} by user {UserId}", apiaryId, userId);
                throw new System.UnauthorizedAccessException();
            }

            var hives = await _hiveRepo.GetAllByApiaryIdAsync(apiaryId);

            return hives.Select(h => new HiveResponse(
                h.Id,
                h.ApiaryId,
                h.Apiary.Name,
                h.Name,
                h.Type,
                h.Status,
                h.Notes,
                h.ReginaPrezenta,
                h.VarstaRegina,
                h.RameAlbine,
                h.RamePuiet,
                h.RameMiere,
                h.UltimaInspectie,
                h.CreatedAt,
                h.UpdatedAt
            ));
        }

        public async Task<HiveResponse> GetHiveByIdAsync(Guid id, Guid userId)
        {
            var hive = await _hiveRepo.GetByIdAsync(id);

            if (hive == null)
            {
                _logger.LogWarning("Hive not found: {HiveId}", id);
                throw new HiveNotFoundException();
            }

            if (hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized access to hive {HiveId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            return new HiveResponse(
                hive.Id,
                hive.ApiaryId,
                hive.Apiary.Name,
                hive.Name,
                hive.Type,
                hive.Status,
                hive.Notes,
                hive.ReginaPrezenta,
                hive.VarstaRegina,
                hive.RameAlbine,
                hive.RamePuiet,
                hive.RameMiere,
                hive.UltimaInspectie,
                hive.CreatedAt,
                hive.UpdatedAt
            );
        }

        public async Task<HiveResponse> CreateHiveAsync(Guid apiaryId, CreateHiveRequest dto, Guid userId)
        {
            // Verify apiary exists and user owns it
            var apiary = await _apiaryRepo.GetByIdAsync(apiaryId);

            if (apiary == null)
            {
                _logger.LogWarning("Apiary not found: {ApiaryId}", apiaryId);
                throw new ApiaryNotFoundException();
            }

            if (apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized hive creation in apiary {ApiaryId} by user {UserId}", apiaryId, userId);
                throw new System.UnauthorizedAccessException();
            }

            var hive = new Hive
            {
                ApiaryId = apiaryId,
                Name = dto.Name,
                Type = dto.Type,
                Status = dto.Status,
                Notes = dto.Notes,
                ReginaPrezenta = dto.ReginaPrezenta,
                VarstaRegina = dto.VarstaRegina,
                RameAlbine = dto.RameAlbine,
                RamePuiet = dto.RamePuiet,
                RameMiere = dto.RameMiere
            };

            await _hiveRepo.AddAsync(hive);
            await _hiveRepo.SaveChangesAsync();

            _logger.LogInformation("Hive created: {HiveId} in apiary {ApiaryId}", hive.Id, apiaryId);

            return new HiveResponse(
                hive.Id,
                hive.ApiaryId,
                apiary.Name,
                hive.Name,
                hive.Type,
                hive.Status,
                hive.Notes,
                hive.ReginaPrezenta,
                hive.VarstaRegina,
                hive.RameAlbine,
                hive.RamePuiet,
                hive.RameMiere,
                hive.UltimaInspectie,
                hive.CreatedAt,
                hive.UpdatedAt
            );
        }

        public async Task<HiveResponse> UpdateHiveAsync(Guid id, UpdateHiveRequest dto, Guid userId)
        {
            var hive = await _hiveRepo.GetByIdAsync(id);

            if (hive == null)
            {
                _logger.LogWarning("Hive not found for update: {HiveId}", id);
                throw new HiveNotFoundException();
            }

            if (hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized update attempt on hive {HiveId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            hive.Name = dto.Name;
            hive.Type = dto.Type;
            hive.Status = dto.Status;
            hive.Notes = dto.Notes;
            hive.ReginaPrezenta = dto.ReginaPrezenta;
            hive.VarstaRegina = dto.VarstaRegina;
            hive.RameAlbine = dto.RameAlbine;
            hive.RamePuiet = dto.RamePuiet;
            hive.RameMiere = dto.RameMiere;

            await _hiveRepo.UpdateAsync(hive);
            await _hiveRepo.SaveChangesAsync();

            _logger.LogInformation("Hive updated: {HiveId}", id);

            return new HiveResponse(
                hive.Id,
                hive.ApiaryId,
                hive.Apiary.Name,
                hive.Name,
                hive.Type,
                hive.Status,
                hive.Notes,
                hive.ReginaPrezenta,
                hive.VarstaRegina,
                hive.RameAlbine,
                hive.RamePuiet,
                hive.RameMiere,
                hive.UltimaInspectie,
                hive.CreatedAt,
                hive.UpdatedAt
            );
        }

        public async Task DeleteHiveAsync(Guid id, Guid userId)
        {
            var hive = await _hiveRepo.GetByIdAsync(id);

            if (hive == null)
            {
                _logger.LogWarning("Hive not found for deletion: {HiveId}", id);
                throw new HiveNotFoundException();
            }

            if (hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized delete attempt on hive {HiveId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            await _hiveRepo.DeleteAsync(hive);
            await _hiveRepo.SaveChangesAsync();

            _logger.LogInformation("Hive deleted: {HiveId}", id);
        }
    }
}
