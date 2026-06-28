using ApiaryServer.Api.Controllers;
using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Exceptions;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Domain.Entities;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using System.Security.Claims;
using Xunit;

namespace ApiaryServer.Tests;

public class AuthControllerTests
{
    private static readonly Guid UserId = Guid.Parse("11111111-1111-1111-1111-111111111111");

    [Fact]
    public void GetUsers_DoesNotExposeUserCollection()
    {
        var authService = new FakeAuthService();
        var controller = new AuthController(authService);

        var response = controller.GetUsers();

        Assert.IsType<ForbidResult>(response);
        Assert.Equal(0, authService.GetAllUsersCalls);
    }

    [Fact]
    public async Task Register_WithValidRequest_ReturnsOkAndCallsService()
    {
        var authService = new FakeAuthService();
        var controller = new AuthController(authService);

        var response = await controller.Register(new RegisterRequest(
            "bee@example.com",
            "Password123!",
            "Ana",
            "Pop",
            null,
            null,
            "Pixel"));

        Assert.IsType<OkObjectResult>(response);
        Assert.Equal(1, authService.RegisterCalls);
    }

    [Fact]
    public async Task Register_WithInvalidModel_ReturnsBadRequestWithoutCallingService()
    {
        var authService = new FakeAuthService();
        var controller = new AuthController(authService);
        controller.ModelState.AddModelError("Email", "Required");

        var response = await controller.Register(new RegisterRequest(
            "",
            "Password123!",
            null,
            null,
            null,
            null,
            null));

        Assert.IsType<BadRequestObjectResult>(response);
        Assert.Equal(0, authService.RegisterCalls);
    }

    [Fact]
    public async Task Register_WhenEmailAlreadyExists_ReturnsConflict()
    {
        var authService = new FakeAuthService
        {
            RegisterException = new DuplicateEmailException()
        };
        var controller = new AuthController(authService);

        var response = await controller.Register(new RegisterRequest(
            "bee@example.com",
            "Password123!",
            null,
            null,
            null,
            null,
            null));

        Assert.IsType<ConflictObjectResult>(response);
    }

    [Fact]
    public async Task Register_WhenEmailDeliveryFails_ReturnsServiceUnavailable()
    {
        var authService = new FakeAuthService
        {
            RegisterException = new EmailDeliveryException()
        };
        var controller = new AuthController(authService);

        var response = await controller.Register(new RegisterRequest(
            "bee@example.com",
            "Password123!",
            null,
            null,
            null,
            null,
            null));

        var status = Assert.IsType<ObjectResult>(response);
        Assert.Equal(StatusCodes.Status503ServiceUnavailable, status.StatusCode);
    }

    [Fact]
    public async Task Login_WithValidCredentials_ReturnsTokens()
    {
        var authService = new FakeAuthService
        {
            LoginResponse = new TokenResponse("access-token", "refresh-token", 900)
        };
        var controller = new AuthController(authService);

        var response = await controller.Login(new LoginRequest("bee@example.com", "Password123!", "Pixel"));

        var ok = Assert.IsType<OkObjectResult>(response);
        Assert.Same(authService.LoginResponse, ok.Value);
        Assert.Equal(1, authService.LoginCalls);
    }

    [Fact]
    public async Task Login_WithInvalidCredentials_ReturnsUnauthorized()
    {
        var authService = new FakeAuthService
        {
            LoginException = new InvalidCredentialsException()
        };
        var controller = new AuthController(authService);

        var response = await controller.Login(new LoginRequest("bee@example.com", "wrong", null));

        Assert.IsType<UnauthorizedObjectResult>(response);
    }

    [Fact]
    public async Task Login_WithUnconfirmedEmail_ReturnsForbiddenStatus()
    {
        var authService = new FakeAuthService
        {
            LoginException = new EmailNotConfirmedException()
        };
        var controller = new AuthController(authService);

        var response = await controller.Login(new LoginRequest("bee@example.com", "Password123!", null));

        var forbidden = Assert.IsType<ObjectResult>(response);
        Assert.Equal(StatusCodes.Status403Forbidden, forbidden.StatusCode);
    }

    [Fact]
    public async Task Refresh_WithTokenReuse_ReturnsUnauthorized()
    {
        var authService = new FakeAuthService
        {
            RefreshException = new TokenReuseException()
        };
        var controller = new AuthController(authService);

        var response = await controller.Refresh(new RefreshRequest("refresh-token"));

        Assert.IsType<UnauthorizedObjectResult>(response);
    }

    [Fact]
    public async Task GetProfile_WithMissingUserClaim_ReturnsUnauthorized()
    {
        var authService = new FakeAuthService();
        var controller = new AuthController(authService);
        SetAnonymousUser(controller);

        var response = await controller.GetProfile();

        Assert.IsType<UnauthorizedObjectResult>(response);
        Assert.Equal(0, authService.GetProfileCalls);
    }

