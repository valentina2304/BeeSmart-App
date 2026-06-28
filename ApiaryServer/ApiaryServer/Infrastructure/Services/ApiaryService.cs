using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Exceptions;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Repositories;
using Microsoft.Extensions.Logging;

namespace ApiaryServer.Infrastructure.Services
{
    public class ApiaryService : IApiaryService
    {
        private readonly IApiaryRepository _apiaryRepo;
        private readonly ILogger<ApiaryService> _logger;

        public ApiaryService(
            IApiaryRepository apiaryRepo,
            ILogger<ApiaryService> logger)
        {
            _apiaryRepo = apiaryRepo;
            _logger = logger;
        }

        public async Task<IEnumerable<ApiaryResponse>> GetAllApiariesAsync(Guid userId)
        {
            var apiaries = await _apiaryRepo.GetAllByUserIdAsync(userId);

            return apiaries.Select(a => new ApiaryResponse(
                a.Id,
                a.UserId,
                a.Name,
                a.Description,
                a.Location,
                a.Hives.Count,
                a.CreatedAt,
                a.UpdatedAt
            ));
        }

        public async Task<ApiaryDetailResponse> GetApiaryByIdAsync(Guid id, Guid userId)
        {
            var apiary = await _apiaryRepo.GetByIdWithHivesAsync(id);

            if (apiary == null)
            {
                _logger.LogWarning("Apiary not found: {ApiaryId}", id);
                throw new ApiaryNotFoundException();
            }

            if (apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized access to apiary {ApiaryId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            var hives = apiary.Hives.Select(h => new HiveResponse(
                h.Id,
                h.ApiaryId,
                apiary.Name,
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
            )).ToList();

            return new ApiaryDetailResponse(
                apiary.Id,
                apiary.UserId,
                apiary.Name,
                apiary.Description,
                apiary.Location,
                hives,
                apiary.CreatedAt,
                apiary.UpdatedAt
            );
        }

        public async Task<ApiaryResponse> CreateApiaryAsync(CreateApiaryRequest dto, Guid userId)
        {
            var apiary = new Apiary
            {
                UserId = userId,
                Name = dto.Name,
                Description = dto.Description,
                Location = dto.Location
            };

            await _apiaryRepo.AddAsync(apiary);
            await _apiaryRepo.SaveChangesAsync();

            _logger.LogInformation("Apiary created: {ApiaryId} by user {UserId}", apiary.Id, userId);

            return new ApiaryResponse(
                apiary.Id,
                apiary.UserId,
                apiary.Name,
                apiary.Description,
                apiary.Location,
                0,
                apiary.CreatedAt,
                apiary.UpdatedAt
            );
        }

        public async Task<ApiaryResponse> UpdateApiaryAsync(Guid id, UpdateApiaryRequest dto, Guid userId)
        {
            var apiary = await _apiaryRepo.GetByIdAsync(id);

            if (apiary == null)
            {
                _logger.LogWarning("Apiary not found for update: {ApiaryId}", id);
                throw new ApiaryNotFoundException();
            }

            if (apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized update attempt on apiary {ApiaryId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            apiary.Name = dto.Name;
            apiary.Description = dto.Description;
            apiary.Location = dto.Location;

            await _apiaryRepo.UpdateAsync(apiary);
            await _apiaryRepo.SaveChangesAsync();

            _logger.LogInformation("Apiary updated: {ApiaryId}", id);

            // Get hive count
            var apiaryWithHives = await _apiaryRepo.GetByIdWithHivesAsync(id);

            return new ApiaryResponse(
                apiary.Id,
                apiary.UserId,
                apiary.Name,
                apiary.Description,
                apiary.Location,
                apiaryWithHives?.Hives.Count ?? 0,
                apiary.CreatedAt,
                apiary.UpdatedAt
            );
        }

        public async Task DeleteApiaryAsync(Guid id, Guid userId)
        {
            var apiary = await _apiaryRepo.GetByIdAsync(id);

            if (apiary == null)
            {
                _logger.LogWarning("Apiary not found for deletion: {ApiaryId}", id);
                throw new ApiaryNotFoundException();
            }

            if (apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized delete attempt on apiary {ApiaryId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            await _apiaryRepo.DeleteAsync(apiary);
            await _apiaryRepo.SaveChangesAsync();

            _logger.LogInformation("Apiary deleted: {ApiaryId}", id);
        }
    }
}
