package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.ContractDao;
import com.evefarm.esi.ContractsApi;
import com.evefarm.esi.dto.ContractDto;
import com.evefarm.model.ContractEntry;
import com.evefarm.model.ContractRow;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ContractService {

    private final AuthService authService;
    private final ContractsApi contractsApi;
    private final EntityNameCacheService entityNameCacheService;
    private final LocationNameCacheService locationNameCacheService;
    private final ContractDao contractDao;

    public ContractService(AuthService authService, ContractsApi contractsApi,
                            EntityNameCacheService entityNameCacheService,
                            LocationNameCacheService locationNameCacheService, ContractDao contractDao) {
        this.authService = authService;
        this.contractsApi = contractsApi;
        this.entityNameCacheService = entityNameCacheService;
        this.locationNameCacheService = locationNameCacheService;
        this.contractDao = contractDao;
    }

    public void refreshContractsForCharacter(long characterId) {
        String accessToken = authService.getValidAccessToken(characterId);
        List<ContractDto> contracts = contractsApi.listContracts(characterId, accessToken);

        Set<Long> entityIds = new HashSet<>();
        Set<Long> locationIds = new HashSet<>();
        for (ContractDto contract : contracts) {
            if (contract.issuerId() != null) {
                entityIds.add((long) contract.issuerId());
            }
            if (contract.assigneeId() != null) {
                entityIds.add((long) contract.assigneeId());
            }
            if (contract.acceptorId() != null) {
                entityIds.add((long) contract.acceptorId());
            }
            if (contract.startLocationId() != null) {
                locationIds.add(contract.startLocationId());
            }
            if (contract.endLocationId() != null) {
                locationIds.add(contract.endLocationId());
            }
        }
        entityNameCacheService.resolveEntities(entityIds);
        locationNameCacheService.resolveLocations(locationIds, accessToken);

        List<ContractEntry> entries = contracts.stream()
                .map(c -> new ContractEntry(c.contractId(), c.type(), c.status(), c.title(), c.collateral(),
                        c.price(), c.reward(), c.volume(), c.dateIssued(), c.dateExpired(), c.dateCompleted(),
                        c.forCorporation(), c.issuerId(), c.assigneeId(), c.acceptorId(),
                        c.startLocationId(), c.endLocationId()))
                .toList();
        contractDao.replaceForCharacter(characterId, entries);
    }

    public List<ContractRow> getContractRows(Set<Long> characterIdFilter) {
        return contractDao.listRows(characterIdFilter);
    }
}
