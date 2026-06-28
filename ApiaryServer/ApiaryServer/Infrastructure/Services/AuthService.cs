using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Application.Exceptions;
using ApiaryServer.Domain.Entities;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using ApiaryServer.Application.Options;
using System.Security.Cryptography;
using System.Text;

public class AuthService : IAuthService
{
    private readonly IUserRepository _users;
    private readonly IRefreshTokenRepository _refreshRepo;
    private readonly IEmailConfirmationTokenRepository _emailConfirmTokenRepo;
    private readonly IPasswordResetTokenRepository _passwordResetTokenRepo;
    private readonly IJwtService _tokens;
    private readonly IEmailService _emailService;
    private readonly ILogger<AuthService> _logger;
    private readonly JwtOptions _jwtOptions;

    public AuthService(
        IUserRepository users,
        IRefreshTokenRepository refreshRepo,
        IEmailConfirmationTokenRepository emailConfirmTokenRepo,
        IPasswordResetTokenRepository passwordResetTokenRepo,
        IJwtService jwtService,
        IEmailService emailService,
        ILogger<AuthService> logger,
        IOptions<JwtOptions> jwtOptions)
    {
        _users = users;
        _refreshRepo = refreshRepo;
        _emailConfirmTokenRepo = emailConfirmTokenRepo;
        _passwordResetTokenRepo = passwordResetTokenRepo;
        _tokens = jwtService;
        _emailService = emailService;
        _logger = logger;
        _jwtOptions = jwtOptions.Value;
    }

    public async Task<IEnumerable<User>> GetAllUsersAsync()
    {
        return await _users.GetAllAsync();
    }

    public async Task<UserProfileResponse> GetUserProfileAsync(Guid userId)
    {
        var user = await _users.GetByIdAsync(userId);
        if (user == null)
        {
            throw new UserNotFoundException();
        }

        return new UserProfileResponse(
            user.Id,
            user.Email,
            user.FirstName,
            user.LastName,
            user.PhoneNumber,
            user.BirthDate,
            user.EmailConfirmed,
            user.CreatedAt,
            user.UpdatedAt
        );
    }

    public async Task<UserProfileResponse> UpdateUserProfileAsync(Guid userId, UpdateProfileRequest dto)
    {
        var user = await _users.GetByIdAsync(userId);
        if (user == null)
        {
            throw new UserNotFoundException();
        }

        // Update fields (email cannot be changed)
        user.FirstName = dto.FirstName;
        user.LastName = dto.LastName;
        user.PhoneNumber = dto.PhoneNumber;
        user.BirthDate = dto.BirthDate;
        user.UpdatedAt = DateTimeOffset.UtcNow;

        await _users.SaveChangesAsync();

        _logger.LogInformation("User profile updated: {UserId}", userId);

        return new UserProfileResponse(
            user.Id,
            user.Email,
            user.FirstName,
            user.LastName,
            user.PhoneNumber,
            user.BirthDate,
            user.EmailConfirmed,
            user.CreatedAt,
            user.UpdatedAt
        );
    }

    public async Task RegisterAsync(RegisterRequest dto)
    {
        var exists = await _users.GetByEmailAsync(dto.Email);
        if (exists != null)
        {
            _logger.LogWarning("Registration attempt with existing email: {Email}", dto.Email);
            throw new DuplicateEmailException();
        }

        var user = new User
        {
            Email = dto.Email,
            PasswordHash = BCrypt.Net.BCrypt.HashPassword(dto.Password),
            FirstName = dto.FirstName,
            LastName = dto.LastName,
            PhoneNumber = dto.PhoneNumber,
            BirthDate = dto.BirthDate,
            EmailConfirmed = false
        };

        await _users.AddAsync(user);
        await _users.SaveChangesAsync();

        _logger.LogInformation("User registered successfully: {Email}", dto.Email);

        // Send confirmation email
        var token = GenerateSecureToken();
        var tokenHash = HashToken(token);
        var confirmToken = new EmailConfirmationToken
        {
            UserId = user.Id,
            TokenHash = tokenHash,
            ExpiresAt = DateTimeOffset.UtcNow.AddHours(24)
        };

        await _emailConfirmTokenRepo.AddAsync(confirmToken);
        await _emailConfirmTokenRepo.SaveChangesAsync();

        try
        {
            await _emailService.SendEmailConfirmationAsync(user.Email, token);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(
                ex,
                "Optional email confirmation delivery failed for {Email}. Account remains usable.",
                user.Email
            );
        }
    }

