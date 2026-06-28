using System.Net;
using System.Net.Mail;
using ApiaryServer.Application.Interfaces;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;

namespace ApiaryServer.Infrastructure.Services
{
    public class EmailService : IEmailService
    {
        private readonly IConfiguration _config;
        private readonly ILogger<EmailService> _logger;
        private readonly SmtpClient _smtpClient;

        public EmailService(IConfiguration config, ILogger<EmailService> logger)
        {
            _config = config;
            _logger = logger;

            var enableSsl = bool.TryParse(_config["Smtp:EnableSsl"], out var parsedEnableSsl) && parsedEnableSsl;

            _smtpClient = new SmtpClient
            {
                Host = _config["Smtp:Host"] ?? throw new InvalidOperationException("SMTP Host not configured"),
                Port = int.Parse(_config["Smtp:Port"] ?? "587"),
                EnableSsl = enableSsl,
                DeliveryMethod = SmtpDeliveryMethod.Network,
                UseDefaultCredentials = false,
                Credentials = new NetworkCredential(
                    _config["Smtp:Username"] ?? throw new InvalidOperationException("SMTP Username not configured"),
                    _config["Smtp:Password"] ?? throw new InvalidOperationException("SMTP Password not configured")
                ),
                Timeout = 10000
            };
        }

        public async Task SendEmailConfirmationAsync(string email, string token)
        {
            var link = BuildAppLink("confirm-email", token, email);
            var htmlLink = WebUtility.HtmlEncode(link);

            var htmlBody = $@"
<!DOCTYPE html>
<html>
<head>
    <meta charset=""utf-8"">
</head>
<body style=""font-family: Arial, sans-serif; line-height: 1.6; color: #333;"">
    <div style=""max-width: 600px; margin: 0 auto; padding: 20px;"">
        <h2 style=""color: #FFA500;"">🐝 Confirmă-ți adresa de email</h2>
        <p>Bine ai venit la BeeSmart! Te rugăm să confirmi adresa de email pentru a-ți activa contul.</p>
        
        <div style=""text-align: center; margin: 30px 0;"">
            <a href=""{htmlLink}"" 
               style=""background-color: #FFA500; 
                      color: white; 
                      padding: 12px 30px; 
                      text-decoration: none; 
                      border-radius: 5px;
                      display: inline-block;
                      font-weight: bold;"">
                Confirmă Email-ul
            </a>
        </div>
        
        <p style=""font-size: 14px; color: #666;"">
            Link-ul este valabil <strong>24 de ore</strong>.
        </p>
        
        <p style=""font-size: 12px; color: #999; margin-top: 30px;"">
            Dacă nu te-ai înregistrat pe BeeSmart, ignoră acest email.
        </p>
    </div>
</body>
</html>
";

            var message = new MailMessage
            {
                From = new MailAddress(_config["Smtp:FromEmail"] ?? _config["Smtp:Username"]!, "BeeSmart App"),
                Subject = "Confirmă-ți adresa de email",
                Body = htmlBody,
                IsBodyHtml = true
            };
            message.To.Add(email);

            try
            {
                await _smtpClient.SendMailAsync(message);
                _logger.LogInformation("Email confirmation sent to {Email}", email);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error sending confirmation email to {Email}", email);
                throw;
            }
            finally
            {
                message.Dispose();
            }
        }

        public async Task SendPasswordResetAsync(string email, string token)
        {
            var link = BuildAppLink("reset-password", token, email);
            var htmlLink = WebUtility.HtmlEncode(link);

            var htmlBody = $@"
<!DOCTYPE html>
<html>
<head>
    <meta charset=""utf-8"">
</head>
<body style=""font-family: Arial, sans-serif; line-height: 1.6; color: #333;"">
    <div style=""max-width: 600px; margin: 0 auto; padding: 20px;"">
        <h2 style=""color: #FFA500;"">🔒 Resetare Parolă</h2>
        <p>Am primit o cerere de resetare a parolei pentru contul tău BeeSmart.</p>
        
        <div style=""text-align: center; margin: 30px 0;"">
            <a href=""{htmlLink}"" 
               style=""background-color: #FFA500; 
                      color: white; 
                      padding: 12px 30px; 
                      text-decoration: none; 
                      border-radius: 5px;
                      display: inline-block;
                      font-weight: bold;"">
                Resetează Parola
            </a>
        </div>
        
        <p style=""font-size: 14px; color: #666;"">
            Link-ul este valabil <strong>1 oră</strong>.
        </p>
        
        <p style=""font-size: 12px; color: #999; margin-top: 30px;"">
            Dacă nu ai solicitat resetarea parolei, ignoră acest email.
        </p>
    </div>
</body>
</html>
";

            var message = new MailMessage
            {
                From = new MailAddress(_config["Smtp:FromEmail"] ?? _config["Smtp:Username"]!, "BeeSmart App"),
                Subject = "Resetează-ți parola",
                Body = htmlBody,
                IsBodyHtml = true
            };
            message.To.Add(email);

            try
            {
                await _smtpClient.SendMailAsync(message);
                _logger.LogInformation("Password reset email sent to {Email}", email);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error sending password reset email to {Email}", email);
                throw;
            }
            finally
            {
                message.Dispose();
            }
        }

        public void Dispose()
        {
            _smtpClient?.Dispose();
        }

        private string BuildAppLink(string action, string token, string email)
        {
            var encodedToken = Uri.EscapeDataString(token);
            var encodedEmail = Uri.EscapeDataString(email);
            var query = $"token={encodedToken}&email={encodedEmail}";

            var useWebConfirmationLinks = bool.TryParse(_config["App:UseWebConfirmationLinks"], out var parsedUseWebConfirmationLinks)
                && parsedUseWebConfirmationLinks;

            if (useWebConfirmationLinks && action == "confirm-email")
            {
                var publicBaseUrl = (_config["App:PublicBaseUrl"] ?? "http://192.168.1.7:8080").TrimEnd('/');
                return $"{publicBaseUrl}/auth/confirm-email-link?{query}";
            }

            var useWebPasswordResetLinks = bool.TryParse(_config["App:UseWebPasswordResetLinks"], out var parsedUseWebPasswordResetLinks)
                && parsedUseWebPasswordResetLinks;

            if (useWebPasswordResetLinks && action == "reset-password")
            {
                var publicBaseUrl = (_config["App:PublicBaseUrl"] ?? "http://192.168.1.7:8080").TrimEnd('/');
                return $"{publicBaseUrl}/auth/reset-password-link?{query}";
            }

            var useAndroidIntentLinks = bool.TryParse(_config["App:UseAndroidIntentLinks"], out var parsedUseAndroidIntentLinks)
                && parsedUseAndroidIntentLinks;

            if (useAndroidIntentLinks)
            {
                var customScheme = _config["App:CustomScheme"] ?? "beesmart";
                var androidPackage = _config["App:AndroidPackage"] ?? "com.example.beesmart";
                var webUrl = (_config["App:WebUrl"] ?? "https://app.beesmart.ro/").TrimEnd('/');
                var fallbackUrl = Uri.EscapeDataString($"{webUrl}/{action}?{query}");

                return $"intent://{action}?{query}#Intent;scheme={customScheme};package={androidPackage};S.browser_fallback_url={fallbackUrl};end";
            }

            var appUrl = _config["App:Url"] ?? "beesmart://";
            return $"{appUrl}{action}?{query}";
        }
    }
}
