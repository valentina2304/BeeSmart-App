using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Application.Exceptions;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using System.Text.RegularExpressions;

namespace ApiaryServer.Api.Controllers
{
    [ApiController]
    [Route("inspections")]
    [Authorize]
    public class InspectionController : ApiControllerBase
    {
        private readonly IInspectionService _inspectionService;
        private readonly IAiAnalysisService _aiAnalysisService;
        private const int MaxAiImageBytes = 6 * 1024 * 1024;
        private const int MaxInspectionPhotoBytes = 8 * 1024 * 1024;
        private const int MaxAiCellDetections = 10000;

        public InspectionController(IInspectionService inspectionService, IAiAnalysisService aiAnalysisService)
        {
            _inspectionService = inspectionService;
            _aiAnalysisService = aiAnalysisService;
        }

        /// <summary>
        /// Get all inspections for the current user
        /// </summary>
        [HttpGet]
        public async Task<IActionResult> GetAll()
        {
            try
            {
                var userId = GetUserId();
                var inspections = await _inspectionService.GetAllInspectionsAsync(userId);
                return Ok(inspections);
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Get all inspections for a specific apiary
        /// </summary>
        [HttpGet("apiary/{apiaryId}")]
        public async Task<IActionResult> GetByApiaryId(Guid apiaryId)
        {
            try
            {
                var userId = GetUserId();
                var inspections = await _inspectionService.GetInspectionsByApiaryIdAsync(apiaryId, userId);
                return Ok(inspections);
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Get all inspections for a specific hive
        /// </summary>
        [HttpGet("hive/{hiveId}")]
        public async Task<IActionResult> GetByHiveId(Guid hiveId)
        {
            try
            {
                var userId = GetUserId();
                var inspections = await _inspectionService.GetInspectionsByHiveIdAsync(hiveId, userId);
                return Ok(inspections);
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
            catch (HiveNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Get a specific inspection by ID with photos
        /// </summary>
        [HttpGet("{id}")]
        public async Task<IActionResult> GetById(Guid id)
        {
            try
            {
                var userId = GetUserId();
                var inspection = await _inspectionService.GetInspectionByIdAsync(id, userId);
                return Ok(inspection);
            }
            catch (InspectionNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Create a new inspection
        /// </summary>
        [HttpPost]
        public async Task<IActionResult> Create([FromBody] CreateInspectionRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                var userId = GetUserId();
                var inspection = await _inspectionService.CreateInspectionAsync(dto, userId);
                return CreatedAtAction(nameof(GetById), new { id = inspection.Id }, inspection);
            }
            catch (HiveNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Update an existing inspection
        /// </summary>
        [HttpPut("{id}")]
        public async Task<IActionResult> Update(Guid id, [FromBody] UpdateInspectionRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                var userId = GetUserId();
                var inspection = await _inspectionService.UpdateInspectionAsync(id, dto, userId);
                return Ok(inspection);
            }
            catch (InspectionNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Delete an inspection
        /// </summary>
        [HttpDelete("{id}")]
        public async Task<IActionResult> Delete(Guid id)
        {
            try
            {
                var userId = GetUserId();
                await _inspectionService.DeleteInspectionAsync(id, userId);
                return NoContent();
            }
            catch (InspectionNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Add a photo to an inspection
        /// </summary>
        [HttpPost("{inspectionId}/photos")]
        public async Task<IActionResult> AddPhoto(Guid inspectionId, [FromBody] AddInspectionPhotoRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            var photoValidation = ValidatePhotoPayload(dto.PhotoUrl, MaxInspectionPhotoBytes, required: true, allowRemoteUrl: true);
            if (photoValidation != null)
                return photoValidation;

            try
            {
                var userId = GetUserId();
                var photo = await _inspectionService.AddPhotoAsync(inspectionId, dto, userId);
                return CreatedAtAction(nameof(GetById), new { id = inspectionId }, photo);
            }
            catch (InspectionNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Update a photo description
        /// </summary>
        [HttpPut("photos/{photoId}")]
        public async Task<IActionResult> UpdatePhoto(Guid photoId, [FromBody] UpdateInspectionPhotoRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                var userId = GetUserId();
                var photo = await _inspectionService.UpdatePhotoAsync(photoId, dto, userId);
                return Ok(photo);
            }
            catch (InspectionPhotoNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Delete a photo
        /// </summary>
        [HttpDelete("photos/{photoId}")]
        public async Task<IActionResult> DeletePhoto(Guid photoId)
        {
            try
            {
                var userId = GetUserId();
                await _inspectionService.DeletePhotoAsync(photoId, userId);
                return NoContent();
            }
            catch (InspectionPhotoNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Analyze comb image using DeepBee AI service
        /// </summary>
        [HttpPost("analyze-cells")]
        public async Task<IActionResult> AnalyzeCells([FromBody] AnalyzeCellsRequest dto, CancellationToken cancellationToken)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            if (string.IsNullOrWhiteSpace(dto.ImageBase64))
                return BadRequest(new { error = "imageBase64 is required" });

            var imageValidation = ValidatePhotoPayload(dto.ImageBase64, MaxAiImageBytes, required: true, allowRemoteUrl: false);
            if (imageValidation != null)
                return imageValidation;

            try
            {
                var analysis = await _aiAnalysisService.AnalyzeCellsAsync(dto.ImageBase64, cancellationToken);
                return Ok(analysis);
            }
            catch (HttpRequestException ex)
            {
                return StatusCode(StatusCodes.Status502BadGateway, new { error = "AI service unavailable", details = ex.Message });
            }
            catch (TaskCanceledException ex) when (!cancellationToken.IsCancellationRequested)
            {
                return StatusCode(StatusCodes.Status504GatewayTimeout, new { error = "AI service timed out", details = ex.Message });
            }
            catch (InvalidOperationException ex)
            {
                return StatusCode(StatusCodes.Status502BadGateway, new { error = "Invalid AI response", details = ex.Message });
            }
        }

        /// <summary>
        /// Persist a DeepBee cell analysis for an inspection.
        /// </summary>
        [HttpPost("{inspectionId}/ai-analyses")]
        public async Task<IActionResult> SaveAiAnalysis(Guid inspectionId, [FromBody] SaveInspectionAiAnalysisRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            var status = string.IsNullOrWhiteSpace(dto.Status) ? "success" : dto.Status.Trim();
            var hasPositiveCells = dto.Results?.Values.Any(value => value > 0) == true;
            if (status.Equals("success", StringComparison.OrdinalIgnoreCase) && !hasPositiveCells)
                return BadRequest(new { error = "successful analysis must contain at least one detected cell" });

            if (dto.Results?.Any(entry => entry.Value < 0) == true)
                return BadRequest(new { error = "cell counts cannot be negative" });

            var cellValidation = ValidateCellDetections(dto.CellDetections);
            if (cellValidation != null)
                return cellValidation;

            try
            {
                var userId = GetUserId();
                var analysis = await _inspectionService.SaveAiAnalysisAsync(inspectionId, dto, userId);
                return CreatedAtAction(nameof(GetById), new { id = inspectionId }, analysis);
            }
            catch (InspectionNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Get the saved DeepBee cell-analysis history for a hive.
        /// </summary>
        [HttpGet("hive/{hiveId}/ai-analyses")]
        public async Task<IActionResult> GetAiAnalysesByHiveId(Guid hiveId)
        {
            try
            {
                var userId = GetUserId();
                var analyses = await _inspectionService.GetAiAnalysesByHiveIdAsync(hiveId, userId);
                return Ok(analyses);
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        private IActionResult? ValidatePhotoPayload(string? payload, int maxBytes, bool required, bool allowRemoteUrl)
        {
            if (string.IsNullOrWhiteSpace(payload))
            {
                return required ? BadRequest(new { error = "image payload is required" }) : null;
            }

            var trimmed = payload.Trim();
            if (Uri.TryCreate(trimmed, UriKind.Absolute, out var uri) &&
                (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps))
            {
                return allowRemoteUrl
                    ? null
                    : BadRequest(new { error = "image payload must be a Base64 image, not a remote URL" });
            }

            if (!TryDecodeImagePayload(trimmed, out var bytes, out var error))
            {
                return BadRequest(new { error });
            }

            if (bytes.Length > maxBytes)
            {
                return StatusCode(StatusCodes.Status413PayloadTooLarge, new
                {
                    error = $"image payload exceeds the {maxBytes / (1024 * 1024)} MB limit"
                });
            }

            return LooksLikeImage(bytes)
                ? null
                : BadRequest(new { error = "image payload is not a supported JPEG, PNG or WebP image" });
        }

        private static bool TryDecodeImagePayload(string payload, out byte[] bytes, out string error)
        {
            var base64 = payload;
            var commaIndex = payload.IndexOf(',');
            if (commaIndex >= 0)
            {
                var header = payload[..commaIndex].ToLowerInvariant();
                if (!header.StartsWith("data:image/") || !header.Contains("base64"))
                {
                    bytes = Array.Empty<byte>();
                    error = "image payload must be an image Base64 data URI";
                    return false;
                }
                base64 = payload[(commaIndex + 1)..];
            }

            base64 = Regex.Replace(base64, @"\s+", "");
            try
            {
                bytes = Convert.FromBase64String(base64);
                if (bytes.Length == 0)
                {
                    error = "image payload decoded to an empty file";
                    return false;
                }
                error = string.Empty;
                return true;
            }
            catch (FormatException)
            {
                bytes = Array.Empty<byte>();
                error = "image payload must be valid Base64";
                return false;
            }
        }

        private static bool LooksLikeImage(byte[] bytes)
        {
            if (bytes.Length >= 3 && bytes[0] == 0xFF && bytes[1] == 0xD8 && bytes[2] == 0xFF)
                return true;

            if (bytes.Length >= 8 &&
                bytes[0] == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47 &&
                bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A)
                return true;

            return bytes.Length >= 12 &&
                bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46 &&
                bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50;
        }

        private IActionResult? ValidateCellDetections(IReadOnlyCollection<CellDetectionDto>? detections)
        {
            if (detections == null)
                return null;

            if (detections.Count > MaxAiCellDetections)
                return BadRequest(new { error = $"cell detections cannot exceed {MaxAiCellDetections} entries" });

            if (detections.Any(IsInvalidCellDetection))
            {
                return BadRequest(new { error = "cell detections contain invalid coordinates, class names or confidence values" });
            }

            return null;
        }

        // A detection is rejected when pixel coordinates are negative, normalized
        // values or confidence are non-finite or fall outside [0, 1], or the class
        // name is missing.
        private static bool IsInvalidCellDetection(CellDetectionDto d) =>
            d.X < 0 ||
            d.Y < 0 ||
            d.Radius < 0 ||
            string.IsNullOrWhiteSpace(d.ClassName) ||
            !double.IsFinite(d.NormalizedX) ||
            !double.IsFinite(d.NormalizedY) ||
            !double.IsFinite(d.NormalizedRadius) ||
            !double.IsFinite(d.Confidence) ||
            d.NormalizedX is < 0 or > 1 ||
            d.NormalizedY is < 0 or > 1 ||
            d.NormalizedRadius is < 0 or > 1 ||
            d.Confidence is < 0 or > 1;
    }
}