    [Fact]
    public async Task GetProfile_WithValidUserClaim_ReturnsProfile()
    {
        var profile = new UserProfileResponse(
            UserId,
            "bee@example.com",
            "Ana",
            "Pop",
            null,
            null,
            true,
            DateTimeOffset.Parse("2026-01-01T00:00:00Z"),
            null);
        var authService = new FakeAuthService
        {
            ProfileResponse = profile
        };
        var controller = new AuthController(authService);
        SetUser(controller, UserId);

        var response = await controller.GetProfile();

        var ok = Assert.IsType<OkObjectResult>(response);
        Assert.Same(profile, ok.Value);
        Assert.Equal(1, authService.GetProfileCalls);
    }

    [Fact]
    public async Task UpdateProfile_WhenUserIsMissing_ReturnsNotFound()
    {
        var authService = new FakeAuthService
        {
            UpdateProfileException = new UserNotFoundException()
        };
        var controller = new AuthController(authService);
        SetUser(controller, UserId);

        var response = await controller.UpdateProfile(new UpdateProfileRequest("Ana", "Pop", null, null));

        Assert.IsType<NotFoundObjectResult>(response);
    }

    private static void SetUser(ControllerBase controller, Guid userId)
    {
        controller.ControllerContext = new ControllerContext
        {
            HttpContext = new DefaultHttpContext
            {
                User = new ClaimsPrincipal(new ClaimsIdentity(
                    new[] { new Claim(ClaimTypes.NameIdentifier, userId.ToString()) },
                    "TestAuth"))
            }
        };
    }

    private static void SetAnonymousUser(ControllerBase controller)
    {
        controller.ControllerContext = new ControllerContext
        {
            HttpContext = new DefaultHttpContext
            {
                User = new ClaimsPrincipal(new ClaimsIdentity())
            }
        };
    }

    private sealed class FakeAuthService : IAuthService
    {
        public int GetAllUsersCalls { get; private set; }
        public int RegisterCalls { get; private set; }
        public int LoginCalls { get; private set; }
        public int GetProfileCalls { get; private set; }

        public Exception? RegisterException { get; init; }
        public TokenResponse? LoginResponse { get; init; }
        public Exception? LoginException { get; init; }
        public Exception? RefreshException { get; init; }
        public UserProfileResponse? ProfileResponse { get; init; }
        public Exception? UpdateProfileException { get; init; }

        public Task<IEnumerable<User>> GetAllUsersAsync()
        {
            GetAllUsersCalls++;
            return Task.FromResult(Enumerable.Empty<User>());
        }

        public Task<UserProfileResponse> GetUserProfileAsync(Guid userId)
        {
            GetProfileCalls++;
            return Task.FromResult(ProfileResponse ?? new UserProfileResponse(
                userId,
                "bee@example.com",
                null,
                null,
                null,
                null,
                true,
                DateTimeOffset.UtcNow,
                null));
        }

        public Task<UserProfileResponse> UpdateUserProfileAsync(Guid userId, UpdateProfileRequest dto)
        {
            if (UpdateProfileException != null)
            {
                throw UpdateProfileException;
            }

            return Task.FromResult(new UserProfileResponse(
                userId,
                "bee@example.com",
                dto.FirstName,
                dto.LastName,
                dto.PhoneNumber,
                dto.BirthDate,
                true,
                DateTimeOffset.UtcNow,
                DateTimeOffset.UtcNow));
        }

        public Task RegisterAsync(RegisterRequest dto)
        {
            RegisterCalls++;
            if (RegisterException != null)
            {
                throw RegisterException;
            }

            return Task.CompletedTask;
        }

        public Task<TokenResponse> LoginAsync(LoginRequest dto)
        {
            LoginCalls++;
            if (LoginException != null)
            {
                throw LoginException;
            }

            return Task.FromResult(LoginResponse ?? new TokenResponse("access", "refresh", 900));
        }

        public Task<TokenResponse> RefreshAsync(RefreshRequest dto)
        {
            if (RefreshException != null)
            {
                throw RefreshException;
            }

            return Task.FromResult(new TokenResponse("access", "refresh", 900));
        }

        public Task LogoutAsync(RefreshRequest dto) =>
            throw new NotImplementedException();

        public Task ResendConfirmationEmailAsync(ResendConfirmationRequest dto) =>
            throw new NotImplementedException();

        public Task ConfirmEmailAsync(ConfirmEmailRequest dto) =>
            throw new NotImplementedException();

        public Task ForgotPasswordAsync(ForgotPasswordRequest dto) =>
            throw new NotImplementedException();

        public Task ResetPasswordAsync(ResetPasswordRequest dto) =>
            throw new NotImplementedException();
    }
}
