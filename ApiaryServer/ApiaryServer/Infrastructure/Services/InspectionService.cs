using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Exceptions;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Data;
using Microsoft.Extensions.Logging;
using Microsoft.EntityFrameworkCore;
using System.Globalization;
using System.Text.Json;
using System.Text;

namespace ApiaryServer.Infrastructure.Services
{
    public class InspectionService : IInspectionService
    {
        private readonly IInspectionRepository _inspectionRepo;
        private readonly IInspectionPhotoRepository _photoRepo;
        private readonly IHiveRepository _hiveRepo;
        private readonly IApiaryRepository _apiaryRepo;
        private readonly AppDbContext _context;
        private readonly ILogger<InspectionService> _logger;

        public InspectionService(
            IInspectionRepository inspectionRepo,
            IInspectionPhotoRepository photoRepo,
            IHiveRepository hiveRepo,
            IApiaryRepository apiaryRepo,
            AppDbContext context,
            ILogger<InspectionService> logger)
        {
            _inspectionRepo = inspectionRepo;
            _photoRepo = photoRepo;
            _hiveRepo = hiveRepo;
            _apiaryRepo = apiaryRepo;
            _context = context;
            _logger = logger;
        }

        public async Task<IEnumerable<InspectionResponse>> GetAllInspectionsAsync(Guid userId)
        {
            var inspections = await _inspectionRepo.GetAllByUserIdAsync(userId);

            return inspections.Select(i => MapToResponse(i, i.Hive.Name, i.Apiary.Name, i.Photos.Count));
        }

        public async Task<IEnumerable<InspectionResponse>> GetInspectionsByApiaryIdAsync(Guid apiaryId, Guid userId)
        {
            if (!await _apiaryRepo.IsOwnedByUserAsync(apiaryId, userId))
            {
                _logger.LogWarning("Unauthorized access to apiary inspections {ApiaryId} by user {UserId}", apiaryId, userId);
                throw new System.UnauthorizedAccessException();
            }

            var inspections = await _inspectionRepo.GetByApiaryIdAsync(apiaryId);

            return inspections.Select(i => MapToResponse(i, i.Hive.Name, i.Apiary.Name, i.Photos.Count));
        }

        public async Task<IEnumerable<InspectionResponse>> GetInspectionsByHiveIdAsync(Guid hiveId, Guid userId)
        {
            if (!await _hiveRepo.IsOwnedByUserAsync(hiveId, userId))
            {
                _logger.LogWarning("Unauthorized access to hive inspections {HiveId} by user {UserId}", hiveId, userId);
                throw new System.UnauthorizedAccessException();
            }

            var inspections = await _inspectionRepo.GetByHiveIdAsync(hiveId);

            return inspections.Select(i => MapToResponse(i, i.Hive.Name, i.Apiary.Name, i.Photos.Count));
        }