    public async Task<TokenResponse> LoginAsync(LoginRequest dto)
    {
        var user = await _users.GetByEmailAsync(dto.Email);

        if (user == null || !BCrypt.Net.BCrypt.Verify(dto.Password, user.PasswordHash))
        {
            _logger.LogWarning("Failed login attempt for email: {Email}", dto.Email);
            throw new InvalidCredentialsException();
        }

        var refresh = _tokens.GenerateRefreshTokenString();
        var refreshHash = _tokens.HashRefreshToken(refresh.token);

        var rt = new RefreshToken
        {
            UserId = user.Id,
            TokenHash = refreshHash,
            Jti = refresh.jti,
            IssuedAt = DateTimeOffset.UtcNow,
            ExpiresAt = _tokens.GetRefreshExpiry(),
            DeviceInfo = dto.DeviceInfo
        };

        await _refreshRepo.AddAsync(rt);
        await _refreshRepo.SaveChangesAsync();

        var access = _tokens.GenerateAccessToken(user, refresh.jti);

        _logger.LogInformation("User logged in: {UserId} from device: {Device}", user.Id, dto.DeviceInfo ?? "Unknown");

        return new TokenResponse(
            access,
            refresh.token,
            (int)TimeSpan.FromMinutes(_jwtOptions.AccessTokenMinutes).TotalSeconds
        );
    }

    public async Task<TokenResponse> RefreshAsync(RefreshRequest dto)
    {
        var hashed = _tokens.HashRefreshToken(dto.RefreshToken);
        var rt = await _refreshRepo.GetByHashAsync(hashed);

        if (rt == null)
        {
            _logger.LogWarning("Refresh attempt with non-existent token");
            throw new InvalidTokenException();
        }

        if (rt.Revoked)
        {
            _logger.LogWarning("Token reuse detected for user: {UserId}. Revoking all tokens.", rt.UserId);
            await RevokeAllUserTokensAsync(rt.UserId);
            throw new TokenReuseException();
        }

        if (rt.ExpiresAt < DateTimeOffset.UtcNow)
        {
            _logger.LogInformation("Expired token refresh attempt for user: {UserId}", rt.UserId);
            throw new TokenExpiredException();
        }

        var newRefresh = _tokens.GenerateRefreshTokenString();
        await _refreshRepo.RevokeAsync(rt, newRefresh.jti);

        var newHash = _tokens.HashRefreshToken(newRefresh.token);

        var newRt = new RefreshToken
        {
            UserId = rt.UserId,
            TokenHash = newHash,
            Jti = newRefresh.jti,
            IssuedAt = DateTimeOffset.UtcNow,
            ExpiresAt = _tokens.GetRefreshExpiry(),
            DeviceInfo = rt.DeviceInfo
        };

        await _refreshRepo.AddAsync(newRt);
        await _refreshRepo.SaveChangesAsync();

        var access = _tokens.GenerateAccessToken(rt.User, newRefresh.jti);

        _logger.LogInformation("Token refreshed for user: {UserId}", rt.UserId);

        return new TokenResponse(
            access,
            newRefresh.token,
            (int)TimeSpan.FromMinutes(_jwtOptions.AccessTokenMinutes).TotalSeconds
        );
    }

    public async Task LogoutAsync(RefreshRequest dto)
    {
        var hashed = _tokens.HashRefreshToken(dto.RefreshToken);
        var rt = await _refreshRepo.GetByHashAsync(hashed);

        if (rt == null)
        {
            _logger.LogWarning("Logout attempt with invalid token");
            throw new InvalidTokenException();
        }

        await _refreshRepo.RevokeAsync(rt);
        await _refreshRepo.SaveChangesAsync();

        _logger.LogInformation("User logged out: {UserId}", rt.UserId);
    }

    public async Task ResendConfirmationEmailAsync(ResendConfirmationRequest dto)
    {
        var user = await _users.GetByEmailAsync(dto.Email);
        if (user == null)
        {
            _logger.LogWarning("Confirmation email resend attempt for non-existent user: {Email}", dto.Email);
            return;
        }

        if (user.EmailConfirmed)
        {
            _logger.LogInformation("Confirmation email resend attempt for already confirmed user: {Email}", dto.Email);
            return;
        }

        var token = GenerateSecureToken();
        var tokenHash = HashToken(token);

        var confirmToken = new EmailConfirmationToken
        {
            UserId = user.Id,
            TokenHash = tokenHash,
            ExpiresAt = DateTimeOffset.UtcNow.AddHours(24)
        };

        await _emailConfirmTokenRepo.AddAsync(confirmToken);
        await _emailConfirmTokenRepo.SaveChangesAsync();

        await SendEmailOrThrow(
            () => _emailService.SendEmailConfirmationAsync(user.Email, token),
            "Confirmation email resend failed",
            user.Email
        );

        _logger.LogInformation("Confirmation email resent to: {Email}", dto.Email);
    }

