using ApiaryServer.Domain.Entities;
using System;

namespace ApiaryServer.Application.Interfaces
{
    public interface IJwtService
    {
        string GenerateAccessToken(User user, string jti);
        (string token, string jti) GenerateRefreshTokenString();
        string HashRefreshToken(string refreshToken);
        DateTimeOffset GetRefreshExpiry();
    }
}