        public async Task<InspectionDetailResponse> GetInspectionByIdAsync(Guid id, Guid userId)
        {
            var inspection = await _inspectionRepo.GetByIdWithPhotosAsync(id);

            if (inspection == null)
            {
                _logger.LogWarning("Inspection not found: {InspectionId}", id);
                throw new InspectionNotFoundException();
            }

            if (inspection.Hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized access to inspection {InspectionId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            return new InspectionDetailResponse(
                inspection.Id,
                inspection.HiveId,
                inspection.Hive.Name,
                inspection.ApiaryId,
                inspection.Apiary.Name,
                inspection.InspectionDate,
                inspection.Temperature,
                inspection.FramesCount,
                inspection.BroodFrames,
                inspection.HoneyFrames,
                inspection.PollenFrames,
                inspection.QueenSeen,
                inspection.EggsSeen,
                inspection.LarvaeSeen,
                inspection.Photos.Select(p => new InspectionPhotoResponse(
                    p.Id,
                    p.InspectionId,
                    p.PhotoUrl,
                    p.Description,
                    p.CreatedAt
                )).ToList(),
                inspection.CreatedAt,
                inspection.UpdatedAt,
                inspection.QueenCellsSeen,
                inspection.QueenCellsWithEggs,
                inspection.BeardingAtEntrance,
                inspection.SpaceNeeded,
                inspection.BroodPattern,
                inspection.HoneyCappingPercent,
                inspection.FeedingGiven,
                inspection.WaterAvailable,
                inspection.MoistureOrMold,
                inspection.DeadBeesAtEntrance,
                inspection.UnusualBehavior,
                inspection.Temperament,
                inspection.OldCombsToReplace,
                inspection.Notes
            );
        }

        public async Task<InspectionResponse> CreateInspectionAsync(CreateInspectionRequest dto, Guid userId)
        {
            // Verify hive exists and user owns it
            var hive = await _hiveRepo.GetByIdAsync(dto.HiveId);
            if (hive == null)
            {
                _logger.LogWarning("Hive not found: {HiveId}", dto.HiveId);
                throw new HiveNotFoundException();
            }

            if (hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized inspection creation on hive {HiveId} by user {UserId}", dto.HiveId, userId);
                throw new System.UnauthorizedAccessException();
            }

            var inspection = new Inspection
            {
                HiveId = dto.HiveId,
                ApiaryId = hive.ApiaryId,
                InspectionDate = dto.InspectionDate,
                Temperature = dto.Temperature,
                FramesCount = dto.FramesCount,
                BroodFrames = dto.BroodFrames,
                HoneyFrames = dto.HoneyFrames,
                PollenFrames = dto.PollenFrames,
                QueenSeen = dto.QueenSeen,
                EggsSeen = dto.EggsSeen,
                LarvaeSeen = dto.LarvaeSeen,
                QueenCellsSeen = dto.QueenCellsSeen,
                QueenCellsWithEggs = dto.QueenCellsWithEggs,
                BeardingAtEntrance = dto.BeardingAtEntrance,
                SpaceNeeded = dto.SpaceNeeded,
                BroodPattern = dto.BroodPattern,
                HoneyCappingPercent = dto.HoneyCappingPercent,
                FeedingGiven = dto.FeedingGiven,
                WaterAvailable = dto.WaterAvailable,
                MoistureOrMold = dto.MoistureOrMold,
                DeadBeesAtEntrance = dto.DeadBeesAtEntrance,
                UnusualBehavior = dto.UnusualBehavior,
                Temperament = dto.Temperament,
                OldCombsToReplace = dto.OldCombsToReplace,
                Notes = dto.Notes
            };

            await _inspectionRepo.AddAsync(inspection);
            await _inspectionRepo.SaveChangesAsync();
            await RefreshHiveLastInspectionAsync(hive.Id);
            await _context.SaveChangesAsync();

            _logger.LogInformation("Inspection created: {InspectionId} for hive {HiveId}", inspection.Id, dto.HiveId);

            return MapToResponse(inspection, hive.Name, hive.Apiary.Name, 0);
        }

        public async Task<InspectionResponse> UpdateInspectionAsync(Guid id, UpdateInspectionRequest dto, Guid userId)
        {
            var inspection = await _inspectionRepo.GetByIdAsync(id);

            if (inspection == null)
            {
                _logger.LogWarning("Inspection not found for update: {InspectionId}", id);
                throw new InspectionNotFoundException();
            }

            if (inspection.Hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized update attempt on inspection {InspectionId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            inspection.InspectionDate = dto.InspectionDate;
            inspection.Temperature = dto.Temperature;
            inspection.FramesCount = dto.FramesCount;
            inspection.BroodFrames = dto.BroodFrames;
            inspection.HoneyFrames = dto.HoneyFrames;
            inspection.PollenFrames = dto.PollenFrames;
            inspection.QueenSeen = dto.QueenSeen;
            inspection.EggsSeen = dto.EggsSeen;
            inspection.LarvaeSeen = dto.LarvaeSeen;
            inspection.QueenCellsSeen = dto.QueenCellsSeen;
            inspection.QueenCellsWithEggs = dto.QueenCellsWithEggs;
            inspection.BeardingAtEntrance = dto.BeardingAtEntrance;
            inspection.SpaceNeeded = dto.SpaceNeeded;
            inspection.BroodPattern = dto.BroodPattern;
            inspection.HoneyCappingPercent = dto.HoneyCappingPercent;
            inspection.FeedingGiven = dto.FeedingGiven;
            inspection.WaterAvailable = dto.WaterAvailable;
            inspection.MoistureOrMold = dto.MoistureOrMold;
            inspection.DeadBeesAtEntrance = dto.DeadBeesAtEntrance;
            inspection.UnusualBehavior = dto.UnusualBehavior;
            inspection.Temperament = dto.Temperament;
            inspection.OldCombsToReplace = dto.OldCombsToReplace;
            inspection.Notes = dto.Notes;

            await _inspectionRepo.UpdateAsync(inspection);
            await _inspectionRepo.SaveChangesAsync();
            await RefreshHiveLastInspectionAsync(inspection.HiveId);
            await _context.SaveChangesAsync();

            _logger.LogInformation("Inspection updated: {InspectionId}", id);

            var photos = await _photoRepo.GetByInspectionIdAsync(id);

            return MapToResponse(inspection, inspection.Hive.Name, inspection.Hive.Apiary.Name, photos.Count());
        }

        public async Task DeleteInspectionAsync(Guid id, Guid userId)
        {
            var inspection = await _inspectionRepo.GetByIdAsync(id);

            if (inspection == null)
            {
                _logger.LogWarning("Inspection not found for deletion: {InspectionId}", id);
                throw new InspectionNotFoundException();
            }

            if (inspection.Hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized delete attempt on inspection {InspectionId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            var hiveId = inspection.HiveId;

            await _inspectionRepo.DeleteAsync(inspection);
            await _inspectionRepo.SaveChangesAsync();
            await RefreshHiveLastInspectionAsync(hiveId);
            await _context.SaveChangesAsync();

            _logger.LogInformation("Inspection deleted: {InspectionId}", id);
        }

        // Photo Management Methods
        public async Task<InspectionPhotoResponse> AddPhotoAsync(Guid inspectionId, AddInspectionPhotoRequest dto, Guid userId)
        {
            var inspection = await _inspectionRepo.GetByIdAsync(inspectionId);

            if (inspection == null)
            {
                _logger.LogWarning("Inspection not found: {InspectionId}", inspectionId);
                throw new InspectionNotFoundException();
            }

            if (inspection.Hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized photo addition to inspection {InspectionId} by user {UserId}", inspectionId, userId);
                throw new System.UnauthorizedAccessException();
            }

            var photo = new InspectionPhoto
            {
                InspectionId = inspectionId,
                PhotoUrl = dto.PhotoUrl,
                Description = dto.Description
            };

            await _photoRepo.AddAsync(photo);
            await _photoRepo.SaveChangesAsync();

            _logger.LogInformation("Photo added to inspection {InspectionId}: {PhotoId}", inspectionId, photo.Id);

            return new InspectionPhotoResponse(
                photo.Id,
                photo.InspectionId,
                photo.PhotoUrl,
                photo.Description,
                photo.CreatedAt
            );
        }

        public async Task<InspectionPhotoResponse> UpdatePhotoAsync(Guid photoId, UpdateInspectionPhotoRequest dto, Guid userId)
        {
            var photo = await _photoRepo.GetByIdAsync(photoId);

            if (photo == null)
            {
                _logger.LogWarning("Photo not found for update: {PhotoId}", photoId);
                throw new InspectionPhotoNotFoundException();
            }

            if (photo.Inspection.Hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized photo update attempt {PhotoId} by user {UserId}", photoId, userId);
                throw new System.UnauthorizedAccessException();
            }

            photo.Description = dto.Description;

            await _photoRepo.UpdateAsync(photo);
            await _photoRepo.SaveChangesAsync();

            _logger.LogInformation("Photo updated: {PhotoId}", photoId);

            return new InspectionPhotoResponse(
                photo.Id,
                photo.InspectionId,
                photo.PhotoUrl,
                photo.Description,
                photo.CreatedAt
            );
        }

        public async Task DeletePhotoAsync(Guid photoId, Guid userId)
        {
            var photo = await _photoRepo.GetByIdAsync(photoId);

            if (photo == null)
            {
                _logger.LogWarning("Photo not found for deletion: {PhotoId}", photoId);
                throw new InspectionPhotoNotFoundException();
            }

            if (photo.Inspection.Hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized photo delete attempt {PhotoId} by user {UserId}", photoId, userId);
                throw new System.UnauthorizedAccessException();
            }

            await _photoRepo.DeleteAsync(photo);
            await _photoRepo.SaveChangesAsync();

            _logger.LogInformation("Photo deleted: {PhotoId}", photoId);
        }

        public async Task<InspectionAiAnalysisResponse> SaveAiAnalysisAsync(
            Guid inspectionId,
            SaveInspectionAiAnalysisRequest dto,
            Guid userId)
        {
            var inspection = await _inspectionRepo.GetByIdAsync(inspectionId);

            if (inspection == null)
            {
                _logger.LogWarning("Inspection not found for AI analysis: {InspectionId}", inspectionId);
                throw new InspectionNotFoundException();
            }

            if (inspection.Hive.Apiary.UserId != userId)
            {
                _logger.LogWarning("Unauthorized AI analysis save on inspection {InspectionId} by user {UserId}", inspectionId, userId);
                throw new System.UnauthorizedAccessException();
            }

            var normalized = NormalizeCounts(dto.Results ?? new Dictionary<string, int>());
            var capped = normalized.GetValueOrDefault(CellCategory.CappedBrood);
            var larvae = normalized.GetValueOrDefault(CellCategory.Larvae);
            var eggs = normalized.GetValueOrDefault(CellCategory.Eggs);
            var honey = normalized.GetValueOrDefault(CellCategory.Honey);
            var pollen = normalized.GetValueOrDefault(CellCategory.Pollen);
            var empty = normalized.GetValueOrDefault(CellCategory.Empty);
            var other = normalized.GetValueOrDefault(CellCategory.Other);
            var total = normalized.Values.Sum();
            var brood = capped + larvae + eggs;
            var stores = honey + pollen;
            var nonEmpty = total - empty;

            var analysis = new InspectionAiAnalysis
            {
                InspectionId = inspection.Id,
                HiveId = inspection.HiveId,
                ApiaryId = inspection.ApiaryId,
                Status = string.IsNullOrWhiteSpace(dto.Status) ? "success" : dto.Status!.Trim(),
                Message = dto.Message,
                RawResultsJson = JsonSerializer.Serialize(dto.Results ?? new Dictionary<string, int>()),
                CellDetectionsJson = JsonSerializer.Serialize(dto.CellDetections ?? Array.Empty<CellDetectionDto>()),
                TotalCells = total,
                CappedBroodCells = capped,
                LarvaeCells = larvae,
                EggsCells = eggs,
                HoneyCells = honey,
                PollenCells = pollen,
                EmptyCells = empty,
                OtherCells = other,
                BroodCells = brood,
                StoresCells = stores,
                BroodDensity = SafeRatio(brood, nonEmpty),
                LarvaeToCappedRatio = SafeRatio(larvae, capped),
                StoresRatio = SafeRatio(stores, total)
            };

            await _context.InspectionAiAnalyses.AddAsync(analysis);
            await _context.SaveChangesAsync();

            _logger.LogInformation("AI analysis saved: {AnalysisId} for inspection {InspectionId}", analysis.Id, inspectionId);

            return ToAiAnalysisResponse(analysis, inspection.InspectionDate);
        }

        public async Task<IEnumerable<InspectionAiAnalysisResponse>> GetAiAnalysesByHiveIdAsync(Guid hiveId, Guid userId)
        {
            if (!await _hiveRepo.IsOwnedByUserAsync(hiveId, userId))
            {
                _logger.LogWarning("Unauthorized access to hive AI analyses {HiveId} by user {UserId}", hiveId, userId);
                throw new System.UnauthorizedAccessException();
            }

            var analyses = await _context.InspectionAiAnalyses
                .Include(a => a.Inspection)
                .Where(a => a.HiveId == hiveId)
                .OrderBy(a => a.Inspection.InspectionDate)
                .ToListAsync();

            return analyses.Select(ToAiAnalysisResponse);
        }

        // Single source of truth for the InspectionResponse projection. Callers pass
        // the hive/apiary names and photo count explicitly because those come from
        // different navigation paths depending on the query.
        private static InspectionResponse MapToResponse(Inspection i, string hiveName, string apiaryName, int photoCount)
        {
            return new InspectionResponse(
                i.Id,
                i.HiveId,
                hiveName,
                i.ApiaryId,
                apiaryName,
                i.InspectionDate,
                i.Temperature,
                i.FramesCount,
                i.BroodFrames,
                i.HoneyFrames,
                i.PollenFrames,
                i.QueenSeen,
                i.EggsSeen,
                i.LarvaeSeen,
                photoCount,
                i.CreatedAt,
                i.UpdatedAt,
                i.QueenCellsSeen,
                i.QueenCellsWithEggs,
                i.BeardingAtEntrance,
                i.SpaceNeeded,
                i.BroodPattern,
                i.HoneyCappingPercent,
                i.FeedingGiven,
                i.WaterAvailable,
                i.MoistureOrMold,
                i.DeadBeesAtEntrance,
                i.UnusualBehavior,
                i.Temperament,
                i.OldCombsToReplace,
                i.Notes
            );
        }

        private enum CellCategory
        {
            CappedBrood,
            Larvae,
            Eggs,
            Honey,
            Pollen,
            Empty,
            Other
        }

        private static Dictionary<CellCategory, int> NormalizeCounts(Dictionary<string, int> raw)
        {
            var counts = Enum.GetValues<CellCategory>().ToDictionary(category => category, _ => 0);

            foreach (var (key, value) in raw)
            {
                if (value <= 0)
                {
                    continue;
                }

                counts[CategorizeCellKey(key)] += value;
            }

            return counts;
        }

        private static CellCategory CategorizeCellKey(string key)
        {
            var normalized = RemoveDiacritics(key).Trim().ToLowerInvariant();

            if ((normalized.Contains("capped") && normalized.Contains("brood")) ||
                normalized is "capped" or "capped_brood" or "cappedbrood" ||
                normalized.Contains("capac") ||
                normalized.Contains("operculat"))
            {
                return CellCategory.CappedBrood;
            }

            if (normalized.StartsWith("larva") ||
                normalized.StartsWith("larvae") ||
                normalized.Contains("larv") ||
                (normalized.Contains("puiet") && normalized.Contains("deschis")))
            {
                return CellCategory.Larvae;
            }

            if (normalized is "egg" or "eggs" or "ou" ||
                normalized.Contains("oua"))
            {
                return CellCategory.Eggs;
            }

            if (normalized.Contains("honey") ||
                normalized.Contains("nectar") ||
                normalized.Contains("miere"))
            {
                return CellCategory.Honey;
            }

            if (normalized.Contains("pollen") ||
                normalized.Contains("polen"))
            {
                return CellCategory.Pollen;
            }

            if (normalized.Contains("empty") ||
                normalized.Contains("vacant") ||
                normalized.Contains("free") ||
                normalized.Contains("goala") ||
                normalized.Contains("goale"))
            {
                return CellCategory.Empty;
            }

            return CellCategory.Other;
        }

        private static string RemoveDiacritics(string value)
        {
            var normalized = value.Normalize(NormalizationForm.FormD);
            var builder = new StringBuilder(normalized.Length);
            foreach (var c in normalized)
            {
                if (CharUnicodeInfo.GetUnicodeCategory(c) != UnicodeCategory.NonSpacingMark)
                {
                    builder.Append(c);
                }
            }
            return builder.ToString().Normalize(NormalizationForm.FormC);
        }

        private static decimal? SafeRatio(int numerator, int denominator)
        {
            if (denominator <= 0)
            {
                return null;
            }

            return Math.Round((decimal)numerator / denominator, 4);
        }

        private async Task RefreshHiveLastInspectionAsync(Guid hiveId)
        {
            var hive = await _context.Hives.FindAsync(hiveId);
            if (hive == null)
            {
                return;
            }

            var latestInspectionDate = await _context.Inspections
                .Where(i => i.HiveId == hiveId)
                .OrderByDescending(i => i.InspectionDate)
                .Select(i => (DateTimeOffset?)i.InspectionDate)
                .FirstOrDefaultAsync();

            hive.UltimaInspectie = latestInspectionDate?.ToString("O");
            hive.UpdatedAt = DateTimeOffset.UtcNow;
        }

        private static InspectionAiAnalysisResponse ToAiAnalysisResponse(InspectionAiAnalysis analysis)
        {
            return ToAiAnalysisResponse(analysis, analysis.Inspection.InspectionDate);
        }

        private static InspectionAiAnalysisResponse ToAiAnalysisResponse(
            InspectionAiAnalysis analysis,
            DateTimeOffset inspectionDate)
        {
            var raw = JsonSerializer.Deserialize<Dictionary<string, int>>(analysis.RawResultsJson)
                ?? new Dictionary<string, int>();
            var cellDetections = DeserializeCellDetections(analysis.CellDetectionsJson);

            return new InspectionAiAnalysisResponse(
                analysis.Id,
                analysis.InspectionId,
                analysis.HiveId,
                analysis.ApiaryId,
                inspectionDate,
                analysis.Status,
                raw,
                analysis.Message,
                analysis.TotalCells,
                analysis.CappedBroodCells,
                analysis.LarvaeCells,
                analysis.EggsCells,
                analysis.HoneyCells,
                analysis.PollenCells,
                analysis.EmptyCells,
                analysis.OtherCells,
                analysis.BroodCells,
                analysis.StoresCells,
                analysis.BroodDensity,
                analysis.LarvaeToCappedRatio,
                analysis.StoresRatio,
                analysis.CreatedAt,
                cellDetections
            );
        }

        private static IReadOnlyList<CellDetectionDto> DeserializeCellDetections(string? json)
        {
            if (string.IsNullOrWhiteSpace(json))
            {
                return Array.Empty<CellDetectionDto>();
            }

            try
            {
                var detections = JsonSerializer.Deserialize<List<CellDetectionDto>>(json);
                return detections is null ? Array.Empty<CellDetectionDto>() : detections;
            }
            catch (JsonException)
            {
                return Array.Empty<CellDetectionDto>();
            }
        }
    }
}
