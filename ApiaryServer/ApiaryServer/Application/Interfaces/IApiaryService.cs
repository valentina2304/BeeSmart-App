using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using ApiaryServer.Application.DTOs;

namespace ApiaryServer.Application.Interfaces;

public interface IApiaryService
{
    Task<IEnumerable<ApiaryResponse>> GetAllApiariesAsync(Guid userId);
    Task<ApiaryDetailResponse> GetApiaryByIdAsync(Guid id, Guid userId);
    Task<ApiaryResponse> CreateApiaryAsync(CreateApiaryRequest dto, Guid userId);
    Task<ApiaryResponse> UpdateApiaryAsync(Guid id, UpdateApiaryRequest dto, Guid userId);
    Task DeleteApiaryAsync(Guid id, Guid userId);
}