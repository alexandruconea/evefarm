package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.IndustryJobDao;
import com.evefarm.esi.IndustryApi;
import com.evefarm.esi.dto.IndustryJobDto;
import com.evefarm.model.IndustryJobEntry;
import com.evefarm.model.IndustryJobRow;

import java.util.HashSet;
import java.util.List;
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
        List<IndustryJobDto> jobs = industryApi.listActiveJobs(characterId, accessToken);

        Set<Integer> typeIds = new HashSet<>();
        for (IndustryJobDto job : jobs) {
            typeIds.add(job.blueprintTypeId());
            if (job.productTypeId() != null) {
                typeIds.add(job.productTypeId());
            }
        }
        typeNameCacheService.resolveTypes(typeIds);

        Set<Long> facilityIds = jobs.stream()
                .map(IndustryJobDto::facilityId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        locationNameCacheService.resolveLocations(facilityIds, accessToken);

        List<IndustryJobEntry> entries = jobs.stream()
                .map(j -> new IndustryJobEntry(j.jobId(), j.activityId(), j.status(), j.blueprintTypeId(),
                        j.productTypeId(), j.runs(), j.cost(), j.facilityId(), j.outputLocationId(),
                        j.startDate(), j.endDate()))
                .toList();
        industryJobDao.replaceForCharacter(characterId, entries);
    }

    public List<IndustryJobRow> getJobRows(Set<Long> characterIdFilter) {
        return industryJobDao.listRows(characterIdFilter);
    }
}
