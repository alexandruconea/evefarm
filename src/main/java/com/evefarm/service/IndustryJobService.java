package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.IndustryJobDao;
import com.evefarm.esi.IndustryApi;
import com.evefarm.esi.dto.IndustryJobDto;
import com.evefarm.model.IndustryJobEntry;
import com.evefarm.model.IndustryJobRow;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class IndustryJobService {

    private final AuthService authService;
    private final IndustryApi industryApi;
    private final TypeNameCacheService typeNameCacheService;
    private final LocationNameCacheService locationNameCacheService;
    private final IndustryJobDao industryJobDao;

    public IndustryJobService(AuthService authService, IndustryApi industryApi,
                               TypeNameCacheService typeNameCacheService,
                               LocationNameCacheService locationNameCacheService, IndustryJobDao industryJobDao) {
        this.authService = authService;
        this.industryApi = industryApi;
        this.typeNameCacheService = typeNameCacheService;
        this.locationNameCacheService = locationNameCacheService;
        this.industryJobDao = industryJobDao;
    }

    public void refreshJobsForCharacter(long characterId) {
        String accessToken = authService.getValidAccessToken(characterId);
        List<IndustryJobDto> jobs = industryApi.listJobsWithHistory(characterId, accessToken);
        typeNameCacheService.resolveTypes(typeIds(jobs));
        locationNameCacheService.resolveLocations(facilityIds(jobs), accessToken);
        industryJobDao.saveForCharacter(characterId, jobs.stream().map(IndustryJobService::entry).toList());
    }

    static Set<Integer> typeIds(List<IndustryJobDto> jobs) {
        Set<Integer> typeIds = new HashSet<>();
        for (IndustryJobDto job : jobs) {
            typeIds.add(job.blueprintTypeId());
            if (job.productTypeId() != null) {
                typeIds.add(job.productTypeId());
            }
        }
        return typeIds;
    }

    static Set<Long> facilityIds(List<IndustryJobDto> jobs) {
        return jobs.stream()
                .map(IndustryJobDto::facilityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    static IndustryJobEntry entry(IndustryJobDto job) {
        return new IndustryJobEntry(job.jobId(), job.activityId(), job.status(), job.blueprintTypeId(),
                job.productTypeId(), job.runs(), job.cost(), job.facilityId(), job.outputLocationId(),
                job.startDate(), job.endDate());
    }

    public List<IndustryJobRow> getJobRows(Set<Long> characterIdFilter) {
        return industryJobDao.listRows(characterIdFilter);
    }
}
