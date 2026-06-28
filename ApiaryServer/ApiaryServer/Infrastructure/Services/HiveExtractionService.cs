using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Exceptions;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Domain.Entities;
using Microsoft.Extensions.Logging;

namespace ApiaryServer.Infrastructure.Services
{
    public class HiveExtractionService : IHiveExtractionService
    {
        private readonly IHiveExtractionRepository _extractionRepo;
        private readonly IApiaryRepository _apiaryRepo;
        private readonly IHiveRepository _hiveRepo;
        private readonly ILogger<HiveExtractionService> _logger;

        public HiveExtractionService(
            IHiveExtractionRepository extractionRepo,
            IApiaryRepository apiaryRepo,
            IHiveRepository hiveRepo,
            ILogger<HiveExtractionService> logger)
        {
            _extractionRepo = extractionRepo;
            _apiaryRepo = apiaryRepo;
            _hiveRepo = hiveRepo;
            _logger = logger;
        }

        public async Task<IEnumerable<ExtractionResponse>> GetAllExtractionsAsync(Guid userId)
        {
            var extractions = await _extractionRepo.GetAllByUserIdAsync(userId);
            return extractions.Select(MapToResponse);
        }

        public async Task<IEnumerable<ExtractionResponse>> GetExtractionsByApiaryIdAsync(Guid apiaryId, Guid userId)
        {
            if (!await _apiaryRepo.IsOwnedByUserAsync(apiaryId, userId))
            {
                _logger.LogWarning("Unauthorized access to apiary extractions {ApiaryId} by user {UserId}", apiaryId, userId);
                throw new System.UnauthorizedAccessException();
            }

            var extractions = await _extractionRepo.GetByApiaryIdAsync(apiaryId);
            return extractions.Select(MapToResponse);
        }

        public async Task<IEnumerable<ExtractionResponse>> GetExtractionsByHiveIdAsync(Guid hiveId, Guid userId)
        {
            if (!await _hiveRepo.IsOwnedByUserAsync(hiveId, userId))
            {
                _logger.LogWarning("Unauthorized access to hive extractions {HiveId} by user {UserId}", hiveId, userId);
                throw new System.UnauthorizedAccessException();
            }

            var extractions = await _extractionRepo.GetByHiveIdAsync(hiveId);
            return extractions.Select(MapToResponse);
        }

        public async Task<ExtractionResponse> GetExtractionByIdAsync(Guid id, Guid userId)
        {
            var extraction = await _extractionRepo.GetByIdAsync(id);

            if (extraction == null)
            {
                _logger.LogWarning("Extraction not found: {ExtractionId}", id);
                throw new ExtractionNotFoundException();
            }

            if (extraction.Hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized access to extraction {ExtractionId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            return MapToResponse(extraction);
        }

        public async Task<ExtractionResponse> CreateExtractionAsync(CreateExtractionRequest dto, Guid userId)
        {
            var hive = await _hiveRepo.GetByIdAsync(dto.HiveId);

            if (hive == null)
            {
                _logger.LogWarning("Hive not found: {HiveId}", dto.HiveId);
                throw new HiveNotFoundException();
            }

            if (hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized extraction creation on hive {HiveId} by user {UserId}", dto.HiveId, userId);
                throw new System.UnauthorizedAccessException();
            }

            var extraction = new HiveExtraction
            {
                HiveId = dto.HiveId,
                ApiaryId = hive.ApiaryId,
                ExtractionDate = dto.ExtractionDate,
                Type = dto.Type,
                Quantity = dto.Quantity,
                Unit = dto.Unit,
                Notes = dto.Notes
            };

            await _extractionRepo.AddAsync(extraction);
            await _extractionRepo.SaveChangesAsync();

            _logger.LogInformation("Extraction created: {ExtractionId} for hive {HiveId}", extraction.Id, dto.HiveId);

            return new ExtractionResponse(
                extraction.Id,
                extraction.HiveId,
                hive.Name,
                extraction.ApiaryId,
                hive.Apiary.Name,
                extraction.ExtractionDate,
                extraction.Type,
                extraction.Quantity,
                extraction.Unit,
                extraction.Notes,
                extraction.CreatedAt,
                extraction.UpdatedAt
            );
        }

        public async Task<ExtractionResponse> UpdateExtractionAsync(Guid id, UpdateExtractionRequest dto, Guid userId)
        {
            var extraction = await _extractionRepo.GetByIdAsync(id);

            if (extraction == null)
            {
                _logger.LogWarning("Extraction not found for update: {ExtractionId}", id);
                throw new ExtractionNotFoundException();
            }

            if (extraction.Hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized extraction update attempt {ExtractionId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            extraction.ExtractionDate = dto.ExtractionDate;
            extraction.Type = dto.Type;
            extraction.Quantity = dto.Quantity;
            extraction.Unit = dto.Unit;
            extraction.Notes = dto.Notes;

            await _extractionRepo.UpdateAsync(extraction);
            await _extractionRepo.SaveChangesAsync();

            _logger.LogInformation("Extraction updated: {ExtractionId}", id);

            return MapToResponse(extraction);
        }

        public async Task DeleteExtractionAsync(Guid id, Guid userId)
        {
            var extraction = await _extractionRepo.GetByIdAsync(id);

            if (extraction == null)
            {
                _logger.LogWarning("Extraction not found for deletion: {ExtractionId}", id);
                throw new ExtractionNotFoundException();
            }

            if (extraction.Hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized extraction delete attempt {ExtractionId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            await _extractionRepo.DeleteAsync(extraction);
            await _extractionRepo.SaveChangesAsync();

            _logger.LogInformation("Extraction deleted: {ExtractionId}", id);
        }

        private static ExtractionResponse MapToResponse(HiveExtraction extraction)
        {
            return new ExtractionResponse(
                extraction.Id,
                extraction.HiveId,
                extraction.Hive.Name,
                extraction.ApiaryId,
                extraction.Apiary.Name,
                extraction.ExtractionDate,
                extraction.Type,
                extraction.Quantity,
                extraction.Unit,
                extraction.Notes,
                extraction.CreatedAt,
                extraction.UpdatedAt
            );
        }
    }
}
