using System.Security.Cryptography;
using System.Text;
using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Exceptions;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Application.Options;
using ApiaryServer.Domain.Entities;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Xunit;

namespace ApiaryServer.Tests;

public class AuthServiceTests
{
    private static readonly Guid UserId = Guid.Parse("11111111-1111-1111-1111-111111111111");

    [Fact]
    public async Task RegisterAsync_CreatesUnconfirmedUserConfirmationTokenAndSendsEmail()
    {
        var f = Fixture();

        await f.Service.RegisterAsync(new RegisterRequest(
            "bee@example.com",
            "Password123!",
            "Ana",
            "Pop",
            "0712345678",
            DateTimeOffset.Parse("1999-01-01T00:00:00Z"),
            "android"));

        var user = Assert.Single(f.Users.Users);
        Assert.Equal("bee@example.com", user.Email);
        Assert.False(user.EmailConfirmed);
        Assert.True(BCrypt.Net.BCrypt.Verify("Password123!", user.PasswordHash));
        Assert.Single(f.ConfirmTokens.Tokens);
        Assert.Equal("bee@example.com", Assert.Single(f.Email.ConfirmationEmails).Email);
        Assert.Equal(1, f.Users.SaveChangesCalls);
        Assert.Equal(1, f.ConfirmTokens.SaveChangesCalls);
    }

    [Fact]
    public async Task RegisterAsync_DuplicateEmailThrowsWithoutSendingEmail()
    {
        var f = Fixture();
        f.Users.Users.Add(User("bee@example.com", confirmed: true));

        await Assert.ThrowsAsync<DuplicateEmailException>(() =>
            f.Service.RegisterAsync(new RegisterRequest(
                "bee@example.com",
                "Password123!",
                null,
                null,
                null,
                null,
                null)));

        Assert.Single(f.Users.Users);
        Assert.Empty(f.ConfirmTokens.Tokens);
        Assert.Empty(f.Email.ConfirmationEmails);
    }

    [Fact]
    public async Task RegisterAsync_WhenEmailDeliveryFails_KeepsCreatedAccountAndCompletes()
    {
        var f = Fixture();
        f.Email.ThrowOnConfirmation = true;

        await f.Service.RegisterAsync(new RegisterRequest(
            "bee@example.com",
            "Password123!",
            null,
            null,
            null,
            null,
            null));

        Assert.Single(f.Users.Users);
        Assert.Single(f.ConfirmTokens.Tokens);
        Assert.Empty(f.Email.ConfirmationEmails);
    }

    [Fact]
    public async Task LoginAsync_ValidConfirmedUserReturnsTokensAndStoresRefreshToken()
    {
        var f = Fixture();
        f.Users.Users.Add(User("bee@example.com", confirmed: true, password: "Password123!"));

        var response = await f.Service.LoginAsync(new LoginRequest("bee@example.com", "Password123!", "Pixel"));

        Assert.Equal("access-refresh-jti-1", response.AccessToken);
        Assert.Equal("refresh-token-1", response.RefreshToken);
        Assert.Equal(900, response.ExpiresIn);
        var token = Assert.Single(f.RefreshTokens.Tokens);
        Assert.Equal(UserId, token.UserId);
        Assert.Equal("hash:refresh-token-1", token.TokenHash);
        Assert.Equal("Pixel", token.DeviceInfo);
        Assert.Equal(1, f.RefreshTokens.SaveChangesCalls);
    }

    [Fact]
    public async Task LoginAsync_UnconfirmedUserReturnsTokensAndStoresRefreshToken()
    {
        var f = Fixture();
        f.Users.Users.Add(User("bee@example.com", confirmed: false, password: "Password123!"));

        var response = await f.Service.LoginAsync(new LoginRequest("bee@example.com", "Password123!", null));

        Assert.Equal("access-refresh-jti-1", response.AccessToken);
        Assert.Equal("refresh-token-1", response.RefreshToken);
        Assert.Single(f.RefreshTokens.Tokens);
    }

    [Fact]
    public async Task LoginAsync_InvalidPasswordThrowsAndDoesNotCreateRefreshToken()
    {
        var f = Fixture();
        f.Users.Users.Add(User("bee@example.com", confirmed: true, password: "Password123!"));

        await Assert.ThrowsAsync<InvalidCredentialsException>(() =>
            f.Service.LoginAsync(new LoginRequest("bee@example.com", "wrong", null)));

        Assert.Empty(f.RefreshTokens.Tokens);
    }