    public async Task ConfirmEmailAsync(ConfirmEmailRequest dto)
    {
        var user = await _users.GetByEmailAsync(dto.Email);
        if (user == null)
        {
            _logger.LogWarning("Email confirmation attempt for non-existent user: {Email}", dto.Email);
            throw new InvalidTokenException();
        }

        if (user.EmailConfirmed)
        {
            _logger.LogInformation("Email confirmation attempt for already confirmed user: {Email}", dto.Email);
            return;
        }

        var tokenHash = HashToken(dto.Token);
        var token = await _emailConfirmTokenRepo.GetValidTokenAsync(user.Id, tokenHash);

        if (token == null)
        {
            _logger.LogWarning("Invalid or expired confirmation token for user: {Email}", dto.Email);
            throw new InvalidTokenException();
        }

        user.EmailConfirmed = true;
        await _users.SaveChangesAsync();

        await _emailConfirmTokenRepo.DeleteAsync(token);
        await _emailConfirmTokenRepo.SaveChangesAsync();

        _logger.LogInformation("Email confirmed for user: {Email}", dto.Email);
    }

    public async Task ForgotPasswordAsync(ForgotPasswordRequest dto)
    {
        var user = await _users.GetByEmailAsync(dto.Email);
        if (user == null)
        {
            _logger.LogWarning("Password reset requested for non-existent user: {Email}", dto.Email);
            return;
        }

        var token = GenerateSecureToken();
        var tokenHash = HashToken(token);

        var resetToken = new PasswordResetToken
        {
            UserId = user.Id,
            TokenHash = tokenHash,
            ExpiresAt = DateTimeOffset.UtcNow.AddHours(1)
        };

        await _passwordResetTokenRepo.AddAsync(resetToken);
        await _passwordResetTokenRepo.SaveChangesAsync();

        await SendEmailOrThrow(
            () => _emailService.SendPasswordResetAsync(user.Email, token),
            "Password reset email delivery failed",
            user.Email
        );

        _logger.LogInformation("Password reset email sent to: {Email}", dto.Email);
    }

    public async Task ResetPasswordAsync(ResetPasswordRequest dto)
    {
        var user = await _users.GetByEmailAsync(dto.Email);
        if (user == null)
        {
            _logger.LogWarning("Password reset attempt for non-existent user: {Email}", dto.Email);
            throw new InvalidTokenException();
        }

        var tokenHash = HashToken(dto.Token);
        var token = await _passwordResetTokenRepo.GetValidTokenAsync(user.Id, tokenHash);

        if (token == null)
        {
            _logger.LogWarning("Invalid or expired password reset token for user: {Email}", dto.Email);
            throw new InvalidTokenException();
        }

        user.PasswordHash = BCrypt.Net.BCrypt.HashPassword(dto.NewPassword);
        await _users.SaveChangesAsync();

        await _passwordResetTokenRepo.DeleteAsync(token);
        await _passwordResetTokenRepo.SaveChangesAsync();

        await RevokeAllUserTokensAsync(user.Id);

        _logger.LogInformation("Password reset successfully for user: {Email}", dto.Email);
    }

    private async Task RevokeAllUserTokensAsync(Guid userId)
    {
        var tokens = await _refreshRepo.GetByUserIdAsync(userId);
        foreach (var token in tokens)
        {
            await _refreshRepo.RevokeAsync(token);
        }
        await _refreshRepo.SaveChangesAsync();

        _logger.LogInformation("All tokens revoked for user: {UserId}", userId);
    }

    private string GenerateSecureToken()
    {
        var bytes = new byte[32];
        using var rng = RandomNumberGenerator.Create();
        rng.GetBytes(bytes);
        return Convert.ToBase64String(bytes);
    }

    private string HashToken(string token)
    {
        using var sha = SHA256.Create();
        var bytes = Encoding.UTF8.GetBytes(token);
        var hash = sha.ComputeHash(bytes);
        return Convert.ToBase64String(hash);
    }

    private async Task SendEmailOrThrow(Func<Task> sendEmail, string operation, string email)
    {
        try
        {
            await sendEmail();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "{Operation} for {Email}", operation, email);
            throw new EmailDeliveryException();
        }
    }
}
