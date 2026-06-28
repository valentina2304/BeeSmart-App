using ApiaryServer.Application.DTOs;
using ApiaryServer.Domain.Entities;

namespace ApiaryServer.Application.Interfaces;

public interface IAuthService
{
    Task<IEnumerable<User>> GetAllUsersAsync();
    Task<UserProfileResponse> GetUserProfileAsync(Guid userId);
    Task<UserProfileResponse> UpdateUserProfileAsync(Guid userId, UpdateProfileRequest dto);
    Task RegisterAsync(RegisterRequest dto);
    Task<TokenResponse> LoginAsync(LoginRequest dto);
    Task<TokenResponse> RefreshAsync(RefreshRequest dto);
    Task LogoutAsync(RefreshRequest dto);
    Task ResendConfirmationEmailAsync(ResendConfirmationRequest dto);
    Task ConfirmEmailAsync(ConfirmEmailRequest dto);
    Task ForgotPasswordAsync(ForgotPasswordRequest dto);
    Task ResetPasswordAsync(ResetPasswordRequest dto);
}