    [Fact]
    public async Task LoginAsync_UnknownEmailThrows()
    {
        var f = Fixture();

        await Assert.ThrowsAsync<InvalidCredentialsException>(() =>
            f.Service.LoginAsync(new LoginRequest("missing@example.com", "Password123!", null)));
    }

    [Fact]
    public async Task RefreshAsync_ValidTokenRevokesOldTokenAndStoresReplacement()
    {
        var f = Fixture();
        var user = User("bee@example.com", confirmed: true);
        var old = RefreshToken("old-refresh", user, expiresAt: DateTimeOffset.UtcNow.AddDays(1));
        f.Users.Users.Add(user);
        f.RefreshTokens.Tokens.Add(old);

        var response = await f.Service.RefreshAsync(new RefreshRequest("old-refresh"));

        Assert.Equal("access-refresh-jti-1", response.AccessToken);
        Assert.Equal("refresh-token-1", response.RefreshToken);
        Assert.True(old.Revoked);
        Assert.Equal("refresh-jti-1", old.ReplacedByJti);
        Assert.Contains(f.RefreshTokens.Tokens, t => t.TokenHash == "hash:refresh-token-1");
        Assert.Equal(1, f.RefreshTokens.RevokeCalls);
        Assert.True(f.RefreshTokens.SaveChangesCalls >= 1);
    }

    [Fact]
    public async Task RefreshAsync_InvalidTokenThrows()
    {
        var f = Fixture();

        await Assert.ThrowsAsync<InvalidTokenException>(() =>
            f.Service.RefreshAsync(new RefreshRequest("missing-refresh")));
    }

    [Fact]
    public async Task RefreshAsync_ExpiredTokenThrowsWithoutCreatingReplacement()
    {
        var f = Fixture();
        var user = User("bee@example.com", confirmed: true);
        f.RefreshTokens.Tokens.Add(RefreshToken("old-refresh", user, expiresAt: DateTimeOffset.UtcNow.AddMinutes(-1)));

        await Assert.ThrowsAsync<TokenExpiredException>(() =>
            f.Service.RefreshAsync(new RefreshRequest("old-refresh")));

        Assert.Single(f.RefreshTokens.Tokens);
    }

    [Fact]
    public async Task RefreshAsync_ReusedRevokedTokenRevokesAllUserTokens()
    {
        var f = Fixture();
        var user = User("bee@example.com", confirmed: true);
        var reused = RefreshToken("old-refresh", user, expiresAt: DateTimeOffset.UtcNow.AddDays(1), revoked: true);
        var other = RefreshToken("other-refresh", user, expiresAt: DateTimeOffset.UtcNow.AddDays(1));
        f.RefreshTokens.Tokens.AddRange(new[] { reused, other });

        await Assert.ThrowsAsync<TokenReuseException>(() =>
            f.Service.RefreshAsync(new RefreshRequest("old-refresh")));

        Assert.All(f.RefreshTokens.Tokens, token => Assert.True(token.Revoked));
    }

    [Fact]
    public async Task LogoutAsync_ValidTokenRevokesIt()
    {
        var f = Fixture();
        var user = User("bee@example.com", confirmed: true);
        var token = RefreshToken("refresh", user, expiresAt: DateTimeOffset.UtcNow.AddDays(1));
        f.RefreshTokens.Tokens.Add(token);

        await f.Service.LogoutAsync(new RefreshRequest("refresh"));

        Assert.True(token.Revoked);
        Assert.Equal(1, f.RefreshTokens.RevokeCalls);
    }

    [Fact]
    public async Task LogoutAsync_InvalidTokenThrows()
    {
        var f = Fixture();

        await Assert.ThrowsAsync<InvalidTokenException>(() =>
            f.Service.LogoutAsync(new RefreshRequest("missing")));
    }

