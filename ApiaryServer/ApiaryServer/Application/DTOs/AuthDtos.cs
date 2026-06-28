using System.ComponentModel.DataAnnotations;

namespace ApiaryServer.Application.DTOs
{
    public record RegisterRequest(
        [EmailAddress][Required] string Email,
        [MinLength(8)][Required] string Password,
        [MaxLength(100)] string? FirstName,
        [MaxLength(100)] string? LastName,
        [Phone] string? PhoneNumber,
        DateTimeOffset? BirthDate,
        string? DeviceInfo
    );

    public record LoginRequest(
        [EmailAddress][Required] string Email,
        [Required] string Password,
        string? DeviceInfo
    );

    public record TokenResponse(string AccessToken, string RefreshToken, int ExpiresIn);

    public record RefreshRequest([Required] string RefreshToken);

    public record UserProfileResponse(
        Guid Id,
        string Email,
        string? FirstName,
        string? LastName,
        string? PhoneNumber,
        DateTimeOffset? BirthDate,
        bool EmailConfirmed,
        DateTimeOffset CreatedAt,
        DateTimeOffset? UpdatedAt
    );

    public record UpdateProfileRequest(
        [MaxLength(100)] string? FirstName,
        [MaxLength(100)] string? LastName,
        [Phone] string? PhoneNumber,
        DateTimeOffset? BirthDate
    );

    public record ResendConfirmationRequest([EmailAddress][Required] string Email);

    public record ConfirmEmailRequest(
        [Required] string Token,
        [EmailAddress][Required] string Email
    );

    public record ForgotPasswordRequest([EmailAddress][Required] string Email);

    public record ResetPasswordRequest(
        [Required] string Token,
        [EmailAddress][Required] string Email,
        [MinLength(8)][Required] string NewPassword
    );
}