using System;
using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;

namespace ApiaryServer.Api.Controllers
{
    /// <summary>
    /// Base controller for authenticated endpoints. Centralizes reading the
    /// authenticated user id from the JWT NameIdentifier claim so individual
    /// controllers no longer duplicate the extraction logic.
    /// </summary>
    public abstract class ApiControllerBase : ControllerBase
    {
        /// <summary>
        /// Returns the current user's id, throwing UnauthorizedAccessException
        /// when the token is missing or malformed.
        /// </summary>
        protected Guid GetUserId()
        {
            if (TryGetUserId() is not { } userId)
            {
                throw new UnauthorizedAccessException("Invalid token");
            }
            return userId;
        }

        /// <summary>
        /// Returns the current user's id, or null when the token is missing or
        /// malformed. Use this when the caller prefers to map the failure to an
        /// HTTP response instead of throwing.
        /// </summary>
        protected Guid? TryGetUserId()
        {
            var userIdClaim = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
            return Guid.TryParse(userIdClaim, out var userId) ? userId : (Guid?)null;
        }
    }
}