    [Fact]
    public async Task ConfirmEmailAsync_ValidTokenMarksUserConfirmedAndDeletesToken()
    {
        var f = Fixture();
        var user = User("bee@example.com", confirmed: false);
        var token = new EmailConfirmationToken
        {
            UserId = user.Id,
            User = user,
            TokenHash = HashToken("confirm-token"),
            ExpiresAt = DateTimeOffset.UtcNow.AddHours(1)
        };
        f.Users.Users.Add(user);
        f.ConfirmTokens.Tokens.Add(token);

        await f.Service.ConfirmEmailAsync(new ConfirmEmailRequest("confirm-token", "bee@example.com"));

        Assert.True(user.EmailConfirmed);
        Assert.Empty(f.ConfirmTokens.Tokens);
        Assert.Equal(1, f.Users.SaveChangesCalls);
        Assert.Equal(1, f.ConfirmTokens.DeleteCalls);
    }

    [Fact]
    public async Task ConfirmEmailAsync_AlreadyConfirmedUserDoesNotRequireToken()
    {
        var f = Fixture();
        f.Users.Users.Add(User("bee@example.com", confirmed: true));

        await f.Service.ConfirmEmailAsync(new ConfirmEmailRequest("anything", "bee@example.com"));

        Assert.Empty(f.ConfirmTokens.Tokens);
        Assert.Equal(0, f.ConfirmTokens.DeleteCalls);
    }

    [Fact]
    public async Task ConfirmEmailAsync_InvalidTokenThrows()
    {
        var f = Fixture();
        f.Users.Users.Add(User("bee@example.com", confirmed: false));

        await Assert.ThrowsAsync<InvalidTokenException>(() =>
            f.Service.ConfirmEmailAsync(new ConfirmEmailRequest("bad-token", "bee@example.com")));
    }

    [Fact]
    public async Task ForgotPasswordAsync_ExistingUserStoresResetTokenAndSendsEmail()
    {
        var f = Fixture();
        f.Users.Users.Add(User("bee@example.com", confirmed: true));

        await f.Service.ForgotPasswordAsync(new ForgotPasswordRequest("bee@example.com"));

        Assert.Single(f.ResetTokens.Tokens);
        Assert.Equal("bee@example.com", Assert.Single(f.Email.PasswordResetEmails).Email);
    }

    [Fact]
    public async Task ForgotPasswordAsync_UnknownUserReturnsWithoutTokenOrEmail()
    {
        var f = Fixture();

        await f.Service.ForgotPasswordAsync(new ForgotPasswordRequest("missing@example.com"));

        Assert.Empty(f.ResetTokens.Tokens);
        Assert.Empty(f.Email.PasswordResetEmails);
    }

    [Fact]
    public async Task ResetPasswordAsync_ValidTokenChangesPasswordDeletesTokenAndRevokesSessions()
    {
        var f = Fixture();
        var user = User("bee@example.com", confirmed: true, password: "OldPassword123!");
        var resetToken = new PasswordResetToken
        {
            UserId = user.Id,
            User = user,
            TokenHash = HashToken("reset-token"),
            ExpiresAt = DateTimeOffset.UtcNow.AddHours(1)
        };
        f.Users.Users.Add(user);
        f.ResetTokens.Tokens.Add(resetToken);
        f.RefreshTokens.Tokens.Add(RefreshToken("active-refresh", user, expiresAt: DateTimeOffset.UtcNow.AddDays(1)));

        await f.Service.ResetPasswordAsync(new ResetPasswordRequest("reset-token", "bee@example.com", "NewPassword123!"));

        Assert.True(BCrypt.Net.BCrypt.Verify("NewPassword123!", user.PasswordHash));
        Assert.Empty(f.ResetTokens.Tokens);
        Assert.All(f.RefreshTokens.Tokens, token => Assert.True(token.Revoked));
    }

    [Fact]
    public async Task ResetPasswordAsync_InvalidTokenThrowsWithoutChangingPassword()
    {
        var f = Fixture();
        var user = User("bee@example.com", confirmed: true, password: "OldPassword123!");
        f.Users.Users.Add(user);

        await Assert.ThrowsAsync<InvalidTokenException>(() =>
            f.Service.ResetPasswordAsync(new ResetPasswordRequest("bad", "bee@example.com", "NewPassword123!")));

        Assert.True(BCrypt.Net.BCrypt.Verify("OldPassword123!", user.PasswordHash));
    }

