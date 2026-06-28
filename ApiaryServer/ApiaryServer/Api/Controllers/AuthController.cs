using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Application.Exceptions;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.RateLimiting;
using System;
using System.Net;
using System.Threading.Tasks;

namespace ApiaryServer.Api.Controllers
{
    [ApiController]
    [Route("auth")]
    public class AuthController : ApiControllerBase
    {
        // Deep link that reopens the Android app from browser-rendered email pages.
        private const string AndroidOpenAppIntent = "intent://open#Intent;scheme=beesmart;package=com.example.beesmart;end";

        private readonly IAuthService _authService;

        public AuthController(IAuthService authService)
        {
            _authService = authService;
        }

        [Authorize]
        [HttpGet("getusers")]
        public IActionResult GetUsers()
        {
            return Forbid();
        }

        /// <summary>
        /// Get current user's profile
        /// </summary>
        [Authorize]
        [HttpGet("profile")]
        public async Task<IActionResult> GetProfile()
        {
            if (TryGetUserId() is not { } userId)
            {
                return Unauthorized(new { error = "Invalid token" });
            }

            try
            {
                var profile = await _authService.GetUserProfileAsync(userId);
                return Ok(profile);
            }
            catch (UserNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Update current user's profile (email cannot be changed)
        /// </summary>
        [Authorize]
        [HttpPut("profile")]
        public async Task<IActionResult> UpdateProfile([FromBody] UpdateProfileRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            if (TryGetUserId() is not { } userId)
            {
                return Unauthorized(new { error = "Invalid token" });
            }

            try
            {
                var profile = await _authService.UpdateUserProfileAsync(userId, dto);
                return Ok(profile);
            }
            catch (UserNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Register a new user
        /// </summary>
        [HttpPost("register")]
        public async Task<IActionResult> Register([FromBody] RegisterRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                await _authService.RegisterAsync(dto);
                return Ok(new { message = "Cont creat cu succes." });
            }
            catch (DuplicateEmailException ex)
            {
                return Conflict(new { error = ex.Message });
            }
            catch (EmailDeliveryException)
            {
                return StatusCode(503, new
                {
                    error = "Email service unavailable",
                    message = "Contul a fost creat, dar emailul de verificare nu a putut fi trimis. Incearca retrimiterea emailului mai tarziu."
                });
            }
        }

        /// <summary>
        /// Login with email and password
        /// </summary>
        [HttpPost("login")]
        [EnableRateLimiting("login")]
        public async Task<IActionResult> Login([FromBody] LoginRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                var response = await _authService.LoginAsync(dto);
                return Ok(response);
            }
            catch (InvalidCredentialsException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
            catch (EmailNotConfirmedException ex)
            {
                return StatusCode(403, new { error = ex.Message });
            }
        }

        /// <summary>
        /// Refresh access token using refresh token
        /// </summary>
        [HttpPost("refresh")]
        public async Task<IActionResult> Refresh([FromBody] RefreshRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                var response = await _authService.RefreshAsync(dto);
                return Ok(response);
            }
            catch (InvalidTokenException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
            catch (TokenReuseException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
            catch (TokenExpiredException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Logout and revoke refresh token
        /// </summary>
        [HttpPost("logout")]
        public async Task<IActionResult> Logout([FromBody] RefreshRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                await _authService.LogoutAsync(dto);
                return Ok(new { message = "Logged out successfully" });
            }
            catch (InvalidTokenException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Resend email confirmation
        /// </summary>
        [HttpPost("resend-confirmation")]
        public async Task<IActionResult> ResendConfirmation([FromBody] ResendConfirmationRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                await _authService.ResendConfirmationEmailAsync(dto);
                return Ok(new { message = "If the email exists, a confirmation email has been sent." });
            }
            catch (EmailDeliveryException)
            {
                return StatusCode(503, new
                {
                    error = "Email service unavailable",
                    message = "Emailul de verificare nu a putut fi trimis momentan. Incearca din nou mai tarziu."
                });
            }
        }

        /// <summary>
        /// Confirm email with token
        /// </summary>
        [HttpPost("confirm-email")]
        public async Task<IActionResult> ConfirmEmail([FromBody] ConfirmEmailRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                await _authService.ConfirmEmailAsync(dto);
                return Ok(new { message = "Email confirmed successfully" });
            }
            catch (InvalidTokenException ex)
            {
                return BadRequest(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Confirm email from a normal browser link. Useful for email clients that block custom app schemes.
        /// </summary>
        [HttpGet("confirm-email-link")]
        public async Task<IActionResult> ConfirmEmailLink([FromQuery] string? token, [FromQuery] string? email)
        {
            if (string.IsNullOrWhiteSpace(token) || string.IsNullOrWhiteSpace(email))
            {
                return ConfirmationPage(
                    "Link invalid",
                    "Linkul de confirmare nu contine toate datele necesare.",
                    false
                );
            }

            try
            {
                await _authService.ConfirmEmailAsync(new ConfirmEmailRequest(token, email));

                return ConfirmationPage(
                    "Email confirmat",
                    "Contul tau BeeSmart a fost activat. Poti deschide aplicatia si te poti autentifica.",
                    true
                );
            }
            catch (InvalidTokenException)
            {
                return ConfirmationPage(
                    "Link expirat sau deja folosit",
                    "Cere un nou email de activare din aplicatia BeeSmart.",
                    false
                );
            }
        }

        /// <summary>
        /// Request password reset email
        /// </summary>
        [HttpPost("forgot-password")]
        public async Task<IActionResult> ForgotPassword([FromBody] ForgotPasswordRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                await _authService.ForgotPasswordAsync(dto);
                return Ok(new { message = "If the email exists, a password reset link has been sent." });
            }
            catch (EmailDeliveryException)
            {
                return StatusCode(503, new
                {
                    error = "Email service unavailable",
                    message = "Emailul de resetare nu a putut fi trimis momentan. Incearca din nou mai tarziu."
                });
            }
        }

        /// <summary>
        /// Reset password with token
        /// </summary>
        [HttpPost("reset-password")]
        public async Task<IActionResult> ResetPassword([FromBody] ResetPasswordRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                await _authService.ResetPasswordAsync(dto);
                return Ok(new { message = "Password reset successfully" });
            }
            catch (InvalidTokenException ex)
            {
                return BadRequest(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Render a browser password reset form. Useful for email clients that block custom app schemes.
        /// </summary>
        [HttpGet("reset-password-link")]
        public IActionResult ResetPasswordLink([FromQuery] string? token, [FromQuery] string? email)
        {
            if (string.IsNullOrWhiteSpace(token) || string.IsNullOrWhiteSpace(email))
            {
                return PasswordResetResultPage(
                    "Link invalid",
                    "Linkul de resetare nu contine toate datele necesare.",
                    false
                );
            }

            return PasswordResetFormPage(token, email);
        }

        /// <summary>
        /// Reset password from the browser form.
        /// </summary>
        [HttpPost("reset-password-link")]
        [Consumes("application/x-www-form-urlencoded")]
        public async Task<IActionResult> ResetPasswordLinkSubmit(
            [FromForm] string? token,
            [FromForm] string? email,
            [FromForm] string? newPassword,
            [FromForm] string? confirmPassword)
        {
            if (string.IsNullOrWhiteSpace(token) || string.IsNullOrWhiteSpace(email))
            {
                return PasswordResetResultPage(
                    "Link invalid",
                    "Linkul de resetare nu contine toate datele necesare.",
                    false
                );
            }

            if (string.IsNullOrWhiteSpace(newPassword) || newPassword.Length < 8)
            {
                return PasswordResetFormPage(token, email, "Parola trebuie sa aiba minimum 8 caractere.");
            }

            if (newPassword != confirmPassword)
            {
                return PasswordResetFormPage(token, email, "Parolele nu coincid.");
            }

            try
            {
                await _authService.ResetPasswordAsync(new ResetPasswordRequest(token, email, newPassword));

                return PasswordResetResultPage(
                    "Parola schimbata",
                    "Parola contului tau BeeSmart a fost schimbata. Poti deschide aplicatia si te poti autentifica.",
                    true
                );
            }
            catch (InvalidTokenException)
            {
                return PasswordResetResultPage(
                    "Link expirat sau deja folosit",
                    "Cere un nou email de resetare din aplicatia BeeSmart.",
                    false
                );
            }
        }

        private ContentResult ConfirmationPage(string title, string message, bool success)
        {
            var accent = success ? "#16a34a" : "#dc2626";
            var encodedTitle = WebUtility.HtmlEncode(title);
            var encodedMessage = WebUtility.HtmlEncode(message);

            var html = $@"<!doctype html>
<html lang=""ro"">
<head>
  <meta charset=""utf-8"">
  <meta name=""viewport"" content=""width=device-width, initial-scale=1"">
  <title>{encodedTitle}</title>
  <style>
    body {{ margin: 0; font-family: Arial, sans-serif; background: #fff8e7; color: #1f2937; }}
    main {{ min-height: 100vh; display: grid; place-items: center; padding: 24px; box-sizing: border-box; }}
    section {{ width: 100%; max-width: 440px; background: white; border: 1px solid #f3d27a; border-radius: 12px; padding: 28px; box-shadow: 0 12px 30px rgba(31, 41, 55, .10); }}
    h1 {{ margin: 0 0 12px; color: {accent}; font-size: 26px; }}
    p {{ margin: 0 0 24px; line-height: 1.5; }}
    a {{ display: inline-block; background: #f5a400; color: white; text-decoration: none; font-weight: 700; padding: 12px 18px; border-radius: 8px; }}
  </style>
</head>
<body>
  <main>
    <section>
      <h1>{encodedTitle}</h1>
      <p>{encodedMessage}</p>
      <a href=""{AndroidOpenAppIntent}"">Deschide BeeSmart</a>
    </section>
  </main>
</body>
</html>";

            return Content(html, "text/html; charset=utf-8");
        }

        private ContentResult PasswordResetFormPage(string token, string email, string? error = null)
        {
            var encodedToken = WebUtility.HtmlEncode(token);
            var encodedEmail = WebUtility.HtmlEncode(email);
            var encodedError = WebUtility.HtmlEncode(error);
            var errorHtml = string.IsNullOrWhiteSpace(error)
                ? string.Empty
                : $@"<p class=""error"">{encodedError}</p>";

            var html = $@"<!doctype html>
<html lang=""ro"">
<head>
  <meta charset=""utf-8"">
  <meta name=""viewport"" content=""width=device-width, initial-scale=1"">
  <title>Resetare parola</title>
  <style>
    body {{ margin: 0; font-family: Arial, sans-serif; background: #fff8e7; color: #1f2937; }}
    main {{ min-height: 100vh; display: grid; place-items: center; padding: 24px; box-sizing: border-box; }}
    section {{ width: 100%; max-width: 440px; background: white; border: 1px solid #f3d27a; border-radius: 12px; padding: 28px; box-shadow: 0 12px 30px rgba(31, 41, 55, .10); }}
    h1 {{ margin: 0 0 12px; color: #f5a400; font-size: 26px; }}
    p {{ margin: 0 0 20px; line-height: 1.5; }}
    label {{ display: block; margin: 14px 0 6px; font-weight: 700; }}
    input {{ width: 100%; box-sizing: border-box; border: 1px solid #d1d5db; border-radius: 8px; padding: 12px; font-size: 16px; }}
    button {{ margin-top: 20px; width: 100%; border: 0; background: #f5a400; color: white; font-weight: 700; padding: 12px 18px; border-radius: 8px; font-size: 16px; }}
    .error {{ color: #dc2626; font-weight: 700; margin-bottom: 12px; }}
  </style>
</head>
<body>
  <main>
    <section>
      <h1>Resetare parola</h1>
      <p>Introdu parola noua pentru contul BeeSmart asociat adresei {encodedEmail}.</p>
      {errorHtml}
      <form method=""post"" action=""/auth/reset-password-link"">
        <input type=""hidden"" name=""token"" value=""{encodedToken}"">
        <input type=""hidden"" name=""email"" value=""{encodedEmail}"">
        <label for=""newPassword"">Parola noua</label>
        <input id=""newPassword"" name=""newPassword"" type=""password"" minlength=""8"" autocomplete=""new-password"" required>
        <label for=""confirmPassword"">Confirma parola</label>
        <input id=""confirmPassword"" name=""confirmPassword"" type=""password"" minlength=""8"" autocomplete=""new-password"" required>
        <button type=""submit"">Schimba parola</button>
      </form>
    </section>
  </main>
</body>
</html>";

            return Content(html, "text/html; charset=utf-8");
        }

        private ContentResult PasswordResetResultPage(string title, string message, bool success)
        {
            var accent = success ? "#16a34a" : "#dc2626";
            var encodedTitle = WebUtility.HtmlEncode(title);
            var encodedMessage = WebUtility.HtmlEncode(message);

            var html = $@"<!doctype html>
<html lang=""ro"">
<head>
  <meta charset=""utf-8"">
  <meta name=""viewport"" content=""width=device-width, initial-scale=1"">
  <title>{encodedTitle}</title>
  <style>
    body {{ margin: 0; font-family: Arial, sans-serif; background: #fff8e7; color: #1f2937; }}
    main {{ min-height: 100vh; display: grid; place-items: center; padding: 24px; box-sizing: border-box; }}
    section {{ width: 100%; max-width: 440px; background: white; border: 1px solid #f3d27a; border-radius: 12px; padding: 28px; box-shadow: 0 12px 30px rgba(31, 41, 55, .10); }}
    h1 {{ margin: 0 0 12px; color: {accent}; font-size: 26px; }}
    p {{ margin: 0 0 24px; line-height: 1.5; }}
    a {{ display: inline-block; background: #f5a400; color: white; text-decoration: none; font-weight: 700; padding: 12px 18px; border-radius: 8px; }}
  </style>
</head>
<body>
  <main>
    <section>
      <h1>{encodedTitle}</h1>
      <p>{encodedMessage}</p>
      <a href=""{AndroidOpenAppIntent}"">Deschide BeeSmart</a>
    </section>
  </main>
</body>
</html>";

            return Content(html, "text/html; charset=utf-8");
        }
    }
}
