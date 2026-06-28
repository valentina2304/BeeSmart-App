using ApiaryServer.Application.Interfaces;
using ApiaryServer.Application.Options;
using ApiaryServer.Domain.Entities;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;
using System;
using System.Collections.Generic;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;

namespace ApiaryServer.Infrastructure.Services
{
    public class JwtService : IJwtService
    {
        private readonly JwtOptions _options;
        private readonly byte[] _secret;
        private readonly SymmetricSecurityKey _signingKey;
        private readonly SigningCredentials _signingCredentials;
        private static readonly JwtSecurityTokenHandler _tokenHandler = new JwtSecurityTokenHandler();
        private readonly int _refreshDays;

        public JwtService(IOptions<JwtOptions> options)
        {
            _options = options?.Value ?? throw new ArgumentNullException(nameof(options));
            _secret = Encoding.UTF8.GetBytes(_options.Secret ?? throw new ArgumentNullException("Jwt:Secret"));
            _signingKey = new SymmetricSecurityKey(_secret);
            _signingCredentials = new SigningCredentials(_signingKey, SecurityAlgorithms.HmacSha256);
            _refreshDays = _options.RefreshTokenDays;
        }

        public string GenerateAccessToken(User user, string jti)
        {
            var claims = new List<Claim>
            {
                new Claim(JwtRegisteredClaimNames.Sub, user.Id.ToString()),
                new Claim(JwtRegisteredClaimNames.Email, user.Email),
                new Claim(JwtRegisteredClaimNames.Jti, jti)
            };

            var now = DateTime.UtcNow;
            var token = new JwtSecurityToken(
                issuer: _options.Issuer,
                audience: _options.Audience,
                claims: claims,
                notBefore: now,
                expires: now.AddMinutes(_options.AccessTokenMinutes),
                signingCredentials: _signingCredentials
            );

            return _tokenHandler.WriteToken(token);
        }

        public (string token, string jti) GenerateRefreshTokenString()
        {
            var bytes = RandomNumberGenerator.GetBytes(64);
            var token = Convert.ToBase64String(bytes);
            var jti = Guid.NewGuid().ToString();
            return (token, jti);
        }

        public string HashRefreshToken(string refreshToken)
        {
            using var sha = SHA256.Create();
            var bytes = sha.ComputeHash(Encoding.UTF8.GetBytes(refreshToken));
            return Convert.ToBase64String(bytes);
        }

        public DateTimeOffset GetRefreshExpiry() => DateTimeOffset.UtcNow.AddDays(_refreshDays);
    }
}