    private static AuthFixture Fixture()
    {
        var users = new FakeUserRepository();
        var refreshTokens = new FakeRefreshTokenRepository();
        var confirmTokens = new FakeEmailConfirmationTokenRepository();
        var resetTokens = new FakePasswordResetTokenRepository();
        var jwt = new FakeJwtService();
        var email = new FakeEmailService();
        var service = new AuthService(
            users,
            refreshTokens,
            confirmTokens,
            resetTokens,
            jwt,
            email,
            new TestLogger<AuthService>(),
            Options.Create(new JwtOptions { AccessTokenMinutes = 15, RefreshTokenDays = 30 }));

        return new AuthFixture(service, users, refreshTokens, confirmTokens, resetTokens, jwt, email);
    }

    private static User User(string email, bool confirmed, string password = "Password123!") =>
        new()
        {
            Id = UserId,
            Email = email,
            PasswordHash = BCrypt.Net.BCrypt.HashPassword(password),
            FirstName = "Ana",
            LastName = "Pop",
            EmailConfirmed = confirmed,
            CreatedAt = DateTimeOffset.Parse("2026-01-01T00:00:00Z")
        };

    private static RefreshToken RefreshToken(
        string rawToken,
        User user,
        DateTimeOffset expiresAt,
        bool revoked = false) =>
        new()
        {
            Id = Guid.NewGuid(),
            UserId = user.Id,
            User = user,
            TokenHash = $"hash:{rawToken}",
            Jti = $"jti-{rawToken}",
            IssuedAt = DateTimeOffset.UtcNow.AddMinutes(-5),
            ExpiresAt = expiresAt,
            Revoked = revoked
        };

    private static string HashToken(string token)
    {
        using var sha = SHA256.Create();
        return Convert.ToBase64String(sha.ComputeHash(Encoding.UTF8.GetBytes(token)));
    }

    private sealed record AuthFixture(
        AuthService Service,
        FakeUserRepository Users,
        FakeRefreshTokenRepository RefreshTokens,
        FakeEmailConfirmationTokenRepository ConfirmTokens,
        FakePasswordResetTokenRepository ResetTokens,
        FakeJwtService Jwt,
        FakeEmailService Email);

    private sealed class FakeUserRepository : IUserRepository
    {
        public List<User> Users { get; } = new();
        public int SaveChangesCalls { get; private set; }

        public Task<IEnumerable<User>> GetAllAsync() => Task.FromResult(Users.AsEnumerable());
        public Task<User?> GetByEmailAsync(string email) =>
            Task.FromResult(Users.SingleOrDefault(u => u.Email.Equals(email, StringComparison.OrdinalIgnoreCase)));
        public Task<User?> GetByIdAsync(Guid id) => Task.FromResult(Users.SingleOrDefault(u => u.Id == id));

        public Task AddAsync(User user)
        {
            Users.Add(user);
            return Task.CompletedTask;
        }

        public Task SaveChangesAsync()
        {
            SaveChangesCalls++;
            return Task.CompletedTask;
        }
    }

    private sealed class FakeRefreshTokenRepository : IRefreshTokenRepository
    {
        public List<RefreshToken> Tokens { get; } = new();
        public int SaveChangesCalls { get; private set; }
        public int RevokeCalls { get; private set; }

        public Task<RefreshToken?> GetByHashAsync(string tokenHash) =>
            Task.FromResult(Tokens.SingleOrDefault(t => t.TokenHash == tokenHash));

        public Task<IEnumerable<RefreshToken>> GetByUserIdAsync(Guid userId) =>
            Task.FromResult(Tokens.Where(t => t.UserId == userId && !t.Revoked).AsEnumerable());

        public Task AddAsync(RefreshToken refreshToken)
        {
            Tokens.Add(refreshToken);
            return Task.CompletedTask;
        }

        public Task SaveChangesAsync()
        {
            SaveChangesCalls++;
            return Task.CompletedTask;
        }

        public Task RevokeAsync(RefreshToken rt, string? replacedByJti = null)
        {
            RevokeCalls++;
            rt.Revoked = true;
            rt.ReplacedByJti = replacedByJti;
            return Task.CompletedTask;
        }

        public Task RevokeAllForUserAsync(Guid userId)
        {
            foreach (var token in Tokens.Where(t => t.UserId == userId))
            {
                token.Revoked = true;
            }
            return Task.CompletedTask;
        }
    }

