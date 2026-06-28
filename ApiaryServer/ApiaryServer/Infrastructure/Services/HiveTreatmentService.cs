using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Exceptions;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Domain.Entities;
using Microsoft.Extensions.Logging;

namespace ApiaryServer.Infrastructure.Services
{
    public class HiveTreatmentService : IHiveTreatmentService
    {
        private readonly IHiveTreatmentRepository _treatmentRepo;
        private readonly IApiaryRepository _apiaryRepo;
        private readonly IHiveRepository _hiveRepo;
        private readonly ILogger<HiveTreatmentService> _logger;

        public HiveTreatmentService(
            IHiveTreatmentRepository treatmentRepo,
            IApiaryRepository apiaryRepo,
            IHiveRepository hiveRepo,
            ILogger<HiveTreatmentService> logger)
        {
            _treatmentRepo = treatmentRepo;
            _apiaryRepo = apiaryRepo;
            _hiveRepo = hiveRepo;
            _logger = logger;
        }

        public async Task<IEnumerable<TreatmentResponse>> GetAllTreatmentsAsync(Guid userId)
        {
            var treatments = await _treatmentRepo.GetAllByUserIdAsync(userId);
            return treatments.Select(MapToResponse);
        }

        public async Task<IEnumerable<TreatmentResponse>> GetTreatmentsByApiaryIdAsync(Guid apiaryId, Guid userId)
        {
            if (!await _apiaryRepo.IsOwnedByUserAsync(apiaryId, userId))
            {
                _logger.LogWarning("Unauthorized access to apiary treatments {ApiaryId} by user {UserId}", apiaryId, userId);
                throw new System.UnauthorizedAccessException();
            }

            var treatments = await _treatmentRepo.GetByApiaryIdAsync(apiaryId);
            return treatments.Select(MapToResponse);
        }

        public async Task<IEnumerable<TreatmentResponse>> GetTreatmentsByHiveIdAsync(Guid hiveId, Guid userId)
        {
            if (!await _hiveRepo.IsOwnedByUserAsync(hiveId, userId))
            {
                _logger.LogWarning("Unauthorized access to hive treatments {HiveId} by user {UserId}", hiveId, userId);
                throw new System.UnauthorizedAccessException();
            }

            var treatments = await _treatmentRepo.GetByHiveIdAsync(hiveId);
            return treatments.Select(MapToResponse);
        }

        public async Task<TreatmentResponse> GetTreatmentByIdAsync(Guid id, Guid userId)
        {
            var treatment = await _treatmentRepo.GetByIdAsync(id);

            if (treatment == null)
            {
                _logger.LogWarning("Treatment not found: {TreatmentId}", id);
                throw new TreatmentNotFoundException();
            }

            if (treatment.Hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized access to treatment {TreatmentId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            return MapToResponse(treatment);
        }

        public async Task<TreatmentResponse> CreateTreatmentAsync(CreateTreatmentRequest dto, Guid userId)
        {
            var hive = await _hiveRepo.GetByIdAsync(dto.HiveId);

            if (hive == null)
            {
                _logger.LogWarning("Hive not found: {HiveId}", dto.HiveId);
                throw new HiveNotFoundException();
            }

            if (hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized treatment creation on hive {HiveId} by user {UserId}", dto.HiveId, userId);
                throw new System.UnauthorizedAccessException();
            }

            var treatment = new HiveTreatment
            {
                HiveId = dto.HiveId,
                ApiaryId = hive.ApiaryId,
                TreatmentDate = dto.TreatmentDate,
                Type = dto.Type,
                ProductName = dto.ProductName,
                Substance = dto.Substance,
                Dosage = dto.Dosage,
                Notes = dto.Notes,
                NextTreatmentDate = dto.NextTreatmentDate
            };

            await _treatmentRepo.AddAsync(treatment);
            await _treatmentRepo.SaveChangesAsync();

            _logger.LogInformation("Treatment created: {TreatmentId} for hive {HiveId}", treatment.Id, dto.HiveId);

            return new TreatmentResponse(
                treatment.Id,
                treatment.HiveId,
                hive.Name,
                treatment.ApiaryId,
                hive.Apiary.Name,
                treatment.TreatmentDate,
                treatment.Type,
                treatment.ProductName,
                treatment.Substance,
                treatment.Dosage,
                treatment.Notes,
                treatment.NextTreatmentDate,
                treatment.CreatedAt,
                treatment.UpdatedAt
            );
        }

        public async Task<TreatmentResponse> UpdateTreatmentAsync(Guid id, UpdateTreatmentRequest dto, Guid userId)
        {
            var treatment = await _treatmentRepo.GetByIdAsync(id);

            if (treatment == null)
            {
                _logger.LogWarning("Treatment not found for update: {TreatmentId}", id);
                throw new TreatmentNotFoundException();
            }

            if (treatment.Hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized treatment update attempt {TreatmentId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            treatment.TreatmentDate = dto.TreatmentDate;
            treatment.Type = dto.Type;
            treatment.ProductName = dto.ProductName;
            treatment.Substance = dto.Substance;
            treatment.Dosage = dto.Dosage;
            treatment.Notes = dto.Notes;
            treatment.NextTreatmentDate = dto.NextTreatmentDate;

            await _treatmentRepo.UpdateAsync(treatment);
            await _treatmentRepo.SaveChangesAsync();

            _logger.LogInformation("Treatment updated: {TreatmentId}", id);

            return MapToResponse(treatment);
        }

        public async Task DeleteTreatmentAsync(Guid id, Guid userId)
        {
            var treatment = await _treatmentRepo.GetByIdAsync(id);

            if (treatment == null)
            {
                _logger.LogWarning("Treatment not found for deletion: {TreatmentId}", id);
                throw new TreatmentNotFoundException();
            }

            if (treatment.Hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized treatment delete attempt {TreatmentId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            await _treatmentRepo.DeleteAsync(treatment);
            await _treatmentRepo.SaveChangesAsync();

            _logger.LogInformation("Treatment deleted: {TreatmentId}", id);
        }

        private static TreatmentResponse MapToResponse(HiveTreatment treatment)
        {
            return new TreatmentResponse(
                treatment.Id,
                treatment.HiveId,
                treatment.Hive.Name,
                treatment.ApiaryId,
                treatment.Apiary.Name,
                treatment.TreatmentDate,
                treatment.Type,
                treatment.ProductName,
                treatment.Substance,
                treatment.Dosage,
                treatment.Notes,
                treatment.NextTreatmentDate,
                treatment.CreatedAt,
                treatment.UpdatedAt
            );
        }
    }
}
