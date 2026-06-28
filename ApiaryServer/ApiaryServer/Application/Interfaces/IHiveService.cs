using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using ApiaryServer.Application.DTOs;

namespace ApiaryServer.Application.Interfaces
{
    public interface IHiveService
    {
        Task<IEnumerable<HiveResponse>> GetAllHivesAsync(Guid userId);
        Task<IEnumerable<HiveResponse>> GetHivesByApiaryIdAsync(Guid apiaryId, Guid userId);
        Task<HiveResponse> GetHiveByIdAsync(Guid id, Guid userId);
        Task<HiveResponse> CreateHiveAsync(Guid apiaryId, CreateHiveRequest dto, Guid userId);
        Task<HiveResponse> UpdateHiveAsync(Guid id, UpdateHiveRequest dto, Guid userId);
        Task DeleteHiveAsync(Guid id, Guid userId);
    }
}