    private sealed class FakeEmailConfirmationTokenRepository : IEmailConfirmationTokenRepository
    {
        public List<EmailConfirmationToken> Tokens { get; } = new();
        public int SaveChangesCalls { get; private set; }
        public int DeleteCalls { get; private set; }

        public Task<EmailConfirmationToken?> GetByHashAsync(string tokenHash) =>
            Task.FromResult(Tokens.SingleOrDefault(t => t.TokenHash == tokenHash));

        public Task<EmailConfirmationToken?> GetValidTokenAsync(Guid userId, string tokenHash) =>
            Task.FromResult(Tokens.SingleOrDefault(t =>
                t.UserId == userId &&
                t.TokenHash == tokenHash &&
                t.ExpiresAt > DateTimeOffset.UtcNow));

        public Task AddAsync(EmailConfirmationToken token)
        {
            Tokens.Add(token);
            return Task.CompletedTask;
        }

        public Task DeleteAsync(EmailConfirmationToken token)
        {
            DeleteCalls++;
            Tokens.Remove(token);
            return Task.CompletedTask;
        }

        public Task SaveChangesAsync()
        {
            SaveChangesCalls++;
            return Task.CompletedTask;
        }
    }

    private sealed class FakePasswordResetTokenRepository : IPasswordResetTokenRepository
    {
        public List<PasswordResetToken> Tokens { get; } = new();
        public int SaveChangesCalls { get; private set; }

        public Task<PasswordResetToken?> GetByHashAsync(string tokenHash) =>
            Task.FromResult(Tokens.SingleOrDefault(t => t.TokenHash == tokenHash));

        public Task<PasswordResetToken?> GetValidTokenAsync(Guid userId, string tokenHash) =>
            Task.FromResult(Tokens.SingleOrDefault(t =>
                t.UserId == userId &&
                t.TokenHash == tokenHash &&
                t.ExpiresAt > DateTimeOffset.UtcNow));

        public Task AddAsync(PasswordResetToken token)
        {
            Tokens.Add(token);
            return Task.CompletedTask;
        }

        public Task DeleteAsync(PasswordResetToken token)
        {
            Tokens.Remove(token);
            return Task.CompletedTask;
        }

        public Task SaveChangesAsync()
        {
            SaveChangesCalls++;
            return Task.CompletedTask;
        }
    }

    private sealed class FakeJwtService : IJwtService
    {
        private int _refreshCounter;

        public string GenerateAccessToken(User user, string jti) => $"access-{jti}";

        public (string token, string jti) GenerateRefreshTokenString()
        {
            _refreshCounter++;
            return ($"refresh-token-{_refreshCounter}", $"refresh-jti-{_refreshCounter}");
        }

        public string HashRefreshToken(string refreshToken) => $"hash:{refreshToken}";
        public DateTimeOffset GetRefreshExpiry() => DateTimeOffset.UtcNow.AddDays(30);
    }

    private sealed class FakeEmailService : IEmailService
    {
        public List<(string Email, string Token)> ConfirmationEmails { get; } = new();
        public List<(string Email, string Token)> PasswordResetEmails { get; } = new();
        public bool ThrowOnConfirmation { get; set; }
        public bool ThrowOnPasswordReset { get; set; }

        public Task SendEmailConfirmationAsync(string email, string token)
        {
            if (ThrowOnConfirmation)
            {
                throw new InvalidOperationException("SMTP down");
            }

            ConfirmationEmails.Add((email, token));
            return Task.CompletedTask;
        }

        public Task SendPasswordResetAsync(string email, string token)
        {
            if (ThrowOnPasswordReset)
            {
                throw new InvalidOperationException("SMTP down");
            }

            PasswordResetEmails.Add((email, token));
            return Task.CompletedTask;
        }
    }

    private sealed class TestLogger<T> : ILogger<T>
    {
        public IDisposable? BeginScope<TState>(TState state) where TState : notnull => null;
        public bool IsEnabled(LogLevel logLevel) => false;
        public void Log<TState>(
            LogLevel logLevel,
            EventId eventId,
            TState state,
            Exception? exception,
            Func<TState, Exception?, string> formatter)
        {
        }
    }
